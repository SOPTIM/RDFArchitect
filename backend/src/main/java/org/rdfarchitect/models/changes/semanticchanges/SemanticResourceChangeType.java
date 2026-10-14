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

/** Types of semantic changes that can occur to a resource. */
public enum SemanticResourceChangeType {
    ADD,
    ADDED_FROM_INHERITANCE,
    DELETE,
    DELETED_FROM_INHERITANCE,
    CHANGE,
    RENAME,
    MOVE,
    SPLIT,
    MERGE,
    // the property survives under a new predicate attached to a different class, via a
    // cross-class move/merge/split that didn't name this class as one of its targets - display
    // only, deliberately left out of SPARQL generation since the surviving RENAME/MOVE entry on
    // the target class already does the one rename/migrate that this property's data needs
    DELETED_FROM_MAPPING,
    // should only be created and used when creating the report
    INHERITS_CHANGE,
    INDIRECT_CHANGE,
}
