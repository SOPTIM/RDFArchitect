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
import { afterEach, beforeEach, expect, test, vi } from "vitest";

import {
    chooseWorkspaceFile,
    workspaceOpenedFromFile,
} from "$lib/desktop/saveWorkspace.js";
import { workspaceStore } from "$lib/stores/workspaceStore.ts";
import { JobState } from "$lib/utils/importProgress.svelte.js";

import WorkspaceImportDialog from "../../src/routes/WorkspaceImportDialog.svelte";

const job = vi.hoisted(() => ({
    start: vi.fn(),
    dismiss: vi.fn(),
    cancel: vi.fn(),
}));

let mounted = null;
let target = null;

function button(label) {
    return [...document.querySelectorAll("button")].find(
        candidate => candidate.textContent.trim() === label,
    );
}

async function settle() {
    for (let i = 0; i < 3; i++) {
        await new Promise(resolve => setTimeout(resolve, 0));
        flushSync();
    }
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "",
    PUBLIC_DESKTOP: true,
}));
vi.mock("$lib/desktop/saveWorkspace.js", () => ({
    canChooseFiles: () => true,
    chooseWorkspaceFile: vi.fn(),
    workspaceOpenedFromFile: vi.fn(),
    workspaceOpenedFromRequest: vi.fn(),
}));
vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { getWorkspaces: vi.fn() },
}));
vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { startWorkspaceImport: vi.fn() },
}));
vi.mock("$lib/utils/importJob.svelte.js", () => ({
    ImportJobRunner: class {
        progress = null;
        start(...args) {
            return job.start(...args);
        }
        dismiss() {
            return job.dismiss();
        }
        cancel() {
            return job.cancel();
        }
    },
    newWorkspaceNameError: () => null,
    openImportedWorkspace: vi.fn(),
}));

beforeEach(() => {
    vi.mocked(workspaceStore.getWorkspaces).mockResolvedValue([]);
    vi.mocked(chooseWorkspaceFile).mockReset();
    vi.mocked(workspaceOpenedFromFile).mockReset();
    job.dismiss.mockResolvedValue({
        state: JobState.COMPLETED,
        workspaceName: "Draft",
        importedSchemaNames: [],
    });
    target = document.createElement("div");
    document.body.appendChild(target);
});

afterEach(() => {
    if (mounted) {
        unmount(mounted);
        mounted = null;
    }
    target.remove();
});

test("selectArchive_anotherFileRefused_keepsFileOfChosenWorkspace", async () => {
    vi.mocked(chooseWorkspaceFile)
        .mockResolvedValueOnce({
            file: new File(["zip"], "Draft.rdfa"),
            handle: "handle of Draft.rdfa",
        })
        .mockResolvedValueOnce({
            file: new File(["text"], "notes.txt"),
            handle: "handle of notes.txt",
        });
    mounted = mount(WorkspaceImportDialog, {
        target,
        props: { showDialog: true },
    });
    await settle();

    button("Select File").click();
    await settle();
    button("Select File").click();
    await settle();
    expect(document.body.textContent).toContain("notes.txt");

    button("Open").click();
    await settle();
    expect(job.start).toHaveBeenCalled();
    // The dialog's close button, the only one without a label.
    button("").click();
    await settle();

    expect(workspaceOpenedFromFile).toHaveBeenCalledWith(
        "Draft",
        "handle of Draft.rdfa",
    );
});
