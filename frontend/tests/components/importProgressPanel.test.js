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

import { mount, unmount } from "svelte";
import { afterEach, describe, expect, test } from "vitest";

import ImportProgressPanel from "$lib/components/ImportProgressPanel.svelte";
import {
    FileState,
    ImportProgress,
    JobState,
} from "$lib/utils/importProgress.svelte.js";

let mounted = null;
let target = null;

function render(progress) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(ImportProgressPanel, { target, props: { progress } });
    return target;
}

function statusOf(files, state) {
    return {
        jobId: "job",
        datasetName: "ds",
        state,
        files,
        importedGraphUris: files
            .filter(file => file.state === FileState.IMPORTED)
            .map(file => file.graphUri),
        failedImports: files
            .filter(file => file.state === FileState.FAILED)
            .map(file => file.fileName),
        warnings: [],
    };
}

function fileOf(index, fileName, state, extra = {}) {
    return {
        index,
        fileName,
        sizeBytes: 100,
        state,
        stage: null,
        graphUri:
            state === FileState.IMPORTED ? `http://graph#${fileName}` : null,
        ...extra,
    };
}

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("ImportProgressPanel", () => {
    test("shows the upload without a file list", () => {
        const progress = new ImportProgress();
        progress.uploadProgress(40);

        const panel = render(progress);

        expect(panel.textContent).toContain("Uploading the files…");
        expect(panel.querySelector('[role="progressbar"]')).not.toBeNull();
        expect(panel.textContent).not.toContain("of 0 files");
    });

    test("shows one row per file and what is happening to the current one", () => {
        const progress = new ImportProgress();
        progress.uploaded();
        progress.apply(
            statusOf(
                [
                    fileOf(0, "first.ttl", FileState.IMPORTED),
                    fileOf(1, "second.ttl", FileState.RUNNING, {
                        stage: "STORING",
                    }),
                    fileOf(2, "third.ttl", FileState.PENDING),
                ],
                JobState.RUNNING,
            ),
        );

        const panel = render(progress);

        expect(panel.textContent).toContain("1 of 3 files");
        expect(panel.textContent).toContain("first.ttl");
        expect(panel.textContent).toContain("third.ttl");
        expect(panel.textContent).toContain("storing");
    });

    test("names the files that could not be imported", () => {
        const progress = new ImportProgress();
        progress.uploaded();
        progress.apply(
            statusOf(
                [
                    fileOf(0, "good.ttl", FileState.IMPORTED),
                    fileOf(1, "broken.ttl", FileState.FAILED),
                ],
                JobState.COMPLETED,
            ),
        );

        const panel = render(progress);
        const bar = panel.querySelector('[role="progressbar"]');

        expect(bar.getAttribute("aria-valuenow")).toBe("100");
        expect(panel.textContent).toContain(
            "Imported 1 schema, 1 file(s) failed",
        );
        expect(panel.textContent).toContain("broken.ttl");
        expect(panel.textContent).toContain("could not be imported");
    });

    test("lists the properties that will not be displayed", () => {
        const progress = new ImportProgress();
        progress.uploaded();
        const status = statusOf(
            [fileOf(0, "schema.ttl", FileState.IMPORTED)],
            JobState.COMPLETED,
        );
        status.warnings = [
            {
                fileName: "schema.ttl",
                undisplayableProperties: ["color", "owner"],
            },
        ];
        progress.apply(status);

        const panel = render(progress);

        expect(panel.textContent).toContain(
            "Some properties will not be displayed",
        );
        expect(panel.textContent).toContain("schema.ttl: color, owner");
    });

    test("explains an import that ended unexpectedly", () => {
        const progress = new ImportProgress();
        progress.fail("Another import is still running.");

        const panel = render(progress);

        expect(panel.textContent).toContain("Import failed");
        expect(panel.textContent).toContain("Another import is still running.");
    });
});
