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

package org.rdfarchitect.services.schemamigration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.migration.ClassPropertyMappingOverview;
import org.rdfarchitect.api.dto.migration.ClassPropertyMappings;
import org.rdfarchitect.api.dto.migration.PropertyMappingOverview;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.models.changes.MappingType;
import org.rdfarchitect.models.changes.ResourceMapping;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.services.compare.TripleChangeAnalyser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Covers how a renamed datatype class is reported to the default-values step: a rename is not a
 * datatype change and must not ask the user for a default value.
 */
class SchemaMigrationServiceTest {

    private static final String PREFIX = "http://example.org#";
    private static final String CIMS_PREFIX =
            "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";

    private MigrationSessionStore migrationSessionStore;
    private SchemaMigrationService service;

    @BeforeEach
    void setUp() {
        migrationSessionStore = new MigrationSessionStore();
        migrationSessionStore.getContext().clear();
        service = new SchemaMigrationService(migrationSessionStore, null, null, null, null, null);
    }

    @Test
    void getDefaultValueViews_enumDatatypeClassRenamed_reportsDatatypeRename() {
        initContext(
                schema("SwitchState", "Switch.state"), schema("SwitchStateKind", "Switch.state"));

        confirmDetectedClassRenames();
        confirmDetectedPropertyRenames();

        assertThat(fieldChangeTypesOf("state"))
                .containsExactly(SemanticFieldChangeType.DATATYPE_RENAME);
    }

    /**
     * Merging a confirmed property rename rebuilds the attribute's field changes from both sides,
     * so the reclassification done at the class step has to be repeated afterwards.
     */
    @Test
    void getDefaultValueViews_datatypeClassAndAttributeRenamed_reportsDatatypeRename() {
        initContext(
                schema("SwitchState", "Switch.state"),
                schema("SwitchStateKind", "Switch.switchState"));

        confirmDetectedClassRenames();
        confirmAttributeRename("Switch", "state", "switchState");

        assertThat(fieldChangeTypesOf("switchState"))
                .contains(SemanticFieldChangeType.DATATYPE_RENAME)
                .doesNotContain(SemanticFieldChangeType.DATATYPE_CHANGE);
    }

    @Test
    void getDefaultValueViews_datatypeClassReplacedByUnrelatedClass_reportsDatatypeChange() {
        var updated = schema("SwitchState", "Switch.state");
        var quality = updated.createResource(PREFIX + "Quality");
        quality.addProperty(RDF.type, RDFS.Class);
        quality.addProperty(RDFS.label, updated.createLiteral("Quality"));
        quality.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);
        updated.getResource(PREFIX + "Switch.state")
                .removeAll(RDFS.range)
                .addProperty(RDFS.range, quality);

        initContext(schema("SwitchState", "Switch.state"), updated);

        confirmDetectedClassRenames();
        confirmDetectedPropertyRenames();

