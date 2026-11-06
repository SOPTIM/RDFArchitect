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

package org.rdfarchitect.services.archive.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.rdfarchitect.services.archive.ArchiveFiles.json;
import static org.rdfarchitect.services.archive.ArchiveFiles.unzip;
import static org.rdfarchitect.services.archive.ArchiveFiles.zip;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.config.AppVersionResolver;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.services.archive.ArchiveFiles;
import org.rdfarchitect.services.archive.WorkspaceArchiveFormat;
import org.rdfarchitect.services.archive.exporting.ExportWorkspaceService;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

class WorkspaceArchiveReaderTest {

    private static final String CORE = "http://example.org/graphs/core";
    private static final String EXTENSION = "http://example.org/graphs/extension";
    private static final String CORE_FOLDER = "example.org-graphs-core";
    private static final String EXTENSION_FOLDER = "example.org-graphs-extension";

    private static final String SCHEMA =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            <http://example.org/core#Thing> a rdfs:Class ; rdfs:label "Thing"@en .
            """;

    private final UUID classUuid = UUID.randomUUID();
    private final UUID workspaceDiagramId = UUID.randomUUID();

    private Map<String, String> archive;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        var databasePort =
                new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        databasePort.createDataset("ws");
        try (var transaction = databasePort.beginTransaction("ws", ReadWrite.WRITE)) {
            transaction.createGraph(CORE, RDFParser.fromString(SCHEMA, Lang.TURTLE).toGraph());
            transaction.createGraph(EXTENSION, RDFParser.fromString(SCHEMA, Lang.TURTLE).toGraph());
            transaction
                    .diagrams()
                    .put(
                            workspaceDiagramId,
                            new CustomDiagram(
                                    workspaceDiagramId,
                                    "Overview",
                                    List.of(new ClassInDiagram(classUuid, new URI(EXTENSION)))));
            transaction
                    .graph(CORE)
                    .getDiagramLayout()
                    .getDiagramLayoutModel()
                    .createResource("_" + classUuid)
                    .addProperty(RDF.type, RDF.Property);
            transaction.commit("Set up the workspace");
        }
        archive =
                unzip(
                        new ExportWorkspaceService(databasePort, new AppVersionResolver("1.2.3"))
                                .exportWorkspace("ws"));
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    void read_validArchive_givesGraphsByTheirUriAgain() {
        var contents = read(archive);

        assertThat(contents.graphs())
                .extracting(WorkspaceArchive.ArchivedGraph::uri)
                .containsExactlyInAnyOrder(CORE, EXTENSION);
        assertThat(contents.diagrams())
                .singleElement()
                .satisfies(
                        diagram ->
                                assertThat(diagram.getClasses())
                                        .containsExactly(
                                                new ClassInDiagram(classUuid, new URI(EXTENSION))));
        assertThat(contents.colors()).containsOnlyKeys(CORE, EXTENSION);
        assertThat(contents.notices()).isEmpty();
    }

    @Test
    void read_layout_hasItsRelativeIrisBack() {
        var core =
                read(archive).graphs().stream()
                        .filter(graph -> graph.uri().equals(CORE))
                        .findFirst()
                        .orElseThrow();

        assertThat(
                        core.layout()
                                .contains(
                                        NodeFactory.createURI("_" + classUuid),
                                        RDF.type.asNode(),
                                        RDF.Property.asNode()))
                .isTrue();
    }

    @Test
    void read_notAZip_isNotTakenForAWorkspaceArchive() {
        var problems = problemsOf("just some text".getBytes());

        assertThat(problems).singleElement().asString().contains("manifest.json is missing");
    }

    @Test
    void read_zipOfSchemas_pointsToTheSchemaImport() {
        var problems = problemsOf(zip(Map.of("core.ttl", SCHEMA)));

        assertThat(problems)
                .singleElement()
                .asString()
                .contains("not a workspace archive")
                .contains("imported as schemas");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.0", "0.9"})
    void read_otherMajorVersion_isRejectedNamingBothVersions(String formatVersion) {
        var problems =
                problemsOf(withManifest(manifest -> manifest.put("formatVersion", formatVersion)));

        assertThat(problems)
                .singleElement()
                .asString()
                .contains("format " + formatVersion, "RDFArchitect 1.2.3", "reads format 1.0");
    }

    @Test
    void read_versionThatIsNoVersion_isRejected() {
        var problems = problemsOf(withManifest(manifest -> manifest.put("formatVersion", "one")));

        assertThat(problems).singleElement().asString().contains("manifest.json", "MAJOR.MINOR");
    }

    @Test
    void read_newerMinorVersion_isReadLeavingOutWhatItAdded() {
        var newer =
                withManifest(
                        manifest -> {
                            manifest.put("formatVersion", "1.4");
                            manifest.put("addedLater", "something");
                        });
        var files = unzip(newer);
        files.put("graphs/" + CORE_FOLDER + "/added-later.json", "{}");

        var contents = read(files);

        assertThat(contents.graphs()).hasSize(2);
        assertThat(contents.notices())
                .singleElement()
                .asString()
                .contains("format 1.4", "left out");
    }

    @Test
    void read_filesTheManifestDoesNotAskFor_areIgnored() {
        archive.put("README.md", "Exported for the review.");
        archive.put("graphs/unknown/schema.ttl", "not even turtle");

        assertThat(read(archive).graphs()).hasSize(2);
    }

    @Test
    void read_reportsEveryProblemAtOnce() {
        archive.remove("graphs/" + CORE_FOLDER + "/shacl.ttl");
        archive.put("graphs/" + EXTENSION_FOLDER + "/schema.ttl", "<http://a> <http://b> .");
        archive.put("colors.json", "{\"gone\": \"#000000\"}");
        archive.put(
                "diagrams.json",
                withCustomDiagrams(
                        archive.get("diagrams.json"),
                        "[{\"diagramId\": \"%s\", \"name\": \"Overview\", \"classes\": {\"gone\": [\"%s\"]}}]"
                                .formatted(workspaceDiagramId, classUuid)));

        var problems = problemsOf(zip(archive));

        assertThat(problems)
                .hasSize(4)
                .anySatisfy(
                        problem ->
                                assertThat(problem)
                                        .isEqualTo(
                                                "graphs/" + CORE_FOLDER + "/shacl.ttl is missing."))
                .anySatisfy(
                        problem ->
                                assertThat(problem)
                                        .startsWith("graphs/" + EXTENSION_FOLDER + "/schema.ttl: ")
                                        .contains("line: 1"))
                .anySatisfy(problem -> assertThat(problem).startsWith("colors.json: \"gone\""))
                .anySatisfy(
                        problem ->
                                assertThat(problem)
                                        .startsWith("diagrams.json: diagram " + workspaceDiagramId)
                                        .contains("\"gone\""));
    }

    @Test
    void read_graphDiagramClassWithoutUuid_isRejected() {
        var path = "graphs/" + CORE_FOLDER + "/diagrams.json";
        var diagramId = UUID.randomUUID();
        archive.put(
                path,
                withCustomDiagrams(
                        archive.get(path),
                        "[{\"diagramId\": \"%s\", \"name\": \"Core\", \"classes\": [null]}]"
                                .formatted(diagramId)));

        assertThat(problemsOf(zip(archive)))
                .containsExactly(path + ": diagram " + diagramId + ": a class has no UUID.");
    }

    @Test
    void read_brokenJson_namesWhereItBreaks() {
        archive.put("diagrams.json", "{\n  \"customDiagrams\": [{\"diagramId\": }]\n}");

        assertThat(problemsOf(zip(archive)))
                .singleElement()
                .asString()
                .startsWith("diagrams.json: ")
                .contains("line 2");
    }

    @Test
    void read_folderThatLeavesTheArchive_isRejected() {
        var problems =
                problemsOf(
                        withManifest(
                                manifest ->
                                        ((ObjectNode) manifest.get("graphs").get(0))
                                                .put("folder", "../outside")));

        assertThat(problems)
                .anySatisfy(
                        problem ->
                                assertThat(problem).contains("\"folder\" has to be a folder name"));
    }

    @Test
    void read_graphsThatClash_areRejected() {
        var problems =
                problemsOf(
                        withManifest(
                                manifest -> {
                                    var graphs = manifest.withArray("graphs");
                                    var second = (ObjectNode) graphs.get(1);
                                    second.put("folder", graphs.get(0).get("folder").asText());
                                    second.put("uri", graphs.get(0).get("uri").asText());
                                }));

        assertThat(problems)
                .anySatisfy(
                        problem ->
                                assertThat(problem)
                                        .contains("two graphs are stored in this folder"))
                .anySatisfy(problem -> assertThat(problem).contains("is used twice"));
    }

    @Test
    void read_graphUriThatIsNoUri_isRejected() {
        var problems =
                problemsOf(
                        withManifest(
                                manifest ->
                                        ((ObjectNode) manifest.get("graphs").get(0))
                                                .put("uri", "not a uri")));

        assertThat(problems)
                .anySatisfy(
                        problem ->
                                assertThat(problem)
                                        .contains("\"not a uri\" is not a valid graph URI"));
    }

    @Test
    void read_missingIds_areRejected() {
        var workspaceDiagrams = json(archive.get("diagrams.json"));
        workspaceDiagrams.remove(List.of("crossProfileDiagramId", "defaultPackageMRID"));
        archive.put("diagrams.json", ArchiveFiles.json(workspaceDiagrams));
        var graphDiagrams = json(archive.get("graphs/" + CORE_FOLDER + "/diagrams.json"));
        graphDiagrams.remove("defaultPackageMRID");
        archive.put("graphs/" + CORE_FOLDER + "/diagrams.json", ArchiveFiles.json(graphDiagrams));

        assertThat(problemsOf(zip(archive)))
                .containsExactlyInAnyOrder(
                        "diagrams.json: \"crossProfileDiagramId\" is missing.",
                        "diagrams.json: \"defaultPackageMRID\" is missing.",
                        "graphs/"
                                + CORE_FOLDER
                                + "/diagrams.json: \"defaultPackageMRID\" is missing.");
    }

    @Test
    void read_namespaces_comeFromTheirOwnFile() {
        archive.put("namespaces.json", "{\"ex\": \"http://example.org/ex#\"}");

        assertThat(read(archive).prefixes().getNsPrefixMap())
                .isEqualTo(Map.of("ex", "http://example.org/ex#"));
    }

    @Test
    void read_prefixWithoutNamespace_isRejected() {
        archive.put("namespaces.json", "{\"ex\": null}");

        assertThat(problemsOf(zip(archive)))
                .containsExactly(
                        "namespaces.json: \"ex\" -> \"null\" is not a valid namespace prefix.");
    }

    @Test
    void read_invalidPrefix_isRejected() {
        archive.put("namespaces.json", "{\"not a prefix\": \"http://example.org/ex#\"}");

        assertThat(problemsOf(zip(archive)))
                .singleElement()
                .asString()
                .startsWith("namespaces.json: \"not a prefix\"");
    }

    @Test
    void read_tooManyFiles_isRejected() {
        var files = new TreeMap<>(archive);
        for (var index = 0; index <= WorkspaceArchiveReader.MAX_ENTRIES; index++) {
            files.put("padding/" + index, "");
        }

        assertThat(problemsOf(zip(files)))
                .singleElement()
                .asString()
                .contains("more than 10000 files");
    }

    @Test
    void read_archiveThatUnpacksTooLarge_isRejected() {
        archive.put("padding", "x".repeat(2 * 1024 * 1024));

        var problems =
                catchThrowableOfType(
                                InvalidWorkspaceArchiveException.class,
                                () ->
                                        WorkspaceArchiveReader.read(
                                                new ByteArrayInputStream(zip(archive)),
                                                1024 * 1024))
                        .getProblems();

        assertThat(problems).containsExactly("The archive unpacks to more than 1 MB.");
    }

    @Test
    void read_emptyGraphEntry_isReportedWithTheOtherProblems() {
        archive.remove("colors.json");
        var problems = problemsOf(withManifest(manifest -> manifest.withArray("graphs").addNull()));

        assertThat(problems)
                .containsExactlyInAnyOrder(
                        "manifest.json: a graph entry is empty.", "colors.json is missing.");
    }

    private static WorkspaceArchive read(Map<String, String> files) {
        return WorkspaceArchiveReader.read(new ByteArrayInputStream(zip(files)));
    }

    private static List<String> problemsOf(byte[] archive) {
        return catchThrowableOfType(
                        InvalidWorkspaceArchiveException.class,
                        () -> WorkspaceArchiveReader.read(new ByteArrayInputStream(archive)))
                .getProblems();
    }

    private static String withCustomDiagrams(String diagramsFile, String customDiagrams) {
        var diagrams = json(diagramsFile);
        diagrams.set("customDiagrams", ArchiveFiles.tree(customDiagrams));
        return ArchiveFiles.json(diagrams);
    }

    private byte[] withManifest(Consumer<ObjectNode> change) {
        var manifest = json(archive.get(WorkspaceArchiveFormat.MANIFEST));
        change.accept(manifest);
        var files = new TreeMap<>(archive);
        files.put(WorkspaceArchiveFormat.MANIFEST, ArchiveFiles.json(manifest));
        return zip(files);
    }
}
