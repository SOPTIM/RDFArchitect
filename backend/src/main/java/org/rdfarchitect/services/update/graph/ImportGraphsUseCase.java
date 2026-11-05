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
     * filenames of files that failed to import and any non-fatal warnings raised while importing.
     *
     * @param importedGraphUris the URIs of successfully imported graphs
     * @param failedFileNames the original filenames of files that could not be imported
     * @param warnings non-fatal warnings about content that was imported but cannot be displayed
     */
    record ImportResult(
            List<String> importedGraphUris,
            List<String> failedFileNames,
            List<ImportWarning> warnings) {
        public ImportResult() {
            this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    /**
     * One thing an import could not carry over as its file states it.
     *
     * <p>Reported as two fields rather than one sentence because an import has several reasons to
     * report something and a caller groups by them: a property stored but not displayable, a type
     * the export refers to without containing, a class declared twice, a header field under a name
     * no ontology field matches.
     *
     * @param resource what it was, named as the file names it
     * @param reason why it could not be carried over as stated
     */
    record UncarriedItem(String resource, String reason) {}

    /**
     * What one file or profile of an import could not carry over, the rest of it having been
     * imported as the file has it.
     *
     * @param fileName the file, or the profile of an export, the items were found in
     * @param graphUri the schema that was written from it, which is what the items belong to
     */
    record ImportWarning(String fileName, String graphUri, List<UncarriedItem> items) {}

    /**
     * Imports multiple graphs into the specified dataset.
     *
     * @param datasetName The name of the dataset where the graphs will be imported.
     * @param files The list of files containing the graph data to be imported. A zip archive is
     *     unpacked and each graph file in it imported on its own.
     * @param graphUris The list of graph URIs corresponding to each file.
     * @param listener Receives the progress of the import, answers the namespace prefix question
     *     and can cancel it; pass {@link GraphImportListener#NOOP} when none of that is needed.
     * @return A record storing both the successfully imported graph URIs and the filenames of files
     *     that failed to import.
     */
    ImportResult importGraphs(
            String datasetName,
            List<MultipartFile> files,
            List<String> graphUris,
            GraphImportListener listener);
}
