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

import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.UUID;

public interface CreateClassLayoutDataUseCase {

    /**
     * Creates the diagram layout data for a newly created class
     *
     * @param graphIdentifier the identifier of the graph
     * @param packageDTO      the DTO used for creating the new class
     * @param className       the name of the newly created class
     * @param classUUID       the UUID of the newly created class
     */
    void createClassLayoutData(GraphIdentifier graphIdentifier, PackageDTO packageDTO, String className, UUID classUUID);
}
