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

import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { graphStore } from "$lib/stores/graphStore.ts";
import {
    ImportJobRunner,
    newWorkspaceNameError,
} from "$lib/utils/importJob.svelte.js";
import { JobState } from "$lib/utils/importProgress.svelte.js";

vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { getImportStatus: vi.fn(), cancelImport: vi.fn() },
}));
vi.mock("$lib/stores/workspaceStore.ts", () => ({ workspaceStore: {} }));
vi.mock("$lib/stores/crossProfileStore.ts", () => ({
    crossProfileStore: {},
}));

describe("newWorkspaceNameError", () => {
    test("accepts a new name", () => {
        expect(newWorkspaceNameError(" fresh ", ["taken"])).toBe("");
    });

    test("asks for a name", () => {
        expect(newWorkspaceNameError("  ", [])).toBe(
            "Enter a name for the new workspace",
        );
    });

    test("rejects a name that is taken", () => {
        expect(newWorkspaceNameError("taken", ["taken"])).toContain(
            "already exists",
        );
    });

    test.each(["a/b", "a\\b", "a?b", "a#b"])("rejects %s", name => {
        expect(newWorkspaceNameError(name, [])).toContain("must not contain");
    });
});

describe("ImportJobRunner", () => {
    beforeEach(() => {
        vi.useFakeTimers();
        vi.mocked(graphStore.getImportStatus).mockReset();
        vi.mocked(graphStore.cancelImport).mockReset();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    test("follows the job until it is done", async () => {
        const status = {
            state: JobState.COMPLETED,
            files: [],
            importedGraphUris: ["http://example.org/core"],
        };
        vi.mocked(graphStore.getImportStatus).mockResolvedValue({
            data: status,
        });
        const onStatus = vi.fn();
        const runner = new ImportJobRunner(onStatus);

        await runner.start("ws", async () => ({ data: { jobId: "job" } }));
        await vi.runAllTimersAsync();

        expect(graphStore.getImportStatus).toHaveBeenCalledWith("ws", "job");
        expect(runner.progress.finished).toBe(true);
        expect(onStatus).toHaveBeenCalledWith(runner.progress);
        expect(await runner.dismiss()).toEqual({
            workspaceName: "ws",
            state: JobState.COMPLETED,
            importedGraphUris: ["http://example.org/core"],
        });
        expect(runner.progress).toBeNull();
    });

    test("reports what a job dismissed while running still finished", async () => {
        const running = { state: JobState.RUNNING, files: [] };
        // Cancelled too late: the backend had already committed the workspace.
        const finished = {
            state: JobState.COMPLETED,
            files: [],
            importedGraphUris: ["http://example.org/core"],
        };
        vi.mocked(graphStore.getImportStatus)
            .mockResolvedValueOnce({ data: running })
            .mockResolvedValueOnce({ data: finished });
        const runner = new ImportJobRunner();
        await runner.start("ws", async () => ({ data: { jobId: "job" } }));
        await vi.advanceTimersToNextTimerAsync();

        const result = await runner.dismiss();

        expect(graphStore.cancelImport).toHaveBeenCalledWith("ws", "job");
        expect(result).toEqual({
            workspaceName: "ws",
            state: JobState.COMPLETED,
            importedGraphUris: ["http://example.org/core"],
        });
    });

    test("keeps polling through a few failed polls", async () => {
        vi.mocked(graphStore.getImportStatus)
            .mockResolvedValueOnce({ error: new Error("gone") })
            .mockResolvedValueOnce({ error: new Error("gone") })
            .mockResolvedValueOnce({
                data: { state: JobState.COMPLETED, files: [] },
            });
        const runner = new ImportJobRunner();

        await runner.start("ws", async () => ({ data: { jobId: "job" } }));
        await vi.runAllTimersAsync();

        expect(runner.progress.jobState).toBe(JobState.COMPLETED);
    });

    test("gives up once the progress cannot be read any more", async () => {
        vi.mocked(graphStore.getImportStatus).mockResolvedValue({
            error: new Error("gone"),
        });
        const runner = new ImportJobRunner();

        await runner.start("ws", async () => ({ data: { jobId: "job" } }));
        await vi.runAllTimersAsync();

        expect(graphStore.getImportStatus).toHaveBeenCalledTimes(4);
        expect(runner.progress.jobState).toBe(JobState.FAILED);
        expect(runner.progress.errorMessage).toContain("could not be read");
    });

    test("fails without polling when the upload fails", async () => {
        const runner = new ImportJobRunner();

        await runner.start("ws", async () => ({
            error: new Error("Payload too large"),
        }));

        expect(runner.progress.jobState).toBe(JobState.FAILED);
        expect(runner.progress.errorMessage).toBe("Payload too large");
        expect(graphStore.getImportStatus).not.toHaveBeenCalled();
    });

    test("cancels a job dismissed while it was being uploaded", async () => {
        const runner = new ImportJobRunner();
        let finishUpload;

        const started = runner.start(
            "ws",
            () => new Promise(resolve => (finishUpload = resolve)),
        );
        await runner.dismiss();
        finishUpload({ data: { jobId: "job" } });
        await started;

        expect(graphStore.cancelImport).toHaveBeenCalledWith("ws", "job");
        expect(graphStore.getImportStatus).not.toHaveBeenCalled();
    });
});
