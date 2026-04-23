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

import org.rdfarchitect.models.changes.triplechanges.TriplePackageChange;

import java.util.ArrayList;
import java.util.List;

/**
 * Object collecting all changes made to a package. This mainly consists of the changes made to the
 * classes contained in the package.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public final class SemanticPackageChange extends SemanticResourceChange {
    List<SemanticClassChange> classChanges = new ArrayList<>();

    public SemanticPackageChange(TriplePackageChange tripleChange) {
        super(tripleChange);
    }

    public SemanticPackageChange(SemanticPackageChange other) {
        super(other);
        this.classChanges = other.getClassChanges();
    }

    @Override
    public SemanticPackageChange copy() {
        return new SemanticPackageChange(this);
    }
}
