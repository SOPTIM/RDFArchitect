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

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.ClassUMLAdaptedMapper;
import org.rdfarchitect.api.dto.DataTypeDTO;
import org.rdfarchitect.api.dto.association.AssociationDTO;
import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.api.dto.association.AssociationPairMapper;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.api.dto.rendering.svelteflow.SvelteFlowDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EdgeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.NodeDTO;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.models.dto.rendering.svelteflow.RenderMergedDiagramSvelteFlowService;
import org.rdfarchitect.services.GetRenderingDataService;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.services.diagrams.CustomDiagramService;
import org.rdfarchitect.services.dl.select.QueryDiagramLayoutService;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassLayoutService;
import org.rdfarchitect.services.dl.update.edgelayout.EdgeKey;
import org.rdfarchitect.services.dl.update.edgelayout.UpdateEdgeLayoutDataService;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.rdfarchitect.services.rendering.GraphToCIMCollectionConverterService;
import org.rdfarchitect.services.rendering.svelteflow.RenderCIMFacadeCollectionSvelteFlowService;
import org.rdfarchitect.services.update.classes.UpdateClassService;
import org.rdfarchitect.services.update.classes.associations.AssociationsService;
import org.rdfarchitect.services.update.graph.ImportGraphsService;
import org.rdfarchitect.services.update.graph.ImportProgressListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Runs the edge layout against a real CGMES profile the way the editor drives it: every diagram is
 * laid out with the positions of all its rendered classes, then the schema is changed through the
 * services. After every step the edge diagram objects of a diagram have to match exactly the edges
 * the renderer draws between classes that have layout data there.
 */
@SpringBootTest
class EdgeLayoutConsistencyTest {

    private static final Path CGMES_2_PATH =
            Path.of(
                    "../external/entsoe-application-profiles-library/CGMES/PastReleases/v2-4/Original/RDFS");
    private static final String EQUIPMENT =
            "EquipmentProfileCoreOperationShortCircuitRDFSAugmented-v2_4_15-4Sep2020.rdf";
    private static final String STEADY_STATE_HYPOTHESIS =
            "SteadyStateHypothesisProfileRDFSAugmented-v2_4_15-4Sep2020.rdf";
    private static final String DATASET = "ds";

    @Autowired private ClassUMLAdaptedMapper classMapper;
    @Autowired private PackageMapper packageMapper;
    @Autowired private AssociationPairMapper associationPairMapper;

