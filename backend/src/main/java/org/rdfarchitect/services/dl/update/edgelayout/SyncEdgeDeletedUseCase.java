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

import java.util.UUID;
import org.rdfarchitect.database.GraphIdentifier;

public interface SyncEdgeDeletedUseCase {

    /**
     * Deletes the edge diagram object and all its diagram object points across every diagram it
     * appears in within the graph (package and custom diagrams share the same diagram layout
     * model).
     *
     * @param graphIdentifier the identifier of the graph the edge belongs to
     * @param identifiedObjectUUID the UUID of the identified object the edge references (the
     *     association for associations, the sub class for inheritances)
     */
    void syncEdgeDeleted(GraphIdentifier graphIdentifier, UUID identifiedObjectUUID);
}
