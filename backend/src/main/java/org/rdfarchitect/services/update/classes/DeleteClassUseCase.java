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

import org.rdfarchitect.database.GraphIdentifier;

/**
 * Use case for deleting a class from the graph.
 */
public interface DeleteClassUseCase {

    /**
     * Deletes the class with the given name from the graph.
     *
     * @param graphIdentifier Graph URI and dataset name of the class to be deleted
     * @param classUUID       UUID of the class to be deleted
     */
    void deleteClass(GraphIdentifier graphIdentifier, String classUUID);
}
