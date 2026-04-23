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

import java.util.ArrayList;
import java.util.List;

public interface ImportGraphsUseCase {

    /**
     * Result of a graph import operation, separating successfully imported graph URIs from the
     * filenames of files that failed to import.
     *
     * @param importedGraphUris the URIs of successfully imported graphs
     * @param failedFileNames the original filenames of files that could not be imported
     */
    record ImportResult(List<String> importedGraphUris, List<String> failedFileNames) {
        public ImportResult() {
            this(new ArrayList<>(), new ArrayList<>());
        }
    }

    /**
     * Imports multiple graphs into the specified dataset.
     *
     * @param datasetName The name of the dataset where the graphs will be imported.
     * @param files The list of files containing the graph data to be imported.
     * @param graphUris The list of graph URIs corresponding to each file.
     * @return A record storing both the successfully imported graph URIs and the filenames of files
     *     that failed to import.
     */
    ImportResult importGraphs(
            String datasetName, List<MultipartFile> files, List<String> graphUris);
}
