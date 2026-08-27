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

import { describe, expect, test } from "vitest";

import {
    FileState,
    ImportPhase,
    ImportProgress,
    JobState,
} from "$lib/utils/importProgress.svelte.js";

function file(overrides) {
    return {
        index: 0,
        fileName: "graph.ttl",
        sizeBytes: 100,
        state: FileState.PENDING,
        stage: null,
        graphUri: null,
        ...overrides,
    };
}

function running(status) {
    return {
        jobId: "job",
        datasetName: "ds",
        state: JobState.RUNNING,
        files: [],
        importedGraphUris: [],
        failedImports: [],
        warnings: [],
        ...status,
    };
}

describe("ImportProgress", () => {
    test("starts by uploading and stays inside the share of the upload", () => {
        const progress = new ImportProgress();

        expect(progress.phase).toBe(ImportPhase.UPLOADING);
        expect(progress.percent).toBe(0);
        expect(progress.statusText).toBe("Uploading the files…");

        progress.uploadProgress(50);

        expect(progress.percent).toBe(12.5);
    });

    test("credits the whole upload once the files are through", () => {
        const progress = new ImportProgress();

        progress.uploaded();

        expect(progress.phase).toBe(ImportPhase.IMPORTING);
        expect(progress.percent).toBe(25);
    });

    test("weights the files of the import by their size", () => {
        const progress = new ImportProgress();
        progress.uploaded();

        progress.apply(
            running({
                files: [
                    file({
                        index: 0,
                        sizeBytes: 300,
                        state: FileState.IMPORTED,
                    }),
                    file({ index: 1, sizeBytes: 100 }),
                ],
            }),
        );

        // three quarters of the bytes are done, of the 75 percent the import accounts for
        expect(progress.percent).toBe(25 + 75 * 0.75);
        expect(progress.finishedCount).toBe(1);
    });

    test("moves on within a file that is still being imported", () => {
        const progress = new ImportProgress();
        progress.uploaded();
        const parsing = running({
            files: [file({ state: FileState.RUNNING, stage: "PARSING" })],
        });
        progress.apply(parsing);
        const whileParsing = progress.percent;

        progress.apply(
            running({
                files: [file({ state: FileState.RUNNING, stage: "STORING" })],
            }),
        );

        expect(progress.percent).toBeGreaterThan(whileParsing);
        expect(progress.statusText).toBe("Importing graph.ttl (storing)…");
    });

    test("counts a skipped or failed file as done", () => {
        const progress = new ImportProgress();
        progress.uploaded();

        progress.apply(
            running({
                files: [
                    file({ index: 0, state: FileState.FAILED }),
                    file({ index: 1, state: FileState.SKIPPED }),
                ],
            }),
        );

        expect(progress.finishedCount).toBe(2);
        expect(progress.failed).toHaveLength(1);
        expect(progress.percent).toBe(100);
    });

    test("summarizes a finished import", () => {
        const progress = new ImportProgress();

        progress.apply(
            running({
                state: JobState.COMPLETED,
                files: [file({ state: FileState.IMPORTED })],
                importedGraphUris: ["http://graph#first"],
            }),
        );

        expect(progress.finished).toBe(true);
        expect(progress.percent).toBe(100);
        expect(progress.statusText).toBe("Imported 1 schema");
    });

    test("says how many files failed when only some were imported", () => {
        const progress = new ImportProgress();

        progress.apply(
            running({
                state: JobState.COMPLETED,
                importedGraphUris: ["http://graph#first"],
                failedImports: ["broken.ttl"],
            }),
        );

        expect(progress.statusText).toBe("Imported 1 schema, 1 file(s) failed");
    });

    test("keeps what a cancelled import managed to import", () => {
        const progress = new ImportProgress();

        progress.apply(
            running({
                state: JobState.CANCELLED,
                importedGraphUris: [
                    "http://graph#first",
                    "http://graph#second",
                ],
            }),
        );

        expect(progress.statusText).toBe("Import cancelled after 2 schemas");
    });

    test("reports the reason an import ended unexpectedly", () => {
        const progress = new ImportProgress();

        progress.apply(
            running({ state: JobState.FAILED, errorMessage: "out of memory" }),
        );

        expect(progress.statusText).toBe("Import failed");
        expect(progress.errorMessage).toBe("out of memory");
    });

    test("cancelling aborts the upload and shows what is happening", () => {
        const progress = new ImportProgress();

        progress.cancel();

        expect(progress.cancelling).toBe(true);
        expect(progress.signal.aborted).toBe(true);
        expect(progress.statusText).toBe("Cancelling the import…");
    });

    test("cancelling during the upload ends the import without an error", () => {
        const progress = new ImportProgress();

        progress.cancel();
        progress.cancelled();

        expect(progress.finished).toBe(true);
        expect(progress.jobState).toBe(JobState.CANCELLED);
        expect(progress.statusText).toBe("Import cancelled");
        expect(progress.errorMessage).toBeNull();
    });

    test("a failure to start is shown as a finished, failed import", () => {
        const progress = new ImportProgress();

        progress.fail("Another import is still running.");

        expect(progress.finished).toBe(true);
        expect(progress.jobState).toBe(JobState.FAILED);
        expect(progress.errorMessage).toBe("Another import is still running.");
    });
});
