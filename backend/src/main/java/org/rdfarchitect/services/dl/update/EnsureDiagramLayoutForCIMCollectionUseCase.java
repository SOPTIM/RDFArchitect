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

package org.rdfarchitect.services.dl.update;

import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;

import java.util.UUID;

public interface EnsureDiagramLayoutForCIMCollectionUseCase {

    /**
     * Ensures that the necessary diagram layout data exists for the {@link CIMCollection} provided
     *
     * @param graphIdentifier the identifier of the graph
     * @param diagramUUID     the UUID of the package or diagram to be rendered
     * @param cimCollection   the CIMCollection containing all packages, classes and enums
     */
    void ensureDiagramLayoutExists(GraphIdentifier graphIdentifier, UUID diagramUUID, CIMCollection cimCollection);

    void ensureDiagramLayoutExists(String datasetName, UUID diagramUUID, CIMCollection cimCollection);
}
