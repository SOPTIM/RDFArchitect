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

package org.rdfarchitect.services.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.rendering.svelteflow.SvelteFlowDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.AttributeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EdgeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EnumEntryDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.NodeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.SuperClassDTO;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.services.rendering.svelteflow.RenderCIMFacadeCollectionSvelteFlowService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

class RenderCIMFacadeCollectionSvelteFlowServiceTest {

    private static final String GRAPH_URI = "http://graph#";
    private static final String NS = "http://example.com#";

    private static final UUID CORE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ROOT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID BASE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID CHILD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID TERMINAL_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID ENUM_UUID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID LOOSE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID EXTERNAL_CAT_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID REMOTE_UUID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID MISSING_TARGET_UUID =
            UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID MISSING_SUPER_CLASS_UUID =
            UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private final RenderCIMFacadeCollectionSvelteFlowService renderer =
            new RenderCIMFacadeCollectionSvelteFlowService();

    private Model model;
    private CIMModelFacade facade;

    @BeforeEach
    void setUp() {
        model = ModelFactory.createDefaultModel();
        facade = new CIMModelFacade(GRAPH_URI, model);

        var core = addCategory("CorePackage", "Core", CORE_UUID);
        var other = addCategory("OtherPackage", "Other", OTHER_UUID);

        var root = addClass("Root", ROOT_UUID, core);
        addAttribute("Root.rootAttr", "rootAttr", root, "M:0..1");

        var base = addClass("Base", BASE_UUID, core);
        base.addProperty(RDFS.subClassOf, root);
        addAttribute("Base.baseAttr", "baseAttr", base, "M:0..1");

        var externalBase = model.createResource(NS + "ExternalBase");

        var child = addClass("Child", CHILD_UUID, core);
        child.addProperty(RDFS.subClassOf, base);
        child.addProperty(RDFS.subClassOf, externalBase);
        child.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
        addAttribute("Child.childAttr", "childAttr", child, "M:1..1");

        var phaseCode = addClass("PhaseCode", ENUM_UUID, core);
        phaseCode.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);
        var entry = model.createResource(NS + "PhaseCode.A");
        entry.addProperty(RDF.type, phaseCode);
        entry.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        entry.addProperty(RDFS.label, model.createLiteral("A", "en"));
        entry.addProperty(CIMS.stereotype, CIMStereotypes.enumLiteral);

        var terminal = addClass("Terminal", TERMINAL_UUID, other);

        var association =
                addAssociation(child, terminal, "Child.Terminals", "Terminals", "M:0..n", "Yes");
        var inverse = addAssociation(terminal, child, "Terminal.Child", "Child", "M:1..1", "No");
        inverse.addProperty(CIMS.inverseRoleName, association);
        association.addProperty(CIMS.inverseRoleName, inverse);

        addClass("Loose", LOOSE_UUID, null);

