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

import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.ImportWarning;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs an import in the background so that the caller can follow its progress instead of waiting
 * for a single long request. A job belongs to the session that started it and is kept for a while
 * after it has finished, so that its final state can still be read.
 */
public interface ImportJobUseCase {

    /** State of the job as a whole. */
    enum JobState {
        RUNNING,
        /** Reading the namespace prefixes of the files to find out whether any of them collide. */
        SCANNING_PREFIXES,
        /**
         * Waiting for the caller to decide what to do with the contested namespace prefixes in
         * {@link ImportJobStatus#prefixComparison()}. Nothing has been imported at this point, and
         * nothing will be until the decisions arrive through {@link #resolvePrefixConflicts(String,
         * UUID, List)} or the job is cancelled.
         */
        AWAITING_PREFIX_RESOLUTION,
        COMPLETED,
        /** Stopped on request; the graphs imported up to that point are kept. */
        CANCELLED,
        /** Ended unexpectedly, leaving the remaining files unimported. */
        FAILED
    }

    /** State of a single file of the import. */
    enum FileState {
        PENDING,
        RUNNING,
        IMPORTED,
        FAILED,
        SKIPPED
    }

    /**
     * Progress of a single file.
     *
     * @param sizeBytes size of the file, or {@code -1} when a zip archive does not declare it
     * @param graphUri the uri the file was imported as, {@code null} until it was imported
     */
    record ImportFileStatus(
            int index, String fileName, long sizeBytes, FileState state, String graphUri) {}

    /**
     * Progress of an import job.
     *
     * @param files one entry per graph file, in the order the import processes them
     * @param prefixComparison the prefixes waiting to be decided on, empty unless the state is
     *     {@code AWAITING_PREFIX_RESOLUTION}
     * @param errorMessage why the job failed, {@code null} unless its state is {@code FAILED}
     */
    record ImportJobStatus(
            UUID jobId,
            String datasetName,
            JobState state,
            List<ImportFileStatus> files,
            List<String> importedGraphUris,
            List<String> failedImports,
            List<ImportWarning> warnings,
            List<PrefixComparison> prefixComparison,
            String errorMessage) {}

    /**
     * Starts an import and returns immediately.
     *
     * @return the id of the started job, to be passed to {@link #getStatus(UUID)}
     */
    UUID startImport(String datasetName, List<MultipartFile> files, List<String> graphUris);

    /** The current progress of the job, or empty if the session has no such job for the dataset. */
    Optional<ImportJobStatus> getStatus(String datasetName, UUID jobId);

    /**
     * Hands the job the decisions it is waiting for and lets it go on importing.
     *
     * @param resolutions one answer per contested binding; bindings left out keep the prefixes of
     *     the dataset as they are
     * @return {@code false} if the session has no such job for the dataset
     * @throws org.rdfarchitect.exception.database.ResourceConflictException if the job is not
     *     waiting for a decision
     */
    boolean resolvePrefixConflicts(
            String datasetName, UUID jobId, List<PrefixResolutionDTO> resolutions);

    /**
     * Asks the job to stop after the file it is currently importing. A job waiting for prefix
     * decisions stops right away, without having imported anything.
     *
     * @return {@code false} if the session has no such job for the dataset
     */
    boolean cancel(String datasetName, UUID jobId);
}
