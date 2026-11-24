/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.rdfarchitect.services.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.rdfarchitect.services.archive.ArchiveFiles.json;
import static org.rdfarchitect.services.archive.ArchiveFiles.unzip;
import static org.rdfarchitect.services.archive.ArchiveFiles.zip;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.ShapesDocument;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceService;
import org.rdfarchitect.services.archive.importing.ImportWorkspaceService;
import org.rdfarchitect.services.archive.importing.InvalidWorkspaceArchiveException;
import org.rdfarchitect.services.shacl.SHACLStoringService;
import org.rdfarchitect.services.update.graph.ImportProgressListener;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** The workspace file carries the shapes documents: their text as it is and all they record. */
class WorkspaceArchiveShapesDocumentsTest {

    private static final String CORE = "core";
    private static final String EXTENSION = "extension";

    private static final String SCHEMA =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            <http://example.org/core#Thing> a rdfs:Class ; rdfs:label "Thing"@en .
            """;

    /** What a re-serialisation would not keep: comments, CRLF, order, spelling, astral text. */
    private static final String OFFICIAL =
            "@prefix sh: <http://www.w3.org/ns/shacl#> .\r\n"
                    + "@prefix ex: <http://example.org/core#> .\r\n"
                    + "\r\n"
                    + "# kept as written 😀\r\n"
                    + "ex:ThingShape   sh:targetClass ex:Thing ;\r\n"
                    + "    a sh:NodeShape ;\r\n"
                    + "    sh:message \"a \\\"quoted\\\" } message\" .\r\n";

    private static final String AUTHORED =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            <http://example.org/core#OtherShape> a sh:NodeShape .
            """;

