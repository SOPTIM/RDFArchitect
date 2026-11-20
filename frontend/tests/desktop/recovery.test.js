/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import {
    discardRecoveredWorkspace,
    listRecoveredWorkspaces,
    restoreRecoveredWorkspace,
    restoredName,
} from "$lib/desktop/recovery.js";
import { graphStore } from "$lib/stores/graphStore.ts";
import { openImportedWorkspace } from "$lib/utils/importJob.svelte.js";

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));
vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: {
        startWorkspaceImport: vi.fn(),
        getImportStatus: vi.fn(),
        cancelImport: vi.fn(),
    },
}));
vi.mock("$lib/utils/importJob.svelte.js", () => ({
    openImportedWorkspace: vi.fn(),
}));

describe("restoredName", () => {
    test("keeps the name while it is free", () => {
        expect(restoredName("Draft", ["Other"])).toBe("Draft");
    });

    test("finds a name of its own when it is taken", () => {
        expect(restoredName("Draft", ["Draft"])).toBe("Draft (recovered)");
        expect(restoredName("Draft", ["Draft", "Draft (recovered)"])).toBe(
            "Draft (recovered 2)",
        );
    });
});

describe("recovery", () => {
    let fetchMock;

    beforeEach(() => {
        vi.useFakeTimers();
        fetchMock = vi.fn();
        vi.stubGlobal("fetch", fetchMock);
        vi.mocked(graphStore.startWorkspaceImport).mockReset();
        vi.mocked(graphStore.getImportStatus).mockReset();
        vi.mocked(graphStore.cancelImport).mockReset();
        vi.mocked(openImportedWorkspace).mockReset();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.useRealTimers();
    });

    test("lists the kept workspaces", async () => {
        fetchMock.mockResolvedValue(
            new Response(
                JSON.stringify([
                    { id: "Draft-1.rdfa", workspaceName: "Draft" },
                ]),
            ),
        );

        expect(await listRecoveredWorkspaces()).toEqual([
            { id: "Draft-1.rdfa", workspaceName: "Draft" },
        ]);
        expect(fetchMock).toHaveBeenCalledWith("/api/desktop/recovery", {
            credentials: "include",
        });
    });

    test("restores a kept workspace under a free name and then discards the copy", async () => {
        fetchMock.mockImplementation(async (url, init) =>
            init?.method === "DELETE"
                ? new Response(null, { status: 204 })
                : new Response("zip"),
        );
        vi.mocked(graphStore.startWorkspaceImport).mockResolvedValue({
            error: null,
            data: { jobId: "job" },
        });
        vi.mocked(graphStore.getImportStatus)
            .mockResolvedValueOnce({ error: null, data: { state: "RUNNING" } })
            .mockResolvedValueOnce({
                error: null,
                data: { state: "COMPLETED", importedSchemaNames: ["g"] },
            });

        const restoring = restoreRecoveredWorkspace(
            { id: "Draft-1.rdfa", workspaceName: "Draft" },
            ["Draft"],
        );
        await vi.advanceTimersByTimeAsync(1000);
        const name = await restoring;

        expect(name).toBe("Draft (recovered)");
        const [importedName, file] = vi.mocked(graphStore.startWorkspaceImport)
            .mock.calls[0];
        expect(importedName).toBe("Draft (recovered)");
        expect(file.name).toBe("Draft (recovered).rdfa");
        expect(openImportedWorkspace).toHaveBeenCalledWith(
            "Draft (recovered)",
            ["g"],
        );
        expect(fetchMock).toHaveBeenLastCalledWith(
            "/api/desktop/recovery/Draft-1.rdfa",
            { method: "DELETE", credentials: "include" },
        );
    });

    test("keeps the copy when the import fails", async () => {
        fetchMock.mockResolvedValue(new Response("zip"));
        vi.mocked(graphStore.startWorkspaceImport).mockResolvedValue({
            error: null,
            data: { jobId: "job" },
        });
        vi.mocked(graphStore.getImportStatus).mockResolvedValue({
            error: null,
            data: { state: "FAILED", errorMessage: "broken file" },
        });

        const restoring = restoreRecoveredWorkspace(
            { id: "Draft-1.rdfa", workspaceName: "Draft" },
            [],
        );
        const outcome = expect(restoring).rejects.toThrow("broken file");
        await vi.advanceTimersByTimeAsync(1000);
        await outcome;

        expect(
            fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE"),
        ).toBe(false);
    });

    test("discards a copy that is gone already without complaint", async () => {
        fetchMock.mockResolvedValue(new Response(null, { status: 404 }));

        await expect(
            discardRecoveredWorkspace("Draft-1.rdfa"),
        ).resolves.toBeUndefined();
    });
});
