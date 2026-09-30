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
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.jena.graph.Graph;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.query.ReadWrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.update.graph.ImportProgressListener.PlannedImport;
import org.rdfarchitect.services.update.graph.PrefixResolutionDTO.Action;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ImportGraphsServiceTest {

    private static final String CIM16 = "http://iec.ch/TC57/2013/CIM-schema-cim16#";
    private static final String CIM18 = "http://iec.ch/TC57/2023/CIM-schema-cim18#";

    private ImportGraphsUseCase importGraphsUseCase;
    private DatabasePort databasePortMock;

    private WorkspaceTransaction transaction;

    @BeforeEach
    void setUp() {
        databasePortMock = mock(DatabasePort.class);
        transaction = mock(WorkspaceTransaction.class);
        when(databasePortMock.beginTransaction(anyString(), any(ReadWrite.class)))
                .thenReturn(transaction);
        importGraphsUseCase = new ImportGraphsService(databasePortMock);
    }

    @Test
    void importGraphs_sameFileNameTwice_autoGeneratesUniqueGraphUris() {
        var datasetName = "ds";

        var file1 =
                new MockMultipartFile(
                        "graph",
                        "graph.ttl",
                        "text/turtle",
                        "@prefix ex: <http://example.com/> . ex:a ex:b ex:c ."
                                .getBytes(StandardCharsets.UTF_8));
        var file2 =
                new MockMultipartFile(
                        "graph",
                        "graph.ttl",
                        "text/turtle",
                        "@prefix ex: <http://example.com/> . ex:d ex:e ex:f ."
                                .getBytes(StandardCharsets.UTF_8));

        when(databasePortMock.listGraphUris(datasetName))
                .thenThrow(new RuntimeException("dataset does not exist"));

        var result =
                importGraphsUseCase.importGraphs(
                        datasetName, List.of(file1, file2), null, ImportProgressListener.NOOP);

        assertThat(result.failedFileNames()).isEmpty();
        assertThat(result.importedGraphUris())
                .containsExactly(RDFA.GRAPH_URI + "graph", RDFA.GRAPH_URI + "graph_1");

        var captor = ArgumentCaptor.forClass(String.class);

        verify(transaction, times(2)).createGraph(captor.capture(), any(Graph.class));

        assertThat(captor.getAllValues())
                .containsExactly(RDFA.GRAPH_URI + "graph", RDFA.GRAPH_URI + "graph_1");
    }

    @Test
    void importGraphs_propertiesWithoutCimMetadata_areReportedAsUndisplayable() {
        var datasetName = "ds";

        // Gadget.color and Gadget.owner are plain rdf:Property declarations without the
        // UML#attribute stereotype or cims:AssociationUsed, so they cannot be displayed.
        // Gadget.size is a conformant attribute and Gadget.parent a conformant association.
        var schema =
                """
                @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
                @prefix uml:  <http://iec.ch/TC57/NonStandard/UML#> .
                @prefix ex:   <http://example.com/> .

                ex:Gadget a rdfs:Class ; rdfs:label "Gadget"@en .

                ex:Gadget.color a rdf:Property ;
                    rdfs:label  "color"@en ;
                    rdfs:domain ex:Gadget ;
                    rdfs:range  ex:String .

                ex:Gadget.owner a rdf:Property ;
                    rdfs:label  "owner"@en ;
                    rdfs:domain ex:Gadget ;
                    rdfs:range  ex:Owner .

                ex:Gadget.size a rdf:Property ;
                    rdfs:label      "size"@en ;
                    rdfs:domain     ex:Gadget ;
                    cims:stereotype uml:attribute ;
                    rdfs:range      ex:String .

                ex:Gadget.parent a rdf:Property ;
                    rdfs:label           "parent"@en ;
                    rdfs:domain          ex:Gadget ;
                    cims:AssociationUsed "Yes" ;
                    rdfs:range           ex:Gadget .
                """;

        var file =
                new MockMultipartFile(
                        "graph",
                        "schema.ttl",
                        "text/turtle",
                        schema.getBytes(StandardCharsets.UTF_8));

        var result =
                importGraphsUseCase.importGraphs(
                        datasetName, List.of(file), null, ImportProgressListener.NOOP);

        assertThat(result.failedFileNames()).isEmpty();
        assertThat(result.importedGraphUris()).containsExactly(RDFA.GRAPH_URI + "schema");
        assertThat(result.warnings()).hasSize(1);

        var warning = result.warnings().getFirst();
        assertThat(warning.fileName()).isEqualTo("schema.ttl");
        assertThat(warning.undisplayableProperties()).containsExactlyInAnyOrder("color", "owner");
    }

    private MockMultipartFile schemaFile(String fileName, String cimNamespace) {
        var schema =
                """
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix cim:  <%s> .

                cim:Gadget a rdfs:Class ; rdfs:label "Gadget"@en .
                """
                        .formatted(cimNamespace);
        return new MockMultipartFile(
                "files", fileName, "text/turtle", schema.getBytes(StandardCharsets.UTF_8));
    }

    private PrefixMapping prefixMapping(String cimNamespace) {
        return new PrefixMappingImpl().setNsPrefix("cim", cimNamespace);
    }

    /** A dataset that binds {@code cim:} to a namespace one of its schemas declares as well. */
    private void workspaceHolding(String datasetName, String cimNamespace) {
        when(databasePortMock.getPrefixMapping(datasetName))
                .thenReturn(prefixMapping(cimNamespace));
        var graphUri = RDFA.GRAPH_URI + "existing";
        when(databasePortMock.listGraphUris(datasetName)).thenReturn(List.of(graphUri));

        var graph = GraphFactory.createDefaultGraph();
        graph.getPrefixMapping().setNsPrefixes(prefixMapping(cimNamespace));
        var context = mock(GraphContext.class);
        when(context.begin(any())).thenReturn(context);
        when(context.getRdfGraph()).thenReturn(graph);
        when(databasePortMock.getGraphWithContext(new GraphIdentifier(datasetName, graphUri)))
                .thenReturn(context);
    }

    private PrefixComparison contestedEntryOf(RecordingListener listener) {
        return listener.comparison.stream()
                .filter(PrefixComparison::contested)
                .reduce(
                        (first, second) -> {
                            throw new AssertionError("More than one prefix is contested.");
                        })
                .orElseThrow(() -> new AssertionError("No prefix is contested."));
    }

    /** The prefixes of the graph that was stored last. */
    private Map<String, String> storedPrefixes() {
        var captor = ArgumentCaptor.forClass(Graph.class);
        verify(databasePortMock, atLeastOnce())
                .createGraph(any(GraphIdentifier.class), captor.capture());
        return captor.getValue().getPrefixMapping().getNsPrefixMap();
    }

    /**
     * Answers the prefix question with a fixed set of decisions and remembers what it was asked.
     */
    private final class RecordingListener implements ImportProgressListener {

        private final List<PrefixResolutionDTO> resolutions;
        private final Map<Integer, String> plannedFileNames = new LinkedHashMap<>();
        private final List<String> failedBeforeAsking = new ArrayList<>();
        private List<PrefixComparison> comparison;
        private int storedGraphsWhenAsked = -1;
        private boolean asked;

        private RecordingListener(List<PrefixResolutionDTO> resolutions) {
            this.resolutions = resolutions;
        }

        @Override
        public void planned(List<PlannedImport> plannedImports) {
            plannedImports.forEach(
                    plannedImport ->
                            plannedFileNames.put(plannedImport.index(), plannedImport.fileName()));
        }

        @Override
        public void finished(int index, Outcome outcome, String graphUri) {
            if (outcome == Outcome.FAILED && !asked) {
                failedBeforeAsking.add(plannedFileNames.get(index));
            }
        }

        @Override
        public ResolvedPrefixes awaitResolvedPrefixes(List<PrefixComparison> comparison) {
            this.asked = true;
            this.comparison = comparison;
            this.storedGraphsWhenAsked =
                    mockingDetails(databasePortMock).getInvocations().stream()
                            .filter(
                                    invocation ->
                                            "createGraph".equals(invocation.getMethod().getName()))
                            .toList()
                            .size();
            return ResolvedPrefixes.of(comparison, resolutions);
        }
    }

    @Test
    void importGraphs_prefixHeldByAnotherNamespace_isAskedAboutBeforeAnythingIsStored() {
        var datasetName = "ds";
        workspaceHolding(datasetName, CIM16);

        var listener = new RecordingListener(List.of());
        importGraphsUseCase.importGraphs(
                datasetName, List.of(schemaFile("dl30.ttl", CIM18)), null, listener);

        assertThat(listener.comparison)
                .extracting(PrefixComparison::prefix)
                .containsExactly("cim:", "rdfs:");
        assertThat(contestedEntryOf(listener))
                .satisfies(
                        entry -> {
                            assertThat(entry.prefix()).isEqualTo("cim:");
                            assertThat(entry.workspace().iri()).isEqualTo(CIM16);
                            assertThat(entry.imported().getFirst().iri()).isEqualTo(CIM18);
                        });
        assertThat(listener.storedGraphsWhenAsked).isZero();
    }

    @Test
    void importGraphs_contestedPrefixLeftUndecided_isImportedWithoutIt() {
        var datasetName = "ds";
        workspaceHolding(datasetName, CIM16);

        importGraphsUseCase.importGraphs(
                datasetName,
                List.of(schemaFile("dl30.ttl", CIM18)),
                null,
                new RecordingListener(List.of()));

        assertThat(storedPrefixes()).doesNotContainKey("cim");
    }

    @Test
    void importGraphs_contestedPrefixRenamed_isImportedUnderTheNewPrefix() {
        var datasetName = "ds";
        workspaceHolding(datasetName, CIM16);

        importGraphsUseCase.importGraphs(
                datasetName,
                List.of(schemaFile("dl30.ttl", CIM18)),
                null,
                new RecordingListener(
                        List.of(new PrefixResolutionDTO("cim:", CIM18, Action.RENAME, "cim2:"))));

        assertThat(storedPrefixes()).containsEntry("cim2", CIM18).doesNotContainKey("cim");
    }

    @Test
    void importGraphs_contestedPrefixHandedToTheImport_keepsTheImportedBinding() {
        var datasetName = "ds";
        workspaceHolding(datasetName, CIM16);

        importGraphsUseCase.importGraphs(
                datasetName,
                List.of(schemaFile("dl30.ttl", CIM18)),
                null,
                new RecordingListener(
                        List.of(new PrefixResolutionDTO("cim:", CIM18, Action.KEEP, null))));

        assertThat(storedPrefixes()).containsEntry("cim", CIM18);
    }

    @Test
    void importGraphs_datasetPrefixRenamed_isRewrittenBeforeTheFirstFileIsStored() {
        var datasetName = "ds";
        workspaceHolding(datasetName, CIM16);

        importGraphsUseCase.importGraphs(
                datasetName,
                List.of(schemaFile("dl30.ttl", CIM18)),
                null,
                new RecordingListener(
                        List.of(
                                new PrefixResolutionDTO("cim:", CIM16, Action.RENAME, "cim16:"),
                                new PrefixResolutionDTO("cim:", CIM18, Action.KEEP, null))));

        var captor = ArgumentCaptor.forClass(PrefixMapping.class);
        verify(databasePortMock).setPrefixMapping(eq(datasetName), captor.capture());
        assertThat(captor.getValue().getNsPrefixMap()).containsExactly(entry("cim16", CIM16));
        assertThat(storedPrefixes()).containsEntry("cim", CIM18);
    }

    @Test
    void importGraphs_datasetPrefixesUntouched_areNotWrittenBack() {
        var datasetName = "ds";
        workspaceHolding(datasetName, CIM16);

        importGraphsUseCase.importGraphs(
                datasetName,
                List.of(schemaFile("dl30.ttl", CIM18)),
                null,
                new RecordingListener(List.of()));

        verify(databasePortMock, never()).setPrefixMapping(any(), any());
    }

    @Test
    void importGraphs_filesDisagreeingOnAPrefix_areAskedAboutEvenWithoutAnExistingBinding() {
        var datasetName = "ds";

        var listener = new RecordingListener(List.of());
        importGraphsUseCase.importGraphs(
                datasetName,
                List.of(schemaFile("a.ttl", CIM16), schemaFile("b.ttl", CIM18)),
                null,
                listener);

        assertThat(contestedEntryOf(listener))
                .satisfies(
                        entry -> {
                            assertThat(entry.prefix()).isEqualTo("cim:");
                            assertThat(entry.workspace()).isNull();
                            assertThat(entry.imported())
                                    .extracting(PrefixBinding::iri)
                                    .containsExactly(CIM16, CIM18);
                        });
    }

    @Test
    void importGraphs_prefixesAgreeing_areNotAskedAbout() {
        var datasetName = "ds";
        workspaceHolding(datasetName, CIM16);

        var listener = new RecordingListener(List.of());
        importGraphsUseCase.importGraphs(
                datasetName, List.of(schemaFile("dl24.ttl", CIM16)), null, listener);

        assertThat(listener.comparison).isNull();
        assertThat(storedPrefixes()).containsEntry("cim", CIM16);
    }

    @Test
    void importGraphs_unreadableFile_isReportedBeforeThePrefixQuestionIsAsked() {
        var datasetName = "ds";
        workspaceHolding(datasetName, CIM16);

        var broken =
                new MockMultipartFile(
                        "files",
                        "broken.ttl",
                        "text/turtle",
                        "ex:Gadget a rdfs:Class .".getBytes(StandardCharsets.UTF_8));

        var listener = new RecordingListener(List.of());
        var result =
                importGraphsUseCase.importGraphs(
                        datasetName,
                        List.of(broken, schemaFile("dl30.ttl", CIM18)),
                        null,
                        listener);

        assertThat(listener.failedBeforeAsking).containsExactly("broken.ttl");
        assertThat(result.failedFileNames()).containsExactly("broken.ttl");
        assertThat(result.importedGraphUris()).containsExactly(RDFA.GRAPH_URI + "dl30");
    }

    @Test
    void importGraphs_unreadableFile_doesNotBringItsPrefixesIntoTheComparison() {
        var datasetName = "ds";
        workspaceHolding(datasetName, CIM16);

        var broken =
                new MockMultipartFile(
                        "files",
                        "broken.ttl",
                        "text/turtle",
                        "@prefix cim: <%s> . cim:Gadget a rdfs:Class ."
                                .formatted(CIM18)
                                .getBytes(StandardCharsets.UTF_8));

        var listener = new RecordingListener(List.of());
        var result = importGraphsUseCase.importGraphs(datasetName, List.of(broken), null, listener);

        assertThat(listener.comparison).isNull();
        assertThat(result.failedFileNames()).containsExactly("broken.ttl");
        assertThat(result.importedGraphUris()).isEmpty();
    }

    @Test
    void importGraphs_conformantSchema_producesNoWarnings() {
        var datasetName = "ds";

        var schema =
                """
                @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
                @prefix uml:  <http://iec.ch/TC57/NonStandard/UML#> .
                @prefix ex:   <http://example.com/> .

                ex:Gadget a rdfs:Class ; rdfs:label "Gadget"@en .

                ex:Gadget.size a rdf:Property ;
                    rdfs:label      "size"@en ;
                    rdfs:domain     ex:Gadget ;
                    cims:stereotype uml:attribute ;
                    rdfs:range      ex:String .

                ex:Gadget.parent a rdf:Property ;
                    rdfs:label           "parent"@en ;
                    rdfs:domain          ex:Gadget ;
                    cims:AssociationUsed "Yes" ;
                    rdfs:range           ex:Gadget .
                """;

        var file =
                new MockMultipartFile(
                        "graph",
                        "schema.ttl",
                        "text/turtle",
                        schema.getBytes(StandardCharsets.UTF_8));

        var result =
                importGraphsUseCase.importGraphs(
                        datasetName, List.of(file), null, ImportProgressListener.NOOP);

        assertThat(result.failedFileNames()).isEmpty();
        assertThat(result.importedGraphUris()).containsExactly(RDFA.GRAPH_URI + "schema");
        assertThat(result.warnings()).isEmpty();
    }
}
