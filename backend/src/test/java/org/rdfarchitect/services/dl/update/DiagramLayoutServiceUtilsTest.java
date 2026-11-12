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

package org.rdfarchitect.services.dl.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.rdfarchitect.dl.DLTestData.ASSOCIATION_DO;
import static org.rdfarchitect.dl.DLTestData.INHERITANCE_BEND_POINT;
import static org.rdfarchitect.dl.DLTestData.INHERITANCE_DO;
import static org.rdfarchitect.dl.DLTestData.INHERITANCE_SOURCE_POINT;
import static org.rdfarchitect.dl.DLTestData.INHERITANCE_TARGET_POINT;
import static org.rdfarchitect.dl.DLTestData.PACKAGE_DIAGRAM;
import static org.rdfarchitect.dl.DLTestData.SUB;
import static org.rdfarchitect.dl.DLTestData.SUB_DO;
import static org.rdfarchitect.dl.DLTestData.SUB_GLUE_POINT;
import static org.rdfarchitect.dl.DLTestData.SUPER;
import static org.rdfarchitect.dl.DLTestData.SUPER_DO;
import static org.rdfarchitect.dl.DLTestData.SUPER_GLUE_POINT;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.dl.DLTestData;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.EdgeKey;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.XYZPosition;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.dl.rdf.resources.DL;

import java.util.UUID;

class DiagramLayoutServiceUtilsTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = DLTestData.load();
    }

    @Test
    void resolveClassGluePoint_returnsTheGluePointAClassHas() {
        assertThat(DiagramLayoutServiceUtils.resolveClassGluePoint(model, SUB_DO))
                .isEqualTo(SUB_GLUE_POINT);
    }

    @Test
    void resolveClassGluePoint_givesAClassWithoutGluePointOneOnce() {
        var classDO = new MRID(UUID.randomUUID());
        DLUpdates.insertDiagramObject(
                model,
                DiagramObject.builder()
                        .mRID(classDO)
                        .name("Legacy")
                        .belongsToDiagram(new MRID(PACKAGE_DIAGRAM))
                        .belongsToIdentifiedObject(new MRID(UUID.randomUUID()))
                        .belongsToDiagramObjectStyle(DiagramObjectStyle.CLASS)
                        .build());
        DLUpdates.insertDiagramObjectPoint(
                model,
                DiagramObjectPoint.builder()
                        .mRID(new MRID(UUID.randomUUID()))
                        .position(new XYZPosition(5F, 6F, 2))
                        .belongsToDiagramObject(classDO)
                        .build());

        var gluePoint = DiagramLayoutServiceUtils.resolveClassGluePoint(model, classDO);

        assertThat(gluePoint).isNotNull();
        assertThat(model.contains(resource(gluePoint), RDF.type, DL.diagramObjectGluePointType))
                .isTrue();
        var classPoint = DiagramLayoutServiceUtils.classPointOf(model, classDO);
        assertThat(classPoint.getBelongsToGluePoint()).isEqualTo(gluePoint);
        assertThat(classPoint.getPosition()).isEqualTo(new XYZPosition(5F, 6F, 2));
        assertThat(DiagramLayoutServiceUtils.resolveClassGluePoint(model, classDO))
                .isEqualTo(gluePoint);
    }

    @Test
    void resolveClassGluePoint_isNullForADiagramObjectWithoutPoint() {
        assertThat(DiagramLayoutServiceUtils.resolveClassGluePoint(model, ASSOCIATION_DO)).isNull();
    }

    @Test
    void insertEdgePoint_storesASequenceNumberAndNoZPosition() {
        var point = new MRID(UUID.randomUUID());

        DiagramLayoutServiceUtils.insertEdgePoint(
                model, point, ASSOCIATION_DO, 7F, 8F, 4, SUB_GLUE_POINT);

        var stored = DLObjectFetcher.fetchDOPsForDO(model, ASSOCIATION_DO).getFirst();
        assertThat(stored.getMRID()).isEqualTo(point);
        assertThat(stored.getSequenceNumber()).isEqualTo(4);
        assertThat(stored.getBelongsToGluePoint()).isEqualTo(SUB_GLUE_POINT);
        assertThat(model.contains(resource(point), DL.zPosition)).isFalse();
    }

    @Test
    void insertEdgeDiagramObject_storesBothIdentifiedObjectsOfTheEdge() {
        var edge = EdgeKey.inheritance(UUID.randomUUID(), UUID.randomUUID());

        var edgeDO =
                DiagramLayoutServiceUtils.insertEdgeDiagramObject(
                        model, PACKAGE_DIAGRAM, edge, "A B");

        assertThat(DLObjectFetcher.fetchDiagramEdgeDOs(model, new MRID(PACKAGE_DIAGRAM)))
                .filteredOn(diagramObject -> diagramObject.getMRID().equals(edgeDO))
                .singleElement()
                .satisfies(
                        diagramObject -> {
                            assertThat(EdgeKey.of(diagramObject)).isEqualTo(edge);
                            assertThat(diagramObject.getName()).isEqualTo("A B");
                        });
        assertThat(DLObjectFetcher.fetchDOPsForDO(model, edgeDO)).isEmpty();
    }

    @Test
    void deleteEdgeLayoutData_removesTheEdgeWithItsPoints_butNotTheGluePointsOfItsClasses() {
        DiagramLayoutServiceUtils.deleteEdgeLayoutData(model, INHERITANCE_DO);

        assertThat(DLObjectFetcher.fetchDiagramEdgeDOs(model, new MRID(PACKAGE_DIAGRAM)))
                .extracting(DiagramObject::getMRID)
                .containsExactly(ASSOCIATION_DO);
        for (var point :
                new MRID[] {
                    INHERITANCE_SOURCE_POINT, INHERITANCE_BEND_POINT, INHERITANCE_TARGET_POINT
                }) {
            assertThat(model.listStatements(resource(point), null, (RDFNode) null).toList())
                    .isEmpty();
        }
        assertThat(DLObjectFetcher.fetchGluePointForDO(model, SUB_DO).getMRID())
                .isEqualTo(SUB_GLUE_POINT);
        assertThat(DLObjectFetcher.fetchGluePointForDO(model, SUPER_DO).getMRID())
                .isEqualTo(SUPER_GLUE_POINT);
        assertThat(DLObjectFetcher.fetchDiagramDOPPerClass(model, PACKAGE_DIAGRAM))
                .containsKeys(SUB, SUPER);
    }

    private Resource resource(MRID mrid) {
        return model.getResource(mrid.getFullMRID());
    }
}
