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

import java.util.Set;
import java.util.UUID;

public interface SyncEdgeDeletedUseCase {

    /**
     * Deletes the edge diagram object for a removed inheritance or association, from the package
     * diagram and (once implemented) the cross-profile diagram. Additionally drops any labels
     * anchored to {@code labelAnchorUuids} — an association's own and inverse UUID — since those
     * become orphaned together with the edge. Inheritance edges have no labels, so callers pass an
     * empty set for that case.
     *
     * @param graphIdentifier the identifier of the graph the edge belonged to
     * @param identifiedObjectUUID the UUID of the identified object the edge referenced
     * @param labelAnchorUuids the UUIDs whose labels are dropped alongside the edge, or an empty
     *     set if the edge carries no labels
     */
    void syncEdgeDeleted(
            GraphIdentifier graphIdentifier, UUID identifiedObjectUUID, Set<UUID> labelAnchorUuids);
}
