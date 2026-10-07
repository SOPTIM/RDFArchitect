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
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.CustomDiagramDTO;
import org.rdfarchitect.api.dto.dl.BendPointDTO;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.rdf.resources.DL;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.services.diagrams.CustomDiagramService;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.rdfarchitect.services.dl.update.SyncDiagramLayoutService;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassLayoutService;
import org.rdfarchitect.services.dl.update.edgelayout.EdgeKey;
import org.rdfarchitect.services.dl.update.edgelayout.UpdateEdgeLayoutDataService;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Edge diagram objects are created lazily, like the layout data of classes: an edge gets one as
 * soon as both of its classes have layout data in a diagram, and loses it as soon as the edge or
 * one of the classes is gone from the diagram.
 */
class EdgeLayoutTest {

    private static final String DATASET = "ds";
    private static final String GRAPH_ONE = "http://example.com/one";
    private static final String GRAPH_TWO = "http://example.com/two";

    private static final UUID PACKAGE = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000001");
    private static final UUID SUB = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000002");
    private static final UUID SUPER = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000003");
    private static final UUID OTHER_SUPER = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000004");
    private static final UUID TARGET = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000005");
    private static final UUID SUB_TO_TARGET =
            UUID.fromString("0a0a0a0a-0000-4000-8000-000000000006");
    private static final UUID TARGET_TO_SUB =
            UUID.fromString("0a0a0a0a-0000-4000-8000-000000000007");

    private static final String PREFIXES =
            """
            @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix cims: <http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#> .
            @prefix ex:   <http://example.com#> .
            @prefix id:   <http://example.org#> .
            """;

    private static final String SCHEMA_ONE =
            PREFIXES
                    + """
                    ex:Package_p id:uuid "%s" ; a cims:ClassCategory ; rdfs:label "p"@en .

                    ex:Super a rdfs:Class ; id:uuid "%s" ; rdfs:label "Super"@en ;
                        cims:belongsToCategory ex:Package_p .
                    ex:OtherSuper a rdfs:Class ; id:uuid "%s" ; rdfs:label "OtherSuper"@en ;
                        cims:belongsToCategory ex:Package_p .
                    ex:Sub a rdfs:Class ; id:uuid "%s" ; rdfs:label "Sub"@en ;
                        rdfs:subClassOf ex:Super ;
                        cims:belongsToCategory ex:Package_p .
                    ex:Target a rdfs:Class ; id:uuid "%s" ; rdfs:label "Target"@en ;
                        cims:belongsToCategory ex:Package_p .

                    ex:Sub.target a rdf:Property ; id:uuid "%s" ; rdfs:label "target"@en ;
                        rdfs:domain ex:Sub ; rdfs:range ex:Target ;
                        cims:inverseRoleName ex:Target.sub ;
                        cims:multiplicity cims:M:0..1 ; cims:AssociationUsed "Yes" .
                    ex:Target.sub a rdf:Property ; id:uuid "%s" ; rdfs:label "sub"@en ;
                        rdfs:domain ex:Target ; rdfs:range ex:Sub ;
                        cims:inverseRoleName ex:Sub.target ;
                        cims:multiplicity cims:M:0..n ; cims:AssociationUsed "No" .
                    """
                            .formatted(
                                    PACKAGE,
                                    SUPER,
                                    OTHER_SUPER,
                                    SUB,
                                    TARGET,
                                    SUB_TO_TARGET,
                                    TARGET_TO_SUB);

    /** A second profile in which the same class has another super class. */
    private static final String SCHEMA_TWO =
            PREFIXES
                    + """
                    ex:OtherSuper a rdfs:Class ; id:uuid "%s" ; rdfs:label "OtherSuper"@en .
                    ex:Sub a rdfs:Class ; id:uuid "%s" ; rdfs:label "Sub"@en ;
                        rdfs:subClassOf ex:OtherSuper .
                    """
                            .formatted(UUID.randomUUID(), UUID.randomUUID());

