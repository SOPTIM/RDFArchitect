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

import lombok.Getter;

import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.ImportResult;
import org.rdfarchitect.services.update.graph.ImportJobUseCase.FileState;
import org.rdfarchitect.services.update.graph.ImportJobUseCase.ImportFileStatus;
import org.rdfarchitect.services.update.graph.ImportJobUseCase.ImportJobStatus;
import org.rdfarchitect.services.update.graph.ImportJobUseCase.JobState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The state of one background import. Written by the thread running the import through the {@link
 * ImportProgressListener} it implements and read by the requests polling for progress, so every
 * access is synchronized and {@link #status()} hands out an immutable copy.
 */
class ImportJob implements ImportProgressListener {

    @Getter private final UUID id;
    @Getter private final String sessionId;
    @Getter private final String datasetName;

    private JobState state = JobState.RUNNING;
    private final List<FileProgress> files = new ArrayList<>();
    private List<String> importedGraphUris = List.of();
    private List<String> failedImports = List.of();
    private List<ImportGraphsUseCase.ImportWarning> warnings = List.of();
    private String errorMessage;
    private boolean cancelRequested;
    private Instant finishedAt;

    ImportJob(UUID id, String sessionId, String datasetName) {
        this.id = id;
        this.sessionId = sessionId;
        this.datasetName = datasetName;
    }

    // -------------------------------------------------------------------------
    // ImportProgressListener
    // -------------------------------------------------------------------------

    @Override
    public synchronized void planned(List<PlannedImport> plannedImports) {
        files.clear();
        for (var plannedImport : plannedImports) {
            files.add(new FileProgress(plannedImport));
        }
    }

    @Override
    public synchronized void started(int index) {
        fileAt(index).state = FileState.RUNNING;
    }

    @Override
    public synchronized void stage(int index, Stage stage) {
        var file = fileAt(index);
        file.state = FileState.RUNNING;
        file.stage = stage;
    }

    @Override
    public synchronized void finished(int index, Outcome outcome, String graphUri) {
        var file = fileAt(index);
        file.state =
                switch (outcome) {
                    case IMPORTED -> FileState.IMPORTED;
                    case FAILED -> FileState.FAILED;
                    case SKIPPED -> FileState.SKIPPED;
                };
        file.stage = null;
        file.graphUri = graphUri;
    }

    @Override
    public synchronized boolean isCancelled() {
        return cancelRequested;
    }

    // -------------------------------------------------------------------------
    // Job lifecycle
    // -------------------------------------------------------------------------

    synchronized void requestCancel() {
        cancelRequested = true;
    }

    synchronized void complete(ImportResult result) {
        importedGraphUris = List.copyOf(result.importedGraphUris());
        failedImports = List.copyOf(result.failedFileNames());
        warnings = List.copyOf(result.warnings());
        state = cancelRequested ? JobState.CANCELLED : JobState.COMPLETED;
        finishedAt = Instant.now();
    }

    synchronized void fail(String message) {
        errorMessage = message;
        state = JobState.FAILED;
        finishedAt = Instant.now();
    }

    /** When the job stopped running, or empty while it still is. */
    synchronized Instant finishedAt() {
        return finishedAt;
    }

    synchronized ImportJobStatus status() {
        return new ImportJobStatus(
                id,
                datasetName,
                state,
                files.stream().map(FileProgress::toStatus).toList(),
                importedGraphUris,
                failedImports,
                warnings,
                errorMessage);
    }

    private FileProgress fileAt(int index) {
        if (index < 0 || index >= files.size()) {
            throw new IllegalArgumentException("No planned file at index " + index);
        }
        return files.get(index);
    }

    /** Mutable progress of a single file; only touched while holding the job's monitor. */
    private static final class FileProgress {

        private final PlannedImport plannedImport;
        private FileState state = FileState.PENDING;
        private Stage stage;
        private String graphUri;

        private FileProgress(PlannedImport plannedImport) {
            this.plannedImport = plannedImport;
        }

        private ImportFileStatus toStatus() {
            return new ImportFileStatus(
                    plannedImport.index(),
                    plannedImport.fileName(),
                    plannedImport.sizeBytes(),
                    state,
                    stage,
                    graphUri);
        }
    }
}
