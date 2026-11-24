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
    markOpenedFrom,
    markWorkspaceSaved,
    saveWorkspaceToItsFile,
} from "$lib/desktop/desktopApi.js";
import {
    desktopSession,
    refreshDesktopStatus,
} from "$lib/desktop/desktopSession.svelte.js";
import {
    chooseWorkspaceFile,
    saveWorkspace,
    suggestedFileName,
    workspaceOpenedFromFile,
    workspaceOpenedFromRequest,
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
    markOpenedFrom: vi.fn(),
    saveWorkspaceToItsFile: vi.fn(),
}));
vi.mock("$lib/desktop/desktopSession.svelte.js", () => ({
    desktopSession: { workspacesWithFile: [], workspaceIds: {} },
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
    /** The id of "Draft", new in every test, so no test inherits another one's file. */
    let draftId;

    function fileOf(text, version, workspaceId = draftId) {
        return {
            blob: new Blob([text]),
            version,
            workspaceId,
            fileName: "Draft.rdfa",
        };
    }

    beforeEach(() => {
        draftId = crypto.randomUUID();
        vi.mocked(fetchWorkspaceFile).mockResolvedValue(
            fileOf("workspace v1", "v1"),
        );
        vi.mocked(markWorkspaceSaved).mockResolvedValue(undefined);
        desktopSession.workspacesWithFile = [];
        desktopSession.workspaceIds = { Draft: draftId };
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

        test("saveWorkspace_firstTime_asksWhereAndRecordsTheVersionWritten", async () => {
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

        test("saveWorkspace_again_writesToTheSameFileWithoutAsking", async () => {
            await saveWorkspace("Draft");
            vi.mocked(fetchWorkspaceFile).mockResolvedValue(
                fileOf("workspace v2", "v2"),
            );

            await saveWorkspace("Draft");

            expect(window.showSaveFilePicker).toHaveBeenCalledTimes(1);
            expect(chosen.written).toEqual(["workspace v1", "workspace v2"]);
            expect(markWorkspaceSaved).toHaveBeenLastCalledWith("Draft", "v2");
        });

        test("saveWorkspace_saveAs_asksAgain", async () => {
            await saveWorkspace("Draft");
            const other = fakeHandle("Copy.rdfa");
            window.showSaveFilePicker = vi.fn(async () => other);

            await saveWorkspace("Draft", { saveAs: true });

            expect(other.written).toEqual(["workspace v1"]);
            expect(workspaceFiles.get(draftId)).toBe(other);
        });

        test("saveWorkspace_pickerCancelled_doesNothing", async () => {
            window.showSaveFilePicker = vi.fn(async () => {
                throw abort();
            });

            const result = await saveWorkspace("Draft");

            expect(result).toBe("cancelled");
            expect(fetchWorkspaceFile).not.toHaveBeenCalled();
            expect(markWorkspaceSaved).not.toHaveBeenCalled();
            expect(toastStore.error).not.toHaveBeenCalled();
        });

        test("saveWorkspace_writingNotAllowed_leavesTheWorkspaceUnsaved", async () => {
            vi.spyOn(console, "error").mockImplementation(() => {});
            chosen = fakeHandle("Draft.rdfa", "denied");

            const result = await saveWorkspace("Draft");

            expect(result).toBe("failed");
            expect(markWorkspaceSaved).not.toHaveBeenCalled();
            expect(toastStore.error).toHaveBeenCalled();
        });

        test("workspaceOpenedFromFile_handle_savesToItLater", async () => {
            const opened = await chooseWorkspaceFile();
            await workspaceOpenedFromFile("Draft", opened.handle);

            expect(await opened.file.text()).toBe("zip");
            expect(markWorkspaceSaved).toHaveBeenCalledWith("Draft");
            expect(workspaceFiles.get(draftId)).toBe(opened.handle);

            await saveWorkspace("Draft");
            expect(window.showSaveFilePicker).not.toHaveBeenCalled();
            expect(chosen.written).toEqual(["workspace v1"]);
        });

        test("saveWorkspace_renamed_writesToTheSameFile", async () => {
            await saveWorkspace("Draft");
            desktopSession.workspaceIds = { Renamed: draftId };

            await saveWorkspace("Renamed");

            expect(window.showSaveFilePicker).toHaveBeenCalledTimes(1);
            expect(chosen.written).toEqual(["workspace v1", "workspace v1"]);
        });

        test("saveWorkspace_createdAnewUnderTheName_asksForAFile", async () => {
            await saveWorkspace("Draft");
            const newId = crypto.randomUUID();
            desktopSession.workspaceIds = { Draft: newId };
            vi.mocked(fetchWorkspaceFile).mockResolvedValue(
                fileOf("new workspace", "n1", newId),
            );
            const other = fakeHandle("New.rdfa");
            window.showSaveFilePicker = vi.fn(async () => other);

            await saveWorkspace("Draft");

            expect(window.showSaveFilePicker).toHaveBeenCalledTimes(1);
            expect(chosen.written).toEqual(["workspace v1"]);
            expect(other.written).toEqual(["new workspace"]);
        });

        test("saveWorkspace_replacedSinceTheLastHeartbeat_writesNothing", async () => {
            vi.spyOn(console, "error").mockImplementation(() => {});
            await saveWorkspace("Draft");
            vi.mocked(fetchWorkspaceFile).mockResolvedValue(
                fileOf("new workspace", "n1", crypto.randomUUID()),
            );

            expect(await saveWorkspace("Draft")).toBe("failed");

            expect(chosen.written).toEqual(["workspace v1"]);
            expect(markWorkspaceSaved).toHaveBeenCalledTimes(1);
        });

        test("saveWorkspace_whileASaveRuns_startsNoOther", async () => {
            let pick;
            window.showSaveFilePicker = vi.fn(
                () => new Promise(resolve => (pick = resolve)),
            );

            const first = saveWorkspace("Draft");
            expect(await saveWorkspace("Draft")).toBe("busy");
            expect(await saveWorkspace("Draft", { saveAs: true })).toBe("busy");
            pick(chosen);

            expect(await first).toBe("saved");
            expect(window.showSaveFilePicker).toHaveBeenCalledTimes(1);
            expect(chosen.written).toEqual(["workspace v1"]);
        });

        test("chooseWorkspaceFile_noFileChosen_returnsNull", async () => {
            window.showOpenFilePicker = vi.fn(async () => {
                throw abort();
            });

            expect(await chooseWorkspaceFile()).toBeNull();
        });
    });

    describe("for a file the operating system handed over", () => {
        beforeEach(() => {
            desktopSession.workspacesWithFile = ["Draft"];
        });

        test("saveWorkspace_handedOverFile_letsTheBackendWriteIt", async () => {
            vi.mocked(saveWorkspaceToItsFile).mockResolvedValue(true);
            window.showSaveFilePicker = vi.fn();

            expect(await saveWorkspace("Draft")).toBe("saved");

            expect(saveWorkspaceToItsFile).toHaveBeenCalledWith("Draft");
            expect(window.showSaveFilePicker).not.toHaveBeenCalled();
            expect(fetchWorkspaceFile).not.toHaveBeenCalled();
            expect(refreshDesktopStatus).toHaveBeenCalled();
        });

        test("saveWorkspace_backendHasNoFileAfterAll_savesElsewhere", async () => {
            vi.mocked(saveWorkspaceToItsFile).mockResolvedValue(false);

            expect(await saveWorkspace("Draft")).toBe("saved");

            expect(saveFile).toHaveBeenCalled();
            expect(markWorkspaceSaved).toHaveBeenCalledWith("Draft", "v1");
        });

        test("saveWorkspace_handedOverFileSaveAs_savesElsewhere", async () => {
            await saveWorkspace("Draft", { saveAs: true });

            expect(saveWorkspaceToItsFile).not.toHaveBeenCalled();
            expect(saveFile).toHaveBeenCalled();
        });

        test("workspaceOpenedFromRequest_request_recordsIt", async () => {
            await workspaceOpenedFromRequest("Draft", "request-1");

            expect(markOpenedFrom).toHaveBeenCalledWith("Draft", "request-1");
            expect(refreshDesktopStatus).toHaveBeenCalled();
        });
    });

    test("saveWorkspace_noFileAccess_downloadsTheFile", async () => {
        const result = await saveWorkspace("Draft");

        expect(result).toBe("saved");
        expect(saveFile).toHaveBeenCalledWith(
            expect.any(Blob),
            "Draft.rdfa",
            expect.objectContaining({ fileExtension: ".rdfa" }),
        );
        expect(markWorkspaceSaved).toHaveBeenCalledWith("Draft", "v1");
    });

    test("saveWorkspace_backendFails_reportsIt", async () => {
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
    ])("suggestedFileName_%s_isSafe", (name, expected) => {
        expect(suggestedFileName(name)).toBe(expected);
    });
});
