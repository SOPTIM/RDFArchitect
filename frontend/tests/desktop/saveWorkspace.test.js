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
    fetchWorkspaceFile,
    markWorkspaceSaved,
} from "$lib/desktop/desktopApi.js";
import { refreshDesktopStatus } from "$lib/desktop/desktopSession.svelte.js";
import {
    chooseWorkspaceFile,
    saveWorkspace,
    suggestedFileName,
    workspaceOpenedFromFile,
} from "$lib/desktop/saveWorkspace.js";
import { workspaceFiles } from "$lib/desktop/workspaceFiles.js";
import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import { saveFile } from "$lib/utils/fileUtils";

/** A file handle that remembers what was written to it. */
function fakeHandle(name, permission = "granted") {
    const handle = {
        name,
        written: [],
        queryPermission: vi.fn(async () => permission),
        requestPermission: vi.fn(async () => permission),
        createWritable: vi.fn(async () => ({
            write: async blob => handle.written.push(await blob.text()),
            close: async () => {},
            abort: async () => {},
        })),
        getFile: vi.fn(async () => new File(["zip"], name)),
    };
    return handle;
}

function abort() {
    return new DOMException("The user aborted a request.", "AbortError");
}

vi.mock("$lib/desktop/desktopApi.js", () => ({
    fetchWorkspaceFile: vi.fn(),
    markWorkspaceSaved: vi.fn(),
}));
vi.mock("$lib/desktop/desktopSession.svelte.js", () => ({
    refreshDesktopStatus: vi.fn(() => Promise.resolve()),
}));
vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn() },
}));
vi.mock("$lib/utils/fileUtils", async importOriginal => ({
    ...(await importOriginal()),
    saveFile: vi.fn(),
}));

describe("saveWorkspace", () => {
    beforeEach(() => {
        vi.mocked(fetchWorkspaceFile).mockResolvedValue({
            blob: new Blob(["workspace v1"]),
            version: "v1",
            fileName: "Draft.rdfa",
        });
        vi.mocked(markWorkspaceSaved).mockResolvedValue(undefined);
        workspaceFiles.forget("Draft");
    });

    afterEach(() => {
        vi.clearAllMocks();
        delete window.showSaveFilePicker;
        delete window.showOpenFilePicker;
    });

    describe("where the browser can write files", () => {
        let chosen;

        beforeEach(() => {
            chosen = fakeHandle("Draft.rdfa");
            window.showSaveFilePicker = vi.fn(async () => chosen);
            window.showOpenFilePicker = vi.fn(async () => [chosen]);
        });

        test("asks where to save the first time and records the version written", async () => {
            const result = await saveWorkspace("Draft");

            expect(result).toBe("saved");
            expect(window.showSaveFilePicker).toHaveBeenCalledWith(
                expect.objectContaining({ suggestedName: "Draft.rdfa" }),
            );
            expect(chosen.written).toEqual(["workspace v1"]);
            expect(markWorkspaceSaved).toHaveBeenCalledWith("Draft", "v1");
            expect(refreshDesktopStatus).toHaveBeenCalled();
            expect(saveFile).not.toHaveBeenCalled();
        });

        test("writes to the same file again without asking", async () => {
            await saveWorkspace("Draft");
            vi.mocked(fetchWorkspaceFile).mockResolvedValue({
                blob: new Blob(["workspace v2"]),
                version: "v2",
                fileName: "Draft.rdfa",
            });

            await saveWorkspace("Draft");

            expect(window.showSaveFilePicker).toHaveBeenCalledTimes(1);
            expect(chosen.written).toEqual(["workspace v1", "workspace v2"]);
            expect(markWorkspaceSaved).toHaveBeenLastCalledWith("Draft", "v2");
        });

        test("asks again for Save As", async () => {
            await saveWorkspace("Draft");
            const other = fakeHandle("Copy.rdfa");
            window.showSaveFilePicker = vi.fn(async () => other);

            await saveWorkspace("Draft", { saveAs: true });

            expect(other.written).toEqual(["workspace v1"]);
            expect(workspaceFiles.get("Draft")).toBe(other);
        });

        test("does nothing when the user cancels the picker", async () => {
            window.showSaveFilePicker = vi.fn(async () => {
                throw abort();
            });

            const result = await saveWorkspace("Draft");

            expect(result).toBe("cancelled");
            expect(fetchWorkspaceFile).not.toHaveBeenCalled();
            expect(markWorkspaceSaved).not.toHaveBeenCalled();
            expect(toastStore.error).not.toHaveBeenCalled();
        });

        test("leaves the workspace unsaved when writing is not allowed", async () => {
            vi.spyOn(console, "error").mockImplementation(() => {});
            chosen = fakeHandle("Draft.rdfa", "denied");

            const result = await saveWorkspace("Draft");

            expect(result).toBe("failed");
            expect(markWorkspaceSaved).not.toHaveBeenCalled();
            expect(toastStore.error).toHaveBeenCalled();
        });

        test("keeps hold of the file a workspace was opened from", async () => {
            const opened = await chooseWorkspaceFile();
            await workspaceOpenedFromFile("Draft", opened.handle);

            expect(await opened.file.text()).toBe("zip");
            expect(markWorkspaceSaved).toHaveBeenCalledWith("Draft");
            expect(workspaceFiles.fileName("Draft")).toBe("Draft.rdfa");

            await saveWorkspace("Draft");
            expect(window.showSaveFilePicker).not.toHaveBeenCalled();
            expect(chosen.written).toEqual(["workspace v1"]);
        });

        test("returns nothing when no file was chosen to open", async () => {
            window.showOpenFilePicker = vi.fn(async () => {
                throw abort();
            });

            expect(await chooseWorkspaceFile()).toBeNull();
        });
    });

    test("downloads the file where the browser cannot write files", async () => {
        const result = await saveWorkspace("Draft");

        expect(result).toBe("saved");
        expect(saveFile).toHaveBeenCalledWith(
            expect.any(Blob),
            "Draft.rdfa",
            expect.objectContaining({ fileExtension: ".rdfa" }),
        );
        expect(markWorkspaceSaved).toHaveBeenCalledWith("Draft", "v1");
    });

    test("reports a backend that could not write the file", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        vi.mocked(fetchWorkspaceFile).mockRejectedValue(new Error("500"));

        expect(await saveWorkspace("Draft")).toBe("failed");
        expect(markWorkspaceSaved).not.toHaveBeenCalled();
        expect(toastStore.error).toHaveBeenCalled();
    });
});

describe("suggestedFileName", () => {
    test.each([
        ["Draft", "Draft.rdfa"],
        ["CGMES 3.0 / EQ", "CGMES 3.0 _ EQ.rdfa"],
        ['a:b*c?"d<e>f|g\\h', "a_b_c__d_e_f_g_h.rdfa"],
        ["tab\there", "tab_here.rdfa"],
        ["  ", "workspace.rdfa"],
    ])("names %s", (name, expected) => {
        expect(suggestedFileName(name)).toBe(expected);
    });
});
