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
        /** Read and reported on, but not stored. */
        SCANNED,
        FAILED,
        SKIPPED
    }

    /**
     * Progress of a single file.
     *
     * @param sizeBytes size of the file, or {@code -1} when a zip archive does not declare it
     * @param graphUri the uri the file was imported as, {@code null} until it was imported
     * @param done how much of this step is behind it, {@code 0} for a step that cannot say
     * @param total how much there is in all, {@code 0} for a step that cannot say. Only one long
     *     pass over a file can; everything else is known by being finished or not
     */
    record ImportFileStatus(
            int index,
            String fileName,
            long sizeBytes,
            FileState state,
            String graphUri,
            long done,
            long total) {}

    /**
     * Progress of an import job.
     *
     * @param files one entry per graph file, in the order the import processes them
     * @param prefixComparison the prefixes waiting to be decided on, empty unless the state is
     *     {@code AWAITING_PREFIX_RESOLUTION}
     * @param outcome what the job produced, {@code null} while it is still running. Which kind it
     *     is says what the job was started to do
     * @param errorMessage why the job failed, {@code null} unless its state is {@code FAILED}
     */
    record ImportJobStatus(
            UUID jobId,
            String datasetName,
            JobState state,
            List<ImportFileStatus> files,
            List<PrefixComparison> prefixComparison,
            ImportOutcome outcome,
            String errorMessage) {}

    /**
     * Starts an import and returns immediately.
     *
     * @return the id of the started job, to be passed to {@link #getStatus(String, UUID)}
     */
    UUID startImport(String datasetName, List<MultipartFile> files, List<String> graphUris);

    /**
     * Starts reading Enterprise Architect exports and returns immediately. The job reports the
     * packages the exports hold in an {@link ImportOutcome.PackagesScanned} outcome and stores
     * nothing.
     *
     * @return the id of the started job, to be passed to {@link #getStatus(String, UUID)}
     */
    UUID startXmiScan(String datasetName, List<MultipartFile> files);

    /**
     * Starts building schemas from the packages chosen out of the exports a previous {@link
     * #startXmiScan} read, and returns immediately. The exports are the ones that read kept, so
     * nothing has to be uploaded again — and they are handed over, so one read can be chosen from
     * once.
     *
     * @param readJobId the job that read the exports
     * @param selection the packages to build, in the order they are to be built
     * @param diagramIds the class diagrams to keep, by {@code xmi:id}
     * @return the id of the started job, or empty if the session has no such read for the dataset
     * @throws org.rdfarchitect.exception.database.ResourceConflictException if the exports of that
     *     read are no longer available
     */
    Optional<UUID> startPackageImport(
            String datasetName,
            UUID readJobId,
            List<SelectedPackageDTO> selection,
            List<String> diagramIds);

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
