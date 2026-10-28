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
import org.rdfarchitect.services.schemamigration.SchemaMigrationService;

@Data
@AllArgsConstructor
public class ClassPropertyMappingOverview {

    private String classLabel;

    private PropertyMappingOverview<SemanticAttributeChange> attributes;

    private PropertyMappingOverview<SemanticAssociationChange> associations;

    private PropertyMappingOverview<SemanticEnumEntryChange> enumEntries;

    public ClassPropertyMappingOverview(
            SemanticClassChange classChange,
            SchemaMigrationService.PotentialPropertyData propertyData) {
        this.classLabel = classChange.getLabel();
        this.attributes =
                new PropertyMappingOverview<>(
                        classChange.getAttributes(),
                        propertyData.attributes(),
                        classChange.getAttributeResourceMappings());
        this.associations =
                new PropertyMappingOverview<>(
                        classChange.getAssociations(),
                        propertyData.associations(),
                        classChange.getAssociationResourceMappings());
        this.enumEntries =
                new PropertyMappingOverview<>(
                        classChange.getEnumEntries(),
                        propertyData.enumEntries(),
                        classChange.getEnumEntryResourceMappings());
    }
}
