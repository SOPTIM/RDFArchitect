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

package org.rdfarchitect.services.update.packages;

import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.UUID;

public interface AddPackageUseCase {

    /**
     * Constructs a new package and adds it to the specified graph. Optional attribute
     * CIMSBelongsToCategory is set to null, the other attributes are supplied by this use case.
     *
     * @param graphIdentifier The graph URI and database name of the graph to add the class to.
     * @param packageDTO The DTO containing the package data.
     */
    UUID addPackage(GraphIdentifier graphIdentifier, PackageDTO packageDTO);
}
