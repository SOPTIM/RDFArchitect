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

package org.rdfarchitect.api.dto.migration;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticEnumEntryChange;

@Data
@AllArgsConstructor
public class PropertyOverview {

    private String label;
    private ResourceRenameOverview<SemanticAttributeChange> attributes;
    private ResourceRenameOverview<SemanticAssociationChange> associations;
    private ResourceRenameOverview<SemanticEnumEntryChange> enumEntries;

    public PropertyOverview(SemanticClassChange classChange) {
        this.label = classChange.getLabel();
        this.attributes = new ResourceRenameOverview<>(classChange.getAttributes(), classChange.getAttributeRenameCandidates());
        this.associations = new ResourceRenameOverview<>(classChange.getAssociations(), classChange.getAssociationRenameCandidates());
        this.enumEntries = new ResourceRenameOverview<>(classChange.getEnumEntries(), classChange.getEnumEntryRenameCandidates());
    }
}
