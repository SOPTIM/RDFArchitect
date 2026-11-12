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

package org.rdfarchitect.models.changes.semanticchanges;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.apache.jena.rdf.model.Resource;
import org.rdfarchitect.models.changes.triplechanges.TripleResourceChange;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public sealed class SemanticPropertyChange extends SemanticResourceChange
        permits SemanticAssociationChange, SemanticAttributeChange, SemanticEnumEntryChange {

    private String domain;

    public SemanticPropertyChange(TripleResourceChange tripleChange) {
        super(tripleChange);
    }

    public SemanticPropertyChange(SemanticPropertyChange other) {
        super(other);
        this.domain = other.domain;
    }

    public SemanticPropertyChange(SemanticResourceChange resourceChange) {
        super(resourceChange);
    }

    public SemanticPropertyChange(Resource resource, SemanticResourceChangeType changeType) {
        super(resource, changeType);
    }

    @Override
    public SemanticPropertyChange copy() {
        return new SemanticPropertyChange(this);
    }
}
