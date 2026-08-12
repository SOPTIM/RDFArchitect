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

/** The stages a documentation export runs through, in order. */
export const ExportStage = {
    DOCUMENT: "document",
    DIAGRAMS: "diagrams",
    ARCHIVE: "archive",
    DONE: "done",
};

/** How a single package diagram ended up in the export. */
export const PackageStatus = {
    PENDING: "pending",
    RENDERING: "rendering",
    DONE: "done",
    /** The package holds no classes, so there is no diagram to render. */
    EMPTY: "empty",
    FAILED: "failed",
};

/**
 * Share of the overall progress each stage accounts for. Rendering the diagrams
 * dominates the export by far - it mounts a diagram per package - so it gets the
 * bulk, and the two stages around it stay visible enough to show that something
 * is happening before the first and after the last package.
 */
const STAGE_SHARE = { document: 10, diagrams: 80, archive: 10 };

const FINISHED_STATUSES = [
    PackageStatus.DONE,
    PackageStatus.EMPTY,
    PackageStatus.FAILED,
];

function clampPercent(value) {
    if (!Number.isFinite(value)) return 0;
    return Math.min(100, Math.max(0, value));
}

/**
 * Progress of one documentation export, shared between the export routine that
 * reports into it and the panel that displays it. Also carries the cancellation:
 * `cancel()` aborts the requests that are in flight and asks the export loop to
 * stop at the next package.
 */
export class ExportProgress {
    stage = $state(ExportStage.DOCUMENT);
    /** @type {{uuid: string, label: string, status: string}[]} */
    packages = $state([]);
    archivePercent = $state(0);
    cancelled = $state(false);

    #controller = new AbortController();

    /** Signal for every request of this export, aborted by {@link cancel}. */
    get signal() {
        return this.#controller.signal;
    }

    get finishedCount() {
        return this.packages.filter(pkg =>
            FINISHED_STATUSES.includes(pkg.status),
        ).length;
    }

    get failed() {
        return this.packages.filter(pkg => pkg.status === PackageStatus.FAILED);
    }

    /** Overall progress in percent, weighted by {@link STAGE_SHARE}. */
    get percent() {
        switch (this.stage) {
            case ExportStage.DOCUMENT:
                return 0;
            case ExportStage.DIAGRAMS: {
                const total = this.packages.length;
                const done = total ? this.finishedCount / total : 1;
                return clampPercent(
                    STAGE_SHARE.document + STAGE_SHARE.diagrams * done,
                );
            }
            case ExportStage.ARCHIVE:
                return clampPercent(
                    STAGE_SHARE.document +
                        STAGE_SHARE.diagrams +
                        (STAGE_SHARE.archive *
                            clampPercent(this.archivePercent)) /
                            100,
                );
            default:
                return 100;
        }
    }

    get statusText() {
        if (this.cancelled) {
            return "Cancelling the export…";
        }
        switch (this.stage) {
            case ExportStage.DOCUMENT:
                return "Generating the document…";
            case ExportStage.DIAGRAMS:
                return this.packages.length
                    ? "Rendering the package diagrams…"
                    : "Collecting the packages…";
            case ExportStage.ARCHIVE:
                return "Packing the archive…";
            default:
                return "Export ready";
        }
    }

    /** The document has been generated; the package diagrams are next. */
    documentReady() {
        this.stage = ExportStage.DIAGRAMS;
    }

    /** @param {{uuid: string, label: string}[]} packages */
    startDiagrams(packages) {
        this.stage = ExportStage.DIAGRAMS;
        this.packages = packages.map(pkg => ({
            uuid: pkg.uuid,
            label: pkg.label,
            status: PackageStatus.PENDING,
        }));
    }

    packageStarted(uuid) {
        this.#setStatus(uuid, PackageStatus.RENDERING);
    }

    packageFinished(uuid, status) {
        this.#setStatus(uuid, status);
    }

    startArchive() {
        this.stage = ExportStage.ARCHIVE;
        this.archivePercent = 0;
    }

    archiveProgress(percent) {
        this.archivePercent = clampPercent(percent);
    }

    finish() {
        this.stage = ExportStage.DONE;
    }

    cancel() {
        if (this.cancelled) return;
        this.cancelled = true;
        this.#controller.abort();
    }

    #setStatus(uuid, status) {
        const pkg = this.packages.find(candidate => candidate.uuid === uuid);
        if (pkg) {
            pkg.status = status;
        }
    }
}
