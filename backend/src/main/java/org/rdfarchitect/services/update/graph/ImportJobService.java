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

import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.context.UserSettings;
import org.rdfarchitect.context.UserSettingsContext;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.exception.database.ResourceConflictException;
import org.rdfarchitect.services.update.graph.ImportOutcome.GraphsImported;
import org.rdfarchitect.services.update.graph.ImportOutcome.ProfilesScanned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class ImportJobService implements ImportJobUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ImportJobService.class);

    /** How long a finished job stays readable before it is dropped. */
    private static final Duration RETENTION = Duration.ofMinutes(10);

    /**
     * How long a job waiting for prefix decisions may go unpolled before it is taken as abandoned.
     * The dialog polls several times a second.
     */
    private static final Duration ABANDONED_AFTER = Duration.ofSeconds(60);

    private final ImportGraphsUseCase importGraphsUseCase;
    private final ImportXmiUseCase importXmiUseCase;
    private final Map<UUID, ImportJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public UUID startImport(String datasetName, List<MultipartFile> files, List<String> graphUris) {
        return start(datasetName, files, graphUris, ImportKind.GRAPH_IMPORT);
    }

    @Override
    public UUID startXmiScan(String datasetName, List<MultipartFile> files) {
        return start(datasetName, files, null, ImportKind.EXPORT_SCAN);
    }

    private UUID start(
            String datasetName,
            List<MultipartFile> files,
            List<String> graphUris,
            ImportKind kind) {
        dropExpiredJobs();

        var sessionId = SessionContext.getSessionId();
        cancelJobs(
                job -> job.getSessionId().equals(sessionId) && job.isAwaitingPrefixResolution(),
                "superseded by a new import of the same session");

        var job = new ImportJob(UUID.randomUUID(), sessionId, datasetName, kind, Instant.now());
        // Claiming the session and registering the job has to be one step, otherwise two parallel
        // starts both pass the guard and import into the same store with their own reservations.
        synchronized (jobs) {
            if (hasRunningJob(sessionId)) {
                throw new ResourceConflictException(
                        "An import is already running for this session.");
            }
            jobs.put(job.getId(), job);
        }

        var userSettings = UserSettingsContext.get();
        // The servlet container deletes the temporary files of the request once its response is
        // sent, so the job has to take over the uploaded content before that happens.
        var ownedFiles = takeOver(job, files);
        var started = false;
        try {
            executor.execute(() -> runJob(job, ownedFiles, graphUris, userSettings, kind));
            started = true;
        } finally {
            if (!started) {
                // Handing the files to the executor went wrong, so the job never ran and must
                // neither keep blocking the session nor leave its spooled files behind.
                jobs.remove(job.getId());
                SpooledMultipartFile.discard(ownedFiles);
            }
        }

        return job.getId();
    }

    @Override
    public Optional<ImportJobStatus> getStatus(String datasetName, UUID jobId) {
        dropExpiredJobs();
        return findJob(datasetName, jobId)
                .map(
                        job -> {
                            job.markPolled(Instant.now());
                            return job.status();
                        });
    }

    @Override
    public boolean resolvePrefixConflicts(
            String datasetName, UUID jobId, List<PrefixResolutionDTO> resolutions) {
        var job = findJob(datasetName, jobId).orElse(null);
        if (job == null) {
            return false;
        }
        var folded = ResolvedPrefixes.of(job.pendingPrefixComparison(), resolutions);
        if (!job.applyResolvedPrefixes(folded)) {
            throw new ResourceConflictException(
                    "The import is not waiting for namespace prefix decisions.");
        }
        logger.info(
                "Import job {} continues with {} namespace prefix decision(s).",
                jobId,
                resolutions.size());
        return true;
    }

    @Override
    public boolean cancel(String datasetName, UUID jobId) {
        var job = findJob(datasetName, jobId);
        job.ifPresent(
                cancelledJob -> {
                    cancelledJob.requestCancel();
                    logger.info("Cancellation requested for import job {}.", cancelledJob.getId());
                });
        return job.isPresent();
    }

    /**
     * Runs the import on its own thread. Every throwable ends the job: one left unfinished is never
     * reaped and holds the session's only import slot for as long as the session lives.
     */
    private void runJob(
            ImportJob job,
            List<MultipartFile> files,
            List<String> graphUris,
            UserSettings userSettings,
            ImportKind kind) {
        // Both contexts live in thread locals that the request thread filled in, and the database
        // is looked up per session, so they have to be re-established on this thread.
        SessionContext.setSessionId(job.getSessionId());
        UserSettingsContext.set(userSettings);
        try {
            var outcome =
                    switch (kind) {
                        case EXPORT_SCAN -> scanExports(job, files);
                        case GRAPH_IMPORT -> importGraphs(job, files, graphUris);
                    };
            job.complete(outcome);
            logger.info("Import job {} finished: {}.", job.getId(), describe(outcome));
        } catch (Throwable throwable) {
            logger.error("Import job {} failed.", job.getId(), throwable);
            job.fail(throwable.getMessage());
            if (throwable instanceof Error error) {
                throw error;
            }
        } finally {
            SpooledMultipartFile.discard(files);
            SessionContext.clear();
            UserSettingsContext.clear();
        }
    }

    /**
     * Reads the uploaded exports and reports what they hold. Nothing is stored, because which of
     * the profiles become schemas is a decision that has not been made at this point.
     */
    private ProfilesScanned scanExports(ImportJob job, List<MultipartFile> files) {
        return new ProfilesScanned(importXmiUseCase.scanProfiles(files, job));
    }

    private GraphsImported importGraphs(
            ImportJob job, List<MultipartFile> files, List<String> graphUris) {
        var result = importGraphsUseCase.importGraphs(job.getDatasetName(), files, graphUris, job);
        return new GraphsImported(
                result.importedGraphUris(), result.failedFileNames(), result.warnings());
    }

    /**
     * How a finished job reads in the log. The switch is exhaustive without a default, so a new
     * kind of outcome cannot be added without being accounted for here.
     */
    private static String describe(ImportOutcome outcome) {
        return switch (outcome) {
            case ProfilesScanned scanned -> scanned.profiles().size() + " profile(s) found";
            case GraphsImported imported ->
                    "%d graph(s) imported, %d file(s) failed"
                            .formatted(
                                    imported.graphUris().size(), imported.failedFileNames().size());
        };
    }

    private boolean hasRunningJob(String sessionId) {
        return jobs.values().stream()
                .anyMatch(
                        job -> job.getSessionId().equals(sessionId) && job.blocksFurtherImports());
    }

    /**
     * A job is only reachable through the session that started it and the dataset it imports into.
     */
    private Optional<ImportJob> findJob(String datasetName, UUID jobId) {
        var job = jobs.get(jobId);
        if (job == null
                || !job.getSessionId().equals(SessionContext.getSessionId())
                || !job.getDatasetName().equals(datasetName)) {
            return Optional.empty();
        }
        return Optional.of(job);
    }

    /** Takes the upload over, and frees the job's session slot again when that fails. */
    private List<MultipartFile> takeOver(ImportJob job, List<MultipartFile> files) {
        var taken = false;
        try {
            var ownedFiles = copyOf(files);
            taken = true;
            return ownedFiles;
        } finally {
            if (!taken) {
                jobs.remove(job.getId());
            }
        }
    }

    private List<MultipartFile> copyOf(List<MultipartFile> files) {
        var ownedFiles = new ArrayList<MultipartFile>(files.size());
        for (var file : files) {
            try {
                ownedFiles.add(SpooledMultipartFile.copyOf(file));
            } catch (IOException | RuntimeException exception) {
                // Whatever was spooled before the failure has nobody left to delete it.
                SpooledMultipartFile.discard(ownedFiles);
                throw new DataAccessException(
                        "Unable to read the uploaded file " + file.getOriginalFilename() + ".",
                        exception);
            }
        }
        return ownedFiles;
    }

    private void dropExpiredJobs() {
        var now = Instant.now();
        cancelJobs(
                job -> job.isAbandoned(now.minus(ABANDONED_AFTER)),
                "nobody is following the namespace prefix question any more");

        var expiredBefore = now.minus(RETENTION);
        jobs.values()
                .removeIf(
                        job -> {
                            var finishedAt = job.finishedAt();
                            return finishedAt != null && finishedAt.isBefore(expiredBefore);
                        });
    }

    private void cancelJobs(Predicate<ImportJob> shouldCancel, String reason) {
        for (var job : jobs.values()) {
            if (shouldCancel.test(job)) {
                logger.info("Cancelling import job {}: {}.", job.getId(), reason);
                job.requestCancel();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
