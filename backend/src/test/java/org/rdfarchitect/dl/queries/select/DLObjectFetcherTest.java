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

package org.rdfarchitect.dl.queries.select;

import static org.assertj.core.api.Assertions.assertThat;
import static org.rdfarchitect.dl.DLTestData.ASSOCIATION_DO;
import static org.rdfarchitect.dl.DLTestData.ASSOCIATION_END;
import static org.rdfarchitect.dl.DLTestData.CUSTOM_DIAGRAM;
import static org.rdfarchitect.dl.DLTestData.INHERITANCE_BEND_POINT;
import static org.rdfarchitect.dl.DLTestData.INHERITANCE_DO;
import static org.rdfarchitect.dl.DLTestData.INHERITANCE_SOURCE_POINT;
import static org.rdfarchitect.dl.DLTestData.INHERITANCE_TARGET_POINT;
import static org.rdfarchitect.dl.DLTestData.INVERSE_ASSOCIATION_END;
import static org.rdfarchitect.dl.DLTestData.LABEL_DO;
import static org.rdfarchitect.dl.DLTestData.LABEL_POINT;
import static org.rdfarchitect.dl.DLTestData.PACKAGE_DIAGRAM;
import static org.rdfarchitect.dl.DLTestData.SUB;
import static org.rdfarchitect.dl.DLTestData.SUB_DO;
import static org.rdfarchitect.dl.DLTestData.SUB_DO_IN_CUSTOM_DIAGRAM;
import static org.rdfarchitect.dl.DLTestData.SUB_GLUE_POINT;
import static org.rdfarchitect.dl.DLTestData.SUB_POINT;
import static org.rdfarchitect.dl.DLTestData.SUPER;
import static org.rdfarchitect.dl.DLTestData.SUPER_DO;
import static org.rdfarchitect.dl.DLTestData.SUPER_GLUE_POINT;
import static org.rdfarchitect.dl.DLTestData.TARGET;
import static org.rdfarchitect.dl.DLTestData.TARGET_DO;

import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.dl.DLTestData;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.EdgeKey;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.XYZPosition;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher.EdgePoint;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher.LabelKey;
import org.rdfarchitect.dl.queries.update.DLUpdates;

import java.util.UUID;

class DLObjectFetcherTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = DLTestData.load();
    }

    @Test
    void fetchDiagram_returnsTheDiagramWithItsName_orNullIfThereIsNone() {
        var diagram = DLObjectFetcher.fetchDiagram(model, PACKAGE_DIAGRAM);

        assertThat(diagram.getMRID()).isEqualTo(new MRID(PACKAGE_DIAGRAM));
        assertThat(diagram.getName()).isEqualTo("Package");
        assertThat(DLObjectFetcher.fetchDiagram(model, UUID.randomUUID())).isNull();
    }

    @Test
    void fetchDiagramMRIDs_returnsAllDiagrams() {
        assertThat(DLObjectFetcher.fetchDiagramMRIDs(model))
                .containsExactlyInAnyOrder(new MRID(PACKAGE_DIAGRAM), new MRID(CUSTOM_DIAGRAM));
    }

    @Test
    void fetchDiagramDOPPerClass_returnsThePositionOfEveryClassOfTheDiagram_withZeroForAMissingZ() {
        var positions = DLObjectFetcher.fetchDiagramDOPPerClass(model, PACKAGE_DIAGRAM);

        assertThat(positions).containsOnlyKeys(SUB, SUPER, TARGET);
        assertThat(positions.get(SUB).getMRID()).isEqualTo(SUB_POINT);
        assertThat(positions.get(SUB).getPosition()).isEqualTo(new XYZPosition(10F, 20F, 1));
        assertThat(positions.get(TARGET).getPosition().getZ()).isZero();
    }

    @Test
    void fetchDOPsForDO_ordersThePointsBySequenceNumberAsNumber() {
        var points = DLObjectFetcher.fetchDOPsForDO(model, INHERITANCE_DO);

        assertThat(points)
                .extracting(DiagramObjectPoint::getMRID)
                .containsExactly(
                        INHERITANCE_SOURCE_POINT, INHERITANCE_BEND_POINT, INHERITANCE_TARGET_POINT);
        assertThat(points)
                .extracting(DiagramObjectPoint::getSequenceNumber)
                .containsExactly(0, 2, 10);
        assertThat(points)
                .extracting(DiagramObjectPoint::getBelongsToGluePoint)
                .containsExactly(SUB_GLUE_POINT, null, SUPER_GLUE_POINT);
        assertThat(points)
                .extracting(DiagramObjectPoint::getBelongsToDiagramObject)
                .containsOnly(INHERITANCE_DO);
    }

    @Test
    void fetchDOPsForDO_returnsNothingForADiagramObjectWithoutPoints() {
        assertThat(DLObjectFetcher.fetchDOPsForDO(model, ASSOCIATION_DO)).isEmpty();
    }

    @Test
    void fetchDOPsForDO_returnsTheSinglePointOfAClass() {
        var points = DLObjectFetcher.fetchDOPsForDO(model, SUB_DO);

        assertThat(points).extracting(DiagramObjectPoint::getMRID).containsExactly(SUB_POINT);
        assertThat(points.getFirst().getBelongsToGluePoint()).isEqualTo(SUB_GLUE_POINT);
    }

    @Test
    void fetchDiagramDOs_returnsTheDiagramObjectsOfEveryStyle() {
        assertThat(DLObjectFetcher.fetchDiagramDOs(model, new MRID(PACKAGE_DIAGRAM)))
                .extracting(DiagramObject::getMRID)
                .containsExactlyInAnyOrder(
                        SUB_DO, SUPER_DO, TARGET_DO, INHERITANCE_DO, ASSOCIATION_DO, LABEL_DO);
    }

    @Test
    void fetchDiagramClassDOs_returnsTheClassesOfTheDiagramOnly() {
        assertThat(DLObjectFetcher.fetchDiagramClassDOs(model, new MRID(PACKAGE_DIAGRAM)))
                .extracting(DiagramObject::getMRID)
                .containsExactlyInAnyOrder(SUB_DO, SUPER_DO, TARGET_DO);
        assertThat(DLObjectFetcher.fetchDiagramClassDOs(model, new MRID(CUSTOM_DIAGRAM)))
                .extracting(DiagramObject::getBelongsToIdentifiedObject)
                .containsExactly(new MRID(SUB));
    }

    @Test
    void fetchDiagramEdgeDOs_returnsTheEdgesWithBothIdentifiedObjects() {
        var edges = DLObjectFetcher.fetchDiagramEdgeDOs(model, new MRID(PACKAGE_DIAGRAM));

        assertThat(edges)
                .extracting(EdgeKey::of)
                .containsExactlyInAnyOrder(
                        EdgeKey.inheritance(SUB, SUPER),
                        EdgeKey.association(ASSOCIATION_END, INVERSE_ASSOCIATION_END));
        assertThat(edges)
                .filteredOn(edge -> edge.getMRID().equals(INHERITANCE_DO))
                .singleElement()
                .satisfies(
                        edge -> {
                            assertThat(edge.getName()).isEqualTo("Sub Super");
                            assertThat(edge.getBelongsToDiagramObjectStyle())
                                    .isEqualTo(DiagramObjectStyle.INHERITANCE);
                            assertThat(edge.getOtherClass()).isEqualTo(new MRID(SUPER));
                        });
        assertThat(DLObjectFetcher.fetchDiagramEdgeDOs(model, new MRID(CUSTOM_DIAGRAM))).isEmpty();
    }

    @Test
    void fetchEdgePoints_returnsTheOrderedPointsOfEveryEdgeWithTheClassesTheyAreGluedTo() {
        var edgePoints = DLObjectFetcher.fetchEdgePoints(model, PACKAGE_DIAGRAM);

        assertThat(edgePoints).containsOnlyKeys(EdgeKey.inheritance(SUB, SUPER));
        var inheritance = edgePoints.get(EdgeKey.inheritance(SUB, SUPER));
        assertThat(inheritance.identifiedObject()).isEqualTo(new MRID(SUB));
        assertThat(inheritance.points())
                .extracting(point -> point.point().getMRID())
                .containsExactly(
                        INHERITANCE_SOURCE_POINT, INHERITANCE_BEND_POINT, INHERITANCE_TARGET_POINT);
        assertThat(inheritance.points())
                .extracting(EdgePoint::gluedClass)
                .containsExactly(new MRID(SUB), null, new MRID(SUPER));
        assertThat(inheritance.points().get(1).point().getPosition())
                .isEqualTo(new XYZPosition(180F, 90F, 0));
    }

    @Test
    void fetchEdgePoints_isEmptyForADiagramWithoutEdges() {
        assertThat(DLObjectFetcher.fetchEdgePoints(model, CUSTOM_DIAGRAM)).isEmpty();
    }

    @Test
    void fetchEdgePoints_takesThePointsOfOneDiagramObjectIfAnEdgeHasTwo() {
        var secondDO =
                DiagramObject.builder()
                        .mRID(new MRID(UUID.randomUUID()))
                        .name("Sub Super")
                        .belongsToDiagram(new MRID(PACKAGE_DIAGRAM))
                        .belongsToIdentifiedObject(new MRID(SUB))
                        .otherClass(new MRID(SUPER))
                        .belongsToDiagramObjectStyle(DiagramObjectStyle.INHERITANCE)
                        .build();
        DLUpdates.insertDiagramObject(model, secondDO);
        DLUpdates.insertDiagramObjectPoint(
                model,
                DiagramObjectPoint.builder()
                        .mRID(new MRID(UUID.randomUUID()))
                        .position(new XYZPosition(1F, 1F, null))
                        .belongsToDiagramObject(secondDO.getMRID())
                        .sequenceNumber(0)
                        .build());

        var points =
                DLObjectFetcher.fetchEdgePoints(model, PACKAGE_DIAGRAM)
                        .get(EdgeKey.inheritance(SUB, SUPER))
                        .points();

        assertThat(points)
                .extracting(point -> point.point().getBelongsToDiagramObject())
                .containsOnly(points.getFirst().point().getBelongsToDiagramObject());
    }

    @Test
    void fetchAllDOs_returnsTheDiagramObjectsOfAnIdentifiedObjectAcrossDiagrams() {
        assertThat(DLObjectFetcher.fetchAllDOs(model, SUB))
                .extracting(DiagramObject::getMRID)
                .containsExactlyInAnyOrder(SUB_DO, SUB_DO_IN_CUSTOM_DIAGRAM, INHERITANCE_DO);
        assertThat(DLObjectFetcher.fetchAllDOs(model, SUB, DiagramObjectStyle.CLASS))
                .extracting(DiagramObject::getMRID)
                .containsExactlyInAnyOrder(SUB_DO, SUB_DO_IN_CUSTOM_DIAGRAM);
    }

    @Test
    void fetchDiagramDOForIdentifiedObject_findsTheDiagramObjectInTheGivenDiagramAndStyle() {
        assertThat(
                        DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                                        model, PACKAGE_DIAGRAM, SUB, DiagramObjectStyle.CLASS)
                                .getMRID())
                .isEqualTo(SUB_DO);
        assertThat(
                        DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                                        model, CUSTOM_DIAGRAM, SUB, DiagramObjectStyle.CLASS)
                                .getMRID())
                .isEqualTo(SUB_DO_IN_CUSTOM_DIAGRAM);
        assertThat(
                        DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                                        model, PACKAGE_DIAGRAM, SUB, DiagramObjectStyle.INHERITANCE)
                                .getMRID())
                .isEqualTo(INHERITANCE_DO);
        assertThat(
                        DLObjectFetcher.fetchDiagramDOForIdentifiedObject(
                                model, CUSTOM_DIAGRAM, SUPER, DiagramObjectStyle.CLASS))
                .isNull();
    }

    @Test
    void fetchGluePoints_resolveTheGluePointOfAClassAndOfAnEndPoint() {
        assertThat(DLObjectFetcher.fetchGluePointForDO(model, SUB_DO).getMRID())
                .isEqualTo(SUB_GLUE_POINT);
        assertThat(DLObjectFetcher.fetchGluePointForDOP(model, INHERITANCE_TARGET_POINT).getMRID())
                .isEqualTo(SUPER_GLUE_POINT);
        assertThat(DLObjectFetcher.fetchGluePointForDOP(model, INHERITANCE_BEND_POINT)).isNull();
        assertThat(DLObjectFetcher.fetchGluePointForDO(model, LABEL_DO)).isNull();
    }

    @Test
    void fetchLabelPositions_returnsTheLabelsOfTheDiagramByTheirKey() {
        var labels = DLObjectFetcher.fetchLabelPositions(model, PACKAGE_DIAGRAM);

        var key = new LabelKey(ASSOCIATION_END, DiagramObjectStyle.MULTIPLICITY);
        assertThat(labels).containsOnlyKeys(key);
        assertThat(labels.get(key).getMRID()).isEqualTo(LABEL_POINT);
        assertThat(labels.get(key).getPosition()).isEqualTo(new XYZPosition(25F, 250F, 0));
        assertThat(DLObjectFetcher.fetchLabelPositions(model, CUSTOM_DIAGRAM)).isEmpty();
    }

    @Test
    void fetchLabelDOs_findTheLabelsByKeyAndDiagram() {
        var key = new LabelKey(ASSOCIATION_END, DiagramObjectStyle.MULTIPLICITY);

        assertThat(DLObjectFetcher.fetchLabelDO(model, PACKAGE_DIAGRAM, key).getMRID())
                .isEqualTo(LABEL_DO);
        assertThat(DLObjectFetcher.fetchLabelDO(model, CUSTOM_DIAGRAM, key)).isNull();
        assertThat(
                        DLObjectFetcher.fetchLabelDO(
                                model,
                                PACKAGE_DIAGRAM,
                                new LabelKey(
                                        ASSOCIATION_END, DiagramObjectStyle.ASSOCIATION_LABEL)))
                .isNull();
        assertThat(DLObjectFetcher.fetchDiagramLabelDOs(model, PACKAGE_DIAGRAM))
                .extracting(DiagramObject::getMRID)
                .containsExactly(LABEL_DO);
        assertThat(DLObjectFetcher.fetchDiagramLabelDOs(model, CUSTOM_DIAGRAM)).isEmpty();
        assertThat(DLObjectFetcher.fetchAllLabelDOs(model))
                .extracting(DiagramObject::getMRID)
                .containsExactly(LABEL_DO);
    }
}