    private DatabasePort databasePort;
    private UpdateClassLayoutService classLayoutService;
    private UpdateEdgeLayoutDataService edgeLayoutService;
    private SyncDiagramLayoutService syncDiagramLayoutService;
    private GraphIdentifier graphOne;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        classLayoutService =
                new UpdateClassLayoutService(databasePort, Mappers.getMapper(PackageMapper.class));
        edgeLayoutService = new UpdateEdgeLayoutDataService(databasePort);
        syncDiagramLayoutService = new SyncDiagramLayoutService(databasePort);
        graphOne = addGraph(GRAPH_ONE, SCHEMA_ONE);
    }

    @Test
    void positionsSentAfterLayouting_createADiagramObjectForEveryEdge() {
        layoutClasses(SUB, SUPER, OTHER_SUPER, TARGET);

        assertThat(edgesOf(PACKAGE))
                .containsExactlyInAnyOrder(
                        EdgeKey.inheritance(SUB, SUPER),
                        EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB));

        var inheritance = edgeDO(PACKAGE, EdgeKey.inheritance(SUB, SUPER));
        assertThat(inheritance.getName()).isEqualTo("Sub Super");
        var otherClassTriple =
                graphLayout()
                        .contains(
                                ResourceFactory.createResource(inheritance.getMRID().getFullMRID()),
                                DL.otherClass,
                                ResourceFactory.createResource(new MRID(SUPER).getFullMRID()));
        assertThat(otherClassTriple).isTrue();
        assertThat(DLObjectFetcher.fetchDOPsForDO(graphLayout(), inheritance.getMRID())).isEmpty();
    }

    @Test
    void edgeGetsItsDiagramObjectOnceBothClassesHaveLayoutData() {
        layoutClasses(SUB);
        assertThat(edgesOf(PACKAGE)).isEmpty();

        layoutClasses(SUPER);
        assertThat(edgesOf(PACKAGE)).containsExactly(EdgeKey.inheritance(SUB, SUPER));
    }

    @Test
    void movingClasses_keepsTheEdgeDiagramObjectAndItsBendPoints() {
        layoutClasses(SUB, SUPER, OTHER_SUPER, TARGET);
        var edge = EdgeKey.inheritance(SUB, SUPER);
        edgeLayoutService.updateBendPoints(
                graphOne, PACKAGE, edge, List.of(bendPoint(10, 20, 0), bendPoint(30, 40, 1)));
        var edgeMRID = edgeDO(PACKAGE, edge).getMRID();

        layoutClasses(SUB, SUPER);

        assertThat(edgeDO(PACKAGE, edge).getMRID()).isEqualTo(edgeMRID);
        assertThat(DLObjectFetcher.fetchDOPsForDO(graphLayout(), edgeMRID))
                .extracting(point -> point.getPosition().getX())
                .containsExactly(10.0F, 30.0F);
    }

    @Test
    void changingTheSuperClass_replacesTheInheritanceEdge() {
        layoutClasses(SUB, SUPER, OTHER_SUPER, TARGET);
        var oldEdge = EdgeKey.inheritance(SUB, SUPER);
        edgeLayoutService.updateBendPoints(
                graphOne, PACKAGE, oldEdge, List.of(bendPoint(10, 20, 0)));
        var oldEdgeMRID = edgeDO(PACKAGE, oldEdge).getMRID();

        editSchema(
                model -> {
                    var sub = model.getResource("http://example.com#Sub");
                    model.removeAll(sub, RDFS.subClassOf, null);
                    sub.addProperty(
                            RDFS.subClassOf, model.getResource("http://example.com#OtherSuper"));
                });
        syncDiagramLayoutService.syncDiagramLayout(graphOne);

        assertThat(edgesOf(PACKAGE))
                .containsExactlyInAnyOrder(
                        EdgeKey.inheritance(SUB, OTHER_SUPER),
                        EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB));
        assertThat(DLObjectFetcher.fetchDOPsForDO(graphLayout(), oldEdgeMRID)).isEmpty();
    }

    @Test
    void deletingAnAssociation_dropsItsEdgeTogetherWithItsLabels() {
        layoutClasses(SUB, SUPER, OTHER_SUPER, TARGET);
        try (var ctx = databasePort.getGraphWithContext(graphOne).begin(ReadWrite.WRITE)) {
            DiagramLayoutServiceUtils.insertLabel(
                    ctx.getDiagramLayout().getDiagramLayoutModel(),
                    PACKAGE,
                    TARGET_TO_SUB,
                    DiagramObjectStyle.MULTIPLICITY,
                    5,
                    5);
            ctx.commit();
        }
        assertThat(DLObjectFetcher.fetchDiagramLabelDOs(graphLayout(), PACKAGE)).hasSize(1);

        editSchema(
                model -> {
                    model.removeAll(model.getResource("http://example.com#Sub.target"), null, null);
                    model.removeAll(model.getResource("http://example.com#Target.sub"), null, null);
                });
        syncDiagramLayoutService.syncDiagramLayout(graphOne);

        assertThat(edgesOf(PACKAGE)).containsExactly(EdgeKey.inheritance(SUB, SUPER));
        assertThat(DLObjectFetcher.fetchDiagramLabelDOs(graphLayout(), PACKAGE)).isEmpty();
    }

    @Test
    void removingAClassFromACustomDiagram_dropsItsEdgesThere() {
        var diagramUUID = UUID.randomUUID();
        classLayoutService.addClassesToCustomDiagram(
                graphOne,
                diagramUUID,
                new ArrayList<>(
                        List.of(
                                classInGraphOne(SUB),
                                classInGraphOne(SUPER),
                                classInGraphOne(TARGET))));
        assertThat(edgesOf(diagramUUID))
                .containsExactlyInAnyOrder(
                        EdgeKey.inheritance(SUB, SUPER),
                        EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB));

        classLayoutService.removeClassesFromCustomDiagram(graphOne, diagramUUID, List.of(SUPER));

        assertThat(edgesOf(diagramUUID))
                .containsExactly(EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB));
    }

    @Test
    void replacingACustomDiagramWithoutAClass_dropsItsLayoutData() {
        var customDiagramService =
                new CustomDiagramService(
                        databasePort, datasetName -> List.of(), syncDiagramLayoutService);
        var diagramUUID = UUID.randomUUID();
        customDiagramService.replaceCustomGraphDiagram(
                graphOne, diagramUUID.toString(), customDiagram(diagramUUID, SUB, SUPER, TARGET));
        classLayoutService.updateClassPositions(
                graphOne, diagramUUID, positions(SUB, SUPER, TARGET));

        customDiagramService.replaceCustomGraphDiagram(
                graphOne, diagramUUID.toString(), customDiagram(diagramUUID, SUB, TARGET));

        assertThat(classesOf(diagramUUID)).containsExactlyInAnyOrder(SUB, TARGET);
        assertThat(edgesOf(diagramUUID))
                .containsExactly(EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB));
    }

    @Test
    void deletingACustomDiagram_deletesItsLayoutData() {
        var customDiagramService =
                new CustomDiagramService(
                        databasePort, datasetName -> List.of(), syncDiagramLayoutService);
        var diagramUUID = UUID.randomUUID();
        customDiagramService.replaceCustomGraphDiagram(
                graphOne, diagramUUID.toString(), customDiagram(diagramUUID, SUB, SUPER));
        classLayoutService.updateClassPositions(graphOne, diagramUUID, positions(SUB, SUPER));

        customDiagramService.deleteCustomGraphDiagram(graphOne, diagramUUID.toString());

        assertThat(DLObjectFetcher.fetchDiagramMRIDs(graphLayout()))
                .doesNotContain(new MRID(diagramUUID));
        assertThat(DLObjectFetcher.fetchAllDOs(graphLayout(), SUB)).isEmpty();
    }

    @Test
    void deletingTheLayoutDataOfAClass_dropsItsEdges() {
        layoutClasses(SUB, SUPER, OTHER_SUPER, TARGET);

        classLayoutService.deleteClassLayoutData(graphOne, SUPER);

        assertThat(edgesOf(PACKAGE))
                .containsExactly(EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB));
    }

    @Test
    void labelsAreNotMistakenForEdges() {
        layoutClasses(SUB, SUPER, OTHER_SUPER, TARGET);

        assertThat(DLObjectFetcher.fetchAllLabelDOs(graphLayout())).isEmpty();
        assertThat(DLObjectFetcher.fetchLabelPositions(graphLayout(), PACKAGE)).isEmpty();
    }

    @Test
    void mergedDiagram_getsAnInheritanceEdgeForEverySuperClassAcrossProfiles() {
        addGraph(GRAPH_TWO, SCHEMA_TWO);
        var diagramUUID = UUID.randomUUID();

        classLayoutService.updateClassPositions(
                DATASET,
                diagramUUID,
                positions(merged("Sub"), merged("Super"), merged("OtherSuper")));

        assertThat(edgesOf(datasetLayout(), diagramUUID))
                .containsExactlyInAnyOrder(
                        EdgeKey.inheritance(merged("Sub"), merged("Super")),
                        EdgeKey.inheritance(merged("Sub"), merged("OtherSuper")));
    }

    @Test
    void crossProfileDiagram_gluesItsClassesAndCreatesTheirEdges() {
        var customDiagramService =
                new CustomDiagramService(
                        databasePort, datasetName -> List.of(), syncDiagramLayoutService);

        var crossProfileDiagram =
                customDiagramService.getCrossProfileDiagram(
                        DATASET,
                        true,
                        CIMProfileModels.loadAll(databasePort, Map.of(), DATASET, null));

        var diagramUUID = crossProfileDiagram.getDiagramId();
        var model = datasetLayout();
        assertThat(DLObjectFetcher.fetchDiagramClassDOs(model, new MRID(diagramUUID)))
                .isNotEmpty()
                .allSatisfy(
                        classDO ->
                                assertThat(
                                                DLObjectFetcher.fetchGluePointForDO(
                                                        model, classDO.getMRID()))
                                        .isNotNull());
        assertThat(edgesOf(model, diagramUUID))
                .containsExactlyInAnyOrder(
                        EdgeKey.inheritance(merged("Sub"), merged("Super")),
                        EdgeKey.association(merged("Sub.target"), merged("Target.sub")));
    }

    @Test
    void associationKey_doesNotDependOnWhichEndComesFirst() {
        assertThat(EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB))
                .isEqualTo(EdgeKey.association(TARGET_TO_SUB, SUB_TO_TARGET));
    }

    private GraphIdentifier addGraph(String graphUri, String schema) {
        var file =
                new MockMultipartFile(
                        "file",
                        "schema.ttl",
                        "text/turtle",
                        schema.getBytes(StandardCharsets.UTF_8));
        var graph =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(graphUri)
                        .build()
                        .graph();
        var identifier = new GraphIdentifier(DATASET, graphUri);
        databasePort.createGraph(identifier, graph);
        return identifier;
    }

    private void editSchema(Consumer<Model> edit) {
        try (var ctx = databasePort.getGraphWithContext(graphOne).begin(ReadWrite.WRITE)) {
            edit.accept(ModelFactory.createModelForGraph(ctx.getRdfGraph()));
            ctx.commit("edit schema");
        }
    }

    private void layoutClasses(UUID... classUUIDs) {
        classLayoutService.updateClassPositions(graphOne, PACKAGE, positions(classUUIDs));
    }

    private static List<ClassPositionDTO> positions(UUID... classUUIDs) {
        var positions = new ArrayList<ClassPositionDTO>();
        for (var i = 0; i < classUUIDs.length; i++) {
            var position = new ClassPositionDTO();
            position.setClassUUID(classUUIDs[i]);
            position.setXPosition(100.0F * i);
            position.setYPosition(50.0F);
            positions.add(position);
        }
        return positions;
    }

    private static BendPointDTO bendPoint(float x, float y, int sequenceNumber) {
        var bendPoint = new BendPointDTO();
        bendPoint.setXPosition(x);
        bendPoint.setYPosition(y);
        bendPoint.setSequenceNumber(sequenceNumber);
        return bendPoint;
    }

    private static CustomDiagramDTO customDiagram(UUID diagramUUID, UUID... classUUIDs) {
        var classes = new ArrayList<ClassInDiagram>();
        for (var classUUID : classUUIDs) {
            classes.add(classInGraphOne(classUUID));
        }
        return new CustomDiagramDTO(diagramUUID, "custom", classes);
    }

    private Set<UUID> classesOf(UUID diagramUUID) {
        return DLObjectFetcher.fetchDiagramClassDOs(graphLayout(), new MRID(diagramUUID)).stream()
                .map(classDO -> classDO.getBelongsToIdentifiedObject().getUuid())
                .collect(Collectors.toSet());
    }

    private static ClassInDiagram classInGraphOne(UUID classUUID) {
        return new ClassInDiagram(classUUID, new URI(GRAPH_ONE));
    }

    private static UUID merged(String localName) {
        return CrossProfileUtils.mergedUuid("http://example.com#" + localName);
    }

    private Model graphLayout() {
        return databasePort
                .getGraphWithContext(graphOne)
                .getDiagramLayout()
                .getDiagramLayoutModelDirect();
    }

    private Model datasetLayout() {
        return databasePort.getDatasetDiagramLayout(DATASET).getDiagramLayoutModel();
    }

    private Set<EdgeKey> edgesOf(UUID diagramUUID) {
        return edgesOf(graphLayout(), diagramUUID);
    }

    private static Set<EdgeKey> edgesOf(Model model, UUID diagramUUID) {
        return DLObjectFetcher.fetchDiagramEdgeDOs(model, new MRID(diagramUUID)).stream()
                .map(EdgeKey::of)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private DiagramObject edgeDO(UUID diagramUUID, EdgeKey edge) {
        return DLObjectFetcher.fetchDiagramEdgeDOs(graphLayout(), new MRID(diagramUUID)).stream()
                .filter(diagramObject -> edge.equals(EdgeKey.of(diagramObject)))
                .findFirst()
                .orElseThrow();
    }
}
