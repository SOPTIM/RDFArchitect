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

package org.rdfarchitect.models.changes;

import lombok.Data;
import lombok.NoArgsConstructor;

import org.rdfarchitect.models.changes.semanticchanges.SemanticPropertyChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Data
@NoArgsConstructor
public class ResourceMapping<T extends SemanticResourceChange> {
    private UUID id = UUID.randomUUID();
    private List<T> oldResources = new ArrayList<>();
    private List<T> newResources = new ArrayList<>();
    private double confidenceScore;
    private MappingType type;

    public ResourceMapping(T deletedResource) {
        this.oldResources.add(deletedResource);
        this.type = MappingType.UNMAPPED;
    }

    public ResourceMapping(T deletedResource, T addedResource, double confidenceScore) {
        this.newResources.add(addedResource);
        this.oldResources.add(deletedResource);
        this.confidenceScore = confidenceScore;
        if (addedResource instanceof SemanticPropertyChange added
                && deletedResource instanceof SemanticPropertyChange deleted
                && !Objects.equals(added.getDomain(), deleted.getDomain())) {
            this.type = MappingType.MOVE;
        } else {
            this.type = MappingType.RENAME;
        }
    }

    public ResourceMapping(
            List<T> deletedResources, List<T> addedResources, double confidenceScore) {
        this.oldResources.addAll(deletedResources);
        this.newResources.addAll(addedResources);
        this.confidenceScore = confidenceScore;
        if (deletedResources.size() > 1 && addedResources.size() == 1) {
            this.type = MappingType.MERGE;
        } else if (deletedResources.size() == 1 && addedResources.size() > 1) {
            this.type = MappingType.SPLIT;
        } else {
            throw new IllegalArgumentException(
                    "Invalid mapping: cannot map multiple deleted resources to multiple added resources.");
        }
    }

    public ResourceMapping(T deletedResource, List<T> addedResources, double confidenceScore) {
        this.oldResources.add(deletedResource);
        this.newResources.addAll(addedResources);
        this.confidenceScore = confidenceScore;
        this.type = MappingType.SPLIT;
    }

    public ResourceMapping(List<T> deletedResources, T addedResource, double confidenceScore) {
        this.oldResources.addAll(deletedResources);
        this.newResources.add(addedResource);
        this.confidenceScore = confidenceScore;
        this.type = MappingType.MERGE;
    }
}
