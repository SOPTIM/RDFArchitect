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

/**
 * Use case for adding a new class to a graph.
 */
public interface AddClassUseCase {

    /**
     * Constructs a new class and adds it to the specified graph.
     * All optional parameters are set to null. The label of the class is set to NewClass with an index number appended if a NewClass already exists in the graph.
     *
     * @param graphIdentifier The graph URI and database name of the graph to add the class to.
     * @param packageDTO      The Package to which the class will be added.
     * @param classURIPrefix  The prefix of the class to be added.
     * @param className       The name of the class to be added.
     */
    void addClass(GraphIdentifier graphIdentifier, PackageDTO packageDTO, String classURIPrefix, String className);
}
