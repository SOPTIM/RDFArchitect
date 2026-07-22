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

package org.rdfarchitect.models.cim.data.dto.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

import java.util.UUID;

class CIMFacadeTest {

    private static final String GRAPH_URI = "http://graph#";
    private static final String NS = "http://example.com#";

    private static final UUID CATEGORY_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SWITCH_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BREAKER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ATTRIBUTE_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID TERMINAL_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID ASSOCIATION_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID INVERSE_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID ENUM_UUID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID ENTRY_UUID = UUID.fromString("00000000-0000-0000-0000-000000000009");

    private Model model;

    @BeforeEach
    void setUp() {
        model = ModelFactory.createDefaultModel();

        var category = model.createResource(NS + "CorePackage");
        category.addProperty(RDF.type, CIMS.classCategory);
        category.addProperty(RDFA.uuid, CATEGORY_UUID.toString());
        category.addProperty(RDFS.label, model.createLiteral("Core", "en"));

        var switchClass = model.createResource(NS + "Switch");
        switchClass.addProperty(RDF.type, RDFS.Class);
        switchClass.addProperty(RDFA.uuid, SWITCH_UUID.toString());
        switchClass.addProperty(RDFS.label, model.createLiteral("Switch", "en"));

        var externalBase = model.createResource(NS + "ExternalBase");

        var breaker = model.createResource(NS + "Breaker");
        breaker.addProperty(RDF.type, RDFS.Class);
        breaker.addProperty(RDFA.uuid, BREAKER_UUID.toString());
        breaker.addProperty(RDFS.label, model.createLiteral("Breaker", "en"));
        breaker.addProperty(RDFS.comment, "A breaker");
        breaker.addProperty(RDFS.subClassOf, switchClass);
        breaker.addProperty(RDFS.subClassOf, externalBase);
        breaker.addProperty(CIMS.belongsToCategory, category);
        breaker.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
        breaker.addProperty(CIMS.stereotype, CIMStereotypes.entsoe);

        var attribute = model.createResource(NS + "Breaker.value");
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFA.uuid, ATTRIBUTE_UUID.toString());
        attribute.addProperty(RDFS.label, model.createLiteral("value", "en"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        attribute.addProperty(RDFS.domain, breaker);
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS.namespace + "M:0..1"));
        attribute.addProperty(CIMS.datatype, model.createResource(XSD.xstring.getURI()));
        attribute.addProperty(CIMS.isFixed, "fixedValue");
        var wrapper = model.createResource();
        wrapper.addProperty(ResourceFactory.createProperty(RDFS.Literal.getURI()), "defaultValue");
        attribute.addProperty(CIMS.isDefault, wrapper);

        var terminal = model.createResource(NS + "Terminal");
        terminal.addProperty(RDF.type, RDFS.Class);
        terminal.addProperty(RDFA.uuid, TERMINAL_UUID.toString());
        terminal.addProperty(RDFS.label, model.createLiteral("Terminal", "en"));

        var association = model.createResource(NS + "Breaker.Terminals");
        association.addProperty(RDF.type, RDF.Property);
        association.addProperty(RDFA.uuid, ASSOCIATION_UUID.toString());
        association.addProperty(RDFS.label, model.createLiteral("Terminals", "en"));
        association.addProperty(RDFS.domain, breaker);
        association.addProperty(RDFS.range, terminal);
        association.addProperty(CIMS.multiplicity, model.createResource(CIMS.namespace + "M:0..n"));
        association.addProperty(CIMS.associationUsed, "Yes");

        var inverse = model.createResource(NS + "Terminal.Breaker");
        inverse.addProperty(RDF.type, RDF.Property);
        inverse.addProperty(RDFA.uuid, INVERSE_UUID.toString());
        inverse.addProperty(RDFS.label, model.createLiteral("Breaker", "en"));
        inverse.addProperty(RDFS.domain, terminal);
        inverse.addProperty(RDFS.range, breaker);
        inverse.addProperty(CIMS.associationUsed, "No");
        inverse.addProperty(CIMS.inverseRoleName, association);
        association.addProperty(CIMS.inverseRoleName, inverse);

