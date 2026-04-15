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

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.rdfarchitect.models.changes.triplechanges.TripleResourceChange;

import java.util.ArrayList;
import java.util.List;

/**
 * Object collecting all relevant information about a change to an enum entry. Allows setting a replacement value in case the enum entry was deleted.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public final class SemanticEnumEntryChange extends SemanticResourceChange {

    private String replacementValue;

    // list of other enum entries that are still available in the enum after this one was removed
    @Builder.Default
    private List<String> allowedValues = new ArrayList<>();

    public SemanticEnumEntryChange(TripleResourceChange tripleChange) {
        super(tripleChange);
        this.allowedValues = new ArrayList<>();
    }

    public SemanticEnumEntryChange(SemanticEnumEntryChange other) {
        super(other);
        this.replacementValue = other.getReplacementValue();
        this.allowedValues = other.getAllowedValues();
    }

    @Override
    public SemanticEnumEntryChange copy() {
        return new SemanticEnumEntryChange(this);
    }
}
