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
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.update.UpdateAction;
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
import org.rdfarchitect.models.changes.semanticchanges.SemanticPropertyChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.services.schemamigration.artifacts.SparqlMigrationBuilder;
import org.rdfarchitect.services.schemamigration.artifacts.SparqlUpdateGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the generated script against instance data, because a property moving down the hierarchy is
 * only correct in terms of what the data looks like afterwards: the predicate is rewritten by IRI,
 * and every class that inherited the old one carries it just the same.
 */
@ExtendWith(MockitoExtension.class)
class MovedPropertyMigrationTest {

    private static final String PREFIX = "http://iec.ch/TC57/CIM100#";

    @Mock private MigrationSessionStore migrationSessionStore;

    private SparqlMigrationBuilder builder;
    private Model schema;
    private Model originalSchema;

    @BeforeEach
    void setUp() {
        schema = ModelFactory.createDefaultModel();
        originalSchema = ModelFactory.createDefaultModel();
        var context = new SchemaMigrationContext();
        context.setUpdatedSchema(schema.getGraph());
        context.setOriginalSchema(originalSchema.getGraph());
        lenient().when(migrationSessionStore.getContext()).thenReturn(context);
        builder = new SparqlMigrationBuilder(new SparqlUpdateGenerator(migrationSessionStore));
    }

    @Test
    void movedAttribute_reachesOnlyTheReceivingClass() {
        var moved = new SemanticAttributeChange();
        prepareMove(moved);

        var data = migrate(moved);

        assertThat(valuesOf(data, "Child.p")).containsExactly("value-of-child");
        assertThat(valuesOf(data, "Base.p")).isEmpty();
    }

    @Test
    void movedAssociation_reachesOnlyTheReceivingClass() {
        var moved = new SemanticAssociationChange();
        prepareMove(moved);

        var data = migrate(moved);

        assertThat(valuesOf(data, "Child.p")).containsExactly("value-of-child");
        assertThat(valuesOf(data, "Base.p")).isEmpty();
    }

    /** A class that keeps deriving from the receiving class keeps its value under the new name. */
    @Test
    void movedAttribute_reachesClassesDerivingFromTheReceivingClass() {
        var moved = new SemanticAttributeChange();
        prepareMove(moved);
        concreteClass("GrandChild", "Child");

        var data = migrate(moved, "grandChild", "GrandChild");

        assertThat(valuesOf(data, "Child.p"))
                .containsExactlyInAnyOrder("value-of-child", "value-of-grandChild");
    }

    /**
     * A split rewrites per target, so a class in none of them has nothing carrying its value over
     * and has to be named explicitly to lose the property.
     */
    @Test
    void splitAttribute_leavesNothingOnClassesInNoTarget() {
        propertyOfOldClass("Base.p", "Base");
        concreteClass("Base", null);
        concreteClass("Child", "Base");
        concreteClass("OtherChild", "Base");

        var ontoChild = splitSibling("Child");
        var ontoOtherChild = splitSibling("OtherChild");
        var script =
                builder.generateMigrationScript(
                        List.of(
                                classChange("Child", ontoChild),
                                classChange("OtherChild", ontoOtherChild)));

        var data = ModelFactory.createDefaultModel();
        instance(data, "baseOnly", "Base");
        instance(data, "child", "Child");
        instance(data, "otherChild", "OtherChild");
        UpdateAction.parseExecute(script, data);

        assertThat(valuesOf(data, "Child.p")).containsExactly("value-of-child");
        assertThat(valuesOf(data, "OtherChild.p")).containsExactly("value-of-otherChild");
        assertThat(valuesOf(data, "Base.p")).isEmpty();
    }

    private SemanticAttributeChange splitSibling(String targetClass) {
        var sibling = new SemanticAttributeChange();
        sibling.setIri(PREFIX + targetClass + ".p");
        sibling.setOldIRI(PREFIX + "Base.p");
        sibling.setLabel("p");
        sibling.setDomain(PREFIX + targetClass);
        sibling.setSemanticResourceChangeType(SemanticResourceChangeType.SPLIT);
        return sibling;
    }

