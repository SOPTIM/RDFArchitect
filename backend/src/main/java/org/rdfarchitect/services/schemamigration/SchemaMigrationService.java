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
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.migration.DefaultValueView;
import org.rdfarchitect.api.dto.migration.PropertyOverview;
import org.rdfarchitect.api.dto.migration.PropertyRenamings;
import org.rdfarchitect.api.dto.migration.ResourceRenameOverview;
import org.rdfarchitect.context.MigrationSessionStore;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changes.RenameCandidate;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.rdf.graph.GraphUtils;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindableWithUUIDs;
import org.rdfarchitect.services.compare.TripleChangeAnalyser;
import org.rdfarchitect.services.schemamigration.defaults.DefaultValueAssigner;
import org.rdfarchitect.services.schemamigration.defaults.GetDefaultValueViewsUseCase;
import org.rdfarchitect.services.schemamigration.defaults.InheritanceChangeHandler;
import org.rdfarchitect.services.schemamigration.defaults.SubmitDefaultValuesUseCase;
import org.rdfarchitect.services.schemamigration.renamings.ClassRenamingsUseCase;
import org.rdfarchitect.services.schemamigration.renamings.ConfirmPropertyRenamingsUseCase;
import org.rdfarchitect.services.schemamigration.renamings.GetClassRenamingsUseCase;
import org.rdfarchitect.services.schemamigration.renamings.GetPropertyRenamingsUseCase;
import org.rdfarchitect.services.schemamigration.renamings.RenameDetector;
import org.rdfarchitect.services.schemamigration.renamings.RenameObjectBuilder;
import org.rdfarchitect.services.schemamigration.scriptgeneration.GenerateMigrationScriptUseCase;
import org.rdfarchitect.services.schemamigration.scriptgeneration.MigrationScriptBuilder;
import org.rdfarchitect.services.update.classes.enumentries.ReplaceOrCreateEnumEntryUseCase;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchemaMigrationService implements SetMigrationContextUseCase, GetClassRenamingsUseCase, GetPropertyRenamingsUseCase, GenerateMigrationScriptUseCase,
          ClassRenamingsUseCase,
          ConfirmPropertyRenamingsUseCase, ClearMigrationContextUseCase, GetDefaultValueViewsUseCase, SubmitDefaultValuesUseCase {

    private final MigrationSessionStore migrationSessionStore;
    private final DatabasePort databasePort;
    private final MigrationScriptBuilder migrationScriptBuilder;

    private static final String GRAPH_URI = "http://example.org/graph";
    private final ReplaceOrCreateEnumEntryUseCase replaceOrCreateEnumEntryUseCase;

    @Override
    public void setMigrationContext(MultipartFile originalSchema, GraphIdentifier updatedSchema) {
        var originalGraph = new GraphFileSourceBuilderImpl()
                  .setFile(originalSchema)
                  .setGraphName(GRAPH_URI)
                  .build()
                  .graph();

        Graph updatedGraph;
        GraphRewindableWithUUIDs loadedGraph = databasePort.getGraphWithContext(updatedSchema).getRdfGraph();
        try {
            loadedGraph.begin(TxnType.READ);
            updatedGraph = GraphUtils.deepCopy(loadedGraph);
        } finally {
            loadedGraph.end();
        }

        initContext(originalGraph, updatedGraph);
    }

    @Override
    public void setMigrationContext(GraphIdentifier originalSchema, GraphIdentifier updatedSchema) {
        Graph originalGraph;
        GraphRewindableWithUUIDs loadedGraph = databasePort.getGraphWithContext(originalSchema).getRdfGraph();
        try {
            loadedGraph.begin(TxnType.READ);
            originalGraph = GraphUtils.deepCopy(loadedGraph);
        } finally {
            loadedGraph.end();
        }

        Graph updatedGraph;
        loadedGraph = databasePort.getGraphWithContext(updatedSchema).getRdfGraph();
        try {
            loadedGraph.begin(TxnType.READ);
            updatedGraph = GraphUtils.deepCopy(loadedGraph);
        } finally {
            loadedGraph.end();
        }

        initContext(originalGraph, updatedGraph);
    }

    @Override
    public void setMigrationContext(MultipartFile originalSchema, MultipartFile updatedSchema) {
        var originalGraph = new GraphFileSourceBuilderImpl()
                  .setFile(originalSchema)
                  .setGraphName(GRAPH_URI + "/original")
                  .build()
                  .graph();
        var updatedGraph = new GraphFileSourceBuilderImpl()
                  .setFile(updatedSchema)
                  .setGraphName(GRAPH_URI + "/updated")
                  .build()
                  .graph();

        initContext(originalGraph, updatedGraph);
    }

    private void initContext(Graph originalGraph, Graph updatedGraph) {
        var context = migrationSessionStore.getContext();
        context.clear();
        context.setOriginalSchema(originalGraph);
        context.setUpdatedSchema(updatedGraph);
        var tripleChanges = TripleChangeAnalyser.compareGraphsDisregardingPackages(originalGraph, updatedGraph);
        context.setTripleDiff(tripleChanges);
        var semanticChanges = SemanticChangeAnalyser.getSemanticChanges(tripleChanges);
        context.setSemanticDiff(semanticChanges);
    }

    @Override
    public ResourceRenameOverview<SemanticClassChange> getClassRenamings() {
        var context = migrationSessionStore.getContext();
        var changes = context.getSemanticDiff();

        var renames = context.getRenameCandidates();
        if (renames == null) {
            renames = RenameDetector.detectClassRenames(changes);
        }

        return new ResourceRenameOverview<>(changes, renames);
    }

    @Override
    public void confirmClassRenamings(List<RenameCandidate<SemanticClassChange>> renames) {
        var context = migrationSessionStore.getContext();
        context.setRenameCandidates(renames);
        var classChanges = new ArrayList<>(context.getSemanticDiff());

        for (var rename : renames) {
            classChanges.remove(rename.getNewResource());
            classChanges.remove(rename.getOldResource());
            classChanges.add((SemanticClassChange) RenameObjectBuilder.createRenameObject(rename));
        }
        // reclassify DATATYPE_CHANGE to DATATYPE_RENAMED where the change is simply
        // a consequence of an enum class rename
        reclassifyEnumDatatypeChanges(classChanges, renames);

        migrationSessionStore.getContext().setDiffAfterClassConfirm(classChanges);
    }

    private void reclassifyEnumDatatypeChanges(
              List<SemanticClassChange> classChanges,
              List<RenameCandidate<SemanticClassChange>> enumRenames) {

        var renameMap = enumRenames.stream()
                                         .collect(Collectors.toMap(
                                                   r -> r.getOldResource().getIri(),
                                                   r -> r.getNewResource().getIri()
                                                                  ));

        for (var classChange : classChanges) {
            for (var attribute : classChange.getAttributes()) {
                for (var fieldChange : attribute.getChanges()) {
                    if (fieldChange.getSemanticFieldChangeType() == SemanticFieldChangeType.DATATYPE_CHANGE
                              && renameMap.containsKey(fieldChange.getFrom())
                              && renameMap.get(fieldChange.getFrom()).equals(fieldChange.getTo())) {
                        fieldChange.setSemanticFieldChangeType(SemanticFieldChangeType.DATATYPE_RENAME);
                    }
                }
            }
        }
    }

    @Override
    public List<PropertyOverview> getPropertyRenamings() {
        var classes = new ArrayList<>(migrationSessionStore.getContext().getDiffAfterClassConfirm());
        var result = new ArrayList<PropertyOverview>();
        for (var cls : classes) {
            cls.setAttributeRenameCandidates(RenameDetector.detectPropertyRenames(cls.getAttributes()));
            cls.setAssociationRenameCandidates(RenameDetector.detectPropertyRenames(cls.getAssociations()));
            cls.setEnumEntryRenameCandidates(RenameDetector.detectPropertyRenames(cls.getEnumEntries()));
            if (cls.getAttributes().size() + cls.getAssociations().size() + cls.getEnumEntries().size() > 0) {
                result.add(new PropertyOverview(cls));
            }
        }

        return result;
    }

    @Override
    public void confirmPropertyRenamings(List<PropertyRenamings> propertyRenames) {
        var context = migrationSessionStore.getContext();
        var oldClassChanges = context.getDiffAfterClassConfirm();
        var newClassChanges = new ArrayList<>(context.getDiffAfterClassConfirm());

        for (var propertyRename : propertyRenames) {
            //update rename candidates in the version after the class confirm, so that the suggested renames reflect the input, when going back to that step
            var oldClassChange = oldClassChanges.stream().filter(c -> c.getLabel().equals(propertyRename.getClassLabel())).findFirst().orElseThrow();
            oldClassChange.setAttributeRenameCandidates(propertyRename.getAttributeRenames());
            oldClassChange.setAssociationRenameCandidates(propertyRename.getAssociationRenames());
            oldClassChange.setEnumEntryRenameCandidates(propertyRename.getEnumEntryRenames());

            var newClassChange = newClassChanges.stream().filter(c -> c.getLabel().equals(propertyRename.getClassLabel())).findFirst().orElseThrow();
            var attributes = newClassChange.getAttributes();
            for (var attributeRename : propertyRename.getAttributeRenames()) {
                attributes.remove(attributeRename.getNewResource());
                attributes.remove(attributeRename.getOldResource());
                var mergedAttribute = (SemanticAttributeChange) RenameObjectBuilder.createRenameObject(attributeRename);
                if (!mergedAttribute.getChanges().isEmpty()) {
                    attributes.add(mergedAttribute);
                }
            }
            var associations = newClassChange.getAssociations();
            for (var associationRename : propertyRename.getAssociationRenames()) {
                associations.remove(associationRename.getNewResource());
                associations.remove(associationRename.getOldResource());
                var mergedAssociation = (SemanticAssociationChange) RenameObjectBuilder.createRenameObject(associationRename);
                if (!mergedAssociation.getChanges().isEmpty()) {
                    associations.add(mergedAssociation);
                }
            }
            var enumEntries = newClassChange.getEnumEntries();
            for (var enumEntryRename : propertyRename.getEnumEntryRenames()) {
                enumEntries.remove(enumEntryRename.getNewResource());
                enumEntries.remove(enumEntryRename.getOldResource());
                var mergedEnumEntry = (SemanticEnumEntryChange) RenameObjectBuilder.createRenameObject(enumEntryRename);
                if (!mergedEnumEntry.getChanges().isEmpty()) {
                    enumEntries.add(mergedEnumEntry);
                }
            }
        }

        context.setDiffAfterPropertyConfirm(newClassChanges);
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
        var classRenames = context.getRenameCandidates();

        InheritanceChangeHandler.processInheritanceChanges(newChangeList, updatedModel, oldModel, classRenames);
        DefaultValueAssigner.assignDefaultValues(newChangeList, updatedModel, result);

        return result;
    }

    @Override
    public void submitDefaultValues(List<DefaultValueView> defaultValueViews) {
        var classes = new ArrayList<>(migrationSessionStore.getContext().getDiffAfterPropertyConfirm());
        var classChangeMap = classes.stream()
                                    .collect(Collectors.toMap(SemanticClassChange::getLabel, c -> c));

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
    public String generateMigrationScript() {
        var changes = migrationSessionStore.getContext().getDiffAfterDefaultValueConfirm();
        return migrationScriptBuilder.generateMigrationScript(changes);
    }

    @Override
    public void clearMigrationContext() {
        migrationSessionStore.clearContext();
    }
}