        var phaseCode = model.createResource(NS + "PhaseCode");
        phaseCode.addProperty(RDF.type, RDFS.Class);
        phaseCode.addProperty(RDFA.uuid, ENUM_UUID.toString());
        phaseCode.addProperty(RDFS.label, model.createLiteral("PhaseCode", "en"));
        phaseCode.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);

        var entry = model.createResource(NS + "PhaseCode.A");
        entry.addProperty(RDF.type, phaseCode);
        entry.addProperty(RDFA.uuid, ENTRY_UUID.toString());
        entry.addProperty(RDFS.label, model.createLiteral("A", "en"));
        entry.addProperty(CIMS.stereotype, CIMStereotypes.enumLiteral);
    }

    private CIMClass breaker() {
        return new CIMClass(GRAPH_URI, model, BREAKER_UUID);
    }

    @Test
    @DisplayName("resolves label, comment, uri and graph uri of a class")
    void classBaseProperties() {
        var breaker = breaker();

        assertThat(breaker.getUuid()).isEqualTo(BREAKER_UUID);
        assertThat(breaker.getGraphUri()).isEqualTo(GRAPH_URI);
        assertThat(breaker.getUri().toString()).isEqualTo(NS + "Breaker");
        assertThat(breaker.getLabel().getValue()).isEqualTo("Breaker");
        assertThat(breaker.getLabel().getLang()).isEqualTo("en");
        assertThat(breaker.getComment().getValue()).isEqualTo("A breaker");
    }

    @Test
    @DisplayName("returns internal super classes as CIMClass and external ones as ExternalCIMClass")
    void superClasses() {
        var superClasses = breaker().getSuperClasses();

        assertThat(superClasses).hasSize(2);
        assertThat(superClasses)
                .filteredOn(CIMClass.class::isInstance)
                .singleElement()
                .satisfies(internal -> assertThat(internal.getUuid()).isEqualTo(SWITCH_UUID));
        assertThat(superClasses)
                .filteredOn(ExternalCIMClass.class::isInstance)
                .singleElement()
                .satisfies(
                        external -> {
                            assertThat(external.getUuid()).isNull();
                            assertThat(external.getUri().toString()).isEqualTo(NS + "ExternalBase");
                            assertThat(external.getLabel().getValue()).isEqualTo("ExternalBase");
                            assertThat(external.getComment()).isNull();
                        });
    }

    @Test
    @DisplayName("resolves the category a class belongs to")
    void belongsToCategory() {
        var category = breaker().getBelongsToCategory();

        assertThat(category).isNotNull();
        assertThat(category.getUuid()).isEqualTo(CATEGORY_UUID);
        assertThat(category.getLabel().getValue()).isEqualTo("Core");
    }

    @Test
    @DisplayName("returns null when a class belongs to no category")
    void belongsToNoCategory() {
        var terminal = new CIMClass(GRAPH_URI, model, TERMINAL_UUID);

        assertThat(terminal.getBelongsToCategory()).isNull();
    }

    @Test
    @DisplayName("returns uri and literal stereotypes")
    void stereotypes() {
        var stereotypes = breaker().getStereotypes();

        assertThat(stereotypes)
                .containsExactlyInAnyOrder(
                        new CIMSStereotype(CIMStereotypes.concreteString),
                        new CIMSStereotype(CIMStereotypes.entsoeString));
    }

    @Test
    @DisplayName("returns only direct attributes with all their relations")
    void attributes() {
        var attributes = breaker().getAttributes();

        assertThat(attributes).hasSize(1);
        var attribute = attributes.getFirst();
        assertThat(attribute.getUuid()).isEqualTo(ATTRIBUTE_UUID);
        assertThat(attribute.getDomain().getUuid()).isEqualTo(BREAKER_UUID);
        assertThat(attribute.getMultiplicity())
                .isEqualTo(new CIMSMultiplicity(CIMS.namespace + "M:0..1"));
        assertThat(attribute.getStereotype())
                .isEqualTo(new CIMSStereotype(CIMStereotypes.attributeString));
        assertThat(attribute.getDataType()).isInstanceOf(ExternalCIMClass.class);
        assertThat(attribute.getDataType().getUri().toString()).isEqualTo(XSD.xstring.getURI());
    }

    @Test
    @DisplayName(
            "reads fixed values from direct literals and default values from blank-node wrappers")
    void fixedAndDefaultValues() {
        var attribute = breaker().getAttributes().getFirst();

        var fixed = attribute.getFixed();
        assertThat(fixed).isNotNull();
        assertThat(fixed.getValue()).isEqualTo("fixedValue");
        assertThat(fixed.isBlankNode()).isFalse();

        var defaultValue = attribute.getDefault();
        assertThat(defaultValue).isNotNull();
        assertThat(defaultValue.getValue()).isEqualTo("defaultValue");
        assertThat(defaultValue.isBlankNode()).isTrue();
    }

    @Test
    @DisplayName("returns only direct associations with domain, range, inverse and associationUsed")
    void associations() {
        var associations = breaker().getAssociations();

        assertThat(associations).hasSize(1);
        var association = associations.getFirst();
        assertThat(association.getUuid()).isEqualTo(ASSOCIATION_UUID);
        assertThat(association.getDomain().getUuid()).isEqualTo(BREAKER_UUID);
        assertThat(association.getRange().getUuid()).isEqualTo(TERMINAL_UUID);
        assertThat(association.getMultiplicity())
                .isEqualTo(new CIMSMultiplicity(CIMS.namespace + "M:0..n"));
        assertThat(association.getAssociationUsed()).isEqualTo(new CIMSAssociationUsed("Yes"));

        var inverse = association.getInverseAssociation();
        assertThat(inverse).isNotNull();
        assertThat(inverse.getUuid()).isEqualTo(INVERSE_UUID);
        assertThat(inverse.getDomain().getUuid()).isEqualTo(TERMINAL_UUID);
        assertThat(inverse.getAssociationUsed()).isEqualTo(new CIMSAssociationUsed("No"));
    }

    @Test
    @DisplayName("throws for attributes and associations missing required properties")
    void requiredProperties() {
        var bareUuid = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        var bare = model.createResource(NS + "Breaker.bare");
        bare.addProperty(RDF.type, RDF.Property);
        bare.addProperty(RDFA.uuid, bareUuid.toString());

        var attribute = new CIMAttribute(GRAPH_URI, model, bareUuid);
        assertThatThrownBy(attribute::getDomain).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(attribute::getMultiplicity).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(attribute::getDataType).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(attribute::getStereotype).isInstanceOf(IllegalStateException.class);

        var association = new CIMAssociation(GRAPH_URI, model, bareUuid);
        assertThatThrownBy(association::getRange).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(association::getInverseAssociation)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(association::getAssociationUsed)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(association::getMultiplicity).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("falls back to the uri suffix for classes without a label")
    void classLabelFallback() {
        var dataTypeResource = model.getResource(XSD.xstring.getURI());
        dataTypeResource.addProperty(RDFA.uuid, "00000000-0000-0000-0000-00000000000c");

        var dataType = breaker().getAttributes().getFirst().getDataType();

        assertThat(dataType).isInstanceOf(CIMClass.class);
        assertThat(dataType.getLabel().getValue()).isEqualTo("string");
    }

    @Test
    @DisplayName("resolves a class category by uuid and falls back to the default category")
    void classCategoryLookup() {
        var facade = new CIMModelFacade(GRAPH_URI, model);

        var category = facade.getCIMClassCategory(CATEGORY_UUID);
        assertThat(category.getUuid()).isEqualTo(CATEGORY_UUID);
        assertThat(category.getLabel().getValue()).isEqualTo("Core");
        assertThat(category.getClasses())
                .extracting(ICIMClass::getUuid)
                .containsExactly(BREAKER_UUID);

        var defaultCategory = facade.getCIMClassCategory(null);
        assertThat(defaultCategory).isInstanceOf(DefaultCIMClassCategory.class);
        assertThat(defaultCategory.getClasses())
                .extracting(ICIMClass::getUuid)
                .containsExactlyInAnyOrder(SWITCH_UUID, TERMINAL_UUID, ENUM_UUID);

        assertThat(facade.getCIMClassCategory(UUID.randomUUID())).isNull();
    }

    @Test
    @DisplayName("resolves an external category that is only referenced via belongsToCategory")
    void externalClassCategory() {
        var externalUuid = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        var externalCategory = model.createResource(NS + "ExternalPackage");
        externalCategory.addProperty(RDFA.uuid, externalUuid.toString());
        model.getResource(NS + "Terminal").addProperty(CIMS.belongsToCategory, externalCategory);

        var facade = new CIMModelFacade(GRAPH_URI, model);

        var category = facade.getCIMClassCategory(externalUuid);
        assertThat(category).isNotNull();
        assertThat(category.getLabel().getValue()).isEqualTo("ExternalPackage");
        assertThat(category.getClasses())
                .extracting(ICIMClass::getUuid)
                .containsExactly(TERMINAL_UUID);

        assertThat(facade.getCIMClassCategories())
                .extracting(ICIMClassCategory::getUuid)
                .contains(CATEGORY_UUID, externalUuid);
    }

    @Test
    @DisplayName("returns enum entries for enumeration classes and none for regular classes")
    void enumEntries() {
        var phaseCode = new CIMClass(GRAPH_URI, model, ENUM_UUID);

        var entries = phaseCode.getEnumEntries();
        assertThat(entries).hasSize(1);
        var entry = entries.getFirst();
        assertThat(entry.getUuid()).isEqualTo(ENTRY_UUID);
        assertThat(entry.getDomain().getUuid()).isEqualTo(ENUM_UUID);
        assertThat(entry.getStereotype())
                .isEqualTo(new CIMSStereotype(CIMStereotypes.enumLiteralString));

        assertThat(breaker().getEnumEntries()).isEmpty();
    }
}
