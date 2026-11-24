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

import { flushSync, mount, unmount } from "svelte";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import {
    discardRecoveredWorkspace,
    listRecoveredWorkspaces,
} from "$lib/desktop/desktopApi.js";
import {
    RecoveredWorkspaceGoneError,
    restoreRecoveredWorkspace,
} from "$lib/desktop/recovery.js";
import RecoveryDialog from "$lib/desktop/RecoveryDialog.svelte";
import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";

const KEPT = [
    {
        id: "Draft-1.rdfa",
        workspaceName: "Draft",
        savedAt: "2026-01-01T10:00:00Z",
    },
    {
        id: "Other-1.rdfa",
        workspaceName: "Other",
        savedAt: "2026-01-01T09:00:00Z",
    },
];

let mounted = null;
let target = null;

async function settle() {
    for (let i = 0; i < 5; i++) {
        await new Promise(resolve => setTimeout(resolve, 0));
        flushSync();
    }
}

async function clickRestoreOf(workspaceName) {
    const entry = [...document.querySelectorAll("li")].find(candidate =>
        candidate.textContent.includes(workspaceName),
    );
    [...entry.querySelectorAll("button")]
        .find(button => button.textContent.trim() === "Restore")
        .click();
    await settle();
}

function offered() {
    return ["Draft", "Other"].filter(name =>
        [...document.querySelectorAll("li")].some(entry =>
            entry.textContent.includes(name),
        ),
    );
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "",
    PUBLIC_DESKTOP: true,
}));
vi.mock("$lib/desktop/desktopApi.js", () => ({
    discardRecoveredWorkspace: vi.fn(),
    listRecoveredWorkspaces: vi.fn(),
}));
vi.mock("$lib/desktop/recovery.js", () => ({
    RecoveredWorkspaceGoneError: class extends Error {},
    restoreRecoveredWorkspace: vi.fn(),
}));
vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
}));
vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { getWorkspaces: vi.fn(async () => []) },
}));

beforeEach(async () => {
    vi.clearAllMocks();
    vi.mocked(listRecoveredWorkspaces).mockResolvedValue(KEPT);
    vi.mocked(discardRecoveredWorkspace).mockResolvedValue();
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(RecoveryDialog, { target });
    await settle();
});

afterEach(() => {
    if (mounted) unmount(mounted);
    mounted = null;
    target?.remove();
});

describe("RecoveryDialog", () => {
    test("restore_copyGone_saysSoAndDropsItFromTheList", async () => {
        vi.mocked(restoreRecoveredWorkspace).mockRejectedValue(
            new RecoveredWorkspaceGoneError(),
        );

        await clickRestoreOf("Draft");

        expect(toastStore.info).toHaveBeenCalledTimes(1);
        expect(toastStore.error).not.toHaveBeenCalled();
        expect(offered()).toEqual(["Other"]);
    });

    test("restore_importFails_keepsItInTheList", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        vi.mocked(restoreRecoveredWorkspace).mockRejectedValue(
            new Error("broken file"),
        );

        await clickRestoreOf("Draft");

        expect(toastStore.error).toHaveBeenCalledTimes(1);
        expect(offered()).toEqual(["Draft", "Other"]);
    });
});
