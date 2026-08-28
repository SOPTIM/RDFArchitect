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

package org.rdfarchitect.services;

import org.rdfarchitect.api.dto.ClassExtensionResultDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.List;

public interface ClassExtensionUseCase {

    /**
     * enables extension of classes by creating an abstract stub of each of them in another graph of
     * the dataset. Classes that are already defined in the new graph are left untouched.
     *
     * @param datasetName the dataset the classes belong to
     * @param classUUIDs the uuids of the classes to be extended, either the ones they carry in a
     *     graph or the ones of their merged classes
     * @param newGraph the identifier of the new graph, where the classes are to be extended
     * @param withInheritance whether the superclasses are stubbed as well. Without them a
     *     superclass stays a referenced only resource in the new graph unless it is defined there.
     * @return one result per requested class, in the order they were requested
     */
    List<ClassExtensionResultDTO> extendClasses(
            String datasetName,
            List<String> classUUIDs,
            GraphIdentifier newGraph,
            boolean withInheritance);
}