        assertThat(fieldChangeTypesOf("state"))
                .contains(SemanticFieldChangeType.DATATYPE_CHANGE)
                .doesNotContain(SemanticFieldChangeType.DATATYPE_RENAME);
    }

    private void initContext(Model original, Model updated) {
        var context = migrationSessionStore.getContext();
        context.setOriginalSchema(original.getGraph());
        context.setUpdatedSchema(updated.getGraph());
        var tripleDiff =
                TripleChangeAnalyser.compareGraphsDisregardingPackages(
                        original.getGraph(), updated.getGraph());
        context.setTripleDiff(tripleDiff);
        context.setSemanticDiff(SemanticChangeAnalyser.getSemanticChanges(tripleDiff));
    }

    private void confirmDetectedClassRenames() {
        var detected =
                service.getClassMappings().getDeletedAndRemapped().stream()
                        .filter(candidate -> !candidate.getNewResources().isEmpty())
                        .toList();
        service.confirmClassRenamings(new ArrayList<>(detected));
    }

    private void confirmDetectedPropertyRenames() {
        var confirmed = new ArrayList<ClassPropertyMappings>();
        for (var overview : service.getPropertyMappings()) {
            var mappings = new ClassPropertyMappings();
            mappings.setClassLabel(overview.getClassLabel());
            mappings.setAttributeMappings(confirmedOf(overview.getAttributes()));
            mappings.setAssociationMappings(confirmedOf(overview.getAssociations()));
            mappings.setEnumEntryMappings(confirmedOf(overview.getEnumEntries()));
            confirmed.add(mappings);
        }
        service.confirmPropertyMappings(confirmed);
    }

    private static <T extends SemanticResourceChange> List<ResourceMapping<T>> confirmedOf(
            PropertyMappingOverview<T> overview) {
        return overview.getDeletedAndRemapped().stream()
                .filter(candidate -> !candidate.getNewResources().isEmpty())
                .toList();
    }

    /**
     * Maps an attribute rename by hand, as the user does when similarity is below the threshold.
     */
    private void confirmAttributeRename(String classLabel, String oldLabel, String newLabel) {
        var confirmed = new ArrayList<ClassPropertyMappings>();
        for (var overview : service.getPropertyMappings()) {
            var renamings = new ClassPropertyMappings();
            renamings.setClassLabel(overview.getClassLabel());
            renamings.setAssociationMappings(confirmedOf(overview.getAssociations()));
            renamings.setEnumEntryMappings(confirmedOf(overview.getEnumEntries()));
            renamings.setAttributeMappings(List.of());

            if (overview.getClassLabel().equals(classLabel)) {
                var deleted =
                        overview.getAttributes().getDeletedAndRemapped().stream()
                                .flatMap(candidate -> candidate.getOldResources().stream())
                                .filter(attribute -> attribute.getLabel().equals(oldLabel))
                                .findFirst();
                var added =
                        overview.getAttributes().getAddedInClass().stream()
                                .filter(attribute -> attribute.getLabel().equals(newLabel))
                                .findFirst();
                if (deleted.isPresent() && added.isPresent()) {
                    renamings.setAttributeMappings(
                            List.of(new ResourceMapping<>(deleted.get(), added.get(), 1.0)));
                }
            }
            confirmed.add(renamings);
        }
        service.confirmPropertyMappings(confirmed);
    }

    /**
     * Maps an attribute move between two unrelated classes by hand - chain-pooled auto-detection
     * only pairs a deletion with an addition found via the deleted side's own superclass/subclass
     * chain, so it never pairs two classes with no relation to each other.
     */
    private void confirmAttributeMove(
            String oldClassLabel, String newClassLabel, String attributeLabel) {
        var overviews = service.getPropertyMappings();
        var added =
                overviews.stream()
                        .filter(o -> o.getClassLabel().equals(newClassLabel))
                        .findFirst()
                        .orElseThrow()
                        .getAttributes()
                        .getAddedInClass()
                        .stream()
                        .filter(attribute -> attribute.getLabel().equals(attributeLabel))
                        .findFirst()
                        .orElseThrow();

        var confirmed = new ArrayList<ClassPropertyMappings>();
        for (var overview : overviews) {
            var mappings = new ClassPropertyMappings();
            mappings.setClassLabel(overview.getClassLabel());
            mappings.setAssociationMappings(confirmedOf(overview.getAssociations()));
            mappings.setEnumEntryMappings(confirmedOf(overview.getEnumEntries()));
            mappings.setAttributeMappings(List.of());

            if (overview.getClassLabel().equals(oldClassLabel)) {
                var deleted =
                        overview.getAttributes().getDeletedAndRemapped().stream()
                                .flatMap(candidate -> candidate.getOldResources().stream())
                                .filter(attribute -> attribute.getLabel().equals(attributeLabel))
                                .findFirst()
                                .orElseThrow();
                mappings.setAttributeMappings(List.of(new ResourceMapping<>(deleted, added, 1.0)));
            }
            confirmed.add(mappings);
        }
        service.confirmPropertyMappings(confirmed);
    }

    private List<SemanticFieldChangeType> fieldChangeTypesOf(String attributeLabel) {
        return service.getDefaultValueViews().stream()
                .flatMap(view -> view.getAttributes().stream())
                .filter(attribute -> attribute.getLabel().equals(attributeLabel))
                .flatMap(attribute -> attribute.getChanges().stream())
                .map(SemanticFieldChange::getSemanticFieldChangeType)
                .filter(
                        type ->
                                type == SemanticFieldChangeType.DATATYPE_CHANGE
                                        || type == SemanticFieldChangeType.DATATYPE_RENAME)
                .toList();
    }

    private static Model schema(String enumName, String attributeName) {
        var model = ModelFactory.createDefaultModel();

        var switchClass = model.createResource(PREFIX + "Switch");
        switchClass.addProperty(RDF.type, RDFS.Class);
        switchClass.addProperty(RDFS.label, model.createLiteral("Switch"));
        switchClass.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

        var stateEnum = model.createResource(PREFIX + enumName);
        stateEnum.addProperty(RDF.type, RDFS.Class);
        stateEnum.addProperty(RDFS.label, model.createLiteral(enumName));
        stateEnum.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);
        for (var entry : List.of("open", "closed")) {
            var enumEntry = model.createResource(PREFIX + enumName + "." + entry);
            enumEntry.addProperty(RDF.type, stateEnum);
            enumEntry.addProperty(RDFS.label, model.createLiteral(entry));
            enumEntry.addProperty(CIMS.stereotype, CIMStereotypes.enumLiteral);
        }

        var attribute = model.createResource(PREFIX + attributeName);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(
                RDFS.label,
                model.createLiteral(attributeName.substring(attributeName.indexOf('.') + 1)));
        attribute.addProperty(RDFS.domain, switchClass);
        attribute.addProperty(RDFS.range, stateEnum);
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

        return model;
    }

    @Test
    void getPropertyRenamings_associationNotUsed_reportsItAsNotInstantiable() {
        initContext(
                associationSchema("Switch.Terminals", false),
                associationSchema("Switch.ConnectedTerminals", false));

        confirmDetectedClassRenames();

        // the rename step needs the flag to leave these out; only the defaults step filled it in
        assertThat(deletedAssociationsUsed()).containsExactly(false);
    }

    @Test
    void getPropertyRenamings_associationUsed_reportsItAsInstantiable() {
        initContext(
                associationSchema("Switch.Terminals", true),
                associationSchema("Switch.ConnectedTerminals", true));

        confirmDetectedClassRenames();

        assertThat(deletedAssociationsUsed()).containsExactly(true);
    }

    /**
     * A deleted association only exists in the original schema, so reading the flag from the
     * updated one would report every deletion as not instantiable.
     */
    @Test
    void getPropertyRenamings_deletedAssociation_readsFlagFromTheOriginalSchema() {
        var updated = ModelFactory.createDefaultModel();
        var switchClass = updated.createResource(PREFIX + "Switch");
        switchClass.addProperty(RDF.type, RDFS.Class);
        switchClass.addProperty(RDFS.label, updated.createLiteral("Switch"));
        switchClass.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

        initContext(associationSchema("Switch.Terminals", true), updated);

        confirmDetectedClassRenames();

        assertThat(deletedAssociationsUsed()).containsExactly(true);
    }

    private List<Boolean> deletedAssociationsUsed() {
        return service.getPropertyMappings().stream()
                .flatMap(overview -> overview.getAssociations().getDeletedAndRemapped().stream())
                .flatMap(candidate -> candidate.getOldResources().stream())
                .map(SemanticAssociationChange::isAssociationUsed)
                .toList();
    }

    /**
     * A "Base" class and a "Sub" class that is {@code rdfs:subClassOf Base}, with a "voltage"
     * attribute declared on whichever of the two {@code attributeOnSub} selects. Using a
     * class-prefixed attribute IRI (as CIM does) means moving the attribute to the other class
     * changes its IRI too, so the triple diff sees a DELETE+ADD pair rather than an in-place change
     * - exactly the shape the rename/move detector is meant to catch.
     */
    private static Model inheritanceSchema(boolean attributeOnSub) {
        var model = ModelFactory.createDefaultModel();

        var base = model.createResource(PREFIX + "Base");
        base.addProperty(RDF.type, RDFS.Class);
        base.addProperty(RDFS.label, model.createLiteral("Base"));
        base.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

        var sub = model.createResource(PREFIX + "Sub");
        sub.addProperty(RDF.type, RDFS.Class);
        sub.addProperty(RDFS.label, model.createLiteral("Sub"));
        sub.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
        sub.addProperty(RDFS.subClassOf, base);

        var range = model.createResource(PREFIX + "VoltageLevel");
        range.addProperty(RDF.type, RDFS.Class);
        range.addProperty(RDFS.label, model.createLiteral("VoltageLevel"));
        range.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

        var domainClass = attributeOnSub ? sub : base;
        var attribute =
                model.createResource(PREFIX + (attributeOnSub ? "Sub" : "Base") + ".voltage");
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFS.label, model.createLiteral("voltage"));
        attribute.addProperty(RDFS.domain, domainClass);
        attribute.addProperty(RDFS.range, range);
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

        return model;
    }

    @Test
    void confirmPropertyMappings_attributeMovedUpToSuperclass_attachesToSuperclass() {
        initContext(inheritanceSchema(true), inheritanceSchema(false));

        confirmDetectedClassRenames();
        confirmDetectedPropertyRenames();

        assertThat(attributeMoveTargetClass("voltage")).isEqualTo("Base");
    }

    @Test
    void confirmPropertyMappings_attributeMovedDownToSubclass_attachesToSubclass() {
        initContext(inheritanceSchema(false), inheritanceSchema(true));

        confirmDetectedClassRenames();
        confirmDetectedPropertyRenames();

        assertThat(attributeMoveTargetClass("voltage")).isEqualTo("Sub");
    }

    /**
     * The chain-wide candidate pool means the same mapping can plausibly be handed back under more
     * than one class's bucket in a single confirm call - it must only be materialized once.
     */
    @Test
    void confirmPropertyMappings_sameMappingSubmittedUnderTwoClassBuckets_materializesOnce() {
        initContext(inheritanceSchema(true), inheritanceSchema(false));
        confirmDetectedClassRenames();

        var subOverview =
                service.getPropertyMappings().stream()
                        .filter(o -> o.getClassLabel().equals("Sub"))
                        .findFirst()
                        .orElseThrow();
        var mapping =
                subOverview.getAttributes().getDeletedAndRemapped().stream()
                        .filter(m -> !m.getNewResources().isEmpty())
                        .findFirst()
                        .orElseThrow();

        var subMappings = new ClassPropertyMappings();
        subMappings.setClassLabel("Sub");
        subMappings.setAttributeMappings(List.of(mapping));
        subMappings.setAssociationMappings(List.of());
        subMappings.setEnumEntryMappings(List.of());

        var baseMappings = new ClassPropertyMappings();
        baseMappings.setClassLabel("Base");
        baseMappings.setAttributeMappings(List.of(mapping));
        baseMappings.setAssociationMappings(List.of());
        baseMappings.setEnumEntryMappings(List.of());

        service.confirmPropertyMappings(List.of(subMappings, baseMappings));

        // the old domain (Sub) also carries its own DELETED_FROM_MAPPING marker under the same
        // label now (see attachDeletionMarkersForLosingDomains) - only the materialized
        // move/rename result itself must not be duplicated
        var materializedVoltageCount =
                migrationSessionStore.getContext().getDiffAfterPropertyConfirm().stream()
                        .flatMap(c -> c.getAttributes().stream())
                        .filter(a -> a.getLabel().equals("voltage"))
                        .filter(
                                a ->
                                        a.getSemanticResourceChangeType()
                                                != SemanticResourceChangeType.DELETED_FROM_MAPPING)
                        .count();

        assertThat(materializedVoltageCount).isEqualTo(1);
    }

    /**
     * A moved attribute's own domain classes (Base/Target) are handled by its own merged
     * RENAME/MOVE object - this covers the subclasses that only find out about the move through
     * inheritance: LostSub derives from Base only, so it loses the attribute entirely; GainedSub
     * derives from Target only, so it gains a fresh one and needs a default value. Base and Target
     * are deliberately unrelated classes, so the mapping is confirmed by hand (like {@link
     * #confirmAttributeRename}) rather than via chain-pooled auto-detection, which only pairs a
     * deletion with an addition found in the deleted side's own superclass/subclass chain.
     */
    @Test
    void getDefaultValueViews_attributeMovedAcrossDomains_propagatesToLostAndGainedSubclasses() {
        initContext(
                movedAttributeWithSubclassesSchema(true),
                movedAttributeWithSubclassesSchema(false));

        confirmDetectedClassRenames();
        confirmAttributeMove("Base", "Target", "voltage");

        var views = service.getDefaultValueViews();

        var gainedView =
                views.stream()
                        .filter(v -> v.getClassLabel().equals("GainedSub"))
                        .findFirst()
                        .orElseThrow();
        assertThat(gainedView.getAttributes())
                .anyMatch(
                        a ->
                                a.getIri().equals(PREFIX + "Target.voltage")
                                        && a.getSemanticResourceChangeType()
                                                == SemanticResourceChangeType.ADDED_FROM_INHERITANCE
                                        && a.getDataType() != null);

        var lostView =
                views.stream()
                        .filter(v -> v.getClassLabel().equals("LostSub"))
                        .findFirst()
                        .orElseThrow();
        assertThat(lostView.getAttributes())
                .anyMatch(
                        a ->
                                a.getIri().equals(PREFIX + "Base.voltage")
                                        && a.getSemanticResourceChangeType()
                                                == SemanticResourceChangeType
                                                        .DELETED_FROM_INHERITANCE
                                        && a.getDataType() == null);
    }

    /**
     * GainedSub has no change of its own - it only shows up in {@code getDefaultValueViews()}
     * because the cross-domain propagation step synthesizes an entry for it. Submitting its default
     * value has to actually stick: before this was fixed, {@code submitDefaultValues} matched views
     * against the pre-propagation class list, which has no entry for GainedSub at all, so the view
     * (and the attribute's default value) was silently dropped and the generated script never
     * initialized the attribute on GainedSub's instances.
     */
    @Test
    void submitDefaultValues_classGainedPropertyOnlyThroughPropagation_keepsItsSubmittedDefault() {
        initContext(
                movedAttributeWithSubclassesSchema(true),
                movedAttributeWithSubclassesSchema(false));

        confirmDetectedClassRenames();
        confirmAttributeMove("Base", "Target", "voltage");

        var views = service.getDefaultValueViews();
        for (var view : views) {
            for (var attribute : view.getAttributes()) {
                if (attribute.getIri().equals(PREFIX + "Target.voltage")) {
                    attribute.setDefaultValue(PREFIX + "VoltageLevel.high");
                }
            }
        }

        service.submitDefaultValues(views);

        var gainedSubChange =
                migrationSessionStore.getContext().getDiffAfterDefaultValueConfirm().stream()
                        .filter(c -> c.getLabel().equals("GainedSub"))
                        .findFirst()
                        .orElseThrow();
        assertThat(gainedSubChange.getAttributes())
                .anyMatch(
                        a ->
                                a.getIri().equals(PREFIX + "Target.voltage")
                                        && a.getSemanticResourceChangeType()
                                                == SemanticResourceChangeType.ADDED_FROM_INHERITANCE
                                        && PREFIX.concat("VoltageLevel.high")
                                                .equals(a.getDefaultValue()));
    }

    private static Model movedAttributeWithSubclassesSchema(boolean attributeOnBase) {
        var model = ModelFactory.createDefaultModel();

        var base = model.createResource(PREFIX + "Base");
        base.addProperty(RDF.type, RDFS.Class);
        base.addProperty(RDFS.label, model.createLiteral("Base"));
        base.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

        var target = model.createResource(PREFIX + "Target");
        target.addProperty(RDF.type, RDFS.Class);
        target.addProperty(RDFS.label, model.createLiteral("Target"));
        target.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

        var lostSub = model.createResource(PREFIX + "LostSub");
        lostSub.addProperty(RDF.type, RDFS.Class);
        lostSub.addProperty(RDFS.label, model.createLiteral("LostSub"));
        lostSub.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
        lostSub.addProperty(RDFS.subClassOf, base);

        var gainedSub = model.createResource(PREFIX + "GainedSub");
        gainedSub.addProperty(RDF.type, RDFS.Class);
        gainedSub.addProperty(RDFS.label, model.createLiteral("GainedSub"));
        gainedSub.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
        gainedSub.addProperty(RDFS.subClassOf, target);

        var voltageLevel = model.createResource(PREFIX + "VoltageLevel");
        voltageLevel.addProperty(RDF.type, RDFS.Class);
        voltageLevel.addProperty(RDFS.label, model.createLiteral("VoltageLevel"));
        voltageLevel.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);
        var entry = model.createResource(PREFIX + "VoltageLevel.high");
        entry.addProperty(RDF.type, voltageLevel);
        entry.addProperty(RDFS.label, model.createLiteral("high"));
        entry.addProperty(CIMS.stereotype, CIMStereotypes.enumLiteral);

        var domainClass = attributeOnBase ? base : target;
        var domainLabel = attributeOnBase ? "Base" : "Target";
        var attribute = model.createResource(PREFIX + domainLabel + ".voltage");
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFS.label, model.createLiteral("voltage"));
        attribute.addProperty(RDFS.domain, domainClass);
        attribute.addProperty(RDFS.range, voltageLevel);
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);

        return model;
    }

    /**
     * The class holding the materialized move/rename result for this attribute - not just any class
     * whose attributes mention the label, since the old domain now also carries a
     * DELETED_FROM_MAPPING marker of its own under the same label (see {@code
     * attachDeletionMarkersForLosingDomains}).
     */
    private String attributeMoveTargetClass(String attributeLabel) {
        return migrationSessionStore.getContext().getDiffAfterPropertyConfirm().stream()
                .filter(
                        c ->
                                c.getAttributes().stream()
                                        .anyMatch(
                                                a ->
                                                        a.getLabel().equals(attributeLabel)
                                                                && a.getSemanticResourceChangeType()
                                                                        != SemanticResourceChangeType
                                                                                .DELETED_FROM_MAPPING))
                .map(SemanticClassChange::getLabel)
                .findFirst()
                .orElseThrow();
    }

    private static Model associationSchema(String associationName, boolean associationUsed) {
        var model = ModelFactory.createDefaultModel();

        for (var className : List.of("Switch", "Terminal")) {
            var cimClass = model.createResource(PREFIX + className);
            cimClass.addProperty(RDF.type, RDFS.Class);
            cimClass.addProperty(RDFS.label, model.createLiteral(className));
            cimClass.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
        }

        var association = model.createResource(PREFIX + associationName);
        association.addProperty(RDF.type, RDF.Property);
        association.addProperty(
                RDFS.label,
                model.createLiteral(associationName.substring(associationName.indexOf('.') + 1)));
        association.addProperty(RDFS.domain, model.getResource(PREFIX + "Switch"));
        association.addProperty(RDFS.range, model.getResource(PREFIX + "Terminal"));
        association.addProperty(CIMS.associationUsed, associationUsed ? "Yes" : "No");
        association.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:0..n"));
        association.addProperty(
                CIMS.inverseRoleName, model.createResource(PREFIX + "Terminal.Switch"));

        return model;
    }

    /**
     * A "Base" class with sibling subclasses (one per label in {@code subLabels}, each {@code
     * rdfs:subClassOf Base}), with a "voltage" attribute declared on whichever classes {@code
     * voltageOwners} names. Used to exercise a merge (voltage declared on several siblings,
     * consolidated onto Base) or a split (declared on Base, pushed down onto several siblings) -
     * per-class chain-pooled detection only ever sees one sibling's own deletion paired with Base's
     * own addition at a time, so recognizing the full merge/split requires collapsing those
     * independent detections.
     */
    private static Model siblingSchema(List<String> subLabels, Set<String> voltageOwners) {
        var model = ModelFactory.createDefaultModel();

        var base = model.createResource(PREFIX + "Base");
        base.addProperty(RDF.type, RDFS.Class);
        base.addProperty(RDFS.label, model.createLiteral("Base"));
        base.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

        for (var label : subLabels) {
            var sub = model.createResource(PREFIX + label);
            sub.addProperty(RDF.type, RDFS.Class);
            sub.addProperty(RDFS.label, model.createLiteral(label));
            sub.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
            sub.addProperty(RDFS.subClassOf, base);
        }

        var range = model.createResource(PREFIX + "VoltageLevel");
        range.addProperty(RDF.type, RDFS.Class);
        range.addProperty(RDFS.label, model.createLiteral("VoltageLevel"));
        range.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

        for (var owner : voltageOwners) {
            var attribute = model.createResource(PREFIX + owner + ".voltage");
            attribute.addProperty(RDF.type, RDF.Property);
            attribute.addProperty(RDFS.label, model.createLiteral("voltage"));
            attribute.addProperty(RDFS.domain, model.getResource(PREFIX + owner));
            attribute.addProperty(RDFS.range, range);
            attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));
            attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
        }

        return model;
    }

    /** Adds one more attribute to a {@link #siblingSchema} model, independent of "voltage". */
    private static void addAttribute(Model model, String domainLabel, String iri, String label) {
        var attribute = model.createResource(PREFIX + iri);
        attribute.addProperty(RDF.type, RDF.Property);
        attribute.addProperty(RDFS.label, model.createLiteral(label));
        attribute.addProperty(RDFS.domain, model.getResource(PREFIX + domainLabel));
        attribute.addProperty(RDFS.range, model.getResource(PREFIX + "VoltageLevel"));
        attribute.addProperty(CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:1..1"));
        attribute.addProperty(CIMS.stereotype, CIMStereotypes.attribute);
    }

    private List<ResourceMapping<SemanticAttributeChange>> deletedAndRemappedAttributesFor(
            List<ClassPropertyMappingOverview> overviews, String classLabel) {
        return overviews.stream()
                .filter(o -> o.getClassLabel().equals(classLabel))
                .findFirst()
                .orElseThrow()
                .getAttributes()
                .getDeletedAndRemapped();
    }

    @Test
    void getPropertyMappings_voltageDeletedFromBothSiblingsAddedOnBase_detectsOneMerge() {
        initContext(
                siblingSchema(List.of("Sub1", "Sub2"), Set.of("Sub1", "Sub2")),
                siblingSchema(List.of("Sub1", "Sub2"), Set.of("Base")));
        confirmDetectedClassRenames();

        var overviews = service.getPropertyMappings();

        var sub1Mappings = deletedAndRemappedAttributesFor(overviews, "Sub1");
        var sub2Mappings = deletedAndRemappedAttributesFor(overviews, "Sub2");
        var baseMappings = deletedAndRemappedAttributesFor(overviews, "Base");

        assertThat(sub1Mappings).hasSize(1);
        var merge = sub1Mappings.getFirst();
        assertThat(merge.getType()).isEqualTo(MappingType.MERGE);
        assertThat(merge.getOldResources())
                .extracting(SemanticResourceChange::getLabel)
                .containsOnly("voltage");
        assertThat(merge.getOldResources()).hasSize(2);
        assertThat(merge.getNewResources()).hasSize(1);

        assertThat(sub2Mappings)
                .as("Sub2 sees the very same merge, not a second independent one")
                .containsExactly(merge);
        assertThat(baseMappings)
                .as("Base owns only the addition, nothing for it to remap")
                .isEmpty();
    }

    @Test
    void getPropertyMappings_voltageDeletedFromBaseAddedOnBothSiblings_detectsOneSplit() {
        initContext(
                siblingSchema(List.of("Sub1", "Sub2"), Set.of("Base")),
                siblingSchema(List.of("Sub1", "Sub2"), Set.of("Sub1", "Sub2")));
        confirmDetectedClassRenames();

        var overviews = service.getPropertyMappings();

        var baseMappings = deletedAndRemappedAttributesFor(overviews, "Base");
        var sub1Mappings = deletedAndRemappedAttributesFor(overviews, "Sub1");
        var sub2Mappings = deletedAndRemappedAttributesFor(overviews, "Sub2");

        assertThat(baseMappings).hasSize(1);
        var split = baseMappings.getFirst();
        assertThat(split.getType()).isEqualTo(MappingType.SPLIT);
        assertThat(split.getOldResources()).hasSize(1);
        assertThat(split.getNewResources())
                .extracting(SemanticResourceChange::getLabel)
                .containsOnly("voltage");
        assertThat(split.getNewResources()).hasSize(2);

        assertThat(sub1Mappings)
                .as("neither sibling owns the deletion, so neither offers it for remapping")
                .isEmpty();
        assertThat(sub2Mappings).isEmpty();
    }

    /**
     * Before the fix, Sub1 and Sub2 (the merge's old domains) lost "voltage" entirely once it was
     * confirmed - nothing in their own property list recorded that it had been merged away, so the
     * review step showed them as classes with no changes at all. Each must now carry its own
     * DELETED_FROM_MAPPING marker for it (a plain DELETE would instead generate its own SPARQL
     * deletion for that exact predicate, racing the merge's own rename), while Base keeps the
     * single materialized merge result.
     */
    @Test
    void confirmPropertyMappings_merge_leavesDeleteMarkersOnBothOldDomains() {
        initContext(
                siblingSchema(List.of("Sub1", "Sub2"), Set.of("Sub1", "Sub2")),
                siblingSchema(List.of("Sub1", "Sub2"), Set.of("Base")));
        confirmDetectedClassRenames();
        confirmDetectedPropertyRenames();

        var classesByLabel =
                migrationSessionStore.getContext().getDiffAfterPropertyConfirm().stream()
                        .collect(Collectors.toMap(SemanticClassChange::getLabel, c -> c));

        for (var sourceLabel : List.of("Sub1", "Sub2")) {
            var voltageAttributes =
                    classesByLabel.get(sourceLabel).getAttributes().stream()
                            .filter(a -> a.getLabel().equals("voltage"))
                            .toList();
            assertThat(voltageAttributes)
                    .as(sourceLabel + " keeps a marker that it lost voltage to the merge")
                    .hasSize(1);
            assertThat(voltageAttributes.getFirst().getSemanticResourceChangeType())
                    .isEqualTo(SemanticResourceChangeType.DELETED_FROM_MAPPING);
        }

        var baseVoltage =
                classesByLabel.get("Base").getAttributes().stream()
                        .filter(a -> a.getLabel().equals("voltage"))
                        .toList();
        assertThat(baseVoltage).hasSize(1);
        assertThat(baseVoltage.getFirst().getSemanticResourceChangeType())
                .isNotEqualTo(SemanticResourceChangeType.DELETED_FROM_MAPPING);
        assertThat(baseVoltage.getFirst().getAdditionalOldIRIs()).hasSize(1);
    }

    /**
     * Revisiting this step (e.g. navigating back after confirming) must not re-run detection - a
     * non-empty mapping list can just as well mean the user already made their own choice, so
     * re-collapsing on every call would silently overwrite that choice with the auto-detected merge
     * again.
     */
    @Test
    void getPropertyMappings_calledTwice_doesNotDuplicateOrChangeTheDetectedMerge() {
        initContext(
                siblingSchema(List.of("Sub1", "Sub2"), Set.of("Sub1", "Sub2")),
                siblingSchema(List.of("Sub1", "Sub2"), Set.of("Base")));
        confirmDetectedClassRenames();

        service.getPropertyMappings();
        var overviews = service.getPropertyMappings();

        var sub1Mappings = deletedAndRemappedAttributesFor(overviews, "Sub1");
        assertThat(sub1Mappings).hasSize(1);
        assertThat(sub1Mappings.getFirst().getType()).isEqualTo(MappingType.MERGE);
    }

    @Test
    void getPropertyMappings_voltageDeletedFromThreeSiblingsAddedOnBase_detectsOneThreeWayMerge() {
        var subs = List.of("Sub1", "Sub2", "Sub3");
        initContext(
                siblingSchema(subs, Set.of("Sub1", "Sub2", "Sub3")),
                siblingSchema(subs, Set.of("Base")));
        confirmDetectedClassRenames();

        var overviews = service.getPropertyMappings();

        var sub1Mappings = deletedAndRemappedAttributesFor(overviews, "Sub1");
        var sub2Mappings = deletedAndRemappedAttributesFor(overviews, "Sub2");
        var sub3Mappings = deletedAndRemappedAttributesFor(overviews, "Sub3");

        assertThat(sub1Mappings).hasSize(1);
        var merge = sub1Mappings.getFirst();
        assertThat(merge.getType()).isEqualTo(MappingType.MERGE);
        assertThat(merge.getOldResources()).hasSize(3);
        assertThat(merge.getNewResources()).hasSize(1);
        assertThat(sub2Mappings).containsExactly(merge);
        assertThat(sub3Mappings).containsExactly(merge);
    }

    @Test
    void getPropertyMappings_voltageDeletedFromBaseAddedOnThreeSiblings_detectsOneThreeWaySplit() {
        var subs = List.of("Sub1", "Sub2", "Sub3");
        initContext(
                siblingSchema(subs, Set.of("Base")),
                siblingSchema(subs, Set.of("Sub1", "Sub2", "Sub3")));
        confirmDetectedClassRenames();

        var overviews = service.getPropertyMappings();

        var baseMappings = deletedAndRemappedAttributesFor(overviews, "Base");

        assertThat(baseMappings).hasSize(1);
        var split = baseMappings.getFirst();
        assertThat(split.getType()).isEqualTo(MappingType.SPLIT);
        assertThat(split.getNewResources()).hasSize(3);
        assertThat(deletedAndRemappedAttributesFor(overviews, "Sub1")).isEmpty();
        assertThat(deletedAndRemappedAttributesFor(overviews, "Sub2")).isEmpty();
        assertThat(deletedAndRemappedAttributesFor(overviews, "Sub3")).isEmpty();
    }

    /**
     * A class that both contributes an old resource to a cross-class merge and independently
     * renames one of its own, unrelated attributes in the same run - the two must not interfere
     * with each other.
     */
    @Test
    void getPropertyMappings_classContributesToMergeAndHasUnrelatedOwnRename_bothCoexist() {
        // TripleChangeAnalyser derives an attribute's label from its IRI suffix, split on the
        // first '.' (the "ClassName." part is discarded) - varying what comes before that first
        // dot keeps the label "status" on both sides while still giving each a distinct IRI.
        var original = siblingSchema(List.of("Sub1", "Sub2"), Set.of("Sub1", "Sub2"));
        addAttribute(original, "Sub1", "Sub1v1.status", "status");
        var updated = siblingSchema(List.of("Sub1", "Sub2"), Set.of("Base"));
        addAttribute(updated, "Sub1", "Sub1v2.status", "status");

        initContext(original, updated);
        confirmDetectedClassRenames();

        var sub1Mappings = deletedAndRemappedAttributesFor(service.getPropertyMappings(), "Sub1");

        assertThat(sub1Mappings).hasSize(2);
        assertThat(sub1Mappings).anyMatch(m -> m.getType() == MappingType.MERGE);
        assertThat(sub1Mappings)
                .anyMatch(
                        m ->
                                m.getType() == MappingType.RENAME
                                        && m.getOldResources()
                                                .getFirst()
                                                .getLabel()
                                                .equals("status"));
    }

    /**
     * Mirrors {@link #siblingSchema}, but for associations instead of attributes - confirms
     * collapseMovesIntoMerges generalizes beyond attributes, as {@code collectMovedPropertyIris}
     * already assumed associations can carry MERGE/SPLIT.
     */
    private static Model siblingAssociationSchema(List<String> subLabels, Set<String> owners) {
        var model = ModelFactory.createDefaultModel();

        var base = model.createResource(PREFIX + "Base");
        base.addProperty(RDF.type, RDFS.Class);
        base.addProperty(RDFS.label, model.createLiteral("Base"));
        base.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

        for (var label : subLabels) {
            var sub = model.createResource(PREFIX + label);
            sub.addProperty(RDF.type, RDFS.Class);
            sub.addProperty(RDFS.label, model.createLiteral(label));
            sub.addProperty(CIMS.stereotype, CIMStereotypes.concrete);
            sub.addProperty(RDFS.subClassOf, base);
        }

        var terminal = model.createResource(PREFIX + "Terminal");
        terminal.addProperty(RDF.type, RDFS.Class);
        terminal.addProperty(RDFS.label, model.createLiteral("Terminal"));
        terminal.addProperty(CIMS.stereotype, CIMStereotypes.concrete);

        for (var owner : owners) {
            // the association's own IRI deliberately does NOT embed the owning class name the
            // way attribute IRIs do - TripleChangeAnalyser derives an association's label from
            // its IRI's whole fragment (unlike attributes, it isn't split on the first '.'), so
            // varying the path instead of the fragment keeps "Terminals" as the label on every
            // owner while still giving each owner's association a distinct IRI.
            var association = model.createResource("http://example.org/" + owner + "#Terminals");
            association.addProperty(RDF.type, RDF.Property);
            association.addProperty(RDFS.label, model.createLiteral("Terminals"));
            association.addProperty(RDFS.domain, model.getResource(PREFIX + owner));
            association.addProperty(RDFS.range, terminal);
            association.addProperty(CIMS.associationUsed, "Yes");
            association.addProperty(
                    CIMS.multiplicity, model.createResource(CIMS_PREFIX + "M:0..n"));
            association.addProperty(
                    CIMS.inverseRoleName, model.createResource(PREFIX + "Terminal." + owner));
        }

        return model;
    }

    @Test
    void getPropertyMappings_associationDeletedFromBothSiblingsAddedOnBase_detectsOneMerge() {
        initContext(
                siblingAssociationSchema(List.of("Sub1", "Sub2"), Set.of("Sub1", "Sub2")),
                siblingAssociationSchema(List.of("Sub1", "Sub2"), Set.of("Base")));
        confirmDetectedClassRenames();

        var overviews = service.getPropertyMappings();
        var sub1Mappings =
                overviews.stream()
                        .filter(o -> o.getClassLabel().equals("Sub1"))
                        .findFirst()
                        .orElseThrow()
                        .getAssociations()
                        .getDeletedAndRemapped();

        assertThat(sub1Mappings).hasSize(1);
        assertThat(sub1Mappings.getFirst().getType()).isEqualTo(MappingType.MERGE);
    }

    /**
     * Two unrelated enumeration classes that each have an entry sharing the same label - since enum
     * classes never participate in {@code rdfs:subClassOf}, their chain-pooled candidates never
     * overlap, so collapseMovesIntoMerges must be a safe no-op here even though it is now
     * generalized to enum entries.
     */
    private static Model unrelatedEnumEntrySchema(boolean entryOnA) {
        var model = ModelFactory.createDefaultModel();

        for (var enumLabel : List.of("StateKindA", "StateKindB")) {
            var enumClass = model.createResource(PREFIX + enumLabel);
            enumClass.addProperty(RDF.type, RDFS.Class);
            enumClass.addProperty(RDFS.label, model.createLiteral(enumLabel));
            enumClass.addProperty(CIMS.stereotype, CIMStereotypes.enumeration);
        }

        var owner = entryOnA ? "StateKindA" : "StateKindB";
        var entry = model.createResource(PREFIX + owner + ".open");
        entry.addProperty(RDF.type, model.getResource(PREFIX + owner));
        entry.addProperty(RDFS.label, model.createLiteral("open"));
        entry.addProperty(CIMS.stereotype, CIMStereotypes.enumLiteral);

        return model;
    }

    @Test
    void getPropertyMappings_enumEntryMovedBetweenUnrelatedEnumClasses_neverLinksAcrossClasses() {
        initContext(unrelatedEnumEntrySchema(true), unrelatedEnumEntrySchema(false));
        confirmDetectedClassRenames();

        var overviews = service.getPropertyMappings();
        var stateKindAMappings =
                overviews.stream()
                        .filter(o -> o.getClassLabel().equals("StateKindA"))
                        .findFirst()
                        .orElseThrow()
                        .getEnumEntries()
                        .getDeletedAndRemapped();

        assertThat(stateKindAMappings).hasSize(1);
        assertThat(stateKindAMappings.getFirst().getNewResources()).isEmpty();
    }
}
