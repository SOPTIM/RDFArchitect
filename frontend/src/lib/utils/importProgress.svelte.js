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

/** The phases an import runs through, in order. */
export const ImportPhase = {
    UPLOADING: "uploading",
    IMPORTING: "importing",
    FINISHED: "finished",
};

/** How a single file of the import ended up, mirroring the states of the backend. */
export const FileState = {
    PENDING: "PENDING",
    RUNNING: "RUNNING",
    IMPORTED: "IMPORTED",
    FAILED: "FAILED",
    SKIPPED: "SKIPPED",
};

/** How the job as a whole ended, mirroring the states of the backend. */
export const JobState = {
    RUNNING: "RUNNING",
    COMPLETED: "COMPLETED",
    CANCELLED: "CANCELLED",
    FAILED: "FAILED",
};

/**
 * Share of the overall progress each phase accounts for. The upload matters on a remote
 * backend and is instant on a local one, so it gets the smaller half.
 */
const PHASE_SHARE = { upload: 25, import: 75 };

/** How far into a file each step is, so that a large file does not look stuck. */
const STAGE_PROGRESS = { PARSING: 0.4, ANALYZING: 0.7, STORING: 0.9 };

const FINISHED_STATES = [
    FileState.IMPORTED,
    FileState.FAILED,
    FileState.SKIPPED,
];

const STAGE_LABELS = {
    PARSING: "reading",
    ANALYZING: "checking",
    STORING: "storing",
};

function clampPercent(value) {
    if (!Number.isFinite(value)) return 0;
    return Math.min(100, Math.max(0, value));
}

function fileProgress(file) {
    if (FINISHED_STATES.includes(file.state)) {
        return 1;
    }
    if (file.state === FileState.RUNNING) {
        return STAGE_PROGRESS[file.stage] ?? 0.2;
    }
    return 0;
}

/**
 * Progress of one import, shared between the dialog that polls the backend for it and the
 * panel that displays it. Also carries the cancellation: `cancel()` aborts an upload that
 * is still running and marks the progress as cancelling, the dialog then asks the backend
 * to stop the job.
 */
export class ImportProgress {
    phase = $state(ImportPhase.UPLOADING);
    uploadPercent = $state(0);
    /** @type {{index: number, fileName: string, sizeBytes: number, state: string, stage: string|null, graphUri: string|null}[]} */
    files = $state([]);
    jobState = $state(JobState.RUNNING);
    importedGraphUris = $state([]);
    failedImports = $state([]);
    /** @type {{fileName: string, undisplayableProperties: string[]}[]} */
    warnings = $state([]);
    /** Set when the import could not be started or ended unexpectedly. */
    errorMessage = $state(null);
    cancelling = $state(false);

    #controller = new AbortController();

    /** Signal for the upload of this import, aborted by {@link cancel}. */
    get signal() {
        return this.#controller.signal;
    }

    get finishedCount() {
        return this.files.filter(file => FINISHED_STATES.includes(file.state))
            .length;
    }

    get finished() {
        return this.phase === ImportPhase.FINISHED;
    }

    get failed() {
        return this.files.filter(file => file.state === FileState.FAILED);
    }

    /** Overall progress in percent, weighted by {@link PHASE_SHARE} and by file size. */
    get percent() {
        if (this.phase === ImportPhase.FINISHED) {
            return 100;
        }
        if (this.phase === ImportPhase.UPLOADING) {
            return clampPercent(
                (PHASE_SHARE.upload * clampPercent(this.uploadPercent)) / 100,
            );
        }
        return clampPercent(
            PHASE_SHARE.upload + PHASE_SHARE.import * this.#importedShare(),
        );
    }

    get statusText() {
        if (this.cancelling && !this.finished) {
            return "Cancelling the import…";
        }
        switch (this.phase) {
            case ImportPhase.UPLOADING:
                return "Uploading the files…";
            case ImportPhase.IMPORTING: {
                const running = this.files.find(
                    file => file.state === FileState.RUNNING,
                );
                if (!running) {
                    return "Preparing the import…";
                }
                const stage = STAGE_LABELS[running.stage];
                return stage
                    ? `Importing ${running.fileName} (${stage})…`
                    : `Importing ${running.fileName}…`;
            }
            default:
                return this.#finishedText();
        }
    }

    /** Applies a status the backend reported for the job. */
    apply(status) {
        this.phase =
            status.state === JobState.RUNNING
                ? ImportPhase.IMPORTING
                : ImportPhase.FINISHED;
        this.files = (status.files ?? []).map(file => ({
            index: file.index ?? 0,
            fileName: file.fileName ?? "",
            sizeBytes: file.sizeBytes ?? -1,
            state: file.state ?? FileState.PENDING,
            stage: file.stage ?? null,
            graphUri: file.graphUri ?? null,
        }));
        this.jobState = status.state ?? JobState.RUNNING;
        this.importedGraphUris = status.importedGraphUris ?? [];
        this.failedImports = status.failedImports ?? [];
        this.warnings = status.warnings ?? [];
        if (status.state === JobState.FAILED) {
            this.errorMessage =
                status.errorMessage ?? "The import ended unexpectedly.";
        }
    }

    uploadProgress(percent) {
        this.uploadPercent = clampPercent(percent);
    }

    /** The upload went through; the backend is importing from here on. */
    uploaded() {
        this.uploadPercent = 100;
        if (this.phase === ImportPhase.UPLOADING) {
            this.phase = ImportPhase.IMPORTING;
        }
    }

    /** Cancelled before the backend took the job over, so there is nothing left to report. */
    cancelled() {
        this.cancelling = true;
        this.jobState = JobState.CANCELLED;
        this.phase = ImportPhase.FINISHED;
    }

    /** The import could not be started or its status could not be read. */
    fail(message) {
        this.errorMessage = message;
        this.jobState = JobState.FAILED;
        this.phase = ImportPhase.FINISHED;
    }

    cancel() {
        if (this.cancelling) return;
        this.cancelling = true;
        this.#controller.abort();
    }

    #importedShare() {
        const total = this.files.reduce(
            (sum, file) => sum + this.#weightOf(file),
            0,
        );
        if (total === 0) {
            return 0;
        }
        const done = this.files.reduce(
            (sum, file) => sum + this.#weightOf(file) * fileProgress(file),
            0,
        );
        return done / total;
    }

    #weightOf(file) {
        return file.sizeBytes > 0 ? file.sizeBytes : 1;
    }

    #finishedText() {
        if (this.jobState === JobState.FAILED) {
            return "Import failed";
        }
        const imported = this.importedGraphUris.length;
        const schemas = `${imported} schema${imported === 1 ? "" : "s"}`;
        if (this.jobState === JobState.CANCELLED) {
            return imported === 0
                ? "Import cancelled"
                : `Import cancelled after ${schemas}`;
        }
        if (this.failedImports.length > 0) {
            return `Imported ${schemas}, ${this.failedImports.length} file(s) failed`;
        }
        return `Imported ${schemas}`;
    }
}
