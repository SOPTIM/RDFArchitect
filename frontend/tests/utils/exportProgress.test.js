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
    ExportProgress,
    ExportStage,
    PackageStatus,
} from "$lib/utils/exportProgress.svelte.js";

function progressWithPackages(count) {
    const progress = new ExportProgress();
    progress.documentReady();
    progress.startDiagrams(
        Array.from({ length: count }, (_, index) => ({
            uuid: `uuid-${index}`,
            label: `Package ${index}`,
        })),
    );
    return progress;
}

describe("ExportProgress", () => {
    test("starts at zero while the document is generated", () => {
        const progress = new ExportProgress();

        expect(progress.stage).toBe(ExportStage.DOCUMENT);
        expect(progress.percent).toBe(0);
    });

    test("credits the document stage once the diagrams start", () => {
        const progress = progressWithPackages(4);

        expect(progress.percent).toBe(10);
    });

    test("advances with every finished package", () => {
        const progress = progressWithPackages(4);

        progress.packageFinished("uuid-0", PackageStatus.DONE);
        expect(progress.percent).toBe(30);

        progress.packageFinished("uuid-1", PackageStatus.EMPTY);
        progress.packageFinished("uuid-2", PackageStatus.FAILED);
        expect(progress.percent).toBe(70);
    });

    test("counts a package as finished whatever its outcome", () => {
        const progress = progressWithPackages(3);

        progress.packageStarted("uuid-0");
        expect(progress.finishedCount).toBe(0);

        progress.packageFinished("uuid-0", PackageStatus.DONE);
        progress.packageFinished("uuid-1", PackageStatus.EMPTY);
        progress.packageFinished("uuid-2", PackageStatus.FAILED);
        expect(progress.finishedCount).toBe(3);
    });

    test("treats a graph without packages as a finished diagram stage", () => {
        const progress = progressWithPackages(0);

        expect(progress.percent).toBe(90);
    });

    test("maps the archive percentage onto the last tenth", () => {
        const progress = progressWithPackages(1);
        progress.packageFinished("uuid-0", PackageStatus.DONE);

        progress.startArchive();
        expect(progress.percent).toBe(90);

        progress.archiveProgress(50);
        expect(progress.percent).toBe(95);

        progress.archiveProgress(100);
        expect(progress.percent).toBe(100);
    });

    test("clamps an out-of-range archive percentage", () => {
        const progress = progressWithPackages(1);
        progress.startArchive();

        progress.archiveProgress(140);
        expect(progress.percent).toBe(100);

        progress.archiveProgress(-20);
        expect(progress.percent).toBe(90);
    });

    test("reports the failed packages", () => {
        const progress = progressWithPackages(3);

        progress.packageFinished("uuid-0", PackageStatus.DONE);
        progress.packageFinished("uuid-1", PackageStatus.FAILED);
        progress.packageFinished("uuid-2", PackageStatus.EMPTY);

        expect(progress.failed.map(pkg => pkg.label)).toEqual(["Package 1"]);
    });

    test("ends at a hundred percent", () => {
        const progress = progressWithPackages(2);

        progress.finish();

        expect(progress.percent).toBe(100);
        expect(progress.statusText).toBe("Export ready");
    });

    test("cancelling aborts the signal of the running requests", () => {
        const progress = progressWithPackages(2);

        expect(progress.signal.aborted).toBe(false);

        progress.cancel();

        expect(progress.cancelled).toBe(true);
        expect(progress.signal.aborted).toBe(true);
        expect(progress.statusText).toBe("Cancelling the export…");
    });

    test("cancelling twice is harmless", () => {
        const progress = progressWithPackages(1);

        progress.cancel();
        expect(() => progress.cancel()).not.toThrow();
        expect(progress.cancelled).toBe(true);
    });

    test("ignores a status for a package that is not part of the export", () => {
        const progress = progressWithPackages(1);

        expect(() =>
            progress.packageFinished("unknown", PackageStatus.DONE),
        ).not.toThrow();
        expect(progress.finishedCount).toBe(0);
    });
});
