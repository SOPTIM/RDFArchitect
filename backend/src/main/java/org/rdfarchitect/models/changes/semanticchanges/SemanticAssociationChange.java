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

/**
 * Object collecting all relevant information about a change to an association. Includes fields for mapping information, that can be provided by the user for data migration.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public final class SemanticAssociationChange extends SemanticResourceChange {

    private String range;

    // allows user to specify a SPARQL mapping to be executed when migrating data
    private String mapping;

    private boolean associationUsed;

    public SemanticAssociationChange(TripleResourceChange tripleChange) {
        super(tripleChange);
    }

    public SemanticAssociationChange(SemanticAttributeChange other) {
        super(other);
    }

    public SemanticAssociationChange(SemanticResourceChange resourceChange) {
        super(resourceChange);
    }

    public SemanticAssociationChange(Resource resource, SemanticResourceChangeType changeType) {
        super(resource, changeType);
    }

    @Override
    public SemanticAssociationChange copy() {
        return new SemanticAssociationChange(this);
    }
}
