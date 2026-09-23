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
import org.rdfarchitect.models.cim.relations.model.properties.CIMAssociationUtils.AssociationEndUuids;

import java.util.Set;
import java.util.UUID;

public interface SyncAssociationEdgesUseCase {

    /**
     * Diffs a class's outgoing associations before and after a save, and keeps the DL's edge
     * diagram objects and labels in sync: removed associations lose their edge and any labels
     * anchored to either of their ends, newly appeared ones get a fresh edge. Associations kept
     * from before need no action — their UUID, and therefore their edge diagram object, stays valid
     * regardless of multiplicity/label changes.
     *
     * @param graphIdentifier the identifier of the graph the class belongs to
     * @param classUUID the UUID of the class whose outgoing associations are diffed
     * @param associationsBefore the end UUIDs of the class's associations as they were before the
     *     save, as fetched by {@code CIMAssociationUtils.associationEndUuidsForClass}
     */
    void syncAssociationEdges(
            GraphIdentifier graphIdentifier,
            UUID classUUID,
            Set<AssociationEndUuids> associationsBefore);
}
