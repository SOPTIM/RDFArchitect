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

import org.rdfarchitect.services.update.graph.ImportJobUseCase.FileState;
import org.rdfarchitect.services.update.graph.ImportJobUseCase.ImportFileStatus;
import org.rdfarchitect.services.update.graph.ImportJobUseCase.ImportJobStatus;
import org.rdfarchitect.services.update.graph.ImportJobUseCase.JobState;
import org.rdfarchitect.services.update.graph.ImportOutcome.GraphsImported;
import org.rdfarchitect.services.update.graph.ImportOutcome.ProfilesScanned;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The state of one background import. Written by the thread running the import through the {@link
 * GraphImportListener} it implements and read by the requests polling for progress, so every access
 * is synchronized and {@link #status()} hands out an immutable copy.
 *
 * <p>What the job produces is one {@link ImportOutcome}, whichever kind its {@link ImportKind}
 * calls for. The job itself is the same either way: a plan of files, a progress per file, and the
 * rules about sessions, cancelling and being given up on.
 */
class ImportJob implements GraphImportListener {

    @Getter private final UUID id;
    @Getter private final String sessionId;
    @Getter private final String datasetName;
    private final ImportKind kind;

    private JobState state = JobState.RUNNING;
    private final List<FileProgress> files = new ArrayList<>();
    private ImportOutcome outcome;
    private String errorMessage;
    private boolean cancelRequested;
    private boolean startedAFile;
    private Instant finishedAt;
    private List<PrefixComparison> prefixComparison = List.of();
    private ResolvedPrefixes resolvedPrefixes;
    private Instant lastPolledAt;

    ImportJob(UUID id, String sessionId, String datasetName, ImportKind kind, Instant startedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.datasetName = datasetName;
        this.kind = kind;
        this.lastPolledAt = startedAt;
    }

    // -------------------------------------------------------------------------
    // GraphImportListener
    // -------------------------------------------------------------------------

    @Override
    public synchronized void planned(List<PlannedImport> plannedImports) {
        files.clear();
        for (var plannedImport : plannedImports) {
            files.add(new FileProgress(plannedImport));
        }
    }

    @Override
    public synchronized void scanningPrefixes() {
        state = JobState.SCANNING_PREFIXES;
    }

    @Override
    public synchronized ResolvedPrefixes awaitResolvedPrefixes(List<PrefixComparison> comparison) {
        if (comparison.isEmpty()) {
            return ResolvedPrefixes.none();
        }
        prefixComparison = List.copyOf(comparison);
        state = JobState.AWAITING_PREFIX_RESOLUTION;
        while (resolvedPrefixes == null && !cancelRequested) {
            try {
                wait();
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                cancelRequested = true;
            }
        }
        prefixComparison = List.of();
        state = JobState.RUNNING;
        return cancelRequested ? ResolvedPrefixes.none() : resolvedPrefixes;
    }

    @Override
    public synchronized void started(int index) {
        state = JobState.RUNNING;
        startedAFile = true;
        fileAt(index).state = FileState.RUNNING;
    }

    @Override
    public synchronized void finished(int index, Outcome outcome, String graphUri) {
        var file = fileAt(index);
        file.state =
                switch (outcome) {
                    case IMPORTED -> FileState.IMPORTED;
                    case SCANNED -> FileState.SCANNED;
                    case FAILED -> FileState.FAILED;
                    case SKIPPED -> FileState.SKIPPED;
                };
        file.graphUri = graphUri;
    }

    @Override
    public synchronized boolean isCancelled() {
        return cancelRequested;
    }

    // -------------------------------------------------------------------------
    // Job lifecycle
    // -------------------------------------------------------------------------

    /** Asks the job to stop, and wakes it if it is waiting on a prefix decision to do so. */
    synchronized void requestCancel() {
        cancelRequested = true;
        notifyAll();
    }

    /**
     * Whether an import of the same session would have to wait for this one. A cancellation only
     * takes effect between files, so a job that has begun on one may still be writing; one that
     * never got that far writes nothing from here on.
     */
    synchronized boolean blocksFurtherImports() {
        return finishedAt == null && (!cancelRequested || startedAFile);
    }

    /**
     * Hands over the decisions the import is waiting for.
     *
     * @return {@code false} if the job is not waiting for any
     */
    synchronized boolean applyResolvedPrefixes(ResolvedPrefixes resolved) {
        if (state != JobState.AWAITING_PREFIX_RESOLUTION) {
            return false;
        }
        resolvedPrefixes = resolved;
        notifyAll();
        return true;
    }

    /** Records that someone is still following this job; see {@link #isAbandoned(Instant)}. */
    synchronized void markPolled(Instant polledAt) {
        lastPolledAt = polledAt;
    }

    /**
     * Whether the job waits for decisions that nobody is going to make any more, because the caller
     * stopped following it. Such a job would otherwise hold its files and its session for good.
     */
    synchronized boolean isAbandoned(Instant abandonedBefore) {
        return isAwaitingPrefixResolution() && lastPolledAt.isBefore(abandonedBefore);
    }

    /** Whether the job is waiting for decisions, and has therefore imported nothing so far. */
    synchronized boolean isAwaitingPrefixResolution() {
        return state == JobState.AWAITING_PREFIX_RESOLUTION;
    }

    /** The comparison the job wants decided, or empty when it is waiting for none. */
    synchronized List<PrefixComparison> pendingPrefixComparison() {
        return prefixComparison;
    }

    synchronized void complete(ImportOutcome producedOutcome) {
        outcome = producedOutcome;
        state = cancelRequested ? JobState.CANCELLED : JobState.COMPLETED;
        finishedAt = Instant.now();
    }

    synchronized void fail(String message) {
        // The import stopped where it stood: the file it was on never finished and the ones behind
        // it never started. Left as they are, the dialog keeps spinning on a file that is gone and
        // its file count never reaches the end.
        for (var file : files) {
            if (file.state == FileState.RUNNING) {
                file.state = FileState.FAILED;
            } else if (file.state == FileState.PENDING) {
                file.state = FileState.SKIPPED;
            }
        }
        // The outcome died with the exception, but every file that made it through reported how it
        // ended, so what the job did manage to do is still recoverable from the progress. A scan
        // recovers nothing, having stored nothing in the first place.
        outcome =
                switch (kind) {
                    case GRAPH_IMPORT ->
                            new GraphsImported(
                                    fileNamesWhere(
                                            file ->
                                                    file.state == FileState.IMPORTED
                                                            && file.graphUri != null,
                                            file -> file.graphUri),
                                    fileNamesWhere(
                                            file -> file.state == FileState.FAILED,
                                            file -> file.plannedImport.fileName()),
                                    List.of());
                    case EXPORT_SCAN -> ProfilesScanned.nothing();
                };
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
                prefixComparison,
                outcome,
                errorMessage);
    }

    private List<String> fileNamesWhere(
            Predicate<FileProgress> matches, Function<FileProgress, String> name) {
        return files.stream().filter(matches).map(name).toList();
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
                    graphUri);
        }
    }
}
