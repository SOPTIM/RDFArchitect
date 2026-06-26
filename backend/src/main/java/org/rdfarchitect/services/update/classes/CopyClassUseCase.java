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

import org.rdfarchitect.api.dto.CopyClassResponseDTO;
import org.rdfarchitect.api.dto.PasteClassesRequestDTO;
import org.rdfarchitect.database.GraphIdentifier;

import java.util.List;

public interface CopyClassUseCase {

    /**
     * Constructs copies of the source classes described in the given paste request and adds them to
     * the target graph. Resolving the source graphs and the shared copy options is part of this use
     * case. All copies are written within a single write transaction so that the paste either
     * succeeds or fails as a whole.
     *
     * @param pasteRequest The paste request containing the source classes (with their origin
     *     dataset and graph) and the shared copy options, e.g. the target package and whether
     *     attributes and associations should be copied as well.
     * @param targetGraphIdentifier The graph URI and database name of the graph the new classes
     *     will be added in.
     * @return One response per source (in input order) containing the UUID and label of each newly
     *     created class.
     */
    List<CopyClassResponseDTO> copyClasses(
            PasteClassesRequestDTO pasteRequest, GraphIdentifier targetGraphIdentifier);
}
