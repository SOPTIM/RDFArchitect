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

package org.rdfarchitect.services.update.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.UncarriedItem;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What importing the profiles chosen from an export leaves behind.
 *
 * <p>The fixture holds a Topology profile whose extension namespace it states nowhere, which is the
 * case a CGMES 2.4.15 profiles-only export presents: it can only be built with the namespace handed
 * in, and refuses rather than degrades without it.
 */
class ImportXmiServiceTest {

    private static final Path EXPORT =
            Path.of("src/test/java/org/rdfarchitect/xmi/read/testdata/profiles-export.xml");

    private static final String DATASET = "ds";
    private static final String TOPOLOGY = "EAPK_TOPOLOGY";
    private static final String MODEL_NS = "http://example.com/ns/CIM#";
    private static final String EXTENSION_NS = "http://example.com/ns/Extension#";

    /** Every diagram the export offers, which is what an unqualified import takes. */
    private static final List<String> ALL_DIAGRAMS = List.of("EAID_DIAG_TP", "EAID_DIAG_PACKAGES");

    /** A caller that has asked to stop. */
    private static final ImportProgressListener CANCELLED =
            new ImportProgressListener() {
                @Override
                public boolean isCancelled() {
                    return true;
                }
            };

    private DatabasePort databasePort;
    private ImportXmiService service;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        service = new ImportXmiService(databasePort, new XmiDiagramStore());
    }

    @Test
    void importPackages_writesTheChosenProfileAsASchema() throws IOException {
        var result = importTopology(null);

        assertThat(result.importedSchemaNames()).containsExactly("Topology");
        assertThat(result.failedFileNames()).isEmpty();
        assertThat(databasePort.listSchemaNames(DATASET)).contains("Topology");
    }

    @Test
    void importPackages_writesTheResourcesOfTheProfile() throws IOException {
        importTopology(null);

        assertThat(graphOf("Topology"))
                .anyMatch(
                        statement ->
                                statement.getSubject().getURI().equals(MODEL_NS + "Terminal")
                                        && statement.getPredicate().equals(RDFS.label));
    }

    @Test
    void importPackages_namesTheSchemaAsAsked() throws IOException {
        var result = importTopology("Topology (custom)");

        assertThat(result.importedSchemaNames()).containsExactly("Topology (custom)");
    }

    @Test
    void importPackages_reportsAProfileTheExportDoesNotHoldEnoughOf() throws IOException {
        // Without the extension namespace the profile refuses, and refusing is an answer rather
        // than a crash: the progress says which profile and why, and nothing was stored.
        var listener = new RecordingListener();
        var result =
                service.importPackages(
                        DATASET,
                        List.of(export()),
                        List.of(
                                new SelectedPackageDTO(
                                        0, TOPOLOGY, "Topology", null, null, null, null)),
                        ALL_DIAGRAMS,
                        listener);

        assertThat(result.importedSchemaNames()).isEmpty();
        assertThat(result.failedFileNames()).containsExactly("Topology");
        assertThat(listener.reasons).singleElement().asString().contains("extension namespace");
        // Not even the workspace: it is created at the first write, and there was none.
        assertThat(databasePort.listDatasets()).doesNotContain(DATASET);
    }

    @Test
    void scanPackages_saysWhyAFileIsRefused() {
        var listener = new RecordingListener();
        service.scanPackages(
                List.of(
                        new MockMultipartFile(
                                "files", "schema.xml", "text/xml", "<rdf:RDF/>".getBytes())),
                listener);

        assertThat(listener.reasons)
                .containsExactly("This is not an Enterprise Architect XMI export.");
    }

    @Test
    void importPackages_leavesOutAChoiceNamingAnExportThatWasNotRead() throws IOException {
        var result =
                service.importPackages(
                        DATASET,
                        List.of(export()),
                        List.of(
                                new SelectedPackageDTO(
                                        7, TOPOLOGY, "Topology", null, null, null, EXTENSION_NS)),
                        ALL_DIAGRAMS,
                        GraphImportListener.NOOP);

        assertThat(result.importedSchemaNames()).isEmpty();
        assertThat(result.failedFileNames()).isEmpty();
    }

    @Test
    void importPackages_reportsOneStepPerChosenProfile() throws IOException {
        var listener = new RecordingListener();

        service.importPackages(
                DATASET,
                List.of(export()),
                List.of(
                        new SelectedPackageDTO(
                                0, TOPOLOGY, "Topology", null, null, null, EXTENSION_NS)),
                ALL_DIAGRAMS,
                listener);

        // The diagrams are a step of their own: on a large schema they take longer than the
        // schemas themselves, and a progress without them would sit complete while it ran.
        assertThat(listener.planned).containsExactly("Topology", "Diagrams");
        assertThat(listener.finished).containsExactly("Topology=IMPORTED", "Diagrams=IMPORTED");
    }

    // -------------------------------------------------------------------------
    // Diagrams
    // -------------------------------------------------------------------------

    @Test
    void importPackages_cancelled_takesTheSchemasBackOut() throws IOException {
        // An import of an export is one act carried out in steps, not one act per step: stopping
        // it halfway must not leave a workspace nobody asked for.
        var result = importTopology(null, ALL_DIAGRAMS, CANCELLED);

        assertThat(result.importedSchemaNames()).isEmpty();
        assertThat(databasePort.listDatasets()).doesNotContain(DATASET);
    }

    @Test
    void importPackages_cancelledInAWorkspaceThatWasAlreadyThere_leavesItStanding()
            throws IOException {
        // Its other schemas are no business of this import's.
        databasePort.createWorkspaceIfAbsent(DATASET);

        importTopology(null, ALL_DIAGRAMS, CANCELLED);

        assertThat(databasePort.listDatasets()).contains(DATASET);
        assertThat(databasePort.listSchemaNames(DATASET)).isEmpty();
    }

    @Test
    void importPackages_cancelledWhileStoringTheDiagrams_writesNothing() throws IOException {
        // The schemas are already in the transaction by then; aborting it takes them along.
        databasePort.createWorkspaceIfAbsent(DATASET);
        var cancelled = new AtomicBoolean();
        var cancelOnceTheDiagramsStart =
                new ImportProgressListener() {
                    @Override
                    public void started(int index) {
                        if (index == 1) {
                            cancelled.set(true);
                        }
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }
                };

        var result = importTopology(null, ALL_DIAGRAMS, cancelOnceTheDiagramsStart);

        assertThat(result.importedSchemaNames()).isEmpty();
        assertThat(databasePort.listSchemaNames(DATASET)).isEmpty();
        assertThat(databasePort.listChanges(DATASET))
                .noneMatch(entry -> entry.message().startsWith("Imported"));
    }

    @Test
    void importPackages_isOneChangeInTheHistory() throws IOException {
        databasePort.createWorkspaceIfAbsent(DATASET);

        importTopology(null);

        assertThat(databasePort.listChanges(DATASET))
                .first()
                .satisfies(
                        entry -> {
                            assertThat(entry.message()).contains("Imported");
                            assertThat(entry.affectedSchemaNames()).containsExactly("Topology");
                        });
    }

    @Test
    void importPackages_storesOnlyTheDiagramsThatWereChosen() throws IOException {
        importTopology(null, List.of(), GraphImportListener.NOOP);

        assertThat(diagramsOf("Topology")).isEmpty();
    }

    @Test
    void importPackages_storesADiagramOfTheExportWithTheSchemaItDraws() throws IOException {
        importTopology(null);

        assertThat(diagramsOf("Topology").values())
                .extracting(CustomDiagram::getName)
                .containsExactly("Topology overview");
    }

    @Test
    void importPackages_storesTheClassesADiagramDraws() throws IOException {
        importTopology(null);

        var diagram = diagramsOf("Topology").values().iterator().next();
        assertThat(diagram.getClasses())
                .extracting(ClassInDiagram::getSchemaName)
                .allMatch(schemaName -> schemaName.toString().equals("Topology"));
        assertThat(diagram.getClasses()).hasSize(2);
    }

    @Test
    void importPackages_storesWhereADiagramDrawsItsClasses() throws IOException {
        importTopology(null);

        try (var transaction = databasePort.beginTransaction(DATASET, ReadWrite.READ)) {
            var ctx = transaction.graph("Topology");
            var diagramId = ctx.getCustomDiagrams().keySet().iterator().next();
            var layout = ctx.getDiagramLayout().getDiagramLayoutModel();
            var positions = DLObjectFetcher.fetchDiagramDOPPerClass(layout, diagramId);

            // The export draws them at 100 and 300; the import pulls a diagram apart as it goes
            // in, a class of ours needing more room than the editor gave it.
            assertThat(positions.values())
                    .extracting(point -> point.getPosition().getX())
                    .containsExactlyInAnyOrder(150.0, 450.0);
        }
    }

    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // What a partial export costs
    // -------------------------------------------------------------------------

    @Test
    void importPackages_warnsAboutAnAttributeWhoseTypeTheExportDoesNotHold() throws IOException {
        // Enterprise Architect writes an EAStub for every element a partial export leaves out,
        // which is enough to name the missing type and not enough to write it.
        var result = importTopology(null);

        assertThat(result.warnings())
                .anySatisfy(
                        warning -> {
                            assertThat(warning.fileName()).isEqualTo("Topology");
                            assertThat(warning.items())
                                    .contains(
                                            new UncarriedItem(
                                                    "Reservoir.grossCapacity",
                                                    "Volume is used as a type here, but the"
                                                            + " export holds no definition of it."
                                                            + " The attribute is kept, with a"
                                                            + " dangling reference."));
                        });
    }

    @Test
    void importPackages_warnsAboutAClassTheExportDeclaresTwice() throws IOException {
        var result = importTopology(null);

        assertThat(result.warnings())
                .anySatisfy(
                        warning ->
                                assertThat(warning.items())
                                        .contains(
                                                new UncarriedItem(
                                                        "String",
                                                        "Declared more than once; only the first"
                                                                + " declaration is imported")));
    }

    @Test
    void importPackages_writesTheAttributeAllTheSameJustWithoutAType() throws IOException {
        importTopology(null);

        assertThat(graphOf("Topology"))
                .anyMatch(
                        statement ->
                                statement.getSubject().getURI().endsWith("Reservoir.grossCapacity")
                                        && statement.getPredicate().equals(RDFS.label));
    }

    @Test
    void importPackages_keepsTheReferenceToATypeTheExportDoesNotDeclare() throws IOException {
        // A dangling reference rather than nothing: it says what the attribute is meant to be,
        // and importing the package that declares the type completes it.
        importTopology(null);

        assertThat(graphOf("Topology"))
                .anyMatch(
                        statement ->
                                statement.getSubject().getURI().endsWith("Reservoir.grossCapacity")
                                        && statement.getPredicate().equals(RDFS.range)
                                        && statement.getObject().isURIResource()
                                        && statement
                                                .getObject()
                                                .asResource()
                                                .getURI()
                                                .endsWith("Volume"));
    }

    // -------------------------------------------------------------------------

    private ImportGraphsUseCase.ImportResult importTopology(String schemaName) throws IOException {
        return importTopology(schemaName, ALL_DIAGRAMS, GraphImportListener.NOOP);
    }

    private ImportGraphsUseCase.ImportResult importTopology(
            String schemaName, List<String> diagramIds, ImportProgressListener listener)
            throws IOException {
        return service.importPackages(
                DATASET,
                List.of(export()),
                List.of(
                        new SelectedPackageDTO(
                                0, TOPOLOGY, "Topology", null, schemaName, null, EXTENSION_NS)),
                diagramIds,
                listener);
    }

    private MultipartFile export() throws IOException {
        return new MockMultipartFile(
                "files", "profiles-export.xml", "text/xml", Files.readAllBytes(EXPORT));
    }

    private List<org.apache.jena.rdf.model.Statement> graphOf(String schemaName) {
        try (var transaction = databasePort.beginTransaction(DATASET, ReadWrite.READ)) {
            return ModelFactory.createModelForGraph(transaction.graph(schemaName).getRdfGraph())
                    .listStatements()
                    .toList();
        }
    }

    private Map<UUID, CustomDiagram> diagramsOf(String schemaName) {
        try (var transaction = databasePort.beginTransaction(DATASET, ReadWrite.READ)) {
            return Map.copyOf(transaction.graph(schemaName).getCustomDiagrams());
        }
    }

    /** Records what the import reported, by profile name rather than by index. */
    private static final class RecordingListener implements ImportProgressListener {

        private final List<String> planned = new java.util.ArrayList<>();
        private final List<String> finished = new java.util.ArrayList<>();
        private final List<String> reasons = new java.util.ArrayList<>();

        @Override
        public void planned(List<PlannedImport> plannedImports) {
            plannedImports.forEach(plannedImport -> planned.add(plannedImport.fileName()));
        }

        @Override
        public void finished(int index, Outcome outcome, String schemaName) {
            finished.add(planned.get(index) + "=" + outcome);
        }

        @Override
        public void failed(int index, String reason) {
            ImportProgressListener.super.failed(index, reason);
            reasons.add(reason);
        }
    }
}