    private DatabasePort databasePort;
    private GetRenderingDataService renderingDataService;
    private RenderMergedDiagramSvelteFlowService mergedRenderingService;
    private UpdateClassLayoutService classLayoutService;
    private UpdateEdgeLayoutDataService edgeLayoutService;
    private UpdateClassService classService;
    private AssociationsService associationsService;
    private GraphIdentifier equipment;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        var renderer = new RenderCIMFacadeCollectionSvelteFlowService();
        var layoutQueries = new QueryDiagramLayoutService(databasePort);
        var customDiagramService = new CustomDiagramService(databasePort, datasetName -> List.of());
        renderingDataService =
                new GetRenderingDataService(
                        databasePort, renderer, datasetName -> List.of(), layoutQueries);
        mergedRenderingService =
                new RenderMergedDiagramSvelteFlowService(
                        databasePort,
                        customDiagramService,
                        layoutQueries,
                        renderer,
                        datasetName -> List.of());
        classLayoutService = new UpdateClassLayoutService(databasePort, packageMapper);
        edgeLayoutService = new UpdateEdgeLayoutDataService(databasePort);
        classService =
                new UpdateClassService(
                        databasePort,
                        classMapper,
                        packageMapper,
                        classLayoutService,
                        classLayoutService,
                        classLayoutService,
                        false,
                        classLayoutService,
                        customDiagramService,
                        edgeLayoutService);
        associationsService =
                new AssociationsService(databasePort, associationPairMapper, edgeLayoutService);
        equipment = importProfile(EQUIPMENT);
    }

    @Test
    void laidOutPackageDiagrams_haveADiagramObjectForEveryRenderedEdge() {
        layoutAllPackages();

        assertAllPackagesConsistent();
        assertThat(edgesOf(packageOf("ACLineSegment")))
                .contains(EdgeKey.inheritance(uuidOf("ACLineSegment"), uuidOf("Conductor")));
    }

    @Test
    void creatingAnAssociation_addsItsEdgeToTheLaidOutDiagrams() {
        layoutAllPackages();

        var uuids =
                associationsService.createAssociation(
                        equipment, associationPair("ACLineSegment", "Conductor"));

        assertAllPackagesConsistent();
        assertThat(edgesOf(packageOf("ACLineSegment")))
                .contains(EdgeKey.association(uuids.fromUUID(), uuids.toUUID()));
    }

    @Test
    void changingASuperClass_movesTheInheritanceEdge() {
        layoutAllPackages();
        var acLineSegment = uuidOf("ACLineSegment");

        var subClassUri = classOf("ACLineSegment").uri();
        var superClassUri = classOf("ConductingEquipment").uri();
        editSchema(
                model -> {
                    var subClass = model.getResource(subClassUri);
                    model.removeAll(subClass, RDFS.subClassOf, null);
                    subClass.addProperty(RDFS.subClassOf, model.getResource(superClassUri));
                });
        edgeLayoutService.syncEdgeLayout(equipment);

        assertAllPackagesConsistent();
        assertThat(edgesOf(packageOf("ACLineSegment")))
                .doesNotContain(EdgeKey.inheritance(acLineSegment, uuidOf("Conductor")))
                .contains(EdgeKey.inheritance(acLineSegment, uuidOf("ConductingEquipment")));
    }

    @Test
    void deletingAClass_dropsAllItsEdges() {
        layoutAllPackages();
        var conductingEquipment = uuidOf("ConductingEquipment");

        classService.deleteClass(equipment, conductingEquipment);

        assertAllPackagesConsistent();
        for (var packageUUID : packageUUIDs()) {
            assertThat(edgesOf(packageUUID))
                    .noneMatch(
                            edge ->
                                    conductingEquipment.equals(edge.identifiedObject())
                                            || conductingEquipment.equals(edge.otherClass()));
        }
    }

    @Test
    void crossProfileDiagram_hasADiagramObjectForEveryRenderedEdge() {
        importProfile(STEADY_STATE_HYPOTHESIS);
        var diagramUUID =
                databasePort.getCrossProfileDiagramInfo(DATASET).getCrossProfileDiagramUUID();

        var rendering = (SvelteFlowDTO) mergedRenderingService.renderCrossProfileDiagram(DATASET);
        classLayoutService.updateClassPositions(DATASET, diagramUUID, positionsOf(rendering));

        var model = databasePort.getDatasetDiagramLayout(DATASET).getDiagramLayoutModel();
        assertConsistent(
                model,
                diagramUUID,
                (SvelteFlowDTO) mergedRenderingService.renderCrossProfileDiagram(DATASET),
                mergedAssociationEndpoints());
        assertThat(DLObjectFetcher.fetchDiagramEdgeDOs(model, new MRID(diagramUUID))).isNotEmpty();
    }

    private GraphIdentifier importProfile(String fileName) {
        byte[] content;
        try {
            content = Files.readAllBytes(CGMES_2_PATH.resolve(fileName));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        var file = new MockMultipartFile("files", fileName, "application/rdf+xml", content);
        var result =
                new ImportGraphsService(databasePort)
                        .importGraphs(DATASET, List.of(file), null, ImportProgressListener.NOOP);
        assertThat(result.failedFileNames()).isEmpty();
        return new GraphIdentifier(DATASET, result.importedGraphUris().getFirst());
    }

    private void layoutAllPackages() {
        for (var packageUUID : packageUUIDs()) {
            var rendering = renderPackage(packageUUID);
            classLayoutService.updateClassPositions(equipment, packageUUID, positionsOf(rendering));
        }
    }

    private void assertAllPackagesConsistent() {
        for (var packageUUID : packageUUIDs()) {
            assertConsistent(
                    graphLayout(),
                    packageUUID,
                    renderPackage(packageUUID),
                    associationEndpointsOfEquipment());
        }
    }

    /**
     * Checks a diagram against its rendering. Only classes that are rendered and have layout data
     * in the diagram are compared: classes rendered without layout data (e.g. an external class
     * that newly relates to the package) have no edge diagram objects yet, and classes that keep
     * their layout data while no longer rendered keep their edges invisibly. Between the compared
     * classes the edge diagram objects are exactly the rendered edges. Additionally every edge
     * diagram object references both of its identified objects and every class point is glued.
     *
     * @param associationEndpoints the classes every association end connects, as identified in the
     *     diagram
     */
    private static void assertConsistent(
            Model model,
            UUID diagramUUID,
            SvelteFlowDTO rendering,
            Map<UUID, Set<UUID>> associationEndpoints) {
        var diagramMRID = new MRID(diagramUUID);
        var classDOs = DLObjectFetcher.fetchDiagramClassDOs(model, diagramMRID);
        var renderedNodes =
                rendering.getNodes().stream().map(NodeDTO::getId).collect(Collectors.toSet());
        var comparedClasses =
                classDOs.stream()
                        .map(classDO -> classDO.getBelongsToIdentifiedObject().getUuid())
                        .filter(renderedNodes::contains)
                        .collect(Collectors.toSet());

        var renderedEdges =
                rendering.getEdges().stream()
                        .filter(edge -> comparedClasses.contains(edge.getSource()))
                        .filter(edge -> comparedClasses.contains(edge.getTarget()))
                        .map(EdgeLayoutConsistencyTest::keyOf)
                        .collect(Collectors.toSet());
        var storedEdges =
                DLObjectFetcher.fetchDiagramEdgeDOs(model, diagramMRID).stream()
                        .map(EdgeKey::of)
                        .toList();
        assertThat(storedEdges).doesNotContainNull().doesNotHaveDuplicates();
        var comparedStoredEdges =
                storedEdges.stream()
                        .filter(
                                edge ->
                                        comparedClasses.containsAll(
                                                endpointsOf(edge, associationEndpoints)))
                        .collect(Collectors.toSet());

        var missingEdges = new HashSet<>(renderedEdges);
        missingEdges.removeAll(comparedStoredEdges);
        var surplusEdges = new HashSet<>(comparedStoredEdges);
        surplusEdges.removeAll(renderedEdges);
        assertThat(missingEdges)
                .as("rendered edges without diagram object in %s", diagramUUID)
                .isEmpty();
        assertThat(surplusEdges)
                .as("diagram objects of edges that are not rendered in %s", diagramUUID)
                .isEmpty();
        assertThat(classDOs)
                .allSatisfy(
                        classDO ->
                                assertThat(
                                                DLObjectFetcher.fetchGluePointForDO(
                                                        model, classDO.getMRID()))
                                        .as("glue point of %s", classDO.getName())
                                        .isNotNull());
    }

    /**
     * The classes an edge connects. An association end that is not part of the schema has no known
     * classes, so its edge diagram object is always compared and shows up as surplus.
     */
    private static Set<UUID> endpointsOf(EdgeKey edge, Map<UUID, Set<UUID>> associationEndpoints) {
        if (edge.style() == DiagramObjectStyle.INHERITANCE) {
            return Set.of(edge.identifiedObject(), edge.otherClass());
        }
        return associationEndpoints.getOrDefault(edge.identifiedObject(), Set.of());
    }

    private Map<UUID, Set<UUID>> associationEndpointsOfEquipment() {
        try (var ctx = databasePort.getGraphWithContext(equipment).begin(ReadWrite.READ)) {
            var model =
                    new CIMModelFacade(
                            equipment.graphUri(),
                            ModelFactory.createModelForGraph(ctx.getRdfGraph()));
            var endpoints = new HashMap<UUID, Set<UUID>>();
            for (var cimClass : model.getCIMClasses()) {
                for (var association : cimClass.getAssociations()) {
                    if (association.isRenderable()) {
                        endpoints.put(
                                association.getUuid(),
                                Set.of(cimClass.getUuid(), association.getRange().getUuid()));
                    }
                }
            }
            return endpoints;
        }
    }

    private Map<UUID, Set<UUID>> mergedAssociationEndpoints() {
        var endpoints = new HashMap<UUID, Set<UUID>>();
        for (var profile : CIMProfileModels.loadAll(databasePort, Map.of(), DATASET, null)) {
            for (var cimClass : profile.model().getCIMClasses()) {
                for (var association : cimClass.getAssociations()) {
                    if (association.isRenderable()) {
                        endpoints.put(
                                mergedUuidOf(association.getUri().toString()),
                                Set.of(
                                        mergedUuidOf(cimClass.getUri().toString()),
                                        mergedUuidOf(association.getRange().getUri().toString())));
                    }
                }
            }
        }
        return endpoints;
    }

    private static UUID mergedUuidOf(String uri) {
        return CrossProfileUtils.mergedUuid(uri);
    }

    private static EdgeKey keyOf(EdgeDTO edge) {
        if ("inheritance".equals(edge.getType())) {
            return EdgeKey.inheritance(edge.getSource(), edge.getTarget());
        }
        var data = edge.getData();
        return EdgeKey.association(
                data.getTargetMultiplicityLabel().getIdentifiedObjectUUID(),
                data.getSourceMultiplicityLabel().getIdentifiedObjectUUID());
    }

    private SvelteFlowDTO renderPackage(UUID packageUUID) {
        var filter = new GraphFilter(true);
        filter.setIncludePropertiesFromOtherProfiles(false);
        filter.setPackageUUID(packageUUID.toString());
        return (SvelteFlowDTO)
                renderingDataService.getRenderingData(equipment, filter, packageUUID);
    }

    private static List<ClassPositionDTO> positionsOf(SvelteFlowDTO rendering) {
        var positions = new ArrayList<ClassPositionDTO>();
        var x = 0.0F;
        for (var node : rendering.getNodes()) {
            var position = new ClassPositionDTO();
            position.setClassUUID(node.getId());
            position.setXPosition(x);
            position.setYPosition(100.0F);
            positions.add(position);
            x += 250.0F;
        }
        return positions;
    }

    private List<UUID> packageUUIDs() {
        return new GraphToCIMCollectionConverterService(databasePort)
                .convert(equipment, new GraphFilter(false)).getPackages().stream()
                        .map(cimPackage -> cimPackage.getUuid())
                        .toList();
    }

    private Set<EdgeKey> edgesOf(UUID packageUUID) {
        return DLObjectFetcher.fetchDiagramEdgeDOs(graphLayout(), new MRID(packageUUID)).stream()
                .map(EdgeKey::of)
                .collect(Collectors.toSet());
    }

    private Model graphLayout() {
        return databasePort
                .getGraphWithContext(equipment)
                .getDiagramLayout()
                .getDiagramLayoutModelDirect();
    }

    private record SchemaClass(UUID uuid, String uri, String namespace, UUID packageUUID) {}

    private SchemaClass classOf(String label) {
        try (var ctx = databasePort.getGraphWithContext(equipment).begin(ReadWrite.READ)) {
            var model =
                    new CIMModelFacade(
                            equipment.graphUri(),
                            ModelFactory.createModelForGraph(ctx.getRdfGraph()));
            var cimClass =
                    model.getCIMClasses().stream()
                            .filter(candidate -> label.equals(candidate.getLabel().getValue()))
                            .findFirst()
                            .orElseThrow();
            return new SchemaClass(
                    cimClass.getUuid(),
                    cimClass.getUri().toString(),
                    cimClass.getUri().getPrefix(),
                    cimClass.getBelongsToCategory().getUuid());
        }
    }

    private UUID uuidOf(String label) {
        return classOf(label).uuid();
    }

    private UUID packageOf(String label) {
        return classOf(label).packageUUID();
    }

    private void editSchema(Consumer<Model> edit) {
        try (var ctx = databasePort.getGraphWithContext(equipment).begin(ReadWrite.WRITE)) {
            edit.accept(ModelFactory.createModelForGraph(ctx.getRdfGraph()));
            ctx.commit("edit schema");
        }
    }

    private AssociationPairDTO associationPair(String fromLabel, String toLabel) {
        var from = classOf(fromLabel);
        var to = classOf(toLabel);
        var prefix = from.namespace();
        var fromEnd =
                AssociationDTO.builder()
                        .prefix(prefix)
                        .label("EdgeLayoutTest" + toLabel)
                        .multiplicity("M:0..n")
                        .domain(from.uri())
                        .range(new DataTypeDTO(toLabel, to.namespace()))
                        .associationUsed(true)
                        .build();
        var toEnd =
                AssociationDTO.builder()
                        .prefix(prefix)
                        .label("EdgeLayoutTest" + fromLabel)
                        .multiplicity("M:1..1")
                        .domain(to.uri())
                        .range(new DataTypeDTO(fromLabel, prefix))
                        .associationUsed(false)
                        .build();
        return new AssociationPairDTO(fromEnd, toEnd);
    }
}
