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
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EnumEntryDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.NodeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.SuperClassDTO;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.services.rendering.svelteflow.RenderCIMFacadeCollectionSvelteFlowService;

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
    private static final UUID REMOTE_UUID =
            UUID.fromString("00000000-0000-0000-0000-00000000000a");

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

        var association = model.createResource(NS + "Child.Terminals");
        association.addProperty(RDF.type, RDF.Property);
        association.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        association.addProperty(RDFS.label, model.createLiteral("Terminals", "en"));
        association.addProperty(RDFS.domain, child);
        association.addProperty(RDFS.range, terminal);
        association.addProperty(CIMS.multiplicity, model.createResource(CIMS.namespace + "M:0..n"));
        association.addProperty(CIMS.associationUsed, "Yes");

        var inverse = model.createResource(NS + "Terminal.Child");
        inverse.addProperty(RDF.type, RDF.Property);
        inverse.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        inverse.addProperty(RDFS.label, model.createLiteral("Child", "en"));
        inverse.addProperty(RDFS.domain, terminal);
        inverse.addProperty(RDFS.range, child);
        inverse.addProperty(CIMS.multiplicity, model.createResource(CIMS.namespace + "M:1..1"));
        inverse.addProperty(CIMS.associationUsed, "No");
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

    private void addAttribute(
            String localName, String label, Resource domain, String multiplicity) {
        var attribute = model.createResource(NS + localName);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFA.uuid, UUID.randomUUID().toString());
        attribute.addProperty(RDFS.label, model.createLiteral(label, "en"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        attribute.addProperty(RDFS.domain, domain);
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS.namespace + multiplicity));
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
        var result = (SvelteFlowDTO) renderer.renderUML(facade, coreFilter(), null);

        assertThat(result.getNodes())
                .extracting(node -> node.getData().getLabel())
                .containsExactlyInAnyOrder("Root", "Base", "Child", "PhaseCode", "Terminal");
        assertThat(nodeByLabel(result, "Child").getId()).isEqualTo(CHILD_UUID);
        assertThat(nodeByLabel(result, "Child").getData().getBelongsToCategory())
                .isEqualTo("Core");
        assertThat(nodeByLabel(result, "Terminal").getData().getBelongsToCategory())
                .isEqualTo("Other");
    }

    @Test
    @DisplayName("renders direct attributes and stereotypes of a class")
    void rendersDirectAttributesAndStereotypes() {
        var result = (SvelteFlowDTO) renderer.renderUML(facade, coreFilter(), null);

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
        var result = (SvelteFlowDTO) renderer.renderUML(facade, coreFilter(), null);

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
        var result = (SvelteFlowDTO) renderer.renderUML(facade, coreFilter(), null);

        var enumData = nodeByLabel(result, "PhaseCode").getData();
        assertThat(enumData.getStereotypes()).contains("abstract", "enumeration");
        assertThat(enumData.getEnumEntries())
                .extracting(EnumEntryDTO::getLabel)
                .containsExactly("A");
    }

    @Test
    @DisplayName("renders inheritance edges between internal classes only")
    void rendersInheritanceEdges() {
        var result = (SvelteFlowDTO) renderer.renderUML(facade, coreFilter(), null);

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
        var result = (SvelteFlowDTO) renderer.renderUML(facade, coreFilter(), null);

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
    @DisplayName("omits attributes and enum entries when disabled by the filter")
    void filterDisablesAttributesAndEnumEntries() {
        var filter = coreFilter();
        filter.setIncludeAttributes(false);
        filter.setIncludeEnumEntries(false);

        var result = (SvelteFlowDTO) renderer.renderUML(facade, filter, null);

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

        var result = (SvelteFlowDTO) renderer.renderUML(facade, filter, null);

        assertThat(result.getEdges()).noneMatch(edge -> edge.getType().equals("inheritance"));
    }

    @Test
    @DisplayName("omits association edges when disabled by the filter")
    void filterDisablesAssociations() {
        var filter = coreFilter();
        filter.setIncludeAssociations(false);

        var result = (SvelteFlowDTO) renderer.renderUML(facade, filter, null);

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

        var result = (SvelteFlowDTO) renderer.renderUML(facade, filter, null);

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

        var result = (SvelteFlowDTO) renderer.renderUML(facade, filter, null);

        assertThat(result.getNodes())
                .extracting(node -> node.getData().getLabel())
                .containsExactly("Loose");
    }

    @Test
    @DisplayName("renders classes of an external category that has only a uuid")
    void rendersExternalCategory() {
        var filter = new GraphFilter(true);
        filter.setPackageUUID(EXTERNAL_CAT_UUID.toString());

        var result = (SvelteFlowDTO) renderer.renderUML(facade, filter, null);

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

        var result = (SvelteFlowDTO) renderer.renderUML(facade, filter, null);

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

        var result = (SvelteFlowDTO) renderer.renderUML(emptyFacade, coreFilter(), null);

        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getEdges()).isEmpty();
    }
}
