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
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.UncarriedItem;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        service = new ImportXmiService(databasePort, new XmiDiagramStore(databasePort));
    }

    @Test
    void importPackages_writesTheChosenProfileAsASchema() throws IOException {
        var result = importTopology(null);

        assertThat(result.importedGraphUris()).containsExactly(RDFA.GRAPH_URI + "Topology");
        assertThat(result.failedFileNames()).isEmpty();
        assertThat(databasePort.listGraphUris(DATASET)).contains(RDFA.GRAPH_URI + "Topology");
    }

    @Test
    void importPackages_writesTheResourcesOfTheProfile() throws IOException {
        importTopology(null);

        assertThat(graphOf(RDFA.GRAPH_URI + "Topology"))
                .anyMatch(
                        statement ->
                                statement.getSubject().getURI().equals(MODEL_NS + "Terminal")
                                        && statement.getPredicate().equals(RDFS.label));
    }

    @Test
    void importPackages_namesTheSchemaAsAsked() throws IOException {
        var result = importTopology("http://example.com/graphs/topology");

        assertThat(result.importedGraphUris())
                .containsExactly("http://example.com/graphs/topology");
    }

    @Test
    void importPackages_reportsAProfileTheExportDoesNotHoldEnoughOf() throws IOException {
        // Without the extension namespace the profile refuses, and refusing is an answer rather
        // than a crash: the progress says which profile and nothing was stored.
        var result =
                service.importPackages(
                        DATASET,
                        List.of(export()),
                        List.of(
                                new SelectedPackageDTO(
                                        0, TOPOLOGY, "Topology", null, null, null, null)),
                        ALL_DIAGRAMS,
                        ImportProgressListener.NOOP);

        assertThat(result.importedGraphUris()).isEmpty();
        assertThat(result.failedFileNames()).containsExactly("Topology");
        // Not even the workspace: it is created at the first write, and there was none.
        assertThat(databasePort.listDatasets()).doesNotContain(DATASET);
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
                        ImportProgressListener.NOOP);

        assertThat(result.importedGraphUris()).isEmpty();
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

        assertThat(result.importedGraphUris()).isEmpty();
        assertThat(databasePort.listDatasets()).doesNotContain(DATASET);
    }

    @Test
    void importPackages_cancelledInAWorkspaceThatWasAlreadyThere_leavesItStanding()
            throws IOException {
        // Its other schemas are no business of this import's.
        databasePort.createWorkspaceIfAbsent(DATASET);

        importTopology(null, ALL_DIAGRAMS, CANCELLED);

        assertThat(databasePort.listDatasets()).contains(DATASET);
        assertThat(databasePort.listGraphUris(DATASET)).isEmpty();
    }

    @Test
    void importPackages_storesOnlyTheDiagramsThatWereChosen() throws IOException {
        importTopology(null, List.of(), ImportProgressListener.NOOP);

        assertThat(diagramsOf(RDFA.GRAPH_URI + "Topology")).isEmpty();
    }

    @Test
    void importPackages_storesADiagramOfTheExportWithTheSchemaItDraws() throws IOException {
        importTopology(null);

        assertThat(diagramsOf(RDFA.GRAPH_URI + "Topology").values())
                .extracting(CustomDiagram::getName)
                .containsExactly("Topology overview");
    }

    @Test
    void importPackages_storesTheClassesADiagramDraws() throws IOException {
        importTopology(null);

        var diagram = diagramsOf(RDFA.GRAPH_URI + "Topology").values().iterator().next();
        assertThat(diagram.getClasses())
                .extracting(ClassInDiagram::getGraphUri)
                .allMatch(graphUri -> graphUri.toString().equals(RDFA.GRAPH_URI + "Topology"));
        assertThat(diagram.getClasses()).hasSize(2);
    }

    @Test
    void importPackages_storesWhereADiagramDrawsItsClasses() throws IOException {
        importTopology(null);

        var graphIdentifier = new GraphIdentifier(DATASET, RDFA.GRAPH_URI + "Topology");
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var diagramId = ctx.getCustomDiagrams().keySet().iterator().next();
            var layout = ctx.getDiagramLayout().getDiagramLayoutModel();
            var positions = DLObjectFetcher.fetchDiagramDOPPerClass(layout, diagramId);

            // The export draws them at 100 and 300; the import pulls a diagram apart as it goes
            // in, a class of ours needing more room than the editor gave it.
            assertThat(positions.values())
                    .extracting(point -> point.getPosition().getX())
                    .containsExactlyInAnyOrder(150f, 450f);
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
                                                    "Volume is not in the export"));
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
                                                        "declared more than once; the first is"
                                                                + " kept")));
    }

    @Test
    void importPackages_writesTheAttributeAllTheSameJustWithoutAType() throws IOException {
        importTopology(null);

        assertThat(graphOf(RDFA.GRAPH_URI + "Topology"))
                .anyMatch(
                        statement ->
                                statement.getSubject().getURI().endsWith("Reservoir.grossCapacity")
                                        && statement.getPredicate().equals(RDFS.label));
    }

    // -------------------------------------------------------------------------

    private ImportGraphsUseCase.ImportResult importTopology(String graphUri) throws IOException {
        return importTopology(graphUri, ALL_DIAGRAMS, ImportProgressListener.NOOP);
    }

    private ImportGraphsUseCase.ImportResult importTopology(
            String graphUri, List<String> diagramIds, ImportProgressListener listener)
            throws IOException {
        return service.importPackages(
                DATASET,
                List.of(export()),
                List.of(
                        new SelectedPackageDTO(
                                0, TOPOLOGY, "Topology", null, graphUri, null, EXTENSION_NS)),
                diagramIds,
                listener);
    }

    private MultipartFile export() throws IOException {
        return new MockMultipartFile(
                "files", "profiles-export.xml", "text/xml", Files.readAllBytes(EXPORT));
    }

    private List<org.apache.jena.rdf.model.Statement> graphOf(String graphUri) {
        var graphIdentifier = new GraphIdentifier(DATASET, graphUri);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return ModelFactory.createModelForGraph(ctx.getRdfGraph()).listStatements().toList();
        }
    }

    private Map<UUID, CustomDiagram> diagramsOf(String graphUri) {
        var graphIdentifier = new GraphIdentifier(DATASET, graphUri);
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            return Map.copyOf(ctx.getCustomDiagrams());
        }
    }

    /** Records what the import reported, by profile name rather than by index. */
    private static final class RecordingListener implements ImportProgressListener {

        private final List<String> planned = new java.util.ArrayList<>();
        private final List<String> finished = new java.util.ArrayList<>();
        private final List<Long> progressed = new java.util.ArrayList<>();

        @Override
        public void planned(List<PlannedImport> plannedImports) {
            plannedImports.forEach(plannedImport -> planned.add(plannedImport.fileName()));
        }

        @Override
        public void progressed(int index, long done, long total) {
            progressed.add(done);
        }

        @Override
        public void finished(int index, Outcome outcome, String graphUri) {
            finished.add(planned.get(index) + "=" + outcome);
        }
    }
}
