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

package org.rdfarchitect.services.update.graph;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImportGraphsUseCase {

    /**
     * Imports multiple graphs into the specified dataset.
     *
     * @param datasetName The name of the dataset where the graphs will be imported.
     * @param files       The list of files containing the graph data to be imported.
     * @param graphUris   The list of graph URIs corresponding to each file.
     *
     * @return A list of graph URIs that were successfully imported.
     */
    List<String> importGraphs(String datasetName, List<MultipartFile> files, List<String> graphUris);
}
