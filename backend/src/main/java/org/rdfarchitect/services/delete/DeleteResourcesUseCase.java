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

package org.rdfarchitect.services.delete;

import org.rdfarchitect.api.dto.delete.ResourceDeleteRequest;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.List;

public interface DeleteResourcesUseCase {

    /**
     * Executes a list of delete requests on a specified graph.
     * @param graphIdentifier The identifier of the graph where the change occurred.
     * @param deleteRequests The List of deleteRequests.
     */
    void executeDeleteRequests(GraphIdentifier graphIdentifier, List<ResourceDeleteRequest> deleteRequests);
}
