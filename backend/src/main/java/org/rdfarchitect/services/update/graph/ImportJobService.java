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

@Service
@RequiredArgsConstructor
public class ImportJobService implements ImportJobUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ImportJobService.class);

    /** How long a finished job stays readable before it is dropped. */
    private static final Duration RETENTION = Duration.ofMinutes(10);

    private final ImportGraphsUseCase importGraphsUseCase;
    private final Map<UUID, ImportJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public UUID startImport(String datasetName, List<MultipartFile> files, List<String> graphUris) {
        dropExpiredJobs();

        var sessionId = SessionContext.getSessionId();
        var job = new ImportJob(UUID.randomUUID(), sessionId, datasetName);
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
        var started = false;
        try {
            // The servlet container deletes the temporary files of the request once its response
            // is sent, so the job has to take over the uploaded content before that happens.
            var ownedFiles = copyOf(files);
            executor.execute(() -> runImport(job, ownedFiles, graphUris, userSettings));
            started = true;
        } finally {
            if (!started) {
                // Taking the files over or handing them to the executor went wrong, so the job
                // never ran and must not keep blocking the session.
                jobs.remove(job.getId());
            }
        }

        logger.info(
                "Started import job {} for dataset \"{}\" with {} file(s).",
                job.getId(),
                datasetName,
                files.size());
        return job.getId();
    }

    @Override
    public Optional<ImportJobStatus> getStatus(String datasetName, UUID jobId) {
        dropExpiredJobs();
        return findJob(datasetName, jobId).map(ImportJob::status);
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

    private void runImport(
            ImportJob job,
            List<MultipartFile> files,
            List<String> graphUris,
            UserSettings userSettings) {
        // Both contexts live in thread locals that the request thread filled in, and the database
        // is looked up per session, so they have to be re-established on this thread.
        SessionContext.setSessionId(job.getSessionId());
        UserSettingsContext.set(userSettings);
        try {
            var result =
                    importGraphsUseCase.importGraphs(job.getDatasetName(), files, graphUris, job);
            job.complete(result);
            logger.info(
                    "Import job {} finished: {} graph(s) imported, {} file(s) failed.",
                    job.getId(),
                    result.importedGraphUris().size(),
                    result.failedFileNames().size());
        } catch (RuntimeException exception) {
            logger.error("Import job {} failed.", job.getId(), exception);
            job.fail(exception.getMessage());
        } finally {
            SessionContext.clear();
            UserSettingsContext.clear();
        }
    }

    private boolean hasRunningJob(String sessionId) {
        return jobs.values().stream()
                .anyMatch(job -> job.getSessionId().equals(sessionId) && job.finishedAt() == null);
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

    private List<MultipartFile> copyOf(List<MultipartFile> files) {
        var ownedFiles = new ArrayList<MultipartFile>(files.size());
        for (var file : files) {
            try {
                ownedFiles.add(InMemoryMultipartFile.copyOf(file));
            } catch (IOException exception) {
                throw new DataAccessException(
                        "Unable to read the uploaded file " + file.getOriginalFilename() + ".",
                        exception);
            }
        }
        return ownedFiles;
    }

    private void dropExpiredJobs() {
        var expiredBefore = Instant.now().minus(RETENTION);
        jobs.values()
                .removeIf(
                        job -> {
                            var finishedAt = job.finishedAt();
                            return finishedAt != null && finishedAt.isBefore(expiredBefore);
                        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
