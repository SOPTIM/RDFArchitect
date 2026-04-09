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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rdfarchitect.models.changes.triplechanges.TriplePropertyChange;

/**
 * Represents a single change to a resource and stores the old and new values of a triple.
 * Includes a type to indicate what semantic meaning the change has to the resource.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticFieldChange {

    private SemanticFieldChangeType semanticFieldChangeType;

    private String from;

    private String to;

    public SemanticFieldChange(SemanticFieldChange semanticFieldChange) {
        this.semanticFieldChangeType = semanticFieldChange.semanticFieldChangeType;
        this.from = semanticFieldChange.from;
        this.to = semanticFieldChange.to;
    }

    public SemanticFieldChange(TriplePropertyChange triplePropertyChange) {
        this.from = triplePropertyChange.getFrom();
        this.to = triplePropertyChange.getTo();
    }
}