    /**
     * Moving a property up onto a class that has instances of its own leaves those without a value,
     * since only the instances that already had one are carried over. They are initialized from the
     * default the user was asked for, and the instances that did get a value keep it.
     */
    @Test
    void movedAttribute_initializesTargetInstancesThatHadNoValue() {
        propertyOfOldClass("Child.p", "Child");
        concreteClass("Base", null);
        concreteClass("Child", "Base");

        var moved = new SemanticAttributeChange();
        moved.setIri(PREFIX + "Base.p");
        moved.setOldIRI(PREFIX + "Child.p");
        moved.setLabel("p");
        moved.setDomain(PREFIX + "Base");
        moved.setSemanticResourceChangeType(SemanticResourceChangeType.MOVE);
        moved.setTargetInstancesNeedValue(true);
        moved.setDefaultValue("filled-in");
        moved.setPrimitiveDataType("http://www.w3.org/2001/XMLSchema#string");

        var script = builder.generateMigrationScript(List.of(classChange("Base", moved)));

        var data = ModelFactory.createDefaultModel();
        data.createResource(PREFIX + "child")
                .addProperty(RDF.type, data.createResource(PREFIX + "Child"))
                .addProperty(data.createProperty(PREFIX + "Child.p"), "value-of-child");
        data.createResource(PREFIX + "baseOnly")
                .addProperty(RDF.type, data.createResource(PREFIX + "Base"));
        UpdateAction.parseExecute(script, data);

        assertThat(valuesOf(data, "Base.p"))
                .containsExactlyInAnyOrder("value-of-child", "filled-in");
    }

    /**
     * A rename inside one class needs no scoping - everything carrying the old name is supposed to
     * end up with the new one - and scoping it would emit one block per class in the hierarchy.
     */
    @Test
    void renameWithinOneClass_staysASingleUnscopedBlock() {
        propertyOfOldClass("Base.p", "Base");
        concreteClass("Base", null);
        concreteClass("Child", "Base");
        var renamed = new SemanticAttributeChange();
        renamed.setIri(PREFIX + "Base.q");
        renamed.setOldIRI(PREFIX + "Base.p");
        renamed.setLabel("q");
        renamed.setDomain(PREFIX + "Base");
        renamed.setSemanticResourceChangeType(SemanticResourceChangeType.RENAME);

        var script = builder.generateMigrationScript(List.of(classChange("Base", renamed)));

        assertThat(script).containsOnlyOnce("DELETE");
        assertThat(script).doesNotContain("?instance a ");
    }

    private void prepareMove(SemanticPropertyChange moved) {
        propertyOfOldClass("Base.p", "Base");
        concreteClass("Base", null);
        concreteClass("Child", "Base");
        concreteClass("OtherChild", "Base");
        moved.setIri(PREFIX + "Child.p");
        moved.setOldIRI(PREFIX + "Base.p");
        moved.setLabel("p");
        moved.setDomain(PREFIX + "Child");
        moved.setSemanticResourceChangeType(SemanticResourceChangeType.MOVE);
    }

    private Model migrate(SemanticPropertyChange moved, String... extraInstanceAndType) {
        var script = builder.generateMigrationScript(List.of(classChange("Child", moved)));

        var data = ModelFactory.createDefaultModel();
        instance(data, "baseOnly", "Base");
        instance(data, "child", "Child");
        instance(data, "otherChild", "OtherChild");
        if (extraInstanceAndType.length == 2) {
            instance(data, extraInstanceAndType[0], extraInstanceAndType[1]);
        }

        UpdateAction.parseExecute(script, data);
        return data;
    }

    private void instance(Model data, String localName, String type) {
        data.createResource(PREFIX + localName)
                .addProperty(RDF.type, data.createResource(PREFIX + type))
                .addProperty(data.createProperty(PREFIX + "Base.p"), "value-of-" + localName);
    }

    private List<String> valuesOf(Model data, String propertyLocalName) {
        return data
                .listStatements(
                        null, data.createProperty(PREFIX + propertyLocalName), (String) null)
                .toList()
                .stream()
                .map(Statement::getString)
                .toList();
    }

    private void concreteClass(String localName, String superClass) {
        for (var model : List.of(schema, originalSchema)) {
            var resource = model.createResource(PREFIX + localName);
            resource.addProperty(RDF.type, RDFS.Class);
            resource.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
            if (superClass != null) {
                resource.addProperty(RDFS.subClassOf, model.createResource(PREFIX + superClass));
            }
        }
    }

    /** Declares the property on the class it sat on before the migration. */
    private void propertyOfOldClass(String propertyLocalName, String classLocalName) {
        originalSchema
                .createResource(PREFIX + propertyLocalName)
                .addProperty(RDFS.domain, originalSchema.createResource(PREFIX + classLocalName));
    }

    private SemanticClassChange classChange(String localName, SemanticPropertyChange property) {
        var attributes = new ArrayList<SemanticAttributeChange>();
        var associations = new ArrayList<SemanticAssociationChange>();
        if (property instanceof SemanticAttributeChange attribute) {
            attributes.add(attribute);
        } else {
            associations.add((SemanticAssociationChange) property);
        }
        return SemanticClassChange.builder()
                .iri(PREFIX + localName)
                .label(localName)
                .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                .attributes(attributes)
                .associations(associations)
                .enumEntries(new ArrayList<>())
                .build();
    }
}
