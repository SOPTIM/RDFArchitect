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

package org.rdfarchitect.services.dl.update.packagelayout;

import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.UUID;

public interface CreatePackageLayoutDataUseCase {

    /**
     * Creates the diagram layout data for a newly created package
     *
     * @param graphIdentifier the identifier of the graph
     * @param packageDTO the DTO used for creating the new package
     * @param newPackageUUID the UUID of the newly created package
     */
    void createPackageLayoutData(
            GraphIdentifier graphIdentifier, PackageDTO packageDTO, UUID newPackageUUID);
}
