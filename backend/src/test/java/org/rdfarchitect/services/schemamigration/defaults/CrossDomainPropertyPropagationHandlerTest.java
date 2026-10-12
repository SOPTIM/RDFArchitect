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

package org.rdfarchitect.services.schemamigration.defaults;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.models.changes.ResourceMapping;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;

import java.util.ArrayList;
import java.util.List;

class CrossDomainPropertyPropagationHandlerTest {

    private static final String PREFIX = "http://example.org#";
    private static final String CIMS_PREFIX =
            "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";
    private static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";

    private Model oldModel;
    private Model newModel;
    private List<SemanticClassChange> classes;

    @BeforeEach
    void setUp() {
        oldModel = ModelFactory.createDefaultModel();
        newModel = ModelFactory.createDefaultModel();
        classes = new ArrayList<>();
    }

    private Resource declareClass(Model model, String name) {
        var cls = model.createResource(PREFIX + name);
        cls.addProperty(RDF.type, RDFS.Class);
        cls.addProperty(RDFS.label, model.createLiteral(name));
        cls.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
        return cls;
    }

    private void declareSubclassOf(Resource subClass, Resource superClass) {
        subClass.addProperty(RDFS.subClassOf, superClass);
    }

    private Resource declareAttribute(Model model, Resource domain, String name) {
        var attribute = model.createResource(PREFIX + name);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFS.label, model.createLiteral(name));
        attribute.addProperty(RDFS.domain, domain);
        attribute.addProperty(CIMS.datatype, model.createResource(XSD_STRING));
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        return attribute;
    }

    private Resource declareAssociation(Model model, Resource domain, Resource range, String name) {
        var association = model.createResource(PREFIX + name);
        association.addProperty(RDF.type, RDF.Property);
        association.addProperty(RDFS.label, model.createLiteral(name));
        association.addProperty(RDFS.domain, domain);
        association.addProperty(RDFS.range, range);
        association.addProperty(CIMS.associationUsed, "No");
        association.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:0..1"));
        association.addProperty(
                CIMS.inverseRoleName, model.createResource(PREFIX + name + ".inverse"));
        return association;
    }

    private SemanticAttributeChange attributeChange(String iri, SemanticResourceChangeType type) {
        return SemanticAttributeChange.builder()
                .iri(iri)
                .label(iri)
                .domain(null)
                .semanticResourceChangeType(type)
                .build();
    }

    private SemanticAttributeChange attributeChange(
            String iri, String domain, SemanticResourceChangeType type) {
        return SemanticAttributeChange.builder()
                .iri(iri)
                .label(iri)
                .domain(domain)
                .semanticResourceChangeType(type)
                .build();
    }

    private SemanticAssociationChange associationChange(
            String iri, String domain, SemanticResourceChangeType type) {
        return SemanticAssociationChange.builder()
                .iri(iri)
                .label(iri)
                .domain(domain)
                .semanticResourceChangeType(type)
                .build();
    }

    private SemanticClassChange classChange(String classIri) {
        return classes.stream().filter(c -> c.getIri().equals(classIri)).findFirst().orElse(null);
    }

    @Nested
    class MoveTest {

        @Test
        void processCrossDomainPropertyMappings_subclassOfOldDomainOnly_deletesOldPredicate() {
            var oldA = declareClass(oldModel, "A");
            declareAttribute(oldModel, oldA, "A.voltage");
            var oldB = declareClass(oldModel, "B");
            var oldLostSub = declareClass(oldModel, "LostSub");
            declareSubclassOf(oldLostSub, oldA);

            var newA = declareClass(newModel, "A");
            var newB = declareClass(newModel, "B");
            declareAttribute(newModel, newB, "B.voltage");
            var newLostSub = declareClass(newModel, "LostSub");
            declareSubclassOf(newLostSub, newA);

            var mapping =
                    new ResourceMapping<>(
                            attributeChange(
                                    PREFIX + "A.voltage",
                                    PREFIX + "A",
                                    SemanticResourceChangeType.DELETE),
                            attributeChange(
                                    PREFIX + "B.voltage",
                                    PREFIX + "B",
                                    SemanticResourceChangeType.ADD),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(mapping), List.of());

            var lostSubChange = classChange(PREFIX + "LostSub");
            assertThat(lostSubChange).isNotNull();
            assertThat(lostSubChange.getAttributes())
                    .anyMatch(
                            a ->
                                    a.getIri().equals(PREFIX + "A.voltage")
                                            && a.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .DELETED_FROM_INHERITANCE);
        }

        @Test
        void processCrossDomainPropertyMappings_subclassOfNewDomainOnly_addsNewPredicate() {
            var oldA = declareClass(oldModel, "A");
            declareAttribute(oldModel, oldA, "A.voltage");
            var oldB = declareClass(oldModel, "B");
            var oldGainedSub = declareClass(oldModel, "GainedSub");
            declareSubclassOf(oldGainedSub, oldB);

            var newA = declareClass(newModel, "A");
            var newB = declareClass(newModel, "B");
            declareAttribute(newModel, newB, "B.voltage");
            var newGainedSub = declareClass(newModel, "GainedSub");
            declareSubclassOf(newGainedSub, newB);

            var mapping =
                    new ResourceMapping<>(
                            attributeChange(
                                    PREFIX + "A.voltage",
                                    PREFIX + "A",
                                    SemanticResourceChangeType.DELETE),
                            attributeChange(
                                    PREFIX + "B.voltage",
                                    PREFIX + "B",
                                    SemanticResourceChangeType.ADD),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(mapping), List.of());

            var gainedSubChange = classChange(PREFIX + "GainedSub");
            assertThat(gainedSubChange).isNotNull();
            assertThat(gainedSubChange.getAttributes())
                    .anyMatch(
                            a ->
                                    a.getIri().equals(PREFIX + "B.voltage")
                                            && a.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE);
        }

        @Test
        void processCrossDomainPropertyMappings_subclassOfBothDomains_getsNoEntry() {
            var oldA = declareClass(oldModel, "A");
            declareAttribute(oldModel, oldA, "A.voltage");
            var oldB = declareClass(oldModel, "B");
            var oldKeptSub = declareClass(oldModel, "KeptSub");
            declareSubclassOf(oldKeptSub, oldA);
            declareSubclassOf(oldKeptSub, oldB);

            var newA = declareClass(newModel, "A");
            var newB = declareClass(newModel, "B");
            declareAttribute(newModel, newB, "B.voltage");
            var newKeptSub = declareClass(newModel, "KeptSub");
            declareSubclassOf(newKeptSub, newA);
            declareSubclassOf(newKeptSub, newB);

            var mapping =
                    new ResourceMapping<>(
                            attributeChange(
                                    PREFIX + "A.voltage",
                                    PREFIX + "A",
                                    SemanticResourceChangeType.DELETE),
                            attributeChange(
                                    PREFIX + "B.voltage",
                                    PREFIX + "B",
                                    SemanticResourceChangeType.ADD),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(mapping), List.of());

            assertThat(classChange(PREFIX + "KeptSub")).isNull();
        }

        @Test
        void processCrossDomainPropertyMappings_associationSubclassOfOldDomainOnly_deletesOld() {
            var oldA = declareClass(oldModel, "A");
            var oldTarget = declareClass(oldModel, "Target");
            declareAssociation(oldModel, oldA, oldTarget, "A.link");
            var oldB = declareClass(oldModel, "B");
            var oldLostSub = declareClass(oldModel, "LostSub");
            declareSubclassOf(oldLostSub, oldA);

            var newA = declareClass(newModel, "A");
            var newB = declareClass(newModel, "B");
            var newTarget = declareClass(newModel, "Target");
            declareAssociation(newModel, newB, newTarget, "B.link");
            var newLostSub = declareClass(newModel, "LostSub");
            declareSubclassOf(newLostSub, newA);

            var mapping =
                    new ResourceMapping<>(
                            associationChange(
                                    PREFIX + "A.link",
                                    PREFIX + "A",
                                    SemanticResourceChangeType.DELETE),
                            associationChange(
                                    PREFIX + "B.link",
                                    PREFIX + "B",
                                    SemanticResourceChangeType.ADD),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(), List.of(mapping));

            var lostSubChange = classChange(PREFIX + "LostSub");
            assertThat(lostSubChange).isNotNull();
            assertThat(lostSubChange.getAssociations())
                    .anyMatch(
                            a ->
                                    a.getIri().equals(PREFIX + "A.link")
                                            && a.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .DELETED_FROM_INHERITANCE);
        }

        @Test
        void processCrossDomainPropertyMappings_associationSubclassOfNewDomainOnly_addsNew() {
            var oldA = declareClass(oldModel, "A");
            var oldTarget = declareClass(oldModel, "Target");
            declareAssociation(oldModel, oldA, oldTarget, "A.link");
            var oldB = declareClass(oldModel, "B");
            var oldGainedSub = declareClass(oldModel, "GainedSub");
            declareSubclassOf(oldGainedSub, oldB);

            var newA = declareClass(newModel, "A");
            var newB = declareClass(newModel, "B");
            var newTarget = declareClass(newModel, "Target");
            declareAssociation(newModel, newB, newTarget, "B.link");
            var newGainedSub = declareClass(newModel, "GainedSub");
            declareSubclassOf(newGainedSub, newB);

            var mapping =
                    new ResourceMapping<>(
                            associationChange(
                                    PREFIX + "A.link",
                                    PREFIX + "A",
                                    SemanticResourceChangeType.DELETE),
                            associationChange(
                                    PREFIX + "B.link",
                                    PREFIX + "B",
                                    SemanticResourceChangeType.ADD),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(), List.of(mapping));

            var gainedSubChange = classChange(PREFIX + "GainedSub");
            assertThat(gainedSubChange).isNotNull();
            assertThat(gainedSubChange.getAssociations())
                    .anyMatch(
                            a ->
                                    a.getIri().equals(PREFIX + "B.link")
                                            && a.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE);
        }

        @Test
        void processCrossDomainPropertyMappings_abstractDerivingClass_getsNoEntry() {
            var oldA = declareClass(oldModel, "A");
            declareAttribute(oldModel, oldA, "A.voltage");
            var oldB = declareClass(oldModel, "B");
            var oldAbstractSub = oldModel.createResource(PREFIX + "AbstractSub");
            oldAbstractSub.addProperty(RDF.type, RDFS.Class);
            oldAbstractSub.addProperty(RDFS.label, oldModel.createLiteral("AbstractSub"));
            declareSubclassOf(oldAbstractSub, oldA);

            var newA = declareClass(newModel, "A");
            var newB = declareClass(newModel, "B");
            declareAttribute(newModel, newB, "B.voltage");
            var newAbstractSub = newModel.createResource(PREFIX + "AbstractSub");
            newAbstractSub.addProperty(RDF.type, RDFS.Class);
            newAbstractSub.addProperty(RDFS.label, newModel.createLiteral("AbstractSub"));
            declareSubclassOf(newAbstractSub, newA);

            var mapping =
                    new ResourceMapping<>(
                            attributeChange(
                                    PREFIX + "A.voltage",
                                    PREFIX + "A",
                                    SemanticResourceChangeType.DELETE),
                            attributeChange(
                                    PREFIX + "B.voltage",
                                    PREFIX + "B",
                                    SemanticResourceChangeType.ADD),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(mapping), List.of());

            assertThat(classChange(PREFIX + "AbstractSub")).isNull();
        }

        @Test
        void processCrossDomainPropertyMappings_classAlreadyListsProperty_notDuplicated() {
            var oldA = declareClass(oldModel, "A");
            declareAttribute(oldModel, oldA, "A.voltage");
            var oldB = declareClass(oldModel, "B");
            var oldLostSub = declareClass(oldModel, "LostSub");
            declareSubclassOf(oldLostSub, oldA);

            var newA = declareClass(newModel, "A");
            var newB = declareClass(newModel, "B");
            declareAttribute(newModel, newB, "B.voltage");
            var newLostSub = declareClass(newModel, "LostSub");
            declareSubclassOf(newLostSub, newA);

            var existing =
                    new ArrayList<>(
                            List.of(
                                    attributeChange(
                                            PREFIX + "A.voltage",
                                            SemanticResourceChangeType.CHANGE)));
            classes.add(
                    SemanticClassChange.builder()
                            .iri(PREFIX + "LostSub")
                            .label("LostSub")
                            .semanticResourceChangeType(SemanticResourceChangeType.CHANGE)
                            .attributes(existing)
                            .associations(new ArrayList<>())
                            .enumEntries(new ArrayList<>())
                            .build());

            var mapping =
                    new ResourceMapping<>(
                            attributeChange(
                                    PREFIX + "A.voltage",
                                    PREFIX + "A",
                                    SemanticResourceChangeType.DELETE),
                            attributeChange(
                                    PREFIX + "B.voltage",
                                    PREFIX + "B",
                                    SemanticResourceChangeType.ADD),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(mapping), List.of());

            assertThat(classChange(PREFIX + "LostSub").getAttributes()).hasSize(1);
        }
    }

    @Nested
    class MergeTest {

        @Test
        void
                processCrossDomainPropertyMappings_lostSubclassOfOneSource_deletesThatSourcesOwnPredicate() {
            var oldA = declareClass(oldModel, "A");
            declareAttribute(oldModel, oldA, "A.voltage");
            var oldB = declareClass(oldModel, "B");
            declareAttribute(oldModel, oldB, "B.volt");
            var oldMerged = declareClass(oldModel, "Merged");
            var oldLostSub = declareClass(oldModel, "LostSub");
            declareSubclassOf(oldLostSub, oldA);

            var newA = declareClass(newModel, "A");
            var newB = declareClass(newModel, "B");
            var newMerged = declareClass(newModel, "Merged");
            declareAttribute(newModel, newMerged, "Merged.voltage");
            var newLostSub = declareClass(newModel, "LostSub");
            declareSubclassOf(newLostSub, newA);

            var mapping =
                    new ResourceMapping<>(
                            List.of(
                                    attributeChange(
                                            PREFIX + "A.voltage",
                                            PREFIX + "A",
                                            SemanticResourceChangeType.DELETE),
                                    attributeChange(
                                            PREFIX + "B.volt",
                                            PREFIX + "B",
                                            SemanticResourceChangeType.DELETE)),
                            attributeChange(
                                    PREFIX + "Merged.voltage",
                                    PREFIX + "Merged",
                                    SemanticResourceChangeType.ADD),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(mapping), List.of());

            var lostSubChange = classChange(PREFIX + "LostSub");
            assertThat(lostSubChange).isNotNull();
            assertThat(lostSubChange.getAttributes())
                    .hasSize(1)
                    .anyMatch(
                            a ->
                                    a.getIri().equals(PREFIX + "A.voltage")
                                            && a.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .DELETED_FROM_INHERITANCE);
        }

        @Test
        void processCrossDomainPropertyMappings_gainedSubclassOfNeitherSource_singleAddedEntry() {
            var oldA = declareClass(oldModel, "A");
            declareAttribute(oldModel, oldA, "A.voltage");
            var oldB = declareClass(oldModel, "B");
            declareAttribute(oldModel, oldB, "B.volt");
            var oldMerged = declareClass(oldModel, "Merged");
            var oldGainedSub = declareClass(oldModel, "GainedSub");
            declareSubclassOf(oldGainedSub, oldMerged);

            var newA = declareClass(newModel, "A");
            var newB = declareClass(newModel, "B");
            var newMerged = declareClass(newModel, "Merged");
            declareAttribute(newModel, newMerged, "Merged.voltage");
            var newGainedSub = declareClass(newModel, "GainedSub");
            declareSubclassOf(newGainedSub, newMerged);

            var mapping =
                    new ResourceMapping<>(
                            List.of(
                                    attributeChange(
                                            PREFIX + "A.voltage",
                                            PREFIX + "A",
                                            SemanticResourceChangeType.DELETE),
                                    attributeChange(
                                            PREFIX + "B.volt",
                                            PREFIX + "B",
                                            SemanticResourceChangeType.DELETE)),
                            attributeChange(
                                    PREFIX + "Merged.voltage",
                                    PREFIX + "Merged",
                                    SemanticResourceChangeType.ADD),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(mapping), List.of());

            var gainedSubChange = classChange(PREFIX + "GainedSub");
            assertThat(gainedSubChange).isNotNull();
            assertThat(gainedSubChange.getAttributes())
                    .hasSize(1)
                    .anyMatch(
                            a ->
                                    a.getIri().equals(PREFIX + "Merged.voltage")
                                            && a.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE);
        }
    }

    @Nested
    class SplitTest {

        /**
         * KeptSub derives from the split's old domain A (via B1, in both schema versions - the
         * split moved the attribute down onto A's own subclasses rather than anywhere KeptSub's
         * hierarchy changed) and continues to derive from split target B1 afterward, so it had the
         * attribute before and keeps having an equivalent one after - it must get no entry here,
         * the (fixed) domain-unscoped rename SPARQL already preserves its value under the new
         * predicate.
         */
        @Test
        void processCrossDomainPropertyMappings_subclassOfOldDomainAndOfOneTarget_getsNoEntry() {
            var oldA = declareClass(oldModel, "A");
            declareAttribute(oldModel, oldA, "A.voltage");
            var oldB1 = declareClass(oldModel, "B1");
            declareSubclassOf(oldB1, oldA);
            var oldB2 = declareClass(oldModel, "B2");
            declareSubclassOf(oldB2, oldA);
            var oldKeptSub = declareClass(oldModel, "KeptSub");
            declareSubclassOf(oldKeptSub, oldB1);

            var newA = declareClass(newModel, "A");
            var newB1 = declareClass(newModel, "B1");
            declareSubclassOf(newB1, newA);
            declareAttribute(newModel, newB1, "B1.voltage");
            var newB2 = declareClass(newModel, "B2");
            declareSubclassOf(newB2, newA);
            declareAttribute(newModel, newB2, "B2.voltage");
            var newKeptSub = declareClass(newModel, "KeptSub");
            declareSubclassOf(newKeptSub, newB1);

            var mapping =
                    new ResourceMapping<>(
                            attributeChange(
                                    PREFIX + "A.voltage",
                                    PREFIX + "A",
                                    SemanticResourceChangeType.DELETE),
                            List.of(
                                    attributeChange(
                                            PREFIX + "B1.voltage",
                                            PREFIX + "B1",
                                            SemanticResourceChangeType.ADD),
                                    attributeChange(
                                            PREFIX + "B2.voltage",
                                            PREFIX + "B2",
                                            SemanticResourceChangeType.ADD)),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(mapping), List.of());

            assertThat(classChange(PREFIX + "KeptSub")).isNull();
        }

        @Test
        void
                processCrossDomainPropertyMappings_subclassOfOneTargetOnly_gainsThatTargetsPredicate() {
            var oldA = declareClass(oldModel, "A");
            declareAttribute(oldModel, oldA, "A.voltage");
            var oldB1 = declareClass(oldModel, "B1");
            var oldB2 = declareClass(oldModel, "B2");
            var oldGainedSub = declareClass(oldModel, "GainedSub");
            declareSubclassOf(oldGainedSub, oldB1);

            var newA = declareClass(newModel, "A");
            var newB1 = declareClass(newModel, "B1");
            declareAttribute(newModel, newB1, "B1.voltage");
            var newB2 = declareClass(newModel, "B2");
            declareAttribute(newModel, newB2, "B2.voltage");
            var newGainedSub = declareClass(newModel, "GainedSub");
            declareSubclassOf(newGainedSub, newB1);

            var mapping =
                    new ResourceMapping<>(
                            attributeChange(
                                    PREFIX + "A.voltage",
                                    PREFIX + "A",
                                    SemanticResourceChangeType.DELETE),
                            List.of(
                                    attributeChange(
                                            PREFIX + "B1.voltage",
                                            PREFIX + "B1",
                                            SemanticResourceChangeType.ADD),
                                    attributeChange(
                                            PREFIX + "B2.voltage",
                                            PREFIX + "B2",
                                            SemanticResourceChangeType.ADD)),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(mapping), List.of());

            var gainedSubChange = classChange(PREFIX + "GainedSub");
            assertThat(gainedSubChange).isNotNull();
            assertThat(gainedSubChange.getAttributes())
                    .hasSize(1)
                    .anyMatch(
                            a ->
                                    a.getIri().equals(PREFIX + "B1.voltage")
                                            && a.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .ADDED_FROM_INHERITANCE);
        }

        @Test
        void processCrossDomainPropertyMappings_subclassOfNeitherTarget_singleDeletedEntry() {
            var oldA = declareClass(oldModel, "A");
            declareAttribute(oldModel, oldA, "A.voltage");
            var oldB1 = declareClass(oldModel, "B1");
            var oldB2 = declareClass(oldModel, "B2");
            var oldLostSub = declareClass(oldModel, "LostSub");
            declareSubclassOf(oldLostSub, oldA);

            var newA = declareClass(newModel, "A");
            var newB1 = declareClass(newModel, "B1");
            declareAttribute(newModel, newB1, "B1.voltage");
            var newB2 = declareClass(newModel, "B2");
            declareAttribute(newModel, newB2, "B2.voltage");
            var newLostSub = declareClass(newModel, "LostSub");
            declareSubclassOf(newLostSub, newA);

            var mapping =
                    new ResourceMapping<>(
                            attributeChange(
                                    PREFIX + "A.voltage",
                                    PREFIX + "A",
                                    SemanticResourceChangeType.DELETE),
                            List.of(
                                    attributeChange(
                                            PREFIX + "B1.voltage",
                                            PREFIX + "B1",
                                            SemanticResourceChangeType.ADD),
                                    attributeChange(
                                            PREFIX + "B2.voltage",
                                            PREFIX + "B2",
                                            SemanticResourceChangeType.ADD)),
                            1.0);

            CrossDomainPropertyPropagationHandler.processCrossDomainPropertyMappings(
                    classes, oldModel, newModel, List.of(mapping), List.of());

            var lostSubChange = classChange(PREFIX + "LostSub");
            assertThat(lostSubChange).isNotNull();
            assertThat(lostSubChange.getAttributes())
                    .hasSize(1)
                    .anyMatch(
                            a ->
                                    a.getIri().equals(PREFIX + "A.voltage")
                                            && a.getSemanticResourceChangeType()
                                                    == SemanticResourceChangeType
                                                            .DELETED_FROM_INHERITANCE);
        }
    }
}
