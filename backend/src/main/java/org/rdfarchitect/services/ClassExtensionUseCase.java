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

import org.rdfarchitect.api.dto.ClassDTO;
import org.rdfarchitect.database.GraphIdentifier;

public interface ClassExtensionUseCase {
    /**
     * enables extension of a class by creating an abstract stub of the class and all its superclasses in the new graph
     *
     * @param graphIdentifier the dataset name and graph URI of the class to extend
     * @param classUUID       the uuid of the class to be extended
     * @param newGraph        the identifier of the new graph, where the class is to be extended
     * @return the uuid of the newly created class stub in the new graph
     */
    ClassDTO extendClass(GraphIdentifier graphIdentifier, String classUUID, GraphIdentifier newGraph);
}
