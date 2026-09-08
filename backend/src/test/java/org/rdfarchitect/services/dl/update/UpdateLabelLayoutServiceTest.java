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

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.dl.ClassPositionDTO;
import org.rdfarchitect.api.dto.dl.LabelPositionDTO;
import org.rdfarchitect.dl.data.dto.DiagramObject;
import org.rdfarchitect.dl.data.dto.relations.DiagramObjectStyle;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.XYOffset;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher;
import org.rdfarchitect.dl.queries.select.DLObjectFetcher.LabelKey;
import org.rdfarchitect.dl.queries.update.DLUpdates;
import org.rdfarchitect.dl.rdf.resources.CIM;
import org.rdfarchitect.dl.rdf.resources.DL;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.services.dl.DiagramLayoutServicesTestBase;
import org.rdfarchitect.services.dl.update.classlayout.UpdateClassLayoutService;
import org.rdfarchitect.services.dl.update.labellayout.UpdateLabelLayoutService;
import org.rdfarchitect.services.dl.update.packagelayout.UpdatePackageLayoutService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class UpdateLabelLayoutServiceTest extends DiagramLayoutServicesTestBase {

    /**
     * The association end a multiplicity belongs to. Any UUID does, the layout only ever stores it
     * as the resource the label is anchored to.
     */
    private static final UUID ASSOCIATION_END_UUID =
            UUID.fromString("1d3f5b8a-2c47-4e91-8f0d-6b2a9c4e7351");

    private static final UUID OTHER_ASSOCIATION_END_UUID =
            UUID.fromString("9c8e7d6a-5b4c-4321-9876-0fedcba98765");

    private static final String CLASS_A_URI = "http://example.com#classA";
    private static final String CLASS_B_URI = "http://example.com#classB";
    private static final String ASSOCIATION_URI = "http://example.com#classA.classB";
    private static final String INVERSE_ASSOCIATION_URI = "http://example.com#classB.classA";

    private static UpdateLabelLayoutService service;
    private static UpdateClassLayoutService classLayoutService;
    private static UpdatePackageLayoutService packageLayoutService;

    @BeforeAll
    static void setUpServices() {
        service = new UpdateLabelLayoutService(databasePort);
        classLayoutService = new UpdateClassLayoutService(databasePort, packageMapper);
        packageLayoutService =
                new UpdatePackageLayoutService(databasePort, packageMapper, converter);
    }

    @BeforeEach
    void setUp() {
        addGraphFromFile("package.ttl");
        updateDiagramLayoutService.createDiagramLayout(graphIdentifier);
    }

    @Test
    void updateLabelPositions_labelMoved_storesTheOffset() {
        service.updateLabelPositions(
                graphIdentifier, PACKAGE_A_UUID, List.of(labelPosition(-37.5F, 62.25F)));

        assertThat(storedOffsets()).containsExactly(entryOf(new XYOffset(-37.5F, 62.25F)));
    }

    @Test
    void updateLabelPositions_labelIsPlacedAsADiagramObjectWithoutAPoint() {
        service.updateLabelPositions(
                graphIdentifier, PACKAGE_A_UUID, List.of(labelPosition(10F, 20F)));

        var labelDO =
                DLObjectFetcher.fetchLabelDO(
                        model(),
                        PACKAGE_A_UUID,
                        new LabelKey(ASSOCIATION_END_UUID, DiagramObjectStyle.MULTIPLICITY));
        assertThat(labelDO).isNotNull();
        assertThat(DLObjectFetcher.fetchDOPForDO(model(), labelDO.getMRID())).isNull();

        var labelResource = model().getResource(labelDO.getMRID().getFullMRID());
        assertThat(labelResource.hasProperty(RDF.type, DL.diagramObjectType)).isTrue();
        assertThat(labelResource.hasProperty(CIM.ioName)).isFalse();
        assertThat(
                        labelResource.hasProperty(
                                DL.diagramObjectStyle,
                                ResourceFactory.createResource(
                                        DiagramObjectStyle.MULTIPLICITY.getMRID().getFullMRID())))
                .isTrue();
    }

    @Test
    void updateLabelPositions_labelMovedTwice_replacesTheOffsetWithoutDuplicating() {
        service.updateLabelPositions(
                graphIdentifier, PACKAGE_A_UUID, List.of(labelPosition(-37.5F, 62.25F)));

        service.updateLabelPositions(
                graphIdentifier, PACKAGE_A_UUID, List.of(labelPosition(10F, -20F)));

        assertThat(storedOffsets()).containsExactly(entryOf(new XYOffset(10F, -20F)));
    }

    @Test
    void updateLabelPositions_offsetIsNull_resetsTheLabelToItsDefaultPlacement() {
        service.updateLabelPositions(
                graphIdentifier, PACKAGE_A_UUID, List.of(labelPosition(-37.5F, 62.25F)));

        service.updateLabelPositions(
                graphIdentifier, PACKAGE_A_UUID, List.of(labelPosition(null, null)));

        assertThat(storedOffsets()).isEmpty();
    }

    @Test
    void updateLabelPositions_severalLabels_shareOneStyleResource() {
        var second = new LabelPositionDTO();
        second.setIdentifiedObjectUUID(OTHER_ASSOCIATION_END_UUID);
        second.setKind(DiagramObjectStyle.MULTIPLICITY.getStyleName());
        second.setXOffset(1F);
        second.setYOffset(2F);

        service.updateLabelPositions(
                graphIdentifier, PACKAGE_A_UUID, List.of(labelPosition(-1F, -2F), second));

        assertThat(storedOffsets()).hasSize(2);
        assertThat(model().listSubjectsWithProperty(RDF.type, DL.diagramObjectStyleType).toList())
                .hasSize(1);
    }

    @Test
    void updateLabelPositions_labelPlaced_isInvisibleToTheClassLayout() {
        var classPosition = new ClassPositionDTO();
        classPosition.setClassUUID(CLASS_A_UUID);
        classPosition.setXPosition(11F);
        classPosition.setYPosition(22F);
        classLayoutService.updateClassPositions(
                graphIdentifier, PACKAGE_A_UUID, List.of(classPosition));

        service.updateLabelPositions(
                graphIdentifier, PACKAGE_A_UUID, List.of(labelPosition(-37.5F, 62.25F)));

        assertThat(DLObjectFetcher.fetchDiagramDOPPerClass(model(), PACKAGE_A_UUID))
                .containsOnlyKeys(CLASS_A_UUID);
        assertThat(DLObjectFetcher.fetchDiagramDOs(model(), new MRID(PACKAGE_A_UUID)))
                .extracting(diagramObject -> diagramObject.getBelongsToIdentifiedObject().getUuid())
                .containsExactly(CLASS_A_UUID);
        assertThat(DLObjectFetcher.fetchAllDOs(model(), ASSOCIATION_END_UUID)).isEmpty();
    }

    /**
     * Labels are told apart from classes by their style. Nothing forces a future kind of label to
     * stay nameless, so the class queries have to ignore one that does carry a name.
     */
    @Test
    void fetchDiagramDOs_labelCarriesAName_isStillIgnoredByTheClassQueries() {
        DLUpdates.insertDiagramObject(
                model(),
                DiagramObject.builder()
                        .mRID(new MRID(UUID.randomUUID()))
                        .name("0..n")
                        .style(DiagramObjectStyle.MULTIPLICITY)
                        .belongsToDiagram(new MRID(PACKAGE_A_UUID))
                        .belongsToIdentifiedObject(new MRID(ASSOCIATION_END_UUID))
                        .offset(new XYOffset(5F, 5F))
                        .build());

        assertThat(DLObjectFetcher.fetchDiagramDOs(model(), new MRID(PACKAGE_A_UUID))).isEmpty();
        assertThat(DLObjectFetcher.fetchAllDOs(model(), ASSOCIATION_END_UUID)).isEmpty();
        assertThat(
                        DLObjectFetcher.fetchDiagramDOForClass(
                                model(), PACKAGE_A_UUID, ASSOCIATION_END_UUID))
                .isNull();
    }

    @Test
    void deleteClassLayoutData_classDeleted_dropsTheLabelsOfItsAssociations() {
        insertAssociationFixture();
        var inverseEndLabel = new LabelPositionDTO();
        inverseEndLabel.setIdentifiedObjectUUID(OTHER_ASSOCIATION_END_UUID);
        inverseEndLabel.setKind(DiagramObjectStyle.MULTIPLICITY.getStyleName());
        inverseEndLabel.setXOffset(5F);
        inverseEndLabel.setYOffset(6F);
        var unrelatedLabel = new LabelPositionDTO();
        unrelatedLabel.setIdentifiedObjectUUID(PACKAGE_A_UUID);
        unrelatedLabel.setKind(DiagramObjectStyle.MULTIPLICITY.getStyleName());
        unrelatedLabel.setXOffset(3F);
        unrelatedLabel.setYOffset(4F);

        service.updateLabelPositions(
                graphIdentifier,
                PACKAGE_A_UUID,
                List.of(labelPosition(-37.5F, 62.25F), inverseEndLabel, unrelatedLabel));

        classLayoutService.deleteClassLayoutData(graphIdentifier, CLASS_A_UUID);

        assertThat(storedOffsets())
                .containsOnlyKeys(new LabelKey(PACKAGE_A_UUID, DiagramObjectStyle.MULTIPLICITY));
    }

    /**
     * Sets up classA, classB and the association pair between them (with classA as domain), whose
     * two ends carry {@link #ASSOCIATION_END_UUID} and {@link #OTHER_ASSOCIATION_END_UUID}. {@code
     * package.ttl} only contains a package, not a class, so the CIM data a delete needs to cascade
     * against is added directly rather than through a shared fixture file other tests also load.
     */
    private static void insertAssociationFixture() {
        var classA = NodeFactory.createURI(CLASS_A_URI);
        var classB = NodeFactory.createURI(CLASS_B_URI);
        var association = NodeFactory.createURI(ASSOCIATION_URI);
        var inverseAssociation = NodeFactory.createURI(INVERSE_ASSOCIATION_URI);

        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.WRITE)) {
            var graph = ctx.getRdfGraph();

            graph.add(classA, RDF.type.asNode(), RDFS.Class.asNode());
            graph.add(
                    classA,
                    RDFA.uuid.asNode(),
                    NodeFactory.createLiteralString(CLASS_A_UUID.toString()));
            graph.add(classB, RDF.type.asNode(), RDFS.Class.asNode());
            graph.add(
                    classB,
                    RDFA.uuid.asNode(),
                    NodeFactory.createLiteralString(UUID.randomUUID().toString()));

            graph.add(association, RDF.type.asNode(), RDF.Property.asNode());
            graph.add(
                    association,
                    RDFA.uuid.asNode(),
                    NodeFactory.createLiteralString(ASSOCIATION_END_UUID.toString()));
            graph.add(association, RDFS.domain.asNode(), classA);
            graph.add(association, RDFS.range.asNode(), classB);
            graph.add(
                    association,
                    CIMS.associationUsed.asNode(),
                    NodeFactory.createLiteralString("Yes"));
            graph.add(association, CIMS.inverseRoleName.asNode(), inverseAssociation);

            graph.add(inverseAssociation, RDF.type.asNode(), RDF.Property.asNode());
            graph.add(
                    inverseAssociation,
                    RDFA.uuid.asNode(),
                    NodeFactory.createLiteralString(OTHER_ASSOCIATION_END_UUID.toString()));
            graph.add(inverseAssociation, RDFS.domain.asNode(), classB);
            graph.add(inverseAssociation, RDFS.range.asNode(), classA);
            graph.add(
                    inverseAssociation,
                    CIMS.associationUsed.asNode(),
                    NodeFactory.createLiteralString("Yes"));
            graph.add(inverseAssociation, CIMS.inverseRoleName.asNode(), association);

            ctx.commit();
        }
    }

    @Test
    void deletePackageLayoutData_packageDeleted_dropsTheLabelsOfItsDiagram() {
        service.updateLabelPositions(
                graphIdentifier, PACKAGE_A_UUID, List.of(labelPosition(-37.5F, 62.25F)));

        packageLayoutService.deletePackageLayoutData(graphIdentifier, PACKAGE_A_UUID);

        assertThat(storedOffsets()).isEmpty();
    }

    private static LabelPositionDTO labelPosition(Float xOffset, Float yOffset) {
        var labelPosition = new LabelPositionDTO();
        labelPosition.setIdentifiedObjectUUID(ASSOCIATION_END_UUID);
        labelPosition.setKind(DiagramObjectStyle.MULTIPLICITY.getStyleName());
        labelPosition.setXOffset(xOffset);
        labelPosition.setYOffset(yOffset);
        return labelPosition;
    }

    private static Map.Entry<LabelKey, XYOffset> entryOf(XYOffset offset) {
        return Map.entry(
                new LabelKey(ASSOCIATION_END_UUID, DiagramObjectStyle.MULTIPLICITY), offset);
    }

    private static Map<LabelKey, XYOffset> storedOffsets() {
        return DLObjectFetcher.fetchLabelOffsets(model(), PACKAGE_A_UUID);
    }

    private static Model model() {
        return databasePort
                .getGraphWithContext(graphIdentifier)
                .getDiagramLayout()
                .getDiagramLayoutModelDirect();
    }
}
