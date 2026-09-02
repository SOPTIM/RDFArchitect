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

public interface DeleteEdgeLayoutDataUseCase {

    /**
     * Deletes the edge diagram object and all its diagram object points identified by the given
     * identified object UUID. Glue points referenced by end points are deliberately left untouched,
     * as they are owned by the connected classes.
     *
     * @param graphIdentifier the identifier of the graph
     * @param diagramUUID the UUID of the diagram the edge belongs to
     * @param identifiedObjectUUID the UUID of the identified object the edge references
     */
    void deleteEdgeLayoutData(
            GraphIdentifier graphIdentifier, UUID diagramUUID, UUID identifiedObjectUUID);
}
