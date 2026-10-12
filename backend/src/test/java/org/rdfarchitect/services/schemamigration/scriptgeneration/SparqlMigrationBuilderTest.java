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

package org.rdfarchitect.services.schemamigration.scriptgeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.context.SchemaMigrationContext;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.services.schemamigration.artifacts.SparqlMigrationBuilder;
import org.rdfarchitect.services.schemamigration.artifacts.SparqlUpdateGenerator;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class SparqlMigrationBuilderTest {

    private static final String PREFIX = "http://iec.ch/TC57/CIM100#";
    private static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";
    private static final String XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer";

    @Mock private MigrationSessionStore migrationSessionStore;

    private SparqlMigrationBuilder builder;

    private Model schema;

    @BeforeEach
    void setUp() {
        schema = ModelFactory.createDefaultModel();
        var context = new SchemaMigrationContext();
        context.setUpdatedSchema(schema.getGraph());
        lenient().when(migrationSessionStore.getContext()).thenReturn(context);
        builder = new SparqlMigrationBuilder(new SparqlUpdateGenerator(migrationSessionStore));
    }

    private Resource concreteClass(String localName) {
        var resource = schema.createResource(PREFIX + localName);
        resource.addProperty(RDF.type, RDFS.Class);
        resource.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
        return resource;
    }

    @Test
    void generateMigrationScript_datatypeChanged_convertsValuesWithDefaultAsFallback() {
        var script = generateFor(datatypeChange(XSD_STRING, XSD_INTEGER, false, List.of()));

        assertThat(script).contains("DELETE").contains("INSERT").contains("COALESCE");
    }

    @Test
    void generateMigrationScript_datatypesMarkedEquivalent_keepsExistingValues() {
        var script = generateFor(datatypeChange(XSD_STRING, XSD_INTEGER, true, List.of()));

        assertThat(script).isBlank();
    }

    @Test
    void generateMigrationScript_enumDatatypeChanged_replacesValues() {
        var script =
                generateFor(
                        datatypeChange(
                                PREFIX + "OldKind",
                                PREFIX + "NewKind",
                                false,
                                List.of(PREFIX + "NewKind.one")));

        assertThat(script).contains("DELETE").contains("INSERT");
    }

    @Test
    void generateMigrationScript_enumDatatypesMarkedEquivalent_keepsExistingValues() {
        var script =
                generateFor(
                        datatypeChange(
                                PREFIX + "OldKind",
                                PREFIX + "NewKind",
                                true,
                                List.of(PREFIX + "NewKind.one")));

        assertThat(script).isBlank();
    }

    @Test
    void generateMigrationScript_movedAttribute_rewritesPredicateLikeARename() {
        var attributeChange =
                SemanticAttributeChange.builder()
                        .iri(PREFIX + "NewClass.voltage")
                        .oldIRI(PREFIX + "OldClass.voltage")
                        .label("voltage")
                        .semanticResourceChangeType(SemanticResourceChangeType.MOVE)
                        .build();

        var script = generateFor(attributeChange);

        assertThat(script)
                .contains("DELETE")
                .contains("INSERT")
                .contains(PREFIX + "OldClass.voltage")
                .contains(PREFIX + "NewClass.voltage");
    }

    @Test
    void generateMigrationScript_mergedAttribute_rewritesEveryOldIRIIntoTheSameNewOne() {
        var attributeChange =
                SemanticAttributeChange.builder()
                        .iri(PREFIX + "NewClass.voltage")
                        .oldIRI(PREFIX + "ClassA.voltage")
                        .additionalOldIRIs(List.of(PREFIX + "ClassB.volt"))
                        .label("voltage")
                        .semanticResourceChangeType(SemanticResourceChangeType.RENAME)
                        .build();

        var script = generateFor(attributeChange);

        assertThat(script)
                .contains(PREFIX + "ClassA.voltage")
                .contains(PREFIX + "ClassB.volt")
                .contains(PREFIX + "NewClass.voltage");
    }

    @Test
    void generateMigrationScript_threeWaySplit_scopesEachTargetToItsOwnClass() {
        concreteClass("ClassA");
        concreteClass("ClassB");
        concreteClass("ClassC");

        var oldIri = PREFIX + "OldClass.voltage";
        var sibling1 = splitSibling(oldIri, PREFIX + "ClassA.voltage", "ClassA");
        var sibling2 = splitSibling(oldIri, PREFIX + "ClassB.voltage", "ClassB");
        var sibling3 = splitSibling(oldIri, PREFIX + "ClassC.voltage", "ClassC");

        var script =
                builder.generateMigrationScript(
                        List.of(
                                classWithAttribute("ClassA", sibling1),
                                classWithAttribute("ClassB", sibling2),
                                classWithAttribute("ClassC", sibling3)));

        // one independent DELETE/WHERE pair per target, each scoped by rdf:type to its own
        // class - a shared, unscoped block would copy every target's value onto every instance
        // regardless of which split target it actually belongs to (RDFA-729)
        assertThat(script.split("DELETE").length - 1).isEqualTo(3);
        assertThat(script)
                .contains(oldIri)
                .contains(PREFIX + "ClassA.voltage")
                .contains(PREFIX + "ClassB.voltage")
                .contains(PREFIX + "ClassC.voltage")
                .contains(PREFIX + "ClassA>")
                .contains(PREFIX + "ClassB>")
                .contains(PREFIX + "ClassC>");
    }

    @Test
    void generateMigrationScript_attributeAddedFromInheritance_scopesToGivenClass() {
        var attributeChange =
                SemanticAttributeChange.builder()
                        .iri(PREFIX + "Base.voltage")
                        .label("voltage")
                        .semanticResourceChangeType(
                                SemanticResourceChangeType.ADDED_FROM_INHERITANCE)
                        .defaultValue("0")
                        .primitiveDataType(XSD_INTEGER)
                        .build();

        var script = generateFor(attributeChange);

        assertThat(script).contains(PREFIX + "Switch").contains(PREFIX + "Base.voltage");
    }

    @Test
    void generateMigrationScript_attributeDeletedFromInheritance_scopesToGivenClass() {
        var attributeChange =
                SemanticAttributeChange.builder()
                        .iri(PREFIX + "Base.voltage")
                        .label("voltage")
                        .semanticResourceChangeType(
                                SemanticResourceChangeType.DELETED_FROM_INHERITANCE)
                        .build();

        var script = generateFor(attributeChange);

        assertThat(script)
                .contains("DELETE")
                .contains(PREFIX + "Switch")
                .contains(PREFIX + "Base.voltage");
    }

    /**
     * This marker (see {@code SchemaMigrationService.attachDeletionMarkersForLosingDomains}) exists
     * purely for the review step to show that the old domain lost the property - the surviving
     * RENAME/MOVE entry on the new domain already generates the one delete+insert this predicate's
     * data needs. Generating a second, independent delete for it here would race that rename and
     * could destroy the data before it gets migrated, depending on class order in the script.
     */
    @Test
    void generateMigrationScript_attributeDeletedFromMapping_generatesNoSparql() {
        var attributeChange =
                SemanticAttributeChange.builder()
                        .iri(PREFIX + "Sub1.voltage")
                        .label("voltage")
                        .semanticResourceChangeType(SemanticResourceChangeType.DELETED_FROM_MAPPING)
                        .build();

        var script = generateFor(attributeChange);

        assertThat(script).isBlank();
    }

    @Test
    void generateMigrationScript_associationDeletedFromMapping_generatesNoSparql() {
        var associationChange =
                SemanticAssociationChange.builder()
                        .iri(PREFIX + "Sub1.link")
                        .label("link")
                        .semanticResourceChangeType(SemanticResourceChangeType.DELETED_FROM_MAPPING)
                        .build();

        var script = generateForAssociation(associationChange);

        assertThat(script).isBlank();
    }

    @Test
    void
            generateMigrationScript_associationAddedFromInheritance_scopesToGivenClassNotAssociationIri() {
        var associationChange =
                SemanticAssociationChange.builder()
                        .iri(PREFIX + "Base.link")
                        .label("link")
                        .semanticResourceChangeType(
                                SemanticResourceChangeType.ADDED_FROM_INHERITANCE)
                        .associationUsed(true)
                        .mapping("?instance <" + PREFIX + "someProp> ?target .")
                        .build();

        var script = generateForAssociation(associationChange);

        assertThat(script).contains(PREFIX + "Switch").contains(PREFIX + "Base.link");
    }

    @Test
    void generateMigrationScript_associationDeletedFromInheritance_scopesToGivenClass() {
        var associationChange =
                SemanticAssociationChange.builder()
                        .iri(PREFIX + "Base.link")
                        .label("link")
                        .semanticResourceChangeType(
                                SemanticResourceChangeType.DELETED_FROM_INHERITANCE)
                        .build();

        var script = generateForAssociation(associationChange);

        assertThat(script)
                .contains("DELETE")
                .contains(PREFIX + "Switch")
                .contains(PREFIX + "Base.link");
    }

    private SemanticAttributeChange splitSibling(String oldIri, String newIri, String domainLocalName) {
        return SemanticAttributeChange.builder()
                .iri(newIri)
                .oldIRI(oldIri)
                .domain(PREFIX + domainLocalName)
                .label("voltage")
                .semanticResourceChangeType(SemanticResourceChangeType.MOVE)
                .build();
    }

    private SemanticClassChange classWithAttribute(
            String classLabel, SemanticAttributeChange attributeChange) {
        return SemanticClassChange.builder()
                .iri(PREFIX + classLabel)
                .label(classLabel)
                .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                .attributes(new ArrayList<>(List.of(attributeChange)))
                .associations(new ArrayList<>())
                .enumEntries(new ArrayList<>())
                .build();
    }

    private String generateForAssociation(SemanticAssociationChange associationChange) {
        var classChange =
                SemanticClassChange.builder()
                        .iri(PREFIX + "Switch")
                        .label("Switch")
                        .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                        .attributes(new ArrayList<>())
                        .associations(new ArrayList<>(List.of(associationChange)))
                        .enumEntries(new ArrayList<>())
                        .build();

        return builder.generateMigrationScript(List.of(classChange));
    }

    private String generateFor(SemanticAttributeChange attributeChange) {
        var classChange =
                SemanticClassChange.builder()
                        .iri(PREFIX + "Switch")
                        .label("Switch")
                        .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                        .attributes(new ArrayList<>(List.of(attributeChange)))
                        .associations(new ArrayList<>())
                        .enumEntries(new ArrayList<>())
                        .build();

        return builder.generateMigrationScript(List.of(classChange));
    }

    private SemanticAttributeChange datatypeChange(
            String oldDataType,
            String newDataType,
            boolean equivalent,
            List<String> allowedValues) {
        var attributeChange =
                SemanticAttributeChange.builder()
                        .iri(PREFIX + "Switch.state")
                        .label("state")
                        .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                        .oldDataType(oldDataType)
                        .dataType(newDataType)
                        .primitiveDataType(newDataType)
                        .defaultValue(allowedValues.isEmpty() ? "0" : allowedValues.getFirst())
                        .dataTypesEquivalent(equivalent)
                        .allowedValues(new ArrayList<>(allowedValues))
                        .build();
        attributeChange
                .getChanges()
                .add(
                        new SemanticFieldChange(
                                SemanticFieldChangeType.DATATYPE_CHANGE, oldDataType, newDataType));
        return attributeChange;
    }
}
