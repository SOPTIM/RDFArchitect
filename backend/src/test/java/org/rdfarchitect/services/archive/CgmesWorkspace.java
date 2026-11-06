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

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.api.dto.rendering.svelteflow.SvelteFlowDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.NodeDTO;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.database.inmemory.diagrams.CustomDiagram;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.services.GetRenderingDataService;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.services.dl.select.QueryDiagramLayoutService;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassLayoutService;
import org.rdfarchitect.services.rendering.GraphToCIMCollectionConverterService;
import org.rdfarchitect.services.rendering.svelteflow.RenderCIMFacadeCollectionSvelteFlowService;
import org.rdfarchitect.services.update.graph.ImportGraphsService;
import org.rdfarchitect.services.update.graph.ImportProgressListener;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class CgmesWorkspace {

    private static final Path CGMES =
            Path.of("../external/entsoe-application-profiles-library/CGMES/CurrentRelease");

    static final String HEADER = "RDFS/61970-600-2_Header-AP-Voc-RDFS2020.rdf";
    static final String TOPOLOGY = "RDFS/61970-600-2_Topology-AP-Voc-RDFS2020.rdf";
    static final String DIAGRAM_LAYOUT = "RDFS/61970-600-2_DiagramLayout-AP-Voc-RDFS2020.rdf";
    static final String TOPOLOGY_SHACL = "SHACL/TTL/61970-600-2_Topology-AP-Con-Simple-SHACL.ttl";

    static final String HAND_PICKED_COLOR = "#123456";

    private final DatabasePort databasePort;
    private final GraphToCIMCollectionConverterService converter;
    private final GetRenderingDataService renderingDataService;
    private final UpdateClassLayoutService classLayoutService;

    CgmesWorkspace(DatabasePort databasePort) {
        this.databasePort = databasePort;
        this.converter = new GraphToCIMCollectionConverterService(databasePort);
        this.renderingDataService =
                new GetRenderingDataService(
                        databasePort,
                        new RenderCIMFacadeCollectionSvelteFlowService(),
                        datasetName -> List.of(),
                        new QueryDiagramLayoutService(databasePort));
        this.classLayoutService =
                new UpdateClassLayoutService(databasePort, Mappers.getMapper(PackageMapper.class));
    }

    List<String> build(String workspace, List<String> profiles) {
        List<MultipartFile> files =
                profiles.stream().map(CgmesWorkspace::file).map(MultipartFile.class::cast).toList();
        var result =
                new ImportGraphsService(databasePort)
                        .importGraphs(workspace, files, null, ImportProgressListener.NOOP);
        assertThat(result.failedFileNames()).isEmpty();
        var graphUris = result.importedGraphUris();

        var shownClasses = new ArrayList<ClassInDiagram>();
        for (var graphUri : graphUris) {
            var graph = new GraphIdentifier(workspace, graphUri);
            var packageUUID = firstPackage(graph);
            var nodes = render(graph, packageUUID);
            layOut(
                    nodes.stream().map(NodeDTO::getId).toList(),
                    positions ->
                            classLayoutService.updateClassPositions(graph, packageUUID, positions));
            shownClasses.add(new ClassInDiagram(nodes.getFirst().getId(), new URI(graphUri)));
        }

        var firstGraph = graphUris.getFirst();
        var graphDiagram =
                new CustomDiagram(UUID.randomUUID(), "Graph diagram", shownClasses.subList(0, 1));
        var workspaceDiagram =
                new CustomDiagram(UUID.randomUUID(), "Workspace diagram", shownClasses);
        try (var transaction = databasePort.beginTransaction(workspace, ReadWrite.WRITE)) {
            var shacl =
                    ModelFactory.createModelForGraph(
                            transaction.graph(firstGraph).getCustomSHACL());
            RDFDataMgr.read(shacl, CGMES.resolve(TOPOLOGY_SHACL).toString());
            transaction
                    .graph(firstGraph)
                    .getCustomDiagrams()
                    .put(graphDiagram.getDiagramId(), graphDiagram);
            transaction.diagrams().put(workspaceDiagram.getDiagramId(), workspaceDiagram);
            transaction.crossProfileColors().setColor(firstGraph, HAND_PICKED_COLOR);
            transaction.commit("Set up the workspace");
        }

        var crossProfileDiagramId = crossProfileDiagramId(workspace);
        layOut(
                shownClasses.stream().map(ClassInDiagram::getUuid).toList(),
                positions ->
                        classLayoutService.updateClassPositions(
                                workspace, workspaceDiagram.getDiagramId(), positions));
        layOut(
                List.of(CrossProfileUtils.mergedUuid("http://iec.ch/TC57/CIM100#TopologicalNode")),
                positions ->
                        classLayoutService.updateClassPositions(
                                workspace, crossProfileDiagramId, positions));
        return graphUris;
    }

    Map<UUID, String> positionsOfFirstPackage(String workspace, String graphUri) {
        var graph = new GraphIdentifier(workspace, graphUri);
        var positions = new LinkedHashMap<UUID, String>();
        for (var node : render(graph, firstPackage(graph))) {
            positions.put(
                    node.getId(), node.getPosition().getX() + "," + node.getPosition().getY());
        }
        return positions;
    }

    private UUID crossProfileDiagramId(String workspace) {
        try (var transaction = databasePort.beginTransaction(workspace, ReadWrite.READ)) {
            return transaction.crossProfileDiagramId();
        }
    }

    private UUID firstPackage(GraphIdentifier graph) {
        return converter.convert(graph, new GraphFilter(false)).getPackages().getFirst().getUuid();
    }

    private List<NodeDTO> render(GraphIdentifier graph, UUID packageUUID) {
        var filter = new GraphFilter(true);
        filter.setIncludePropertiesFromOtherProfiles(false);
        filter.setPackageUUID(packageUUID.toString());
        return ((SvelteFlowDTO) renderingDataService.getRenderingData(graph, filter, packageUUID))
                .getNodes();
    }

    private static void layOut(List<UUID> classes, Consumer<List<ClassPositionDTO>> send) {
        var positions = new ArrayList<ClassPositionDTO>();
        for (var index = 0; index < classes.size(); index++) {
            var position = new ClassPositionDTO();
            position.setClassUUID(classes.get(index));
            position.setXPosition(100.0F + index * 40);
            position.setYPosition(50.0F + index * 30);
            positions.add(position);
        }
        send.accept(positions);
    }

    private static MockMultipartFile file(String profile) {
        var path = CGMES.resolve(profile);
        try {
            return new MockMultipartFile(
                    "files",
                    path.getFileName().toString(),
                    "application/rdf+xml",
                    Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
