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

package org.rdfarchitect.services.versioncontrol;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.changelog.ChangeLogEntry;

public interface RedoUseCase {

    /**
     * Reapplies the most recently undone named commit for the given graph, restoring all associated
     * graph participants to the state before the undo.
     *
     * @param graphIdentifier the identifier of the graph to operate on
     * @return the changelog entry that was redone, or {@code null} if no redo was available
     */
    ChangeLogEntry redo(GraphIdentifier graphIdentifier);
}
