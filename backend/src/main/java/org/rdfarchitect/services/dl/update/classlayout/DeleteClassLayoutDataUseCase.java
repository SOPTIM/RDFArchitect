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

package org.rdfarchitect.services.dl.update.classlayout;

import org.rdfarchitect.database.GraphIdentifier;

import java.util.UUID;

public interface DeleteClassLayoutDataUseCase {

    /**
     * Deletes all diagram layout data associated with the diagram object and diagram object point
     * identified by the given class UUID.
     *
     * @param graphIdentifier the identifier of the graph
     * @param classUUID the UUID of the class identifying the diagram object
     */
    void deleteClassLayoutData(GraphIdentifier graphIdentifier, UUID classUUID);
}
