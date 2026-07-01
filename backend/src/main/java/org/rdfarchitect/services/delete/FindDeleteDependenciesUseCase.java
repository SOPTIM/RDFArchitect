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

import org.rdfarchitect.api.dto.delete.relations.AffectedResource;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FindDeleteDependenciesUseCase {

    /**
     * Finds the resources that would be affected by deleting the specified resources. The graph is
     * read once and all requested resources are analysed against that single snapshot.
     *
     * @param graphIdentifier The identifier of the graph.
     * @param uuids The resources to find dependencies for.
     * @return A map from each requested UUID (in input order) to an {@link AffectedResource}
     *     containing the affected resources and their relations.
     */
    Map<UUID, AffectedResource> getDeleteDependencies(
            GraphIdentifier graphIdentifier, List<UUID> uuids);
}
