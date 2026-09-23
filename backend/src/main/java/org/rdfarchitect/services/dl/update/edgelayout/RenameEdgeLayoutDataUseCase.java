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

package org.rdfarchitect.services.dl.update.edgelayout;

import org.rdfarchitect.database.GraphIdentifier;

import java.util.UUID;

public interface RenameEdgeLayoutDataUseCase {

    /**
     * Renames an existing edge diagram object across all diagrams it appears in within the graph.
     *
     * @param graphIdentifier the identifier of the graph the edge belongs to
     * @param identifiedObjectUUID the UUID of the identified object the edge references (the
     *     association for associations, the sub class for inheritances)
     * @param newName the new name to set on the edge diagram object
     */
    void renameEdge(GraphIdentifier graphIdentifier, UUID identifiedObjectUUID, String newName);
}
