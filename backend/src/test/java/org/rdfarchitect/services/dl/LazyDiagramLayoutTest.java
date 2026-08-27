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

package org.rdfarchitect.services.dl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.api.dto.rendering.svelteflow.SvelteFlowDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.NodeDTO;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.services.GetRenderingDataService;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassLayoutService;
import org.rdfarchitect.services.rendering.GraphToCIMCollectionConverterService;
import org.rdfarchitect.services.rendering.svelteflow.RenderCIMFacadeCollectionSvelteFlowService;
import org.rdfarchitect.services.update.graph.ImportGraphsService;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The import no longer writes diagram layout data. This pins down the behaviour it relies on
 * instead: an imported class is rendered even though it has no layout data, and the positions the
 * editor sends after laying the diagram out create that layout data on the fly.
 */
class LazyDiagramLayoutTest {

    private static final String SCHEMA =
            """
            @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
            @prefix ex:   <http://example.com#> .

            ex:Package_gadgets a cims:ClassCategory ; rdfs:label "gadgets"@en .

            ex:Gadget a rdfs:Class ;
                rdfs:label             "Gadget"@en ;
                cims:belongsToCategory ex:Package_gadgets .

            ex:Widget a rdfs:Class ;
                rdfs:label             "Widget"@en ;
                cims:belongsToCategory ex:Package_gadgets .
            """;

    private DatabasePort databasePort;
    private GraphToCIMCollectionConverterService converter;
    private GetRenderingDataService renderingDataService;
    private UpdateClassLayoutService classLayoutService;
    private GraphIdentifier graphIdentifier;

    @BeforeEach
    void setUp() {
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        converter = new GraphToCIMCollectionConverterService(databasePort);
        renderingDataService =
                new GetRenderingDataService(
                        databasePort,
                        new RenderCIMFacadeCollectionSvelteFlowService(),
                        datasetName -> List.of());
        classLayoutService =
                new UpdateClassLayoutService(databasePort, Mappers.getMapper(PackageMapper.class));

        var file =
                new MockMultipartFile(
                        "files",
                        "gadgets.ttl",
                        "text/turtle",
                        SCHEMA.getBytes(StandardCharsets.UTF_8));
        var result = new ImportGraphsService(databasePort).importGraphs("ds", List.of(file), null);
        assertThat(result.failedFileNames()).isEmpty();
        graphIdentifier = new GraphIdentifier("ds", result.importedGraphUris().getFirst());
    }

    @Test
    void importedGraph_hasNoLayoutDataButStillRendersItsClasses() {
        var packageUUID = onlyPackageUUID();

        assertThat(diagramExists(packageUUID)).isFalse();

        var nodes = renderPackage(packageUUID);

        assertThat(nodes)
                .extracting(node -> node.getData().getLabel())
                .containsOnly("Gadget", "Widget");
        assertThat(nodes)
                .allSatisfy(
                        node -> {
                            assertThat(node.getPosition().getX()).isZero();
                            assertThat(node.getPosition().getY()).isZero();
                        });
    }

    @Test
    void positionsSentAfterLayouting_createLayoutDataAndSurviveTheNextRender() {
        var packageUUID = onlyPackageUUID();
        var gadgetUUID = nodeUUID("Gadget", renderPackage(packageUUID));

        var position = new ClassPositionDTO();
        position.setClassUUID(gadgetUUID);
        position.setXPosition(120.0F);
        position.setYPosition(340.0F);
        classLayoutService.updateClassPositions(graphIdentifier, packageUUID, List.of(position));

        assertThat(diagramExists(packageUUID)).isTrue();

        var nodes = renderPackage(packageUUID);
        var gadget =
                nodes.stream()
                        .filter(node -> node.getId().equals(gadgetUUID))
                        .findFirst()
                        .orElseThrow();
        assertThat(gadget.getPosition().getX()).isEqualTo(120.0F);
        assertThat(gadget.getPosition().getY()).isEqualTo(340.0F);
    }

    private UUID onlyPackageUUID() {
        var packages = converter.convert(graphIdentifier, new GraphFilter(false)).getPackages();
        assertThat(packages).hasSize(1);
        return packages.getFirst().getUuid();
    }

    private List<NodeDTO> renderPackage(UUID packageUUID) {
        var filter = new GraphFilter(true);
        filter.setIncludePropertiesFromOtherProfiles(false);
        filter.setPackageUUID(packageUUID.toString());
        var rendering = renderingDataService.getRenderingData(graphIdentifier, filter, packageUUID);
        return ((SvelteFlowDTO) rendering).getNodes();
    }

    private UUID nodeUUID(String label, List<NodeDTO> nodes) {
        return nodes.stream()
                .filter(node -> label.equals(node.getData().getLabel()))
                .map(NodeDTO::getId)
                .findFirst()
                .orElseThrow();
    }

    private boolean diagramExists(UUID packageUUID) {
        var model =
                databasePort
                        .getGraphWithContext(graphIdentifier)
                        .getDiagramLayout()
                        .getDiagramLayoutModelDirect();
        return DLObjectFetcher.fetchDiagram(model, packageUUID) != null;
    }
}