    private static final String OTHER =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            <http://example.org/core#ThirdShape> a sh:NodeShape .
            """;

    private DatabasePort databasePort;
    private SHACLStoringService shacl;
    private ExportWorkspaceService exportService;
    private ImportWorkspaceService importService;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        shacl = new SHACLStoringService(databasePort);
        exportService = new ExportWorkspaceService(databasePort, new AppVersionResolver("1.2.3"));
        importService = new ImportWorkspaceService(databasePort);
        databasePort.createDataset("original");
        databasePort.enableEditing("original");
        try (var transaction = databasePort.beginTransaction("original", ReadWrite.WRITE)) {
            for (var schemaName : List.of(CORE, EXTENSION, "gone")) {
                transaction.createGraph(
                        schemaName, RDFParser.fromString(SCHEMA, Lang.TURTLE).toGraph());
            }
            transaction.commit("Set up the workspace");
        }
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private UUID create(String name, String sourceFile, List<String> schemas, String text) {
        return shacl.createShapesDocument("original", schemas, name, sourceFile, text, Lang.TURTLE)
                .getId();
    }

    /** One document of every kind the file has to carry. */
    private void givenDocumentsOfEveryKind() {
        create("official.ttl", "61970-600-2_Core-AP-Con-Simple-SHACL.ttl", List.of(CORE), OFFICIAL);
        var both = create("both.ttl", null, List.of(CORE, EXTENSION), AUTHORED);
        shacl.updateShapesDocument("original", both, null, false, null, null);
        create("none.ttl", null, List.of(), AUTHORED);
        create("empty.ttl", null, List.of(CORE), "");
        create("comments.ttl", null, List.of(EXTENSION), "# nothing but a comment\n");
        create("elsewhere.ttl", null, List.of("gone"), AUTHORED);
        // Deleting a schema leaves the documents naming it as they are.
        try (var transaction = databasePort.beginTransaction("original", ReadWrite.WRITE)) {
            transaction.deleteGraph("gone");
            transaction.commit("Deleted a schema");
        }
    }

    private record Recorded(
            UUID id,
            String name,
            ShapesDocument.Origin origin,
            String sourceFileName,
            boolean enabled,
            int order,
            List<String> schemas,
            byte[] text,
            long triples) {}

    private List<Recorded> documentsOf(String workspaceName) {
        try (var transaction = databasePort.beginTransaction(workspaceName, ReadWrite.READ)) {
            return transaction.shapesDocuments().values().stream()
                    .sorted(Comparator.comparing(ShapesDocument::getId))
                    .map(
                            document ->
                                    new Recorded(
                                            document.getId(),
                                            document.getName(),
                                            document.getOrigin(),
                                            document.getSourceFileName(),
                                            document.isEnabled(),
                                            document.getOrder(),
                                            document.getSchemas().stream().sorted().toList(),
                                            document.getRawText().getBytes(StandardCharsets.UTF_8),
                                            document.getGraph().size()))
                    .toList();
        }
    }

    private void importArchive(byte[] archive) {
        importService.importWorkspace(
                "imported", new ByteArrayInputStream(archive), ImportProgressListener.NOOP);
    }

    private List<String> problemsOf(Map<String, String> files) {
        return catchThrowableOfType(
                        InvalidWorkspaceArchiveException.class, () -> importArchive(zip(files)))
                .getProblems();
    }

    @Test
    void everyDocumentComesBackWithItsTextByteForByteAndAllItRecords() {
        givenDocumentsOfEveryKind();

        importArchive(exportService.exportWorkspace("original"));

        var original = documentsOf("original");
        var imported = documentsOf("imported");
        assertThat(imported).hasSize(6);
        assertThat(imported)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(original);
        assertThat(databasePort.canUndo("imported")).isFalse();
    }

    @Test
    void exportingAnImportedWorkspaceGivesTheSameDocumentFiles() {
        givenDocumentsOfEveryKind();
        var exported = unzip(exportService.exportWorkspace("original"));

        importArchive(zip(exported));
        var reexported = unzip(exportService.exportWorkspace("imported"));

        assertThat(shaclFiles(reexported)).isEqualTo(shaclFiles(exported)).hasSize(7);
    }

    private static Map<String, String> shaclFiles(Map<String, String> files) {
        var shacl = new java.util.TreeMap<String, String>();
        files.forEach(
                (path, content) -> {
                    if (path.startsWith("shacl/")) {
                        shacl.put(path, content);
                    }
                });
        return shacl;
    }

    @Test
    void theTextIsAFileOfItsOwnAndTheListSaysTheRest() {
        var id = create("official.ttl", "official.ttl", List.of(CORE), OFFICIAL);

        var files = unzip(exportService.exportWorkspace("original"));

        assertThat(files).containsEntry("shacl/" + id + ".ttl", OFFICIAL);
        var listed = (ObjectNode) ArchiveFiles.tree(files.get("shacl/documents.json")).get(0);
        assertThat(listed.get("id").asText()).isEqualTo(id.toString());
        assertThat(listed.get("name").asText()).isEqualTo("official.ttl");
        assertThat(listed.get("sourceFileName").asText()).isEqualTo("official.ttl");
        assertThat(listed.get("origin").asText()).isEqualTo("IMPORTED");
        assertThat(listed.get("enabled").asBoolean()).isTrue();
        assertThat(listed.get("order").asInt()).isZero();
        assertThat(listed.get("schemas")).hasSize(1);
        assertThat(json(files.get(WorkspaceArchiveFormat.MANIFEST)).get("formatVersion").asText())
                .isEqualTo("1.1");
    }

    @Test
    void aWorkspaceWithoutDocumentsListsNone() {
        var files = unzip(exportService.exportWorkspace("original"));

        assertThat(ArchiveFiles.tree(files.get("shacl/documents.json"))).isEmpty();
    }

    /** The file as a reader of format 1.0 sees it: without {@code shacl/}. */
    private static Map<String, String> asFormat10(Map<String, String> files) {
        files.keySet().removeIf(path -> path.startsWith("shacl/"));
        var manifest = json(files.get(WorkspaceArchiveFormat.MANIFEST));
        manifest.put("formatVersion", "1.0");
        files.put(WorkspaceArchiveFormat.MANIFEST, ArchiveFiles.json(manifest));
        return files;
    }

    private static Graph shaclOf(Map<String, String> files, String schemaName) {
        return ArchiveFiles.turtle(files.get("schemas/" + schemaName + "/shacl.ttl"));
    }

    private static Graph parsed(String... texts) {
        var graph = GraphFactory.createDefaultGraph();
        for (var text : texts) {
            RDFParser.fromString(text, Lang.TURTLE).parse(graph);
        }
        return graph;
    }

    @Test
    void eachSchemasShaclFileHoldsTheEnabledDocumentsApplyingToIt() {
        create("official.ttl", null, List.of(CORE), OFFICIAL);
        create("both.ttl", null, List.of(CORE, EXTENSION), AUTHORED);
        var off = create("off.ttl", null, List.of(CORE), OTHER);
        shacl.updateShapesDocument("original", off, null, false, null, null);
        create("none.ttl", null, List.of(), OTHER);

        var files = unzip(exportService.exportWorkspace("original"));

        assertThat(shaclOf(files, CORE).isIsomorphicWith(parsed(OFFICIAL, AUTHORED))).isTrue();
        assertThat(shaclOf(files, EXTENSION).isIsomorphicWith(parsed(AUTHORED))).isTrue();
        assertThat(shaclOf(files, "gone").isEmpty()).isTrue();
    }

    @Test
    void listsThatLookAlikeInTwoDocumentsStayTwoListsInTheShaclFile() {
        var list =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                <http://example.org/core#%s> sh:in ( <http://example.org/core#Thing> ) .
                """;
        create("a.ttl", null, List.of(CORE), list.formatted("A"));
        create("b.ttl", null, List.of(CORE), list.formatted("B"));

