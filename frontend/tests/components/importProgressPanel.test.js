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

import { flushSync, mount, unmount } from "svelte";
import { afterEach, describe, expect, test, vi } from "vitest";

import ImportProgressPanel from "$lib/components/ImportProgressPanel.svelte";
import {
    FileState,
    ImportProgress,
    JobState,
    OutcomeKind,
} from "$lib/utils/importProgress.svelte.js";

let mounted = null;
let target = null;

function render(progress) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(ImportProgressPanel, { target, props: { progress } });
    // The panel derives from derived state, which settles a tick after mounting.
    flushSync();
    return target;
}

/** The rendered text with its line breaks collapsed, which is how one reads it. */
function textOf(node) {
    return node.textContent.replace(/\s+/g, " ").trim();
}

/** Clicks the line that opens the list of what could not be carried over. */
function openWarnings(panel) {
    [...panel.querySelectorAll("button")]
        .find(button => button.textContent.includes("warning"))
        .click();
    flushSync();
}

function statusOf(files, state) {
    return {
        jobId: "job",
        datasetName: "ds",
        state,
        files,
        outcome: {
            kind: OutcomeKind.GRAPHS_IMPORTED,
            graphUris: files
                .filter(file => file.state === FileState.IMPORTED)
                .map(file => file.graphUri),
            failedFileNames: files
                .filter(file => file.state === FileState.FAILED)
                .map(file => file.fileName),
            warnings: [],
        },
    };
}

function fileOf(index, fileName, state, extra = {}) {
    return {
        index,
        fileName,
        sizeBytes: 100,
        state,
        graphUri:
            state === FileState.IMPORTED ? `http://graph#${fileName}` : null,
        ...extra,
    };
}

// The issue table can open the schema a finding points to, which drags in the generated client
// and the router. Neither is what these tests are about.
vi.mock("$lib/actions/validationActions.js", () => ({
    canOpenOccurrence: () => false,
    openOccurrence: () => {},
}));

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

    test("shows one row per file", () => {
        const progress = new ImportProgress();
        progress.uploaded();
        progress.apply(
            statusOf(
                [
                    fileOf(0, "first.ttl", FileState.IMPORTED),
                    fileOf(1, "second.ttl", FileState.RUNNING),
                    fileOf(2, "third.ttl", FileState.PENDING),
                ],
                JobState.RUNNING,
            ),
        );

        const panel = render(progress);

        expect(panel.textContent).toContain("1 of 3 files");
        expect(panel.textContent).toContain("first.ttl");
        expect(panel.textContent).toContain("second.ttl");
        expect(panel.textContent).toContain("third.ttl");
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

    test("lists what could not be carried over, with the reason each line gives", () => {
        const progress = new ImportProgress();
        progress.uploaded();
        const status = statusOf(
            [fileOf(0, "schema.ttl", FileState.IMPORTED)],
            JobState.COMPLETED,
        );
        status.outcome.warnings = [
            {
                fileName: "schema.ttl",
                graphUri: "http://graph#schema",
                items: [
                    {
                        resource: "color",
                        reason: "stored, but carries no CIM stereotype",
                    },
                    {
                        resource: "license",
                        reason: "no ontology field of that name",
                    },
                ],
            },
        ];
        progress.apply(status);

        const panel = render(progress);

        // The panel says how many there are; the list itself is a click away.
        expect(textOf(panel)).toContain("2 warnings");
        expect(textOf(panel)).not.toContain("license");

        openWarnings(panel);

        // The dialog lists them the way the validation report lists its findings.
        const dialog = textOf(document.querySelector("[role='dialog']"));
        expect(dialog).toContain("schema.ttl");
        expect(dialog).toContain("license");
        expect(dialog).toContain("no ontology field of that name");
    });

    test("lists a warning per file even when the same file was imported twice", () => {
        // Nothing stops the dialog from adding one file twice, and the backend keeps both under
        // suffixed graph uris, so the warnings arrive with a repeated file name.
        const progress = new ImportProgress();
        progress.uploaded();
        const status = statusOf(
            [
                fileOf(0, "schema.ttl", FileState.IMPORTED),
                fileOf(1, "schema.ttl", FileState.IMPORTED),
            ],
            JobState.COMPLETED,
        );
        status.outcome.warnings = [
            {
                fileName: "schema.ttl",
                graphUri: "http://graph#schema",
                items: [{ resource: "color", reason: "no stereotype" }],
            },
            {
                fileName: "schema.ttl",
                graphUri: "http://graph#schema_1",
                items: [{ resource: "owner", reason: "no stereotype" }],
            },
        ];
        progress.apply(status);

        const panel = render(progress);
        openWarnings(panel);

        const dialog = textOf(document.querySelector("[role='dialog']"));
        expect(dialog).toContain("color");
        expect(dialog).toContain("owner");
    });

    test("explains an import that ended unexpectedly", () => {
        const progress = new ImportProgress();
        progress.fail("Another import is still running.");

        const panel = render(progress);

        expect(panel.textContent).toContain("Import failed");
        expect(panel.textContent).toContain("Another import is still running.");
    });
});
