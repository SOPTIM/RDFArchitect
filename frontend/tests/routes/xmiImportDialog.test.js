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
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import XmiImportDialogHost from "./XmiImportDialogHost.svelte";

const startXmiScan = vi.hoisted(() => vi.fn());
const getImportStatus = vi.hoisted(() => vi.fn());
const importSelectedPackages = vi.hoisted(() => vi.fn());
const cancelImport = vi.hoisted(() => vi.fn());
const invalidate = vi.hoisted(() => vi.fn());
const getWorkspaces = vi.hoisted(() => vi.fn());

let mounted = null;
let target = null;

function dialog() {
    return document.querySelector("[role='dialog']");
}

function textOf(node) {
    return node.textContent.replace(/\s+/g, " ").trim();
}

function buttonLabelled(label) {
    return [...dialog().querySelectorAll("button")].find(
        button => button.textContent.trim() === label,
    );
}

function render() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(XmiImportDialogHost, {
        target,
        props: {
            droppedFile: new File(["<xmi/>"], "export.xml", {
                type: "application/xml",
            }),
        },
    });
    flushSync();
    return mounted;
}

/** Starts a read, which is what puts the dialog into a run of its own. */
async function startReading() {
    buttonLabelled("Read").click();
    await vi.waitFor(() => expect(startXmiScan).toHaveBeenCalled());
    flushSync();
}

vi.mock("$lib/stores/graphStore", () => ({
    graphStore: {
        startXmiScan,
        getImportStatus,
        importSelectedPackages,
        cancelImport,
        invalidateWorkspace: invalidate,
    },
}));
vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { invalidate, getWorkspaces },
}));
vi.mock("$lib/stores/crossProfileStore.ts", () => ({
    crossProfileStore: { invalidateWorkspace: invalidate },
}));
vi.mock("$lib/actions/validationActions.js", () => ({
    canOpenOccurrence: () => false,
    openOccurrence: () => {},
}));

beforeEach(() => {
    startXmiScan.mockResolvedValue({ data: { jobId: "read-1" } });
    getImportStatus.mockResolvedValue({
        data: { jobId: "read-1", state: "RUNNING", files: [] },
    });
    cancelImport.mockResolvedValue({ data: null });
    getWorkspaces.mockResolvedValue([{ label: "cgmes" }]);
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.clearAllMocks();
});

describe("XmiImportDialog", () => {
    test("opens on the export to read", () => {
        render();

        expect(textOf(dialog())).toContain("Select File");
    });

    test("shows the progress of a read once it has started", async () => {
        render();

        await startReading();

        expect(textOf(dialog())).toContain("Uploading the files");
    });

    test("opens on nothing again after closing itself mid-import", async () => {
        // The dialog is mounted once and reopened, and closing it from within does not reach
        // onClose: without a teardown of its own the previous run would still be on screen.
        const host = render();
        await startReading();

        buttonLabelled("Cancel").click();
        flushSync();
        host.setOpen(true);
        flushSync();

        expect(textOf(dialog())).toContain("Select File");
        expect(textOf(dialog())).not.toContain("Uploading the files");
    });

    test("reads an export into a workspace of its own", async () => {
        render();
        await vi.waitFor(() => expect(getWorkspaces).toHaveBeenCalled());
        flushSync();

        // The name is taken from the dropped file, which names no workspace there is yet.
        expect(textOf(dialog())).not.toContain("Workspace already exists");
        expect(buttonLabelled("Read").disabled).toBe(false);
    });

    test("refuses a name a workspace already has, an export becoming one of its own", async () => {
        getWorkspaces.mockResolvedValue([{ label: "export" }]);

        render();

        // The names are fetched when the dialog opens, so the refusal arrives a tick later.
        await vi.waitFor(() => {
            flushSync();
            expect(textOf(dialog())).toContain("Workspace already exists");
        });
        expect(buttonLabelled("Read").disabled).toBe(true);
    });

    test("stops the job it was following when it closes itself", async () => {
        render();
        await startReading();

        buttonLabelled("Cancel").click();
        await vi.waitFor(() => expect(cancelImport).toHaveBeenCalled());
    });
});
