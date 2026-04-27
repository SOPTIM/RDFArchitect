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

package org.rdfarchitect.services.update.classes;

import org.rdfarchitect.api.dto.packages.PackageDTO;
import org.rdfarchitect.database.GraphIdentifier;

public interface CopyClassUseCase {

    /**
     * Constructs a class based on the given class and adds it to the specified graph.
     *
     * @param graphIdentifier       The graph URI and database name of the graph of the class that will be copied.
     * @param classUUID             The UUID of the class that will be copied.
     * @param targetGraphIdentifier The graph URI and database name of the graph the new class will be added in.
     * @param targetPackageDTO      The package the new class will be added in.
     * @param copyAbstract          If true, only the class itself will be copied, if false, all attributes will also be copied.
     */
    void copyClass(GraphIdentifier graphIdentifier, String classUUID, GraphIdentifier targetGraphIdentifier, PackageDTO targetPackageDTO, boolean copyAbstract);
}
