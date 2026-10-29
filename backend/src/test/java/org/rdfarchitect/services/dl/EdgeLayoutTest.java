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
import static org.assertj.core.api.Assertions.tuple;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.rdfarchitect.api.dto.CustomDiagramDTO;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.api.dto.dl.DiagramLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgeLayoutDTO;
import org.rdfarchitect.api.dto.dl.EdgeLayoutDTO.Kind;
import org.rdfarchitect.api.dto.dl.EdgePointDTO;
import org.rdfarchitect.api.dto.dl.EdgePointDTO.Side;
import org.rdfarchitect.api.dto.dl.LabelPositionDTO;
import org.rdfarchitect.api.dto.packages.PackageMapper;
import org.rdfarchitect.api.dto.rendering.svelteflow.SvelteFlowDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EdgeDTO;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.database.inmemory.diagrams.ClassInDiagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.EdgeKey;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.rdf.resources.DL;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.models.dto.rendering.svelteflow.RenderMergedDiagramSvelteFlowService;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.GetRenderingDataService;
import org.rdfarchitect.services.diagrams.CrossProfileUtils;
import org.rdfarchitect.services.diagrams.CustomDiagramService;
import org.rdfarchitect.services.dl.select.QueryDiagramLayoutService;
import org.rdfarchitect.services.dl.update.DiagramLayoutServiceUtils;
import org.rdfarchitect.services.dl.update.SyncDiagramLayoutService;
import org.rdfarchitect.services.dl.update.UpdateDiagramLayoutService;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassLayoutService;
import org.rdfarchitect.services.dl.update.edgelayout.UpdateEdgeLayoutDataService;
import org.rdfarchitect.services.dl.update.labellayout.UpdateLabelLayoutService;
import org.rdfarchitect.services.rendering.CIMProfileModels;
import org.rdfarchitect.services.rendering.svelteflow.RenderCIMFacadeCollectionSvelteFlowService;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(inheritanceLayout(point("a", 10, 20), point("b", 30, 40))));
        var edgeMRID = edgeDO(PACKAGE, edge).getMRID();

        layoutClasses(SUB, SUPER);

        assertThat(edgeDO(PACKAGE, edge).getMRID()).isEqualTo(edgeMRID);
        assertThat(DLObjectFetcher.fetchDOPsForDO(graphLayout(), edgeMRID))
                .extracting(point -> point.getPosition().getX())
                .containsExactly(10.0D, 30.0D);
    }

    @Test
    void changingTheSuperClass_replacesTheInheritanceEdge() {
        layoutClasses(SUB, SUPER, OTHER_SUPER, TARGET);
        var oldEdge = EdgeKey.inheritance(SUB, SUPER);
        edgeLayoutService.updateEdgeLayouts(
                graphOne, PACKAGE, List.of(inheritanceLayout(point("a", 10, 20))));
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
    void savingAnEdge_storesItsPointsInOrderUnderTheirIdsAndGluesItsDockingPoints() {
        layoutClasses(SUB, SUPER);
        var ids = newIds(3);

        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        inheritanceLayout(
                                point(ids.get(0), 0, 0, Side.SOURCE),
                                point(ids.get(1), 50, 50),
                                point(ids.get(2), 100, 100, Side.TARGET))));

        var points = pointsOf(EdgeKey.inheritance(SUB, SUPER));
        assertThat(points)
                .extracting(point -> point.getMRID().getUuid().toString())
                .containsExactlyElementsOf(ids);
        assertThat(points)
                .extracting(DiagramObjectPoint::getSequenceNumber)
                .containsExactly(0, 1, 2);
        assertThat(points)
                .extracting(point -> point.getPosition().getX())
                .containsExactly(0D, 50D, 100D);
        assertThat(points)
                .extracting(DiagramObjectPoint::getBelongsToGluePoint)
                .containsExactly(gluePointOfClass(SUB), null, gluePointOfClass(SUPER));
    }

    @Test
    void aDockingPointIsGluedByItsSide_evenIfTheEdgeHasNoDockingPointAtTheOtherSide() {
        layoutClasses(SUB, SUPER);

        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(inheritanceLayout(point("m", 50, 50), point("t", 100, 100, Side.TARGET))));

        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER)))
                .extracting(DiagramObjectPoint::getBelongsToGluePoint)
                .containsExactly(null, gluePointOfClass(SUPER));
    }

    @Test
    void savingAgainWithTheStoredIds_keepsThePointsAndMovesOnlyTheChangedOne() {
        layoutClasses(SUB, SUPER);
        var ids = newIds(3);
        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        inheritanceLayout(
                                point(ids.get(0), 0, 0, Side.SOURCE),
                                point(ids.get(1), 50, 50),
                                point(ids.get(2), 100, 100, Side.TARGET))));

        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        inheritanceLayout(
                                point(ids.get(0), 0, 0, Side.SOURCE),
                                point(ids.get(1), 70, 20),
                                point(ids.get(2), 100, 100, Side.TARGET))));

        var points = pointsOf(EdgeKey.inheritance(SUB, SUPER));
        assertThat(points)
                .extracting(point -> point.getMRID().getUuid().toString())
                .containsExactlyElementsOf(ids);
        assertThat(points)
                .extracting(point -> point.getPosition().getX())
                .containsExactly(0D, 70D, 100D);
    }

    @Test
    void insertingAPoint_keepsTheStoredPointsAndShiftsTheFollowingSequenceNumbers() {
        layoutClasses(SUB, SUPER);
        var ids = newIds(3);
        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(inheritanceLayout(point(ids.get(0), 10, 10), point(ids.get(2), 30, 30))));

        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        inheritanceLayout(
                                point(ids.get(0), 10, 10),
                                point(ids.get(1), 20, 20),
                                point(ids.get(2), 30, 30))));

        var points = pointsOf(EdgeKey.inheritance(SUB, SUPER));
        assertThat(points)
                .extracting(point -> point.getMRID().getUuid().toString())
                .containsExactlyElementsOf(ids);
        assertThat(points)
                .extracting(DiagramObjectPoint::getSequenceNumber)
                .containsExactly(0, 1, 2);
    }

    @Test
    void pointsThatAreNotSentAnymore_areDeleted() {
        layoutClasses(SUB, SUPER);
        var ids = newIds(2);
        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(inheritanceLayout(point(ids.get(0), 10, 10), point(ids.get(1), 30, 30))));

        edgeLayoutService.updateEdgeLayouts(
                graphOne, PACKAGE, List.of(inheritanceLayout(point(ids.get(1), 30, 30))));
        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER)))
                .extracting(point -> point.getMRID().getUuid().toString())
                .containsExactly(ids.get(1));

        edgeLayoutService.updateEdgeLayouts(graphOne, PACKAGE, List.of(inheritanceLayout()));
        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER))).isEmpty();
    }

    @Test
    void anAssociationSavedFromEitherSide_isStoredInTheSameOrder() {
        layoutClasses(SUB, TARGET);
        assertThat(EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB).identifiedObject())
                .isEqualTo(SUB_TO_TARGET);

        var ids = newIds(3);
        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        edgeLayout(
                                Kind.ASSOCIATION,
                                TARGET_TO_SUB,
                                SUB_TO_TARGET,
                                TARGET,
                                SUB,
                                point(ids.get(0), 0, 0, Side.SOURCE),
                                point(ids.get(1), 50, 50),
                                point(ids.get(2), 100, 100, Side.TARGET))));

        var edge = EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB);
        assertThat(pointsOf(edge))
                .extracting(point -> point.getPosition().getX())
                .containsExactly(100D, 50D, 0D);
        assertThat(pointsOf(edge))
                .extracting(DiagramObjectPoint::getBelongsToGluePoint)
                .containsExactly(gluePointOfClass(SUB), null, gluePointOfClass(TARGET));

        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        edgeLayout(
                                Kind.ASSOCIATION,
                                SUB_TO_TARGET,
                                TARGET_TO_SUB,
                                SUB,
                                TARGET,
                                point(ids.get(2), 100, 100, Side.SOURCE),
                                point(ids.get(1), 50, 50),
                                point(ids.get(0), 0, 0, Side.TARGET))));

        assertThat(pointsOf(edge))
                .extracting(point -> point.getMRID().getUuid().toString())
                .containsExactly(ids.get(2), ids.get(1), ids.get(0));
    }

    @Test
    void aNewPointWhoseIdIsNoUuid_isStoredUnderAnMRIDOfItsOwn() {
        layoutClasses(SUB, SUPER);

        edgeLayoutService.updateEdgeLayouts(
                graphOne, PACKAGE, List.of(inheritanceLayout(point("not a uuid", 10, 10))));

        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER)))
                .singleElement()
                .extracting(point -> point.getMRID().getUuid())
                .isNotNull();
    }

    @Test
    void aNewPointWhoseIdIsTakenByAnotherEdge_isStoredUnderAnMRIDOfItsOwn() {
        layoutClasses(SUB, SUPER, TARGET);
        var id = newIds(1).getFirst();
        edgeLayoutService.updateEdgeLayouts(
                graphOne, PACKAGE, List.of(inheritanceLayout(point(id, 10, 10))));

        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        edgeLayout(
                                Kind.ASSOCIATION,
                                SUB_TO_TARGET,
                                TARGET_TO_SUB,
                                SUB,
                                TARGET,
                                point(id, 20, 20))));

        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER)))
                .extracting(point -> point.getMRID().getUuid().toString())
                .containsExactly(id);
        assertThat(pointsOf(EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB)))
                .extracting(point -> point.getMRID().getUuid().toString())
                .hasSize(1)
                .doesNotContain(id);
    }

    @Test
    void savingAnEdgeOfAClassWithoutLayoutData_createsTheClassLayoutDataAt00() {
        layoutClasses(SUB);

        edgeLayoutService.updateEdgeLayouts(
                graphOne, PACKAGE, List.of(inheritanceLayout(point("a", 10, 10))));

        assertThat(classesOf(PACKAGE)).containsExactlyInAnyOrder(SUB, SUPER);
        var superClassDO =
                DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                        graphLayout(), PACKAGE, SUPER, DiagramObjectStyle.CLASS);
        var superClassPoint =
                DiagramLayoutServiceUtils.classPointOf(graphLayout(), superClassDO.getMRID());
        assertThat(superClassPoint.getPosition().getX()).isZero();
        assertThat(superClassPoint.getPosition().getY()).isZero();
        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER))).hasSize(1);
    }

    @Test
    void edgesThatAreNotPartOfTheSchema_areIgnored() {
        layoutClasses(SUB, SUPER, OTHER_SUPER, TARGET);

        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        edgeLayout(
                                Kind.INHERITANCE,
                                SUB,
                                OTHER_SUPER,
                                SUB,
                                OTHER_SUPER,
                                point("a", 10, 10)),
                        edgeLayout(Kind.INHERITANCE, null, SUPER, SUB, SUPER, point("b", 10, 10))));

        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER))).isEmpty();
    }

    @Test
    void renderedEdges_carryTheStoredPoints_andSendingThemBackChangesNothing() {
        layoutClasses(SUB, SUPER);
        var ids = newIds(3);
        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        inheritanceLayout(
                                point(ids.get(0), 0, 0, Side.SOURCE),
                                point(ids.get(1), 50, 50),
                                point(ids.get(2), 100, 100, Side.TARGET))));

        var edgePoints = renderedEdge(EdgeKey.inheritance(SUB, SUPER)).getData().getEdgePoints();

        assertThat(edgePoints).extracting(EdgePointDTO::getId).containsExactlyElementsOf(ids);
        assertThat(edgePoints).extracting(EdgePointDTO::getX).containsExactly(0D, 50D, 100D);
        assertThat(edgePoints)
                .extracting(EdgePointDTO::getSide)
                .containsExactly(Side.SOURCE, null, Side.TARGET);

        var sentBack =
                edgePoints.stream()
                        .map(
                                point ->
                                        point(
                                                point.getId(),
                                                point.getX(),
                                                point.getY(),
                                                point.getSide()))
                        .toArray(EdgePointDTO[]::new);
        edgeLayoutService.updateEdgeLayouts(
                graphOne, PACKAGE, List.of(inheritanceLayout(sentBack)));
        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER)))
                .extracting(point -> point.getMRID().getUuid().toString())
                .containsExactlyElementsOf(ids);
    }

    @Test
    void moreThanTenPoints_keepTheirOrder() {
        layoutClasses(SUB, SUPER);
        var points = new EdgePointDTO[12];
        var expectedX = new ArrayList<Double>();
        for (var i = 0; i < points.length; i++) {
            points[i] = point("p" + i, i * 10D, 0);
            expectedX.add(i * 10D);
        }

        edgeLayoutService.updateEdgeLayouts(graphOne, PACKAGE, List.of(inheritanceLayout(points)));

        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER)))
                .extracting(point -> point.getPosition().getX())
                .containsExactlyElementsOf(expectedX);
        assertThat(renderedEdge(EdgeKey.inheritance(SUB, SUPER)).getData().getEdgePoints())
                .extracting(EdgePointDTO::getX)
                .containsExactlyElementsOf(expectedX);
    }

    @Test
    void anAssociationSavedFromTheOtherSide_isRenderedInTheDirectionItIsDrawn() {
        layoutClasses(SUB, TARGET);
        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        edgeLayout(
                                Kind.ASSOCIATION,
                                TARGET_TO_SUB,
                                SUB_TO_TARGET,
                                TARGET,
                                SUB,
                                point("atTarget", 0, 0, Side.SOURCE),
                                point("between", 50, 50),
                                point("atSub", 100, 100, Side.TARGET))));

        var edge = renderedEdge(EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB));

        var expectedX =
                SUB.equals(edge.getSource()) ? List.of(100D, 50D, 0D) : List.of(0D, 50D, 100D);
        assertThat(edge.getData().getEdgePoints())
                .extracting(EdgePointDTO::getX)
                .containsExactlyElementsOf(expectedX);
        assertThat(edge.getData().getEdgePoints())
                .extracting(EdgePointDTO::getSide)
                .containsExactly(Side.SOURCE, null, Side.TARGET);
    }

    @Test
    void diagramLayout_storesClassesBeforeTheEdgesBetweenThem() {
        var diagramLayout = new DiagramLayoutDTO();
        diagramLayout.setClasses(positions(SUB, SUPER));
        diagramLayout.setEdges(
                List.of(
                        inheritanceLayout(
                                point("s", 0, 50, Side.SOURCE), point("t", 100, 50, Side.TARGET))));

        new UpdateDiagramLayoutService(databasePort)
                .updateDiagramLayout(graphOne, PACKAGE, diagramLayout);

        assertThat(classesOf(PACKAGE)).containsExactlyInAnyOrder(SUB, SUPER);
        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER)))
                .extracting(DiagramObjectPoint::getBelongsToGluePoint)
                .containsExactly(gluePointOfClass(SUB), gluePointOfClass(SUPER));
    }

    @Test
    void movingAClass_movesTheDockingPointsGluedToIt() {
        layoutClasses(SUB, SUPER);
        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        inheritanceLayout(
                                point("s", 0, 60, Side.SOURCE),
                                point("m", 50, 60),
                                point("t", 100, 60, Side.TARGET))));

        classLayoutService.updateClassPositions(graphOne, PACKAGE, List.of(position(SUB, 10, 80)));

        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER)))
                .extracting(
                        point -> point.getPosition().getX(), point -> point.getPosition().getY())
                .containsExactly(tuple(10D, 90D), tuple(50D, 60D), tuple(100D, 60D));
    }

    @Test
    void movingBothClassesByTheSameDistance_movesTheWholeEdge() {
        layoutClasses(SUB, SUPER);
        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        inheritanceLayout(
                                point("s", 0, 60, Side.SOURCE),
                                point("m", 50, 60),
                                point("t", 100, 60, Side.TARGET))));

        classLayoutService.updateClassPositions(
                graphOne, PACKAGE, List.of(position(SUB, 10, 80), position(SUPER, 110, 80)));

        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER)))
                .extracting(
                        point -> point.getPosition().getX(), point -> point.getPosition().getY())
                .containsExactly(tuple(10D, 90D), tuple(60D, 90D), tuple(110D, 90D));
    }

    @Test
    void diagramLayoutWithAllEdges_deletesThePointsOfTheEdgesNotSent() {
        layoutClasses(SUB, SUPER, OTHER_SUPER, TARGET);
        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        inheritanceLayout(point("a", 10, 10)),
                        edgeLayout(
                                Kind.ASSOCIATION,
                                SUB_TO_TARGET,
                                TARGET_TO_SUB,
                                SUB,
                                TARGET,
                                point("b", 20, 20))));
        var diagramLayout = new DiagramLayoutDTO();
        diagramLayout.setEdges(List.of(inheritanceLayout(point("c", 30, 30))));
        diagramLayout.setAllEdges(true);

        new UpdateDiagramLayoutService(databasePort)
                .updateDiagramLayout(graphOne, PACKAGE, diagramLayout);

        assertThat(pointsOf(EdgeKey.inheritance(SUB, SUPER)))
                .extracting(point -> point.getPosition().getX())
                .containsExactly(30D);
        assertThat(pointsOf(EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB))).isEmpty();
    }

    @Test
    void mergedDiagramLayout_storesTheEdgesUnderTheMergedUuids() {
        var diagramUUID = UUID.randomUUID();
        var diagramLayout = new DiagramLayoutDTO();
        diagramLayout.setClasses(positions(merged("Sub"), merged("Super")));
        diagramLayout.setEdges(
                List.of(
                        edgeLayout(
                                Kind.INHERITANCE,
                                merged("Sub"),
                                merged("Super"),
                                merged("Sub"),
                                merged("Super"),
                                point("a", 10, 10))));

        new UpdateDiagramLayoutService(databasePort)
                .updateDiagramLayout(DATASET, diagramUUID, diagramLayout);

        var edgeDO =
                DLObjectFetcher.fetchDiagramEdgeDOs(datasetLayout(), new MRID(diagramUUID)).stream()
                        .filter(
                                diagramObject ->
                                        EdgeKey.inheritance(merged("Sub"), merged("Super"))
                                                .equals(EdgeKey.of(diagramObject)))
                        .findFirst()
                        .orElseThrow();
        assertThat(DLObjectFetcher.fetchDOPsForDO(datasetLayout(), edgeDO.getMRID())).hasSize(1);
    }

    @Test
    void labelPositionsOfAnEdgeKind_areIgnored() {
        layoutClasses(SUB, SUPER, OTHER_SUPER, TARGET);
        var label = new LabelPositionDTO();
        label.setIdentifiedObjectUUID(SUB_TO_TARGET);
        label.setKind("association");
        label.setX(5D);
        label.setY(5D);

        new UpdateLabelLayoutService(databasePort)
                .updateLabelPositions(graphOne, PACKAGE, List.of(label));

        assertThat(DLObjectFetcher.fetchDiagramEdgeDOs(graphLayout(), new MRID(PACKAGE)))
                .hasSize(2);
        assertThat(edgesOf(PACKAGE))
                .containsExactlyInAnyOrder(
                        EdgeKey.inheritance(SUB, SUPER),
                        EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB));
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
    void deletingTheLayoutDataOfAClass_dropsThePointsOfItsEdges_andKeepsTheOtherEdgesGlued() {
        layoutClasses(SUB, SUPER, OTHER_SUPER, TARGET);
        var association = EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB);
        edgeLayoutService.updateEdgeLayouts(
                graphOne,
                PACKAGE,
                List.of(
                        inheritanceLayout(
                                point("s", 0, 0, Side.SOURCE), point("t", 100, 0, Side.TARGET)),
                        edgeLayout(
                                Kind.ASSOCIATION,
                                SUB_TO_TARGET,
                                TARGET_TO_SUB,
                                SUB,
                                TARGET,
                                point("a", 0, 10, Side.SOURCE),
                                point("b", 50, 50),
                                point("c", 300, 50, Side.TARGET))));
        var inheritanceMRID = edgeDO(PACKAGE, EdgeKey.inheritance(SUB, SUPER)).getMRID();

        classLayoutService.deleteClassLayoutData(graphOne, SUPER);

        assertThat(edgesOf(PACKAGE)).containsExactly(association);
        assertThat(DLObjectFetcher.fetchDOPsForDO(graphLayout(), inheritanceMRID)).isEmpty();
        assertThat(pointsOf(association))
                .extracting(DiagramObjectPoint::getBelongsToGluePoint)
                .containsExactly(gluePointOfClass(SUB), null, gluePointOfClass(TARGET));
    }

    @Test
    void crossProfileDiagram_rendersTheStoredPointsUnderTheMergedUuids() {
        var customDiagramService =
                new CustomDiagramService(
                        databasePort, datasetName -> List.of(), syncDiagramLayoutService);
        var diagramUUID =
                customDiagramService
                        .getCrossProfileDiagram(
                                DATASET,
                                true,
                                CIMProfileModels.loadAll(databasePort, Map.of(), DATASET, null))
                        .getDiagramId();
        var ids = newIds(3);
        edgeLayoutService.updateEdgeLayouts(
                DATASET,
                diagramUUID,
                List.of(
                        edgeLayout(
                                Kind.INHERITANCE,
                                merged("Sub"),
                                merged("Super"),
                                merged("Sub"),
                                merged("Super"),
                                point(ids.get(0), 0, 0, Side.SOURCE),
                                point(ids.get(1), 50, 50),
                                point(ids.get(2), 100, 100, Side.TARGET))));

        var rendering =
                (SvelteFlowDTO)
                        new RenderMergedDiagramSvelteFlowService(
                                        databasePort,
                                        customDiagramService,
                                        new QueryDiagramLayoutService(databasePort),
                                        new RenderCIMFacadeCollectionSvelteFlowService(),
                                        datasetName -> List.of())
                                .renderCrossProfileDiagram(DATASET);

        var edgePoints =
                edgeIn(rendering, EdgeKey.inheritance(merged("Sub"), merged("Super")))
                        .getData()
                        .getEdgePoints();
        assertThat(edgePoints).extracting(EdgePointDTO::getId).containsExactlyElementsOf(ids);
        assertThat(edgePoints)
                .extracting(EdgePointDTO::getSide)
                .containsExactly(Side.SOURCE, null, Side.TARGET);
    }

    @Test
    void concurrentSavesOfAWorkspaceDiagram_keepOneDiagramObjectPerClass() throws Exception {
        var customDiagramService =
                new CustomDiagramService(
                        databasePort, datasetName -> List.of(), syncDiagramLayoutService);
        var renderer =
                new RenderMergedDiagramSvelteFlowService(
                        databasePort,
                        customDiagramService,
                        new QueryDiagramLayoutService(databasePort),
                        new RenderCIMFacadeCollectionSvelteFlowService(),
                        datasetName -> List.of());
        var sessionId = SessionContext.getSessionId();
        var errors = new CopyOnWriteArrayList<Throwable>();
        var layout = new DiagramLayoutDTO();
        layout.setClasses(
                positions(merged("Sub"), merged("Super"), merged("Target"), merged("OtherSuper")));
        layout.setEdges(
                List.of(
                        edgeLayout(
                                Kind.INHERITANCE,
                                merged("Sub"),
                                merged("Super"),
                                merged("Sub"),
                                merged("Super"),
                                point("s", 1, 1, Side.SOURCE))));

        for (var round = 0; round < 10; round++) {
            var diagramUUID = UUID.randomUUID();
            customDiagramService.replaceCustomDatasetDiagram(
                    DATASET,
                    diagramUUID.toString(),
                    customDiagram(diagramUUID, SUB, SUPER, TARGET, OTHER_SUPER));
            var start = new CountDownLatch(1);
            Runnable save =
                    () ->
                            inSession(
                                    sessionId,
                                    start,
                                    errors,
                                    () ->
                                            new UpdateDiagramLayoutService(databasePort)
                                                    .updateDiagramLayout(
                                                            DATASET, diagramUUID, layout));
            Runnable render =
                    () ->
                            inSession(
                                    sessionId,
                                    start,
                                    errors,
                                    () ->
                                            renderer.renderCustomDatasetDiagram(
                                                    DATASET, diagramUUID));
            var threads = List.of(new Thread(save), new Thread(save), new Thread(render));
            threads.forEach(Thread::start);
            start.countDown();
            for (var thread : threads) {
                thread.join();
            }

            assertThat(errors).isEmpty();
            assertThat(DLObjectFetcher.fetchDiagramClassDOs(datasetLayout(), new MRID(diagramUUID)))
                    .extracting(DiagramObject::getBelongsToIdentifiedObject)
                    .doesNotHaveDuplicates()
                    .hasSize(4);
        }
    }

    @Test
    void associationKey_doesNotDependOnWhichEndComesFirst() {
        assertThat(EdgeKey.association(SUB_TO_TARGET, TARGET_TO_SUB))
                .isEqualTo(EdgeKey.association(TARGET_TO_SUB, SUB_TO_TARGET));
    }

    private static void inSession(
            String sessionId, CountDownLatch start, List<Throwable> errors, Runnable action) {
        SessionContext.setSessionId(sessionId);
        try {
            start.await();
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add(e);
        } catch (RuntimeException e) {
            errors.add(e);
        } finally {
            SessionContext.clear();
        }
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
            position.setXPosition(100.0D * i);
            position.setYPosition(50.0D);
            positions.add(position);
        }
        return positions;
    }

    private static ClassPositionDTO position(UUID classUUID, double x, double y) {
        var position = new ClassPositionDTO();
        position.setClassUUID(classUUID);
        position.setXPosition(x);
        position.setYPosition(y);
        return position;
    }

    private static EdgeLayoutDTO inheritanceLayout(EdgePointDTO... points) {
        return edgeLayout(Kind.INHERITANCE, SUB, SUPER, SUB, SUPER, points);
    }

    private static EdgeLayoutDTO edgeLayout(
            Kind kind,
            UUID sourceObject,
            UUID targetObject,
            UUID sourceClass,
            UUID targetClass,
            EdgePointDTO... points) {
        var edgeLayout = new EdgeLayoutDTO();
        edgeLayout.setKind(kind);
        edgeLayout.setSourceObject(sourceObject);
        edgeLayout.setTargetObject(targetObject);
        edgeLayout.setSourceClass(sourceClass);
        edgeLayout.setTargetClass(targetClass);
        edgeLayout.setPoints(List.of(points));
        return edgeLayout;
    }

    private static EdgePointDTO point(String id, double x, double y) {
        return new EdgePointDTO(id, x, y, null);
    }

    private static EdgePointDTO point(String id, double x, double y, Side side) {
        return new EdgePointDTO(id, x, y, side);
    }

    private EdgeDTO renderedEdge(EdgeKey key) {
        var filter = new GraphFilter(true);
        filter.setIncludePropertiesFromOtherProfiles(false);
        filter.setPackageUUID(PACKAGE.toString());
        var rendering =
                (SvelteFlowDTO)
                        new GetRenderingDataService(
                                        databasePort,
                                        new RenderCIMFacadeCollectionSvelteFlowService(),
                                        datasetName -> List.of(),
                                        new QueryDiagramLayoutService(databasePort))
                                .getRenderingData(graphOne, filter, PACKAGE);
        return edgeIn(rendering, key);
    }

    private static EdgeDTO edgeIn(SvelteFlowDTO rendering, EdgeKey key) {
        return rendering.getEdges().stream()
                .filter(
                        edge ->
                                key.equals(
                                        EdgeKey.of(
                                                DiagramObjectStyle.byName(edge.getType()),
                                                edge.getData().getSourceObject(),
                                                edge.getData().getTargetObject())))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> newIds(int count) {
        return Stream.generate(() -> UUID.randomUUID().toString()).limit(count).toList();
    }

    private List<DiagramObjectPoint> pointsOf(EdgeKey edge) {
        return DLObjectFetcher.fetchDOPsForDO(graphLayout(), edgeDO(PACKAGE, edge).getMRID());
    }

    private MRID gluePointOfClass(UUID classUUID) {
        var classDO =
                DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                        graphLayout(), PACKAGE, classUUID, DiagramObjectStyle.CLASS);
        return DLObjectFetcher.fetchGluePointForDO(graphLayout(), classDO.getMRID()).getMRID();
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
