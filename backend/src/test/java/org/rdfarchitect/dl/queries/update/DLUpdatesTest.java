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

package org.rdfarchitect.dl.queries.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.rdfarchitect.dl.DLTestData.CUSTOM_DIAGRAM;
import static org.rdfarchitect.dl.DLTestData.INHERITANCE_BEND_POINT;
import static org.rdfarchitect.dl.DLTestData.INHERITANCE_DO;
import static org.rdfarchitect.dl.DLTestData.INHERITANCE_SOURCE_POINT;
import static org.rdfarchitect.dl.DLTestData.PACKAGE_DIAGRAM;
import static org.rdfarchitect.dl.DLTestData.SUB_DO;
import static org.rdfarchitect.dl.DLTestData.SUB_DO_IN_CUSTOM_DIAGRAM;
import static org.rdfarchitect.dl.DLTestData.SUB_GLUE_POINT;
import static org.rdfarchitect.dl.DLTestData.SUB_POINT;
import static org.rdfarchitect.dl.DLTestData.SUPER_DO;
import static org.rdfarchitect.dl.DLTestData.SUPER_GLUE_POINT;
import static org.rdfarchitect.dl.DLTestData.SUPER_POINT;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.dl.DLTestData;
import org.rdfarchitect.dl.data.dto.Diagram;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.DiagramObjectGluePoint;
import org.rdfarchitect.dl.data.dto.DiagramObjectPoint;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.XYZPosition;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.rdf.resources.CIM;
import org.rdfarchitect.dl.rdf.resources.DL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class DLUpdatesTest {

    private static final MRID DIAGRAM = new MRID(UUID.randomUUID());
    private static final MRID DIAGRAM_OBJECT = new MRID(UUID.randomUUID());
    private static final MRID POINT = new MRID(UUID.randomUUID());
    private static final MRID GLUE_POINT = new MRID(UUID.randomUUID());
    private static final MRID CLASS = new MRID(UUID.randomUUID());
    private static final MRID OTHER_CLASS = new MRID(UUID.randomUUID());

    private final Model model = ModelFactory.createDefaultModel();

    @Test
    void insertDiagram_writesTypeNameAndNegativeOrientation() {
        DLUpdates.insertDiagram(model, Diagram.builder().mRID(DIAGRAM).name("Package").build());

        assertThat(propertiesOf(DIAGRAM))
                .containsOnly(
                        Map.entry(RDF.type, DL.diagramType),
                        Map.entry(CIM.ioName, plain("Package")),
                        Map.entry(DL.orientation, DL.negativeOrientation));
    }

    @Test
    void replaceDiagram_replacesTheDiagramButKeepsItsDiagramObjects() {
        var model = DLTestData.load();

        DLUpdates.replaceDiagram(
                model,
                new MRID(PACKAGE_DIAGRAM),
                Diagram.builder().mRID(new MRID(PACKAGE_DIAGRAM)).name("Renamed").build());

        assertThat(DLObjectFetcher.fetchDiagram(model, PACKAGE_DIAGRAM).getName())
                .isEqualTo("Renamed");
        assertThat(DLObjectFetcher.fetchDiagramDOs(model, new MRID(PACKAGE_DIAGRAM))).hasSize(6);
    }

    @Test
    void insertDiagramObject_writesAllReferencesAndTheOtherClassOfAnEdge() {
        DLUpdates.insertDiagramObject(model, edgeDO().otherClass(OTHER_CLASS).build());

        assertThat(propertiesOf(DIAGRAM_OBJECT))
                .containsOnly(
                        Map.entry(RDF.type, DL.diagramObjectType),
                        Map.entry(CIM.ioName, plain("Sub Super")),
                        Map.entry(DL.belongsToDiagram, resource(DIAGRAM)),
                        Map.entry(DL.belongsToIdentifiedObject, resource(CLASS)),
                        Map.entry(
                                DL.belongsToDiagramObjectStyle,
                                resource(DiagramObjectStyle.INHERITANCE.getMRID())),
                        Map.entry(DL.otherClass, resource(OTHER_CLASS)));
        assertThat(DL.otherClass.getURI())
                .isEqualTo("https://rdf-architect.soptim.de#DiagramObject.otherClass");
    }

    @Test
    void insertDiagramObject_leavesOutTheOtherClassAndStyleIfThereAreNone() {
        DLUpdates.insertDiagramObject(model, edgeDO().belongsToDiagramObjectStyle(null).build());

        assertThat(propertiesOf(DIAGRAM_OBJECT))
                .doesNotContainKeys(DL.otherClass, DL.belongsToDiagramObjectStyle);
    }

    @Test
    void insertDiagramObject_insertsItsStyleOnce() {
        DLUpdates.insertDiagramObject(model, edgeDO().build());
        DLUpdates.insertDiagramObject(model, edgeDO().mRID(new MRID(UUID.randomUUID())).build());

        assertThat(propertiesOf(DiagramObjectStyle.INHERITANCE.getMRID()))
                .containsOnly(
                        Map.entry(RDF.type, DL.diagramObjectStyleType),
                        Map.entry(CIM.ioName, plain("inheritance")));
    }

    @Test
    void insertDiagramObjectPoint_writesPositionSequenceNumberAndGluePointAsInTheProfile() {
        DLUpdates.insertDiagramObjectPoint(
                model,
                DiagramObjectPoint.builder()
                        .mRID(POINT)
                        .position(new XYZPosition(10.5F, -20F, 3))
                        .belongsToDiagramObject(DIAGRAM_OBJECT)
                        .sequenceNumber(10)
                        .belongsToGluePoint(GLUE_POINT)
                        .build());

        assertThat(propertiesOf(POINT))
                .containsOnly(
                        Map.entry(RDF.type, DL.diagramObjectPointType),
                        Map.entry(DL.xPosition, plain("10.5")),
                        Map.entry(DL.yPosition, plain("-20.0")),
                        Map.entry(DL.zPosition, plain("3")),
                        Map.entry(DL.sequenceNumber, plain("10")),
                        Map.entry(DL.belongsToDiagramObject, resource(DIAGRAM_OBJECT)),
                        Map.entry(DL.belongsToGluePoint, resource(GLUE_POINT)));
    }

    @Test
    void insertDiagramObjectPoint_leavesOutTheOptionalTriples() {
        DLUpdates.insertDiagramObjectPoint(
                model,
                DiagramObjectPoint.builder()
                        .mRID(POINT)
                        .position(new XYZPosition(1F, 2F, null))
                        .belongsToDiagramObject(DIAGRAM_OBJECT)
                        .build());

        assertThat(propertiesOf(POINT))
                .doesNotContainKeys(DL.zPosition, DL.sequenceNumber, DL.belongsToGluePoint);
    }

    @Test
    void insertedObjects_areReadBackUnchanged() {
        var edge = edgeDO().otherClass(OTHER_CLASS).build();
        var point =
                DiagramObjectPoint.builder()
                        .mRID(POINT)
                        .position(new XYZPosition(10.5F, 20F, 0))
                        .belongsToDiagramObject(DIAGRAM_OBJECT)
                        .sequenceNumber(3)
                        .belongsToGluePoint(GLUE_POINT)
                        .build();
        DLUpdates.insertDiagram(model, Diagram.builder().mRID(DIAGRAM).name("Package").build());
        DLUpdates.insertDiagramObject(model, edge);
        DLUpdates.insertDiagramObjectPoint(model, point);

        assertThat(DLObjectFetcher.fetchDiagramEdgeDOs(model, DIAGRAM)).containsExactly(edge);
        assertThat(DLObjectFetcher.fetchDOPsForDO(model, DIAGRAM_OBJECT)).containsExactly(point);
    }

    @Test
    void updateDiagramObjectName_replacesTheName() {
        DLUpdates.insertDiagramObject(model, edgeDO().build());

        DLUpdates.updateDiagramObjectName(model, edgeDO().build(), "Renamed");

        assertThat(
                        model.listObjectsOfProperty(
                                        resource(DIAGRAM_OBJECT).asResource(), CIM.ioName)
                                .toList())
                .extracting(RDFNode::toString)
                .containsExactly("Renamed");
    }

    @Test
    void insertDiagramObjectGluePoint_writesItsTypeOnly() {
        DLUpdates.insertDiagramObjectGluePoint(
                model, DiagramObjectGluePoint.builder().mRID(GLUE_POINT).build());

        assertThat(propertiesOf(GLUE_POINT))
                .containsOnly(Map.entry(RDF.type, DL.diagramObjectGluePointType));
    }

    @Test
    void deleteDiagramObjectPoint_removesThatPointOnly() {
        var model = DLTestData.load();

        DLUpdates.deleteDiagramObjectPoint(model, INHERITANCE_BEND_POINT);

        assertThat(statementsAbout(model, INHERITANCE_BEND_POINT)).isEmpty();
        assertThat(DLObjectFetcher.fetchDOPsForDO(model, INHERITANCE_DO)).hasSize(2);
        assertThat(statementsAbout(model, SUB_GLUE_POINT)).isNotEmpty();
    }

    @Test
    void deleteDiagramObjectGluePointWithReferences_removesTheGluePointAndEveryReferenceToIt() {
        var model = DLTestData.load();

        DLUpdates.deleteDiagramObjectGluePointWithReferences(model, SUB_GLUE_POINT);

        assertThat(statementsAbout(model, SUB_GLUE_POINT)).isEmpty();
        assertThat(
                        model.listSubjectsWithProperty(
                                        DL.belongsToGluePoint, resource(SUB_GLUE_POINT))
                                .toList())
                .isEmpty();
        assertThat(statementsAbout(model, SUB_POINT)).isNotEmpty();
        assertThat(statementsAbout(model, INHERITANCE_SOURCE_POINT)).isNotEmpty();
    }

    @Test
    void deleteDiagramObjectCascade_removesAClassWithItsPointAndGluePoint() {
        var model = DLTestData.load();

        DLUpdates.deleteDiagramObjectCascade(model, SUB_DO);

        assertThat(statementsAbout(model, SUB_DO)).isEmpty();
        assertThat(statementsAbout(model, SUB_POINT)).isEmpty();
        assertThat(statementsAbout(model, SUB_GLUE_POINT)).isEmpty();
        assertThat(statementsAbout(model, SUPER_DO)).isNotEmpty();
        assertThat(DLObjectFetcher.fetchGluePointForDO(model, SUPER_DO).getMRID())
                .isEqualTo(SUPER_GLUE_POINT);
    }

    @Test
    void deleteDiagramCascade_removesTheDiagramWithEverythingInIt_andNothingOfOtherDiagrams() {
        var model = DLTestData.load();

        DLUpdates.deleteDiagramCascade(model, new MRID(PACKAGE_DIAGRAM));

        assertThat(DLObjectFetcher.fetchDiagram(model, PACKAGE_DIAGRAM)).isNull();
        for (var removed :
                List.of(
                        SUB_DO,
                        SUPER_DO,
                        INHERITANCE_DO,
                        SUB_POINT,
                        SUPER_POINT,
                        SUB_GLUE_POINT,
                        INHERITANCE_SOURCE_POINT,
                        INHERITANCE_BEND_POINT)) {
            assertThat(statementsAbout(model, removed)).as(removed.getFullMRID()).isEmpty();
        }
        assertThat(DLObjectFetcher.fetchDiagram(model, CUSTOM_DIAGRAM)).isNotNull();
        assertThat(DLObjectFetcher.fetchDiagramClassDOs(model, new MRID(CUSTOM_DIAGRAM)))
                .extracting(DiagramObject::getMRID)
                .containsExactly(SUB_DO_IN_CUSTOM_DIAGRAM);
        assertThat(DLObjectFetcher.fetchGluePointForDO(model, SUB_DO_IN_CUSTOM_DIAGRAM))
                .isNotNull();
        assertThat(statementsAbout(model, DiagramObjectStyle.CLASS.getMRID())).isNotEmpty();
    }

    private static DiagramObject.DiagramObjectBuilder<?, ?> edgeDO() {
        return DiagramObject.builder()
                .mRID(DIAGRAM_OBJECT)
                .name("Sub Super")
                .belongsToDiagram(DIAGRAM)
                .belongsToIdentifiedObject(CLASS)
                .belongsToDiagramObjectStyle(DiagramObjectStyle.INHERITANCE);
    }

    private Map<Property, RDFNode> propertiesOf(MRID mrid) {
        var properties = new HashMap<Property, RDFNode>();
        for (var statement : statementsAbout(model, mrid)) {
            assertThat(properties.put(statement.getPredicate(), statement.getObject()))
                    .as("single value of " + statement.getPredicate())
                    .isNull();
        }
        return properties;
    }

    private static List<Statement> statementsAbout(Model model, MRID mrid) {
        return model.listStatements(model.getResource(mrid.getFullMRID()), null, (RDFNode) null)
                .toList();
    }

    private static RDFNode resource(MRID mrid) {
        return ResourceFactory.createResource(mrid.getFullMRID());
    }

    private static RDFNode plain(String text) {
        return ResourceFactory.createPlainLiteral(text);
    }
}
