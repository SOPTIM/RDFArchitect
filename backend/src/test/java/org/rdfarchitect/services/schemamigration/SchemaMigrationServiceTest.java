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
import org.rdfarchitect.api.dto.migration.PropertyRenamings;
import org.rdfarchitect.api.dto.migration.ResourceRenameOverview;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.models.changes.RenameCandidate;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.services.compare.TripleChangeAnalyser;

import java.util.ArrayList;
import java.util.List;

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
                service.getClassRenamings().getDeletedAndRenamed().stream()
                        .filter(candidate -> candidate.getNewResource() != null)
                        .toList();
        service.confirmClassRenamings(new ArrayList<>(detected));
    }

    private void confirmDetectedPropertyRenames() {
        var confirmed = new ArrayList<PropertyRenamings>();
        for (var overview : service.getPropertyRenamings()) {
            var renamings = new PropertyRenamings();
            renamings.setClassLabel(overview.getLabel());
            renamings.setAttributeRenames(confirmedOf(overview.getAttributes()));
            renamings.setAssociationRenames(confirmedOf(overview.getAssociations()));
            renamings.setEnumEntryRenames(confirmedOf(overview.getEnumEntries()));
            confirmed.add(renamings);
        }
        service.confirmPropertyRenamings(confirmed);
    }

    private static <T extends SemanticResourceChange> List<RenameCandidate<T>> confirmedOf(
            ResourceRenameOverview<T> overview) {
        return overview.getDeletedAndRenamed().stream()
                .filter(candidate -> candidate.getNewResource() != null)
                .toList();
    }

    /**
     * Maps an attribute rename by hand, as the user does when similarity is below the threshold.
     */
    private void confirmAttributeRename(String classLabel, String oldLabel, String newLabel) {
        var confirmed = new ArrayList<PropertyRenamings>();
        for (var overview : service.getPropertyRenamings()) {
            var renamings = new PropertyRenamings();
            renamings.setClassLabel(overview.getLabel());
            renamings.setAssociationRenames(confirmedOf(overview.getAssociations()));
            renamings.setEnumEntryRenames(confirmedOf(overview.getEnumEntries()));
            renamings.setAttributeRenames(List.of());

            if (overview.getLabel().equals(classLabel)) {
                var deleted =
                        overview.getAttributes().getDeletedAndRenamed().stream()
                                .map(RenameCandidate::getOldResource)
                                .filter(attribute -> attribute.getLabel().equals(oldLabel))
                                .findFirst();
                var added =
                        overview.getAttributes().getAdded().stream()
                                .filter(attribute -> attribute.getLabel().equals(newLabel))
                                .findFirst();
                if (deleted.isPresent() && added.isPresent()) {
                    renamings.setAttributeRenames(
                            List.of(
                                    new RenameCandidate<SemanticAttributeChange>(
                                            deleted.get(), added.get(), 1.0)));
                }
            }
            confirmed.add(renamings);
        }
        service.confirmPropertyRenamings(confirmed);
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
        return service.getPropertyRenamings().stream()
                .flatMap(overview -> overview.getAssociations().getDeletedAndRenamed().stream())
                .map(candidate -> candidate.getOldResource().isAssociationUsed())
                .toList();
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
}
