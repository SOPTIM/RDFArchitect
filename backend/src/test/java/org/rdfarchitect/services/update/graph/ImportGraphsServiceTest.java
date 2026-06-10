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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.jena.graph.Graph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.dl.update.packagelayout.CreateDiagramLayoutUseCase;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

class ImportGraphsServiceTest {

    private ImportGraphsUseCase importGraphsUseCase;
    private CreateDiagramLayoutUseCase createDiagramLayoutUseCaseMock;
    private DatabasePort databasePortMock;

    @BeforeEach
    void setUp() {
        createDiagramLayoutUseCaseMock = mock(CreateDiagramLayoutUseCase.class);
        databasePortMock = mock(DatabasePort.class);
        importGraphsUseCase =
                new ImportGraphsService(createDiagramLayoutUseCaseMock, databasePortMock);
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

        var result = importGraphsUseCase.importGraphs(datasetName, List.of(file1, file2), null);

        assertThat(result.failedFileNames()).isEmpty();
        assertThat(result.importedGraphUris())
                .containsExactly(RDFA.GRAPH_URI + "graph", RDFA.GRAPH_URI + "graph_1");

        var captor = ArgumentCaptor.forClass(GraphIdentifier.class);

        verify(databasePortMock, times(2)).createGraph(captor.capture(), any(Graph.class));

        assertThat(captor.getAllValues())
                .extracting(GraphIdentifier::graphUri)
                .containsExactly(RDFA.GRAPH_URI + "graph", RDFA.GRAPH_URI + "graph_1");

        verify(createDiagramLayoutUseCaseMock, times(2)).createDiagramLayout(any());
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

        var result = importGraphsUseCase.importGraphs(datasetName, List.of(file), null);

        assertThat(result.failedFileNames()).isEmpty();
        assertThat(result.importedGraphUris()).containsExactly(RDFA.GRAPH_URI + "schema");
        assertThat(result.warnings()).hasSize(1);

        var warning = result.warnings().getFirst();
        assertThat(warning.fileName()).isEqualTo("schema.ttl");
        assertThat(warning.undisplayableProperties())
                .containsExactlyInAnyOrder("color", "owner");
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

        var result = importGraphsUseCase.importGraphs(datasetName, List.of(file), null);

        assertThat(result.failedFileNames()).isEmpty();
        assertThat(result.importedGraphUris()).containsExactly(RDFA.GRAPH_URI + "schema");
        assertThat(result.warnings()).isEmpty();
    }
}
