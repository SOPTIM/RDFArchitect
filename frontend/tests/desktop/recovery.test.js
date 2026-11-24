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
    fetchRecoveredWorkspace,
    markRestoredFrom,
} from "$lib/desktop/desktopApi.js";
import {
    RecoveredWorkspaceGoneError,
    restoreRecoveredWorkspace,
    restoredName,
} from "$lib/desktop/recovery.js";
import { graphStore } from "$lib/stores/graphStore.ts";
import { openImportedWorkspace } from "$lib/utils/importJob.svelte.js";

const KEPT = { id: "Draft-1.rdfa", workspaceName: "Draft" };

vi.mock("$lib/desktop/desktopApi.js", () => ({
    fetchRecoveredWorkspace: vi.fn(),
    markRestoredFrom: vi.fn(),
}));
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
    test("restoredName_free_keepsTheName", () => {
        expect(restoredName("Draft", ["Other"])).toBe("Draft");
    });

    test("restoredName_taken_findsANameOfItsOwn", () => {
        expect(restoredName("Draft", ["Draft"])).toBe("Draft (recovered)");
        expect(restoredName("Draft", ["Draft", "Draft (recovered)"])).toBe(
            "Draft (recovered 2)",
        );
    });
});

describe("restoreRecoveredWorkspace", () => {
    beforeEach(() => {
        vi.useFakeTimers();
        vi.mocked(fetchRecoveredWorkspace).mockReset();
        vi.mocked(fetchRecoveredWorkspace).mockResolvedValue(new Blob(["zip"]));
        vi.mocked(markRestoredFrom).mockReset();
        vi.mocked(markRestoredFrom).mockResolvedValue();
        vi.mocked(graphStore.startWorkspaceImport).mockReset();
        vi.mocked(graphStore.startWorkspaceImport).mockResolvedValue({
            error: null,
            data: { jobId: "job" },
        });
        vi.mocked(graphStore.getImportStatus).mockReset();
        vi.mocked(graphStore.cancelImport).mockReset();
        vi.mocked(openImportedWorkspace).mockReset();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    function importEndsAs(data) {
        vi.mocked(graphStore.getImportStatus)
            .mockResolvedValueOnce({ error: null, data: { state: "RUNNING" } })
            .mockResolvedValue({ error: null, data });
    }

    test("restoreRecoveredWorkspace_nameTaken_restoresUnderAFreeNameAndReportsIt", async () => {
        importEndsAs({ state: "COMPLETED", importedSchemaNames: ["g"] });

        const restoring = restoreRecoveredWorkspace(KEPT, ["Draft"]);
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
        expect(markRestoredFrom).toHaveBeenCalledWith(
            "Draft (recovered)",
            "Draft-1.rdfa",
        );
    });

    test("restoreRecoveredWorkspace_importFails_keepsTheCopy", async () => {
        importEndsAs({ state: "FAILED", errorMessage: "broken file" });

        const restoring = restoreRecoveredWorkspace(KEPT, []);
        const outcome = expect(restoring).rejects.toThrow("broken file");
        await vi.advanceTimersByTimeAsync(1000);
        await outcome;

        expect(markRestoredFrom).not.toHaveBeenCalled();
    });

    test("restoreRecoveredWorkspace_copyCannotBeRemoved_stillReportsTheRestore", async () => {
        importEndsAs({ state: "COMPLETED", importedSchemaNames: ["g"] });
        vi.mocked(markRestoredFrom).mockRejectedValue(new Error("500"));
        vi.spyOn(console, "warn").mockImplementation(() => {});

        const restoring = restoreRecoveredWorkspace(KEPT, []);
        await vi.advanceTimersByTimeAsync(1000);

        await expect(restoring).resolves.toBe("Draft");
    });

    test("restoreRecoveredWorkspace_copyGone_throwsWithoutImporting", async () => {
        vi.mocked(fetchRecoveredWorkspace).mockResolvedValue(null);

        await expect(
            restoreRecoveredWorkspace(KEPT, []),
        ).rejects.toBeInstanceOf(RecoveredWorkspaceGoneError);
        expect(graphStore.startWorkspaceImport).not.toHaveBeenCalled();
    });
});
