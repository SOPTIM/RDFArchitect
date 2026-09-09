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

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.migration.ClassMappingOverview;
import org.rdfarchitect.api.dto.migration.ClassPropertyMappingOverview;
import org.rdfarchitect.api.dto.migration.ClassPropertyMappings;
import org.rdfarchitect.api.dto.migration.DefaultValueView;
import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changes.MappingType;
import org.rdfarchitect.models.changes.ResourceMapping;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticPropertyChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.relations.model.CIMClassUtils;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.compare.TripleChangeAnalyser;
import org.rdfarchitect.services.schemamigration.artifacts.GenerateMigrationReportUseCase;
import org.rdfarchitect.services.schemamigration.artifacts.GenerateMigrationScriptUseCase;
import org.rdfarchitect.services.schemamigration.artifacts.MigrationReportBuilder;
import org.rdfarchitect.services.schemamigration.artifacts.MigrationScriptBuilder;
import org.rdfarchitect.services.schemamigration.defaults.DefaultValueAssigner;
import org.rdfarchitect.services.schemamigration.defaults.GetDefaultValueViewsUseCase;
import org.rdfarchitect.services.schemamigration.defaults.InheritanceChangeHandler;
import org.rdfarchitect.services.schemamigration.defaults.SubmitDefaultValuesUseCase;
import org.rdfarchitect.services.schemamigration.renamings.ClassRenamingsUseCase;
import org.rdfarchitect.services.schemamigration.renamings.ConfirmPropertyMappingsUseCase;
import org.rdfarchitect.services.schemamigration.renamings.GetClassMappingsUseCase;
import org.rdfarchitect.services.schemamigration.renamings.GetPropertyMappingsUseCase;
import org.rdfarchitect.services.schemamigration.renamings.RenameDetector;
import org.rdfarchitect.services.schemamigration.renamings.RenameObjectBuilder;
import org.rdfarchitect.services.validation.SchemaValidationReportToMarkdownService;
import org.rdfarchitect.services.validation.SchemaValidationService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchemaMigrationService
        implements SetMigrationContextUseCase,
                GetClassMappingsUseCase,
                GetPropertyMappingsUseCase,
                GenerateMigrationScriptUseCase,
                ClassRenamingsUseCase,
                ConfirmPropertyMappingsUseCase,
                ClearMigrationContextUseCase,
                GetDefaultValueViewsUseCase,
                SubmitDefaultValuesUseCase,
                GenerateMigrationReportUseCase,
                MigrationChangesUseCase {

    private final MigrationSessionStore migrationSessionStore;
    private final DatabasePort databasePort;
    private final MigrationScriptBuilder migrationScriptBuilder;
    private final MigrationReportBuilder migrationReportBuilder;
    private final SchemaValidationService validationService;

    private static final String GRAPH_URI = "http://example.org/graph";
    private final SchemaValidationReportToMarkdownService schemaValidationReportToMarkdownService;

    @Override
    public void setMigrationContext(
            MultipartFile originalSchema, GraphIdentifier updatedSchema, boolean ignorePrefixes) {
        var originalGraph =
                new GraphFileSourceBuilderImpl()
                        .setFile(originalSchema)
                        .setGraphName(GRAPH_URI)
                        .build()
                        .graph();

        Graph updatedGraph;
        try (var updatedCtx =
                databasePort.getGraphWithContext(updatedSchema).begin(ReadWrite.READ)) {
            updatedGraph = GraphUtils.deepCopy(updatedCtx.getRdfGraph());
        }

        initContext(originalGraph, updatedGraph, ignorePrefixes);
    }

    @Override
    public void setMigrationContext(
            GraphIdentifier originalSchema, GraphIdentifier updatedSchema, boolean ignorePrefixes) {
        Graph originalGraph;
        try (var originalCtx =
                databasePort.getGraphWithContext(originalSchema).begin(ReadWrite.READ)) {
            originalGraph = GraphUtils.deepCopy(originalCtx.getRdfGraph());
        }

        Graph updatedGraph;
        try (var updatedCtx =
                databasePort.getGraphWithContext(updatedSchema).begin(ReadWrite.READ)) {
            updatedGraph = GraphUtils.deepCopy(updatedCtx.getRdfGraph());
        }

        initContext(originalGraph, updatedGraph, ignorePrefixes);
    }

    @Override
    public void setMigrationContext(
            GraphIdentifier originalSchema, MultipartFile updatedSchema, boolean ignorePrefixes) {
        Graph originalGraph;
        try (var originalCtx =
                databasePort.getGraphWithContext(originalSchema).begin(ReadWrite.READ)) {
            originalGraph = GraphUtils.deepCopy(originalCtx.getRdfGraph());
        }

        var updatedGraph =
                new GraphFileSourceBuilderImpl()
                        .setFile(updatedSchema)
                        .setGraphName(GRAPH_URI)
                        .build()
                        .graph();

        initContext(originalGraph, updatedGraph, ignorePrefixes);
    }

    @Override
    public void setMigrationContext(
            MultipartFile originalSchema, MultipartFile updatedSchema, boolean ignorePrefixes) {
        var originalGraph =
                new GraphFileSourceBuilderImpl()
                        .setFile(originalSchema)
                        .setGraphName(GRAPH_URI + "/original")
                        .build()
                        .graph();
        var updatedGraph =
                new GraphFileSourceBuilderImpl()
                        .setFile(updatedSchema)
                        .setGraphName(GRAPH_URI + "/updated")
                        .build()
                        .graph();

        initContext(originalGraph, updatedGraph, ignorePrefixes);
    }

    private void initContext(Graph originalGraph, Graph updatedGraph, boolean ignorePrefixes) {
        var context = migrationSessionStore.getContext();
        context.clear();
        context.setOriginalSchema(originalGraph);
        context.setUpdatedSchema(updatedGraph);
        context.setIgnorePrefixes(ignorePrefixes);
        var tripleChanges =
                TripleChangeAnalyser.compareGraphsDisregardingPackages(originalGraph, updatedGraph);
        context.setTripleDiff(tripleChanges);
        var semanticChanges = SemanticChangeAnalyser.getSemanticChanges(tripleChanges);
        context.setSemanticDiff(semanticChanges);
    }

    @Override
    public ClassMappingOverview getClassMappings() {
        var context = migrationSessionStore.getContext();
        var changes = context.getSemanticDiff();

        var renames = context.getResourceMappings();
        if (renames == null) {
            renames = RenameDetector.detectClassRenames(changes);
        }

        return new ClassMappingOverview(changes, renames);
    }

    @Override
    public void confirmClassRenamings(List<ResourceMapping<SemanticClassChange>> renames) {
        var context = migrationSessionStore.getContext();
        context.setResourceMappings(renames);
        var classChanges = new ArrayList<>(context.getSemanticDiff());

        for (var rename : renames) {
            classChanges.remove(rename.getNewResources().getFirst());
            classChanges.remove(rename.getOldResources().getFirst());
            classChanges.add(RenameObjectBuilder.createRenameObject(rename));
        }
        // reclassify DATATYPE_CHANGE to DATATYPE_RENAMED where the change is simply
        // a consequence of an enum class rename
        reclassifyChangesDueToRenames(classChanges, renames);

        migrationSessionStore.getContext().setDiffAfterClassConfirm(classChanges);
    }

    private void reclassifyChangesDueToRenames(
            List<SemanticClassChange> classChanges,
            List<ResourceMapping<SemanticClassChange>> enumRenames) {
        if (enumRenames == null) {
            return;
        }

        var renameMap =
                enumRenames.stream()
                        .flatMap(
                                r ->
                                        r.getOldResources().stream()
                                                .flatMap(
                                                        oldRes ->
                                                                r.getNewResources().stream()
                                                                        .map(
                                                                                newRes ->
                                                                                        Map.entry(
                                                                                                oldRes
                                                                                                        .getIri(),
                                                                                                newRes
                                                                                                        .getIri()))))
                        .collect(
                                Collectors.groupingBy(
                                        Map.Entry::getKey,
                                        Collectors.mapping(
                                                Map.Entry::getValue, Collectors.toList())));

        for (var classChange : classChanges) {
            reclassifyIfRename(
                    classChange.getChanges(),
                    SemanticFieldChangeType.SUPERCLASS_CHANGE,
                    SemanticFieldChangeType.SUPERCLASS_RENAME,
                    renameMap);

            classChange
                    .getAssociations()
                    .forEach(
                            a ->
                                    reclassifyIfRename(
                                            a.getChanges(),
                                            SemanticFieldChangeType.TARGET_CHANGE,
                                            SemanticFieldChangeType.TARGET_RENAME,
                                            renameMap));

            classChange
                    .getAttributes()
                    .forEach(
                            a ->
                                    reclassifyIfRename(
                                            a.getChanges(),
                                            SemanticFieldChangeType.DATATYPE_CHANGE,
                                            SemanticFieldChangeType.DATATYPE_RENAME,
                                            renameMap));
        }
    }

    private void reclassifyIfRename(
            List<SemanticFieldChange> changes,
            SemanticFieldChangeType changeType,
            SemanticFieldChangeType renameType,
            Map<String, List<String>> renameMap) {

        for (var change : changes) {
            if (change.getSemanticFieldChangeType() == changeType
                    && renameMap
                            .getOrDefault(change.getFrom(), new ArrayList<>())
                            .contains(change.getTo())) {
                change.setSemanticFieldChangeType(renameType);
            }
        }
    }

    @Override
    public List<ClassPropertyMappingOverview> getPropertyMappings() {
        var context = migrationSessionStore.getContext();
        var classChanges =
                context.getDiffAfterClassConfirm().stream()
                        .collect(Collectors.toMap(SemanticClassChange::getIri, c -> c));
        var result = new ArrayList<ClassPropertyMappingOverview>();

        var originalModel = ModelFactory.createModelForGraph(context.getOriginalSchema());
        var updatedModel = ModelFactory.createModelForGraph(context.getUpdatedSchema());

        for (var cls : classChanges.values()) {
            markAssociationsUsed(cls.getAssociations(), originalModel, updatedModel);
            var potentialProperties = findPotentialProperties(cls, classChanges, updatedModel);

            if (cls.getAttributeResourceMappings().isEmpty()) {
                cls.setAttributeResourceMappings(
                        RenameDetector.detectPropertyRenames(potentialProperties.attributes));
            }
            if (cls.getAssociationResourceMappings().isEmpty()) {
                cls.setAssociationResourceMappings(
                        RenameDetector.detectPropertyRenames(potentialProperties.associations));
            }
            if (cls.getEnumEntryResourceMappings().isEmpty()) {
                cls.setEnumEntryResourceMappings(
                        RenameDetector.detectPropertyRenames(potentialProperties.enumEntries));
            }
            if (cls.getAttributes().size()
                            + cls.getAssociations().size()
                            + cls.getEnumEntries().size()
                    > 0) {
                result.add(new ClassPropertyMappingOverview(cls, potentialProperties));
            }
        }

        return result;
    }

    /**
     * Records whether each association can be instantiated, so the rename step can leave out the
     * ones that carry no instance data and have nothing to map. Only the default-values step fills
     * this in otherwise, which is a step too late.
     *
     * <p>The flag has to come from the schema the association still exists in — a deleted one is
     * only in the original — with the other schema as a fallback.
     */
    private void markAssociationsUsed(
            List<SemanticAssociationChange> associations, Model originalModel, Model updatedModel) {
        for (var association : associations) {
            var deleted =
                    association.getSemanticResourceChangeType()
                            == SemanticResourceChangeType.DELETE;
            association.setAssociationUsed(
                    isAssociationUsed(
                            association.getIri(), deleted ? originalModel : updatedModel));
        }
    }

    private boolean isAssociationUsed(String iri, Model model) {
        var association = model.getResource(iri);
        return CIMPropertyUtils.isAssociationUsed(association);
    }

    public record PotentialPropertyData(
            List<SemanticAttributeChange> attributes,
            List<SemanticAssociationChange> associations,
            List<SemanticEnumEntryChange> enumEntries) {}

    private PotentialPropertyData findPotentialProperties(
            SemanticClassChange classChange,
            Map<String, SemanticClassChange> classChanges,
            Model model) {

        var superClasses = CIMClassUtils.listSuperClasses(model.getResource(classChange.getIri()));
        var derivedClasses =
                CIMClassUtils.findDerivingClasses(model.getResource(classChange.getIri()));

        var attributes = new ArrayList<>(classChange.getAttributes());
        var associations = new ArrayList<>(classChange.getAssociations());
        var enumEntries = new ArrayList<>(classChange.getEnumEntries());

        for (var superClass : superClasses) {
            var superClassChange = classChanges.get(superClass.getURI());
            if (superClassChange != null) {
                attributes.addAll(superClassChange.getAttributes());
                associations.addAll(superClassChange.getAssociations());
                enumEntries.addAll(superClassChange.getEnumEntries());
            }
        }

        for (var derivedClass : derivedClasses) {
            var derivedClassChange = classChanges.get(derivedClass.getURI());
            if (derivedClassChange != null) {
                attributes.addAll(derivedClassChange.getAttributes());
                associations.addAll(derivedClassChange.getAssociations());
                enumEntries.addAll((derivedClassChange.getEnumEntries()));
            }
        }

        return new PotentialPropertyData(attributes, associations, enumEntries);
    }

    @Override
    public void confirmPropertyMappings(List<ClassPropertyMappings> classPropertyMappings) {
        var context = migrationSessionStore.getContext();
        var oldClassChanges = context.getDiffAfterClassConfirm();
        var newClassChanges = new ArrayList<SemanticClassChange>();
        for (var cls : oldClassChanges) {
            newClassChanges.add(new SemanticClassChange(cls));
        }
        var newClassChangesByIri =
                newClassChanges.stream()
                        .collect(Collectors.toMap(SemanticClassChange::getIri, c -> c));

        var allAttributeMappings = new ArrayList<ResourceMapping<SemanticAttributeChange>>();
        var allAssociationMappings = new ArrayList<ResourceMapping<SemanticAssociationChange>>();
        var allEnumEntryMappings = new ArrayList<ResourceMapping<SemanticEnumEntryChange>>();

        for (var propertyRename : classPropertyMappings) {
            // update rename candidates in the version after the class confirm, so that the
            // suggested renames reflect the input, when going back to that step
            var oldClassChange =
                    oldClassChanges.stream()
                            .filter(c -> c.getLabel().equals(propertyRename.getClassLabel()))
                            .findFirst()
                            .orElseThrow();
            oldClassChange.setAttributeResourceMappings(propertyRename.getAttributeMappings());
            oldClassChange.setAssociationResourceMappings(propertyRename.getAssociationMappings());
            oldClassChange.setEnumEntryResourceMappings(propertyRename.getEnumEntryMappings());

            // a mapping that crosses class boundaries can be detected while processing either the
            // source or the target class's candidate pool, so the same mapping id can legitimately
            // show up under more than one class's bucket here - collect everything first and
            // materialize/attach each mapping exactly once, by its own resolved target class,
            // rather than blindly attaching it to whichever bucket it happened to arrive under.
            allAttributeMappings.addAll(propertyRename.getAttributeMappings());
            allAssociationMappings.addAll(propertyRename.getAssociationMappings());
            allEnumEntryMappings.addAll(propertyRename.getEnumEntryMappings());
        }

        applyPropertyRenames(
                newClassChangesByIri, SemanticClassChange::getAttributes, allAttributeMappings);
        applyPropertyRenames(
                newClassChangesByIri, SemanticClassChange::getAssociations, allAssociationMappings);
        applyPropertyRenames(
                newClassChangesByIri, SemanticClassChange::getEnumEntries, allEnumEntryMappings);

        // merging a property rename rebuilds its field changes from both sides, which mints a
        // fresh DATATYPE_CHANGE even where the datatype was only renamed - so reclassify again
        reclassifyChangesDueToRenames(newClassChanges, context.getResourceMappings());

        context.setDiffAfterPropertyConfirm(newClassChanges);
    }

    /**
     * Replaces both sides of every confirmed mapping with the merged change(s), so a renamed,
     * moved, merged, or split property is listed once per resulting property rather than as
     * unrelated additions plus deletions - attached to the class the result actually belongs to
     * (its own domain), not to whichever class the mapping happened to be submitted under.
     */
    private <T extends SemanticResourceChange> void applyPropertyRenames(
            Map<String, SemanticClassChange> classesByIri,
            Function<SemanticClassChange, List<T>> propertyListAccessor,
            List<ResourceMapping<T>> renames) {
        var materializedMappingIds = new HashSet<UUID>();

        for (var rename : renames) {
            if (!materializedMappingIds.add(rename.getId())) {
                // the chain-wide candidate pool means the same mapping id can legitimately show
                // up more than once here (surfaced while processing more than one class) - only
                // the first occurrence is applied, the rest are no-ops rather than re-removing
                // (and thereby deleting) the property that occurrence already materialized.
                continue;
            }

            var oldIris = rename.getOldResources().stream().map(this::iriOf).toList();
            var newIris = rename.getNewResources().stream().map(this::iriOf).toList();

            // the old and new resources of a cross-class mapping can each live in a different
            // class's own property list, so every class has to be checked rather than just the
            // class the mapping was submitted under.
            for (var classChange : classesByIri.values()) {
                propertyListAccessor
                        .apply(classChange)
                        .removeIf(
                                property ->
                                        oldIris.contains(property.getIri())
                                                || newIris.contains(property.getIri()));
            }

            if (rename.getType() == MappingType.SPLIT) {
                for (var merged : RenameObjectBuilder.createSplitObjects(rename)) {
                    attachMergedProperty(classesByIri, propertyListAccessor, merged);
                }
            } else {
                attachMergedProperty(
                        classesByIri,
                        propertyListAccessor,
                        RenameObjectBuilder.createRenameObject(rename));
            }
        }
    }

    private <T extends SemanticResourceChange> void attachMergedProperty(
            Map<String, SemanticClassChange> classesByIri,
            Function<SemanticClassChange, List<T>> propertyListAccessor,
            T merged) {
        if (merged.getChanges().isEmpty()) {
            return;
        }
        var domain =
                merged instanceof SemanticPropertyChange propertyChange
                        ? propertyChange.getDomain()
                        : null;
        var targetClass = domain != null ? classesByIri.get(domain) : null;
        if (targetClass != null) {
            propertyListAccessor.apply(targetClass).add(merged);
        }
    }

    private String iriOf(SemanticResourceChange change) {
        return change != null ? change.getIri() : null;
    }

    @Override
    public List<DefaultValueView> getDefaultValueViews() {
        var newChangeList = new ArrayList<SemanticClassChange>();
        for (var cls : migrationSessionStore.getContext().getDiffAfterPropertyConfirm()) {
            newChangeList.add(new SemanticClassChange(cls));
        }
        var result = new ArrayList<DefaultValueView>();

        var context = migrationSessionStore.getContext();
        var oldModel = ModelFactory.createModelForGraph(context.getOriginalSchema());
        var updatedModel = ModelFactory.createModelForGraph(context.getUpdatedSchema());
        var classRenames = context.getResourceMappings();
        var movedPropertyIris = collectMovedPropertyIris(context.getDiffAfterClassConfirm());

        InheritanceChangeHandler.processInheritanceChanges(
                newChangeList, updatedModel, oldModel, classRenames, movedPropertyIris);
        DefaultValueAssigner.assignDefaultValues(newChangeList, updatedModel, result);

        return result;
    }

    /**
     * IRIs of every property already covered by a confirmed cross-class move, on either side -
     * inheritance propagation must leave these alone, since a moved property is already represented
     * by its own merged entry rather than by an inherited add/delete.
     */
    private Set<String> collectMovedPropertyIris(List<SemanticClassChange> classChanges) {
        var movedPropertyIris = new HashSet<String>();
        for (var classChange : classChanges) {
            addMovedPropertyIris(movedPropertyIris, classChange.getAttributeResourceMappings());
            addMovedPropertyIris(movedPropertyIris, classChange.getAssociationResourceMappings());
        }
        return movedPropertyIris;
    }

    private <T extends SemanticResourceChange> void addMovedPropertyIris(
            Set<String> movedPropertyIris, List<ResourceMapping<T>> mappings) {
        for (var mapping : mappings) {
            if (mapping.getType() != MappingType.MOVE) {
                continue;
            }
            mapping.getOldResources().forEach(r -> movedPropertyIris.add(iriOf(r)));
            mapping.getNewResources().forEach(r -> movedPropertyIris.add(iriOf(r)));
        }
    }

    @Override
    public void submitDefaultValues(List<DefaultValueView> defaultValueViews) {
        var classes =
                new ArrayList<>(migrationSessionStore.getContext().getDiffAfterPropertyConfirm());
        var classChangeMap =
                classes.stream().collect(Collectors.toMap(SemanticClassChange::getLabel, c -> c));

        for (var defaultValueView : defaultValueViews) {
            var classChange = classChangeMap.get(defaultValueView.getClassLabel());
            if (classChange != null) {
                classChange.setAttributes(defaultValueView.getAttributes());
                classChange.setAssociations(defaultValueView.getAssociations());
                classChange.setEnumEntries(defaultValueView.getEnumEntries());
            }
        }

        var newChangeList = new ArrayList<>(classChangeMap.values());
        migrationSessionStore.getContext().setDiffAfterDefaultValueConfirm(newChangeList);
    }

    @Override
    public List<SemanticClassChange> getMigrationChanges() {
        return migrationSessionStore.getContext().getDiffAfterDefaultValueConfirm();
    }

    @Override
    public void confirmMigrationChanges(List<SemanticClassChange> changes) {
        migrationSessionStore.getContext().setDiffAfterDefaultValueConfirm(changes);
    }

    @Override
    public String generateMigrationScript() {
        var changes = migrationSessionStore.getContext().getDiffAfterDefaultValueConfirm();
        return migrationScriptBuilder.generateMigrationScript(changes);
    }

    @Override
    public String generateDetailedMigrationReport(
            CGMESVersion originalCGMESVersion, CGMESVersion updatedCGMESVersion) {
        var context = migrationSessionStore.getContext();
        var updatedGraph = context.getUpdatedSchema();
        var originalGraph = context.getOriginalSchema();
        var changes = context.getDiffAfterDefaultValueConfirm();
        var ignorePrefixes = context.isIgnorePrefixes();

        var newChangeList = new ArrayList<SemanticClassChange>();
        for (var change : changes) {
            newChangeList.add(new SemanticClassChange(change));
        }

        var sb = new StringBuilder();

        var originalValidationReport =
                validationService.validateSchema(context.getOriginalSchema(), originalCGMESVersion);
        sb.append(
                schemaValidationReportToMarkdownService.convertToMarkdown(
                        originalValidationReport, "Validation Report Original Schema"));

        var updatedValidationReport =
                validationService.validateSchema(context.getUpdatedSchema(), updatedCGMESVersion);
        sb.append(
                schemaValidationReportToMarkdownService.convertToMarkdown(
                        updatedValidationReport, "Validation Report Updated Schema"));

        sb.append(
                migrationReportBuilder.generateDetailedMigrationReport(
                        newChangeList, originalGraph, updatedGraph, ignorePrefixes));

        return sb.toString();
    }

    @Override
    public String generateSummaryMigrationReport(
            CGMESVersion originalCGMESVersion, CGMESVersion updatedCGMESVersion) {
        var context = migrationSessionStore.getContext();
        var changes = context.getDiffAfterDefaultValueConfirm();
        var ignorePrefixes = context.isIgnorePrefixes();
        var originalGraph = context.getOriginalSchema();
        var updatedGraph = context.getUpdatedSchema();

        var newChangeList = new ArrayList<SemanticClassChange>();
        for (var change : changes) {
            newChangeList.add(new SemanticClassChange(change));
        }

        var sb = new StringBuilder();

        var originalValidationReport =
                validationService.validateSchema(context.getOriginalSchema(), originalCGMESVersion);
        sb.append(
                schemaValidationReportToMarkdownService.convertToMarkdown(
                        originalValidationReport, "Validation Report Original Schema"));

        var updatedValidationReport =
                validationService.validateSchema(context.getUpdatedSchema(), updatedCGMESVersion);
        sb.append(
                schemaValidationReportToMarkdownService.convertToMarkdown(
                        updatedValidationReport, "Validation Report Updated Schema"));

        sb.append(
                migrationReportBuilder.generateSummaryMigrationReport(
                        newChangeList, originalGraph, updatedGraph, ignorePrefixes));
        return sb.toString();
    }

    @Override
    public void clearMigrationContext() {
        migrationSessionStore.clearContext();
    }
}
