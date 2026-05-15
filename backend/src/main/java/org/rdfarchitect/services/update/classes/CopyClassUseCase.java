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

import org.rdfarchitect.api.dto.CopyClassRequestDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.UUID;

public interface CopyClassUseCase {

    /**
     * Constructs a class based on the given class and adds it to the specified graph.
     *
     * @param graphIdentifier The graph URI and database name of the graph of the class that will be
     *     copied.
     * @param classUUID The UUID of the class that will be copied.
     * @param targetGraphIdentifier The graph URI and database name of the graph the new class will
     *     be added in.
     * @param copyClassRequestDTO The DTO that contains the information about how the class should
     *     be copied, e.g. if attributes and associations should be copied as well.
     * @return The UUID of the newly created class.
     */
    UUID copyClass(
            GraphIdentifier graphIdentifier,
            UUID classUUID,
            GraphIdentifier targetGraphIdentifier,
            CopyClassRequestDTO copyClassRequestDTO);
}