        var files = unzip(exportService.exportWorkspace("original"));

        assertThat(shaclOf(files, CORE).size()).isEqualTo(6);
    }

    @Test
    void aFileOfFormat10BringsEachSchemasConstraintsAsADocument() {
        create("official.ttl", null, List.of(CORE), OFFICIAL);
        var files = asFormat10(unzip(exportService.exportWorkspace("original")));

        var result =
                importService.importWorkspace(
                        "imported",
                        new ByteArrayInputStream(zip(files)),
                        ImportProgressListener.NOOP);

        assertThat(result.notices()).isEmpty();
        assertThat(result.importedSchemaNames()).containsExactlyInAnyOrder(CORE, EXTENSION, "gone");
        assertThat(documentsOf("imported"))
                .singleElement()
                .satisfies(
                        document -> {
                            assertThat(document.name()).isEqualTo("core-shacl.ttl");
                            assertThat(document.origin()).isEqualTo(ShapesDocument.Origin.IMPORTED);
                            assertThat(document.sourceFileName())
                                    .isEqualTo("schemas/core/shacl.ttl");
                            assertThat(document.enabled()).isTrue();
                            assertThat(document.order()).isZero();
                            assertThat(document.schemas()).containsExactly(CORE);
                            assertThat(new String(document.text(), StandardCharsets.UTF_8))
                                    .isEqualTo(files.get("schemas/core/shacl.ttl"));
                        });
        try (var transaction = databasePort.beginTransaction("imported", ReadWrite.READ)) {
            var document = transaction.shapesDocuments().values().iterator().next();
            assertThat(document.getGraph().isIsomorphicWith(parsed(OFFICIAL))).isTrue();
        }
    }

    @Test
    void aFileOfFormat10GivesSchemasWithoutConstraintsNoDocument() {
        var files = asFormat10(unzip(exportService.exportWorkspace("original")));

        importArchive(zip(files));

        assertThat(documentsOf("imported")).isEmpty();
    }

    @Test
    void aFileOfFormat10WhoseConstraintsCannotBeSavedIsRefused() {
        var files = asFormat10(unzip(exportService.exportWorkspace("original")));
        files.put(
                "schemas/core/shacl.ttl",
                "<http://example.org/a|b> a <http://www.w3.org/ns/shacl#NodeShape> .\n");

        assertThat(problemsOf(files))
                .singleElement()
                .asString()
                .startsWith("schemas/core/shacl.ttl: ")
                .contains("http://example.org/a|b");
    }

    @Test
    void aFileOfFormat11TakesItsDocumentsFromTheListAndNotFromTheShaclFiles() {
        create("official.ttl", null, List.of(CORE), OFFICIAL);
        var files = unzip(exportService.exportWorkspace("original"));
        files.put("schemas/core/shacl.ttl", OTHER);
        files.remove("schemas/" + EXTENSION + "/shacl.ttl");

        importArchive(zip(files));

        assertThat(documentsOf("imported"))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(documentsOf("original"));
    }

    @Test
    void aFileOfFormat11WithoutTheListIsRefused() {
        var files = unzip(exportService.exportWorkspace("original"));
        files.remove("shacl/documents.json");

        assertThat(problemsOf(files)).containsExactly("shacl/documents.json is missing.");
    }

    @Test
    void everyBrokenDocumentIsReportedAndNothingIsImported() {
        var missingText = create("missing.ttl", null, List.of(CORE), AUTHORED);
        var unreadable = create("unreadable.ttl", null, List.of(CORE), AUTHORED);
        var badIri = create("bad-iri.ttl", null, List.of(CORE), AUTHORED);
        var badSchema = create("bad-schema.ttl", null, List.of(CORE), AUTHORED);
        var files = unzip(exportService.exportWorkspace("original"));
        files.remove("shacl/" + missingText + ".ttl");
        files.put("shacl/" + unreadable + ".ttl", "<http://example.org/a> a");
        files.put("shacl/" + badIri + ".ttl", "<http://example.org/a|b> a <http://e/C> .\n");
        files.put(
                "shacl/documents.json",
                withEntry(
                        files.get("shacl/documents.json"),
                        badSchema,
                        entry -> ((ArrayNode) entry.get("schemas")).add("a;b")));

        var problems = problemsOf(files);

        assertThat(problems)
                .containsExactlyInAnyOrder(
                        "shacl/" + missingText + ".ttl is missing.",
                        problems.stream()
                                .filter(p -> p.startsWith("shacl/" + unreadable + ".ttl: "))
                                .findFirst()
                                .orElse("no problem with the unreadable text"),
                        problems.stream()
                                .filter(p -> p.startsWith("shacl/" + badIri + ".ttl: "))
                                .findFirst()
                                .orElse("no problem with the IRI"),
                        "shacl/documents.json: document "
                                + badSchema
                                + ": \"a;b\" is not a valid schema name.");
        assertThat(problems)
                .anySatisfy(problem -> assertThat(problem).contains("line: 1"))
                .anySatisfy(problem -> assertThat(problem).contains("%7C"));
        assertThat(databasePort.listDatasets()).doesNotContain("imported");
    }

    @Test
    void anEntryLackingAFieldIsReportedNamingIt() {
        var id = create("official.ttl", null, List.of(CORE), OFFICIAL);
        var files = unzip(exportService.exportWorkspace("original"));
        files.put(
                "shacl/documents.json",
                withEntry(files.get("shacl/documents.json"), id, entry -> entry.remove("enabled")));

        assertThat(problemsOf(files))
                .singleElement()
                .asString()
                .startsWith("shacl/documents.json: \"enabled\" is missing or null");
    }

    @Test
    void twoDocumentsOfOneNameAreRefused() {
        var first = create("first.ttl", null, List.of(CORE), AUTHORED);
        create("second.ttl", null, List.of(CORE), AUTHORED);
        var files = unzip(exportService.exportWorkspace("original"));
        files.put(
                "shacl/documents.json",
                withEntry(
                        files.get("shacl/documents.json"),
                        first,
                        entry -> entry.put("name", "SECOND.ttl")));

        assertThat(problemsOf(files))
                .singleElement()
                .asString()
                .containsIgnoringCase("another document is named \"second.ttl\"");
    }

    private static String withEntry(String list, UUID id, Consumer<ObjectNode> change) {
        var entries = (ArrayNode) ArchiveFiles.tree(list);
        var changed = new ArrayList<ObjectNode>();
        entries.forEach(
                entry -> {
                    var object = (ObjectNode) entry;
                    if (object.get("id").asText().equals(id.toString())) {
                        change.accept(object);
                    }
                    changed.add(object);
                });
        return ArchiveFiles.json(ArchiveFiles.JSON.valueToTree(changed));
    }
}
