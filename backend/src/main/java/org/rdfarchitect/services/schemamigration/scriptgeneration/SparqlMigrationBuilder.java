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

import lombok.RequiredArgsConstructor;

import org.apache.jena.update.UpdateRequest;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SparqlMigrationBuilder implements MigrationScriptBuilder {

    private final SparqlUpdateGenerator updateGenerator;

    @Override
    public String generateMigrationScript(List<SemanticClassChange> classChanges) {
        var script = new UpdateRequest();
        for (var classChange : classChanges) {
            script.add(generateUpdateForClass(classChange));
        }
        return script.toString();
    }

    private String generateUpdateForClass(SemanticClassChange classChange) {
        var result = new UpdateRequest();

        String update =
                switch (classChange.getSemanticResourceChangeType()) {
                    case DELETE -> updateGenerator.generateDeleteClassUpdate(classChange);
                    case RENAME -> updateGenerator.generateRenameClassUpdate(classChange);
                    default -> null;
                };

        if (update != null) {
            result.add(update);
        }

        if (classChange.getSemanticResourceChangeType() != SemanticResourceChangeType.DELETE
                && classChange.getSemanticResourceChangeType() != SemanticResourceChangeType.ADD) {
            processClassChanges(classChange, result);
        }

        processPropertyChanges(classChange, result);

        return result.toString();
    }

    private void processClassChanges(SemanticClassChange classChange, UpdateRequest result) {
        for (var change : classChange.getChanges()) {
            if (change.getSemanticFieldChangeType() == SemanticFieldChangeType.MADE_ABSTRACT) {
                result.add(updateGenerator.generateDeleteClassUpdate(classChange));
            }
        }
    }

    private void processPropertyChanges(SemanticClassChange classChange, UpdateRequest result) {
        for (var attribute : classChange.getAttributes()) {
            result.add(generateUpdateForAttribute(attribute, classChange.getIri()));
        }

        for (var association : classChange.getAssociations()) {
            result.add(generateUpdateForAssociation(association));
        }

        if (classChange.getSemanticResourceChangeType() != SemanticResourceChangeType.DELETE) {
            for (var enumEntry : classChange.getEnumEntries()) {
                result.add(generateUpdateForEnumEntry(enumEntry));
            }
        }
    }

    private String generateUpdateForAttribute(
            SemanticAttributeChange attributeChange, String classIri) {
        var result = new UpdateRequest();

        var update =
                switch (attributeChange.getSemanticResourceChangeType()) {
                    case DELETE -> updateGenerator.generateDeletePropertyUpdate(attributeChange);
                    case RENAME -> updateGenerator.generateRenameAttributeUpdate(attributeChange);
                    case ADD ->
                            updateGenerator.generateAddAttributeUpdate(attributeChange, classIri);
                    case ADDED_FROM_INHERITANCE ->
                            updateGenerator.generateAddAttributeToSingleClassUpdate(
                                    attributeChange, classIri);
                    case DELETED_FROM_INHERITANCE ->
                            updateGenerator.generateDeletePropertyFromSingleClassUpdate(
                                    attributeChange, classIri);
                    default -> null;
                };

        if (update != null) {
            result.add(update);
        }

        var type = attributeChange.getSemanticResourceChangeType();
        if (type == SemanticResourceChangeType.CHANGE
                || type == SemanticResourceChangeType.RENAME) {
            processAttributeFieldChanges(attributeChange, classIri, result);
        }

        return result.toString();
    }

    private void processAttributeFieldChanges(
            SemanticAttributeChange attributeChange, String classIri, UpdateRequest result) {
        for (var change : attributeChange.getChanges()) {
            var update =
                    switch (change.getSemanticFieldChangeType()) {
                        case DATATYPE_CHANGE ->
                                updateGenerator.generateDatatypeChangedUpdate(attributeChange);
                        case MADE_REQUIRED ->
                                updateGenerator.generateAddAttributeToSingleClassUpdate(
                                        attributeChange, classIri);
                        case FIXED_VALUE_CHANGE ->
                                updateGenerator.generateFixedValueUpdate(attributeChange);
                        case DOMAIN_RENAME ->
                                updateGenerator.generateDomainRenameUpdate(attributeChange);
                        default -> null;
                    };

            if (update != null) {
                result.add(update);
            }
        }
    }

    private String generateUpdateForEnumEntry(SemanticEnumEntryChange enumEntryChange) {
        var result = new UpdateRequest();

        var update =
                switch (enumEntryChange.getSemanticResourceChangeType()) {
                    case DELETE -> updateGenerator.generateDeleteEnumEntryUpdate(enumEntryChange);
                    case RENAME -> updateGenerator.generateRenameEnumEntryUpdate(enumEntryChange);
                    default -> null;
                };

        if (update != null) {
            result.add(update);
        }

        return result.toString();
    }

    private String generateUpdateForAssociation(SemanticAssociationChange associationChange) {
        var result = new UpdateRequest();

        var update =
                switch (associationChange.getSemanticResourceChangeType()) {
                    case DELETE -> updateGenerator.generateDeletePropertyUpdate(associationChange);
                    case ADD -> updateGenerator.generateAddAssociationUpdate(associationChange);
                    case ADDED_FROM_INHERITANCE ->
                            updateGenerator.generateAddAssociationToSingleClassUpdate(
                                    associationChange, associationChange.getIri());
                    case RENAME ->
                            updateGenerator.generateRenameAssociationUpdate(associationChange);
                    default -> null;
                };

        if (update != null) {
            result.add(update);
        }

        var type = associationChange.getSemanticResourceChangeType();
        if (type == SemanticResourceChangeType.CHANGE
                || type == SemanticResourceChangeType.RENAME) {
            processAssociationFieldChanges(associationChange, result);
        }

        return result.toString();
    }

    private void processAssociationFieldChanges(
            SemanticAssociationChange associationChange, UpdateRequest result) {
        for (var change : associationChange.getChanges()) {
            var update =
                    switch (change.getSemanticFieldChangeType()) {
                        case TARGET_CHANGE ->
                                updateGenerator.generateAddAssociationUpdate(associationChange);
                        case ASSOCIATION_USED_CHANGE ->
                                updateGenerator.generateAssociationTargetChangeUpdate(
                                        associationChange);
                        case DOMAIN_RENAME ->
                                updateGenerator.generateDomainRenameUpdate(associationChange);
                        default -> null;
                    };

            if (update != null) {
                result.add(update);
            }
        }
    }
}
