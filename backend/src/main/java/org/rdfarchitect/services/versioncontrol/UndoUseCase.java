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

public interface UndoUseCase {

    /**
     * Undoes the most recent named commit for the given graph, reverting all associated graph
     * participants to their previous state.
     *
     * @param graphIdentifier the identifier of the graph to operate on
     * @return the changelog entry that was undone, or {@code null} if no undo was available
     */
    ChangeLogEntry undo(GraphIdentifier graphIdentifier);
}
