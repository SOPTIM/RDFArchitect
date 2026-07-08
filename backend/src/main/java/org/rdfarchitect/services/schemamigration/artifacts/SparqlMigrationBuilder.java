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

package org.rdfarchitect.services.schemamigration.artifacts;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SparqlMigrationBuilder implements MigrationScriptBuilder {

    private final SparqlUpdateGenerator updateGenerator;

    @Override
    public String generateMigrationScript(List<SemanticClassChange> classChanges) {
        var sb = new StringBuilder();
        for (var classChange : classChanges) {
            var classBlock = generateUpdateForClass(classChange);
            if (!classBlock.isBlank()) {
                sb.append(classBlock);
            }
        }
        return sb.toString();
    }

    private String generateUpdateForClass(SemanticClassChange classChange) {
        var sb = new StringBuilder();

        appendComment(sb, classChange);

        String update =
                switch (classChange.getSemanticResourceChangeType()) {
                    case DELETE -> updateGenerator.generateDeleteClassUpdate(classChange);
                    case RENAME -> updateGenerator.generateRenameClassUpdate(classChange);
                    default -> null;
                };

        if (update != null && !update.isBlank()) {
            sb.append(update).append("\n");
        }

        if (classChange.getSemanticResourceChangeType() != SemanticResourceChangeType.DELETE
                && classChange.getSemanticResourceChangeType() != SemanticResourceChangeType.ADD) {
            processClassChanges(classChange, sb);
        }

        processPropertyChanges(classChange, sb);

        return sb.toString();
    }

    private void processClassChanges(SemanticClassChange classChange, StringBuilder sb) {
        for (var change : classChange.getChanges()) {
            if (change.getSemanticFieldChangeType() == SemanticFieldChangeType.MADE_ABSTRACT) {
                sb.append(updateGenerator.generateDeleteClassUpdate(classChange)).append("\n");
            }
        }
    }

    private void processPropertyChanges(SemanticClassChange classChange, StringBuilder sb) {
        for (var attribute : classChange.getAttributes()) {
            var block = generateUpdateForAttribute(attribute, classChange.getIri());
            if (!block.isBlank()) {
                sb.append(block);
            }
        }

        for (var association : classChange.getAssociations()) {
            var block = generateUpdateForAssociation(association);
            if (!block.isBlank()) {
                sb.append(block);
            }
        }

        if (classChange.getSemanticResourceChangeType() != SemanticResourceChangeType.DELETE) {
            for (var enumEntry : classChange.getEnumEntries()) {
                var block = generateUpdateForEnumEntry(enumEntry);
                if (!block.isBlank()) {
                    sb.append(block);
                }
            }
        }
    }

    private String generateUpdateForAttribute(
            SemanticAttributeChange attributeChange, String classIri) {
        var sb = new StringBuilder();
        appendComment(sb, attributeChange);

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

        if (update != null && !update.isBlank()) {
            sb.append(update).append("\n");
        }

        var type = attributeChange.getSemanticResourceChangeType();
        if (type == SemanticResourceChangeType.CHANGE
                || type == SemanticResourceChangeType.RENAME) {
            processAttributeFieldChanges(attributeChange, classIri, sb);
        }

        return sb.toString();
    }

    private void processAttributeFieldChanges(
            SemanticAttributeChange attributeChange, String classIri, StringBuilder sb) {
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

            if (update != null && !update.isBlank()) {
                sb.append(update).append("\n");
            }
        }
    }

    private String generateUpdateForEnumEntry(SemanticEnumEntryChange enumEntryChange) {
        var sb = new StringBuilder();
        appendComment(sb, enumEntryChange);

        var update =
                switch (enumEntryChange.getSemanticResourceChangeType()) {
                    case DELETE -> updateGenerator.generateDeleteEnumEntryUpdate(enumEntryChange);
                    case RENAME -> updateGenerator.generateRenameEnumEntryUpdate(enumEntryChange);
                    default -> null;
                };

        if (update != null && !update.isBlank()) {
            sb.append(update).append("\n");
        }

        return sb.toString();
    }

    private String generateUpdateForAssociation(SemanticAssociationChange associationChange) {
        var sb = new StringBuilder();
        appendComment(sb, associationChange);

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

        if (update != null && !update.isBlank()) {
            sb.append(update).append("\n");
        }

        var type = associationChange.getSemanticResourceChangeType();
        if (type == SemanticResourceChangeType.CHANGE
                || type == SemanticResourceChangeType.RENAME) {
            processAssociationFieldChanges(associationChange, sb);
        }

        return sb.toString();
    }

    private void processAssociationFieldChanges(
            SemanticAssociationChange associationChange, StringBuilder sb) {
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

            if (update != null && !update.isBlank()) {
                sb.append(update).append("\n");
            }
        }
    }

    private void appendComment(StringBuilder sb, SemanticResourceChange resourceChange) {
        if (resourceChange.getComment() != null && !resourceChange.getComment().isBlank()) {
            for (var line : resourceChange.getComment().split("\\R")) {
                sb.append("# ").append(line).append("\n");
            }
        }
    }
}