        var externalCategory = model.createResource(NS + "ExternalPackage");
        externalCategory.addProperty(RDFA.uuid, EXTERNAL_CAT_UUID.toString());
        addClass("Remote", REMOTE_UUID, externalCategory);
    }

    private Resource addCategory(String localName, String label, UUID uuid) {
        var category = model.createResource(NS + localName);
        category.addProperty(RDF.type, CIMS.classCategory);
        category.addProperty(RDFA.uuid, uuid.toString());
        category.addProperty(RDFS.label, model.createLiteral(label, "en"));
        return category;
    }

    private Resource addClass(String label, UUID uuid, Resource category) {
        var cimClass = model.createResource(NS + label);
        cimClass.addProperty(RDF.type, RDFS.Class);
        cimClass.addProperty(RDFA.uuid, uuid.toString());
        cimClass.addProperty(RDFS.label, model.createLiteral(label, "en"));
        if (category != null) {
            cimClass.addProperty(CIMS.belongsToCategory, category);
        }
        return cimClass;
    }

    private Resource addAssociation(
            Resource domain,
            Resource range,
            String localName,
            String label,
            String multiplicity,
            String associationUsed) {
        var association = model.createResource(NS + localName);
        association.addProperty(RDF.type, RDF.Property);
        association.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        association.addProperty(RDFS.label, model.createLiteral(label, "en"));
        association.addProperty(RDFS.domain, domain);
        association.addProperty(RDFS.range, range);
        association.addProperty(
                CIMS.multiplicity, model.createResource(CIMS.namespace + multiplicity));
        association.addProperty(CIMS.associationUsed, associationUsed);
        return association;
    }

    private void addAttribute(
            String localName, String label, Resource domain, String multiplicity) {
        var attribute = model.createResource(NS + localName);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        attribute.addProperty(RDFS.label, model.createLiteral(label, "en"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        attribute.addProperty(RDFS.domain, domain);
        attribute.addProperty(
                CIMS.multiplicity, model.createResource(CIMS.namespace + multiplicity));
        attribute.addProperty(CIMS.datatype, model.createResource(XSD.xstring.getURI()));
    }

    private GraphFilter coreFilter() {
        var filter = new GraphFilter(true);
        filter.setPackageUUID(CORE_UUID.toString());
        return filter;
    }

    private NodeDTO nodeByLabel(SvelteFlowDTO diagram, String label) {
        return diagram.getNodes().stream()
                .filter(node -> node.getData().getLabel().equals(label))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("renders all package classes plus externally related classes as nodes")
    void rendersPackageClasses() {
        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        assertThat(result.getNodes())
                .extracting(node -> node.getData().getLabel())
                .containsExactlyInAnyOrder("Root", "Base", "Child", "PhaseCode", "Terminal");
        assertThat(nodeByLabel(result, "Child").getId()).isEqualTo(CHILD_UUID);
        assertThat(nodeByLabel(result, "Child").getData().getBelongsToCategory()).isEqualTo("Core");
        assertThat(nodeByLabel(result, "Terminal").getData().getBelongsToCategory())
                .isEqualTo("Other");
    }

    @Test
    @DisplayName("marks a deleted class that is still referenced by a kept association as external")
    void marksReferencedOnlyAssociationTargetAsExternal() {
        var deleted = model.createResource(NS + "Deleted");
        deleted.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        var association = model.createResource(NS + "Child.Deleted");
        association.addProperty(RDF.type, RDF.Property);
        association.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        association.addProperty(RDFS.label, model.createLiteral("Deleted", "en"));
        association.addProperty(RDFS.domain, model.getResource(NS + "Child"));
        association.addProperty(RDFS.range, deleted);
        association.addProperty(CIMS.multiplicity, model.createResource(CIMS.namespace + "M:0..n"));
        association.addProperty(CIMS.associationUsed, "Yes");
        var inverse = model.createResource(NS + "Deleted.Child");
        inverse.addProperty(RDF.type, RDF.Property);
        inverse.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        inverse.addProperty(RDFS.label, model.createLiteral("Child", "en"));
        inverse.addProperty(RDFS.domain, deleted);
        inverse.addProperty(RDFS.range, model.getResource(NS + "Child"));
        inverse.addProperty(CIMS.multiplicity, model.createResource(CIMS.namespace + "M:1..1"));
        inverse.addProperty(CIMS.associationUsed, "No");
        inverse.addProperty(CIMS.inverseRoleName, association);
        association.addProperty(CIMS.inverseRoleName, inverse);

        var result = (SvelteFlowDTO) renderer.renderUML(facade, coreFilter(), null);

        var deletedNode = nodeByLabel(result, "Deleted");
        assertThat(deletedNode.getData().isExternal()).isTrue();
        assertThat(deletedNode.getData().getAttributes()).isEmpty();
        assertThat(deletedNode.getData().getSuperClasses()).isEmpty();
        assertThat(nodeByLabel(result, "Child").getData().isExternal()).isFalse();
    }

    @Test
    @DisplayName("renders direct attributes and stereotypes of a class")
    void rendersDirectAttributesAndStereotypes() {
        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        var childData = nodeByLabel(result, "Child").getData();
        assertThat(childData.getStereotypes()).doesNotContain("abstract");
        assertThat(childData.getAttributes())
                .singleElement()
                .satisfies(
                        attribute -> {
                            assertThat(attribute.getLabel()).isEqualTo("childAttr");
                            assertThat(attribute.getType()).isEqualTo("string");
                            assertThat(attribute.getMultiplicity()).isEqualTo("1..1");
                        });

        var baseData = nodeByLabel(result, "Base").getData();
        assertThat(baseData.getStereotypes()).contains("abstract");
        assertThat(baseData.getAttributes())
                .extracting(AttributeDTO::getLabel)
                .containsExactly("baseAttr");
    }

    @Test
    @DisplayName("renders the transitive super class chain with inherited attributes")
    void rendersInheritedProperties() {
        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        var superClasses = nodeByLabel(result, "Child").getData().getSuperClasses();
        assertThat(superClasses)
                .extracting(SuperClassDTO::getLabel)
                .containsExactlyInAnyOrder("Base", "Root", "ExternalBase");

        var baseSuperClass =
                superClasses.stream()
                        .filter(superClass -> superClass.getLabel().equals("Base"))
                        .findFirst()
                        .orElseThrow();
        assertThat(baseSuperClass.getUuid()).isEqualTo(BASE_UUID);
        assertThat(baseSuperClass.getAttributes())
                .extracting(AttributeDTO::getLabel)
                .containsExactly("baseAttr");

        var rootSuperClass =
                superClasses.stream()
                        .filter(superClass -> superClass.getLabel().equals("Root"))
                        .findFirst()
                        .orElseThrow();
        assertThat(rootSuperClass.getAttributes())
                .extracting(AttributeDTO::getLabel)
                .containsExactly("rootAttr");

        var externalSuperClass =
                superClasses.stream()
                        .filter(superClass -> superClass.getLabel().equals("ExternalBase"))
                        .findFirst()
                        .orElseThrow();
        assertThat(externalSuperClass.getUuid()).isNull();
        assertThat(externalSuperClass.getAttributes()).isEmpty();
    }

    @Test
    @DisplayName("renders enum entries for enumeration classes")
    void rendersEnumEntries() {
        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        var enumData = nodeByLabel(result, "PhaseCode").getData();
        assertThat(enumData.getStereotypes()).contains("abstract", "enumeration");
        assertThat(enumData.getEnumEntries())
                .extracting(EnumEntryDTO::getLabel)
                .containsExactly("A");
    }

    @Test
    @DisplayName("renders inheritance edges between internal classes only")
    void rendersInheritanceEdges() {
        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        var inheritanceEdges =
                result.getEdges().stream()
                        .filter(edge -> edge.getType().equals("inheritance"))
                        .toList();
        assertThat(inheritanceEdges)
                .extracting(edge -> List.of(edge.getSource(), edge.getTarget()))
                .containsExactlyInAnyOrder(
                        List.of(CHILD_UUID, BASE_UUID), List.of(BASE_UUID, ROOT_UUID));
    }

    @Test
    @DisplayName("renders a single association edge per association pair")
    void rendersAssociationEdges() {
        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        var associationEdges =
                result.getEdges().stream()
                        .filter(edge -> edge.getType().equals("association"))
                        .toList();
        assertThat(associationEdges).hasSize(1);
        var edge = associationEdges.getFirst();
        assertThat(edge.getSource()).isEqualTo(CHILD_UUID);
        assertThat(edge.getTarget()).isEqualTo(TERMINAL_UUID);
        assertThat(edge.getData().getFromMultiplicity()).isEqualTo("0..n");
        assertThat(edge.getData().getToMultiplicity()).isEqualTo("1..1");
        assertThat(edge.getData().isUseToAssociation()).isTrue();
        assertThat(edge.getData().isUseFromAssociation()).isFalse();
    }

    @Test
    @DisplayName("skips association targets the graph does not define as classes")
    void skipsUndefinedAssociationTarget() {
        var child = model.getResource(NS + "Child");
        var missingTarget = model.createResource(NS + "MissingTarget");
        missingTarget.addProperty(RDFA.uuid, MISSING_TARGET_UUID.toString());
        var from =
                addAssociation(child, missingTarget, "Child.missing", "missing", "M:0..n", "Yes");
        var to =
                addAssociation(
                        missingTarget, child, "MissingTarget.child", "child", "M:1..1", "No");
        from.addProperty(CIMS.inverseRoleName, to);
        to.addProperty(CIMS.inverseRoleName, from);

        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        assertThat(result.getNodes())
                .extracting(node -> node.getData().getLabel())
                .doesNotContain("MissingTarget");
        assertThat(result.getEdges())
                .extracting(edge -> edge.getTarget())
                .doesNotContain(MISSING_TARGET_UUID);
    }

    @Test
    @DisplayName("skips super classes the graph does not define as classes")
    void skipsUndefinedSuperClass() {
        var missingSuperClass = model.createResource(NS + "MissingSuperClass");
        missingSuperClass.addProperty(RDFA.uuid, MISSING_SUPER_CLASS_UUID.toString());
        model.getResource(NS + "Child").addProperty(RDFS.subClassOf, missingSuperClass);

        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        assertThat(result.getNodes())
                .extracting(node -> node.getData().getLabel())
                .doesNotContain("MissingSuperClass");
        assertThat(result.getEdges())
                .extracting(edge -> edge.getTarget())
                .doesNotContain(MISSING_SUPER_CLASS_UUID);
    }

    @Test
    @DisplayName("omits attributes and enum entries when disabled by the filter")
    void filterDisablesAttributesAndEnumEntries() {
        var filter = coreFilter();
        filter.setIncludeAttributes(false);
        filter.setIncludeEnumEntries(false);

        var result =
                (SvelteFlowDTO) renderer.renderUML(facade, filter, null, List.of(), null, null);

        var childData = nodeByLabel(result, "Child").getData();
        assertThat(childData.getAttributes()).isEmpty();
        assertThat(childData.getSuperClasses())
                .allSatisfy(superClass -> assertThat(superClass.getAttributes()).isEmpty());
        assertThat(nodeByLabel(result, "PhaseCode").getData().getEnumEntries()).isEmpty();
    }

    @Test
    @DisplayName("omits inheritance edges when disabled by the filter")
    void filterDisablesInheritance() {
        var filter = coreFilter();
        filter.setIncludeInheritance(false);

        var result =
                (SvelteFlowDTO) renderer.renderUML(facade, filter, null, List.of(), null, null);

        assertThat(result.getEdges()).noneMatch(edge -> edge.getType().equals("inheritance"));
    }

    @Test
    @DisplayName("omits association edges when disabled by the filter")
    void filterDisablesAssociations() {
        var filter = coreFilter();
        filter.setIncludeAssociations(false);

        var result =
                (SvelteFlowDTO) renderer.renderUML(facade, filter, null, List.of(), null, null);

        assertThat(result.getNodes())
                .extracting(node -> node.getData().getLabel())
                .doesNotContain("Terminal");
        assertThat(result.getEdges()).noneMatch(edge -> edge.getType().equals("association"));
    }

    @Test
    @DisplayName("omits classes of other packages when external relations are disabled")
    void filterDisablesExternalRelations() {
        var filter = coreFilter();
        filter.setIncludeRelationsToExternalPackages(false);

        var result =
                (SvelteFlowDTO) renderer.renderUML(facade, filter, null, List.of(), null, null);

        assertThat(result.getNodes())
                .extracting(node -> node.getData().getLabel())
                .containsExactlyInAnyOrder("Root", "Base", "Child", "PhaseCode");
        assertThat(result.getEdges()).noneMatch(edge -> edge.getType().equals("association"));
    }

    @Test
    @DisplayName("renders classes without a category for the default package")
    void rendersDefaultPackage() {
        var filter = new GraphFilter(true);
        filter.setPackageUUID("default");

        var result =
                (SvelteFlowDTO) renderer.renderUML(facade, filter, null, List.of(), null, null);

        assertThat(result.getNodes())
                .extracting(node -> node.getData().getLabel())
                .containsExactly("Loose");
    }

    @Test
    @DisplayName("renders classes of an external category that has only a uuid")
    void rendersExternalCategory() {
        var filter = new GraphFilter(true);
        filter.setPackageUUID(EXTERNAL_CAT_UUID.toString());

        var result =
                (SvelteFlowDTO) renderer.renderUML(facade, filter, null, List.of(), null, null);

        assertThat(result.getNodes())
                .extracting(node -> node.getData().getLabel())
                .containsExactly("Remote");
        assertThat(nodeByLabel(result, "Remote").getData().getBelongsToCategory())
                .isEqualTo("ExternalPackage");
    }

    @Test
    @DisplayName("renders only the classes specified by allowedUUIDs")
    void rendersAllowedUUIDsOnly() {
        var filter = new GraphFilter(true);
        filter.setAllowedUUIDs(List.of(CHILD_UUID.toString(), BASE_UUID.toString()));

        var result =
                (SvelteFlowDTO) renderer.renderUML(facade, filter, null, List.of(), null, null);

        assertThat(result.getNodes())
                .extracting(node -> node.getData().getLabel())
                .containsExactlyInAnyOrder("Base", "Child");
        assertThat(result.getEdges())
                .singleElement()
                .satisfies(
                        edge -> {
                            assertThat(edge.getType()).isEqualTo("inheritance");
                            assertThat(edge.getSource()).isEqualTo(CHILD_UUID);
                            assertThat(edge.getTarget()).isEqualTo(BASE_UUID);
                        });
    }

    @Test
    @DisplayName("returns an empty diagram for an empty model")
    void rendersEmptyModel() {
        var emptyFacade = new CIMModelFacade(GRAPH_URI, ModelFactory.createDefaultModel());

        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(emptyFacade, coreFilter(), null, List.of(), null, null);

        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getEdges()).isEmpty();
    }

    private static final String OTHER_GRAPH_URI = "http://graph2#";
    private static final String OTHER_COLOR = "#ff0000";

    private CIMModelFacade buildOtherProfile() {
        var otherModel = ModelFactory.createDefaultModel();

        var child = otherModel.createResource(NS + "Child");
        child.addProperty(RDF.type, RDFS.Class);
        child.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        child.addProperty(RDFS.label, otherModel.createLiteral("Child", "en"));

        var attribute = otherModel.createResource(NS + "Child.otherChildAttr");
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        attribute.addProperty(RDFS.label, otherModel.createLiteral("otherChildAttr", "en"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        attribute.addProperty(RDFS.domain, child);
        attribute.addProperty(
                CIMS.multiplicity, otherModel.createResource(CIMS.namespace + "M:0..1"));
        attribute.addProperty(CIMS.datatype, otherModel.createResource(XSD.xstring.getURI()));

        var phaseCode = otherModel.createResource(NS + "PhaseCode");
        phaseCode.addProperty(RDF.type, RDFS.Class);
        phaseCode.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        phaseCode.addProperty(RDFS.label, otherModel.createLiteral("PhaseCode", "en"));
        phaseCode.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);
        var entry = otherModel.createResource(NS + "PhaseCode.B");
        entry.addProperty(RDF.type, phaseCode);
        entry.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        entry.addProperty(RDFS.label, otherModel.createLiteral("B", "en"));
        entry.addProperty(CIMS.stereotype, CIMStereotypes.enumLiteral);

        return new CIMModelFacade(OTHER_GRAPH_URI, otherModel);
    }

    @Test
    @DisplayName("merges attributes and enum entries from other profiles when enabled")
    void mergesOtherProfileProperties() {
        var filter = coreFilter();
        filter.setIncludePropertiesFromOtherProfiles(true);

        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(
                                facade,
                                filter,
                                null,
                                List.of(
                                        new CIMProfileModel(
                                                OTHER_GRAPH_URI,
                                                OTHER_COLOR,
                                                null,
                                                buildOtherProfile())),
                                null,
                                null);

        var childAttributes = nodeByLabel(result, "Child").getData().getAttributes();
        assertThat(childAttributes)
                .filteredOn(attribute -> attribute.getLabel().equals("childAttr"))
                .singleElement()
                .satisfies(
                        attribute -> {
                            assertThat(attribute.getGraphUri()).isEqualTo(GRAPH_URI);
                            assertThat(attribute.getColor()).isNull();
                        });
        assertThat(childAttributes)
                .filteredOn(attribute -> attribute.getLabel().equals("otherChildAttr"))
                .singleElement()
                .satisfies(
                        attribute -> {
                            assertThat(attribute.getGraphUri()).isEqualTo(OTHER_GRAPH_URI);
                            assertThat(attribute.getColor()).isEqualTo(OTHER_COLOR);
                        });

        var enumEntries = nodeByLabel(result, "PhaseCode").getData().getEnumEntries();
        assertThat(enumEntries)
                .filteredOn(entry -> entry.getLabel().equals("A"))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getGraphUri()).isEqualTo(GRAPH_URI));
        assertThat(enumEntries)
                .filteredOn(entry -> entry.getLabel().equals("B"))
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.getGraphUri()).isEqualTo(OTHER_GRAPH_URI);
                            assertThat(entry.getColor()).isEqualTo(OTHER_COLOR);
                        });
    }

    @Test
    @DisplayName("colorizes the rendered graph's own properties with the primary color")
    void colorizesOwnPropertiesWithPrimaryColor() {
        var filter = coreFilter();
        filter.setIncludePropertiesFromOtherProfiles(true);

        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(
                                facade,
                                filter,
                                null,
                                List.of(
                                        new CIMProfileModel(
                                                OTHER_GRAPH_URI,
                                                OTHER_COLOR,
                                                null,
                                                buildOtherProfile())),
                                "#123456",
                                null);

        assertThat(nodeByLabel(result, "Child").getData().getAttributes())
                .filteredOn(attribute -> attribute.getLabel().equals("childAttr"))
                .singleElement()
                .satisfies(
                        attribute -> {
                            assertThat(attribute.getGraphUri()).isEqualTo(GRAPH_URI);
                            assertThat(attribute.getColor()).isEqualTo("#123456");
                        });
    }

    @Test
    @DisplayName("leaves attributes ungrouped when other-profile merge is disabled")
    void keepsAttributesUngroupedWhenDisabled() {
        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(
                                facade,
                                coreFilter(),
                                null,
                                List.of(
                                        new CIMProfileModel(
                                                OTHER_GRAPH_URI,
                                                OTHER_COLOR,
                                                null,
                                                buildOtherProfile())),
                                null,
                                null);

        var childAttributes = nodeByLabel(result, "Child").getData().getAttributes();
        assertThat(childAttributes).extracting(AttributeDTO::getLabel).containsExactly("childAttr");
        assertThat(childAttributes)
                .allSatisfy(attribute -> assertThat(attribute.getGraphUri()).isNull());
    }

    private static UUID mergedUuid(String label) {
        return UUID.nameUUIDFromBytes((NS + label).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("tags merged properties with the short name of the profile they come from")
    void mergedDiagramTagsPropertiesWithGraphKeyword() {
        var result =
                (SvelteFlowDTO)
                        renderer.renderMergedUML(
                                List.of(
                                        new CIMProfileModel(GRAPH_URI, null, "core", facade),
                                        new CIMProfileModel(
                                                OTHER_GRAPH_URI,
                                                OTHER_COLOR,
                                                "other",
                                                buildOtherProfile())),
                                null);

        assertThat(nodeByLabel(result, "Child").getData().getAttributes())
                .extracting(AttributeDTO::getLabel, AttributeDTO::getGraphKeyword)
                .containsExactly(tuple("childAttr", "core"), tuple("otherChildAttr", "other"));
        assertThat(nodeByLabel(result, "PhaseCode").getData().getEnumEntries())
                .extracting(EnumEntryDTO::getLabel, EnumEntryDTO::getGraphKeyword)
                .containsExactly(tuple("A", "core"), tuple("B", "other"));
    }

    @Test
    @DisplayName("tags own properties with the rendered graph's short name when merging")
    void packageDiagramTagsOwnPropertiesWithGraphKeyword() {
        var filter = coreFilter();
        filter.setIncludePropertiesFromOtherProfiles(true);

        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(
                                facade,
                                filter,
                                null,
                                List.of(
                                        new CIMProfileModel(
                                                OTHER_GRAPH_URI,
                                                OTHER_COLOR,
                                                "other",
                                                buildOtherProfile())),
                                null,
                                "core");

        assertThat(nodeByLabel(result, "Child").getData().getAttributes())
                .extracting(AttributeDTO::getLabel, AttributeDTO::getGraphKeyword)
                .containsExactly(tuple("childAttr", "core"), tuple("otherChildAttr", "other"));
    }

    @Test
    @DisplayName(
            "merged diagram combines same-uri classes into one node with per-source properties")
    void mergedDiagramCombinesClassesAcrossProfiles() {
        var result =
                (SvelteFlowDTO)
                        renderer.renderMergedUML(
                                List.of(
                                        new CIMProfileModel(GRAPH_URI, "#111111", null, facade),
                                        new CIMProfileModel(
                                                OTHER_GRAPH_URI,
                                                OTHER_COLOR,
                                                null,
                                                buildOtherProfile())),
                                null);

        var childNodes =
                result.getNodes().stream()
                        .filter(node -> node.getData().getLabel().equals("Child"))
                        .toList();
        assertThat(childNodes).hasSize(1);
        assertThat(childNodes.getFirst().getId()).isEqualTo(mergedUuid("Child"));
        assertThat(childNodes.getFirst().getData().getAttributes())
                .extracting(AttributeDTO::getLabel)
                .containsExactlyInAnyOrder("childAttr", "otherChildAttr");
        assertThat(childNodes.getFirst().getData().getAttributes())
                .filteredOn(attribute -> attribute.getLabel().equals("otherChildAttr"))
                .singleElement()
                .satisfies(
                        attribute -> {
                            assertThat(attribute.getGraphUri()).isEqualTo(OTHER_GRAPH_URI);
                            assertThat(attribute.getColor()).isEqualTo(OTHER_COLOR);
                        });

        assertThat(nodeByLabel(result, "PhaseCode").getData().getEnumEntries())
                .extracting(EnumEntryDTO::getLabel)
                .containsExactlyInAnyOrder("A", "B");
    }

    private void addEnumEntry(String localName, String label, Resource enumClass) {
        var entry = model.createResource(NS + localName);
        entry.addProperty(RDF.type, enumClass);
        entry.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        entry.addProperty(RDFS.label, model.createLiteral(label, "en"));
        entry.addProperty(CIMS.stereotype, CIMStereotypes.enumLiteral);
    }

    @Test
    @DisplayName("merged diagram sorts properties by graph uri and then by label")
    void mergedDiagramSortsPropertiesByGraphAndLabel() {
        var child = model.getResource(NS + "Child");
        addAttribute("Child.zetaAttr", "zetaAttr", child, "M:0..1");
        addAttribute("Child.alphaAttr", "AlphaAttr", child, "M:0..1");
        addEnumEntry("PhaseCode.C", "C", model.getResource(NS + "PhaseCode"));
        addEnumEntry("PhaseCode.b", "b", model.getResource(NS + "PhaseCode"));

        var result =
                (SvelteFlowDTO)
                        renderer.renderMergedUML(
                                List.of(
                                        new CIMProfileModel(GRAPH_URI, "#111111", null, facade),
                                        new CIMProfileModel(
                                                OTHER_GRAPH_URI,
                                                OTHER_COLOR,
                                                null,
                                                buildOtherProfile())),
                                null);

        assertThat(nodeByLabel(result, "Child").getData().getAttributes())
                .extracting(AttributeDTO::getLabel)
                .containsExactly("AlphaAttr", "childAttr", "zetaAttr", "otherChildAttr");
        assertThat(nodeByLabel(result, "PhaseCode").getData().getEnumEntries())
                .extracting(EnumEntryDTO::getLabel)
                .containsExactly("A", "b", "C", "B");
    }

    @Test
    @DisplayName("merged diagram renders inheritance and deduplicated association edges by uri")
    void mergedDiagramRendersEdges() {
        var result =
                (SvelteFlowDTO)
                        renderer.renderMergedUML(
                                List.of(new CIMProfileModel(GRAPH_URI, null, null, facade)), null);

        var inheritanceEdges =
                result.getEdges().stream()
                        .filter(edge -> edge.getType().equals("inheritance"))
                        .map(edge -> List.of(edge.getSource(), edge.getTarget()))
                        .toList();
        assertThat(inheritanceEdges)
                .containsExactlyInAnyOrder(
                        List.of(mergedUuid("Child"), mergedUuid("Base")),
                        List.of(mergedUuid("Base"), mergedUuid("Root")));

        var associationEdges =
                result.getEdges().stream()
                        .filter(edge -> edge.getType().equals("association"))
                        .toList();
        assertThat(associationEdges)
                .singleElement()
                .satisfies(
                        edge -> {
                            assertThat(List.of(edge.getSource(), edge.getTarget()))
                                    .containsExactlyInAnyOrder(
                                            mergedUuid("Child"), mergedUuid("Terminal"));
                            assertThat(
                                            List.of(
                                                    edge.getData().getFromMultiplicity(),
                                                    edge.getData().getToMultiplicity()))
                                    .containsExactlyInAnyOrder("0..n", "1..1");
                        });
    }

    private List<EdgeDTO> mergedAssociationEdges(List<CIMProfileModel> profiles) {
        var result = (SvelteFlowDTO) renderer.renderMergedUML(profiles, null);
        return result.getEdges().stream()
                .filter(edge -> edge.getType().equals("association"))
                .toList();
    }

    @Test
    @DisplayName("merged diagram keeps every association between the same two classes")
    void mergedDiagramKeepsAllAssociationsBetweenSameClasses() {
        var child = model.getResource(NS + "Child");
        var terminal = model.getResource(NS + "Terminal");
        linkInverse(
                addAssociationEnd("Child.OtherTerminals", child, terminal),
                addAssociationEnd("Terminal.OtherChild", terminal, child));

        var edges =
                mergedAssociationEdges(List.of(new CIMProfileModel(GRAPH_URI, null, null, facade)));

        assertThat(edges).hasSize(2);
        assertThat(edges)
                .allSatisfy(
                        edge ->
                                assertThat(List.of(edge.getSource(), edge.getTarget()))
                                        .containsExactlyInAnyOrder(
                                                mergedUuid("Child"), mergedUuid("Terminal")));
    }

    @Test
    @DisplayName("merged diagram renders an association shared by two profiles only once")
    void mergedDiagramDeduplicatesAssociationAcrossProfiles() {
        var edges =
                mergedAssociationEdges(
                        List.of(
                                new CIMProfileModel(GRAPH_URI, null, null, facade),
                                new CIMProfileModel(OTHER_GRAPH_URI, OTHER_COLOR, null, facade)));

        assertThat(edges).hasSize(1);
    }

    @Test
    @DisplayName("merged diagram lists super classes outside the diagram as label-only")
    void mergedDiagramRendersExternalSuperClassLabelOnly() {
        var result =
                (SvelteFlowDTO)
                        renderer.renderMergedUML(
                                List.of(new CIMProfileModel(GRAPH_URI, null, null, facade)), null);

        var externalSuperClass =
                nodeByLabel(result, "Child").getData().getSuperClasses().stream()
                        .filter(superClass -> superClass.getLabel().equals("ExternalBase"))
                        .findFirst()
                        .orElseThrow();
        assertThat(externalSuperClass.getUuid()).isNull();
        assertThat(externalSuperClass.getAttributes()).isEmpty();
    }

    private void addAssociationWithoutRange(String localName, Resource domain) {
        var association = model.createResource(NS + localName);
        association.addProperty(RDF.type, RDF.Property);
        association.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        association.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        association.addProperty(RDFS.domain, domain);
        association.addProperty(CIMS.multiplicity, model.createResource(CIMS.namespace + "M:0..n"));
        association.addProperty(CIMS.associationUsed, "Yes");
        association.addProperty(
                CIMS.inverseRoleName, model.createResource(NS + localName + ".inv"));
    }

    @Test
    @DisplayName("skips associations without a range instead of failing the package diagram")
    void skipsAssociationWithoutRangeInPackageDiagram() {
        addAssociationWithoutRange("Child.Broken", model.getResource(NS + "Child"));

        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        assertThat(result.getNodes()).isNotEmpty();
        assertThat(result.getEdges())
                .filteredOn(edge -> edge.getType().equals("association"))
                .hasSize(1);
    }

    @Test
    @DisplayName("skips associations without a range instead of failing the merged diagram")
    void skipsAssociationWithoutRangeInMergedDiagram() {
        addAssociationWithoutRange("Child.Broken", model.getResource(NS + "Child"));

        var result =
                (SvelteFlowDTO)
                        renderer.renderMergedUML(
                                List.of(new CIMProfileModel(GRAPH_URI, null, null, facade)), null);

        assertThat(result.getNodes()).isNotEmpty();
        assertThat(result.getEdges())
                .filteredOn(edge -> edge.getType().equals("association"))
                .hasSize(1);
    }

    private void addAttributeWithoutDataType(String localName, Resource domain) {
        var attribute = model.createResource(NS + localName);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        attribute.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        attribute.addProperty(RDFS.domain, domain);
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS.namespace + "M:0..1"));
    }

    @Test
    @DisplayName("skips attributes without a datatype instead of failing the package diagram")
    void skipsAttributeWithoutDataTypeInPackageDiagram() {
        addAttributeWithoutDataType("Child.brokenAttr", model.getResource(NS + "Child"));

        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        assertThat(nodeByLabel(result, "Child").getData().getAttributes())
                .extracting(AttributeDTO::getLabel)
                .containsExactly("childAttr");
    }

    @Test
    @DisplayName("skips attributes without a datatype instead of failing the merged diagram")
    void skipsAttributeWithoutDataTypeInMergedDiagram() {
        addAttributeWithoutDataType("Child.brokenAttr", model.getResource(NS + "Child"));

        var result =
                (SvelteFlowDTO)
                        renderer.renderMergedUML(
                                List.of(new CIMProfileModel(GRAPH_URI, null, null, facade)), null);

        assertThat(nodeByLabel(result, "Child").getData().getAttributes())
                .extracting(AttributeDTO::getLabel)
                .containsExactly("childAttr");
    }

    private Resource addAssociationEnd(String localName, Resource domain, Resource range) {
        var end = model.createResource(NS + localName);
        end.addProperty(RDF.type, RDF.Property);
        end.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        end.addProperty(RDFS.label, model.createLiteral(localName, "en"));
        end.addProperty(RDFS.domain, domain);
        end.addProperty(RDFS.range, range);
        end.addProperty(CIMS.multiplicity, model.createResource(CIMS.namespace + "M:0..n"));
        end.addProperty(CIMS.associationUsed, "Yes");
        return end;
    }

    private void linkInverse(Resource from, Resource inverse) {
        from.addProperty(CIMS.inverseRoleName, inverse);
        inverse.addProperty(CIMS.inverseRoleName, from);
    }

    @Test
    @DisplayName("skips associations whose inverse end has no multiplicity")
    void skipsAssociationWithIncompleteInverse() {
        var base = model.getResource(NS + "Base");
        var terminal = model.getResource(NS + "Terminal");
        var inverse = addAssociationEnd("Terminal.Base", terminal, base);
        inverse.removeAll(CIMS.multiplicity);
        linkInverse(addAssociationEnd("Base.Terminals", base, terminal), inverse);

        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        assertThat(result.getEdges())
                .filteredOn(edge -> edge.getType().equals("association"))
                .hasSize(1);
    }

    @Test
    @DisplayName("skips associations whose associationUsed is neither Yes nor No")
    void skipsAssociationWithInvalidAssociationUsed() {
        var base = model.getResource(NS + "Base");
        var terminal = model.getResource(NS + "Terminal");
        var from = addAssociationEnd("Base.Terminals", base, terminal);
        from.removeAll(CIMS.associationUsed);
        from.addProperty(CIMS.associationUsed, "true");
        linkInverse(from, addAssociationEnd("Terminal.Base", terminal, base));

        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        assertThat(result.getEdges())
                .filteredOn(edge -> edge.getType().equals("association"))
                .hasSize(1);
    }

    @Test
    @DisplayName("skips associations whose inverse end has no uuid")
    void skipsAssociationWithInverseWithoutUuid() {
        var base = model.getResource(NS + "Base");
        var terminal = model.getResource(NS + "Terminal");
        var inverse = addAssociationEnd("Terminal.Base", terminal, base);
        inverse.removeAll(RDFA.uuid);
        linkInverse(addAssociationEnd("Base.Terminals", base, terminal), inverse);

        var result =
                (SvelteFlowDTO)
                        renderer.renderUML(facade, coreFilter(), null, List.of(), null, null);

        assertThat(result.getEdges())
                .filteredOn(edge -> edge.getType().equals("association"))
                .hasSize(1);
    }

    @Test
    @DisplayName("merged diagram is empty when there are no profiles")
    void mergedDiagramEmptyForNoProfiles() {
        var result = (SvelteFlowDTO) renderer.renderMergedUML(List.of(), null);

        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getEdges()).isEmpty();
    }

    @Test
    @DisplayName("merged node uuid uses the full class uri, not the rdfs:label")
    void mergedUuidUsesFullUriNotLabel() {
        var labelModel = ModelFactory.createDefaultModel();
        var cimClass = labelModel.createResource(NS + "ACLS");
        cimClass.addProperty(RDF.type, RDFS.Class);
        cimClass.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        cimClass.addProperty(RDFS.label, labelModel.createLiteral("ACLineSegment", "en"));
        var labelFacade = new CIMModelFacade(GRAPH_URI, labelModel);

        var result =
                (SvelteFlowDTO)
                        renderer.renderMergedUML(
                                List.of(new CIMProfileModel(GRAPH_URI, null, null, labelFacade)),
                                null);

        assertThat(result.getNodes())
                .singleElement()
                .satisfies(
                        node -> {
                            assertThat(node.getData().getLabel()).isEqualTo("ACLineSegment");
                            assertThat(node.getId()).isEqualTo(mergedUuid("ACLS"));
                            assertThat(node.getId()).isNotEqualTo(mergedUuid("ACLineSegment"));
                        });
    }
}
