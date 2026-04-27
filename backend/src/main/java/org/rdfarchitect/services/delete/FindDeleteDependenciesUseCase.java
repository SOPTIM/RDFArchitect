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

import java.util.UUID;

public interface FindDeleteDependenciesUseCase {

    /**
     * Finds the resources that would be affected by deleting a specified resource.
     *
     * @param graphIdentifier The identifier of the graph.
     * @param uuid The resource to find dependencies for.
     * @return An {@link AffectedResource} containing the affected resources with and their
     *     relations.
     */
    AffectedResource getDeleteDependencies(GraphIdentifier graphIdentifier, UUID uuid);
}
