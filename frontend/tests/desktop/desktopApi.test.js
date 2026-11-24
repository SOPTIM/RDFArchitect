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

import { beforeEach, describe, expect, test, vi } from "vitest";

import * as api from "$lib/api/generated/index.ts";
import {
    discardRecoveredWorkspace,
    fetchOpenRequest,
    fetchRecoveredWorkspace,
    fetchWorkspaceFile,
    fileNameOfContentDisposition,
    markOpenedFrom,
    markWorkspaceSaved,
    saveWorkspaceToItsFile,
    sendHeartbeat,
} from "$lib/desktop/desktopApi.js";

/** What the generated client answers: data and response, or the error body and response. */
function answer(status, { data, headers, error } = {}) {
    const response = new Response(null, { status, headers });
    return status < 400 ? { data, response } : { error, response };
}

vi.mock("$lib/api/generated/index.ts", () => ({
    discardRecoveredWorkspace: vi.fn(),
    getOpenRequestFile: vi.fn(),
    getRecoveredWorkspace: vi.fn(),
    getWorkspaceFile: vi.fn(),
    listRecoveredWorkspaces: vi.fn(),
    markOpenedFromRequest: vi.fn(),
    markWorkspaceSaved: vi.fn(),
    reportWindowClosed: vi.fn(),
    reportWindowOpen: vi.fn(),
    saveWorkspaceToFile: vi.fn(),
}));

describe("fileNameOfContentDisposition", () => {
    test.each([
        [`attachment; filename="Draft.rdfa"`, "Draft.rdfa"],
        [`attachment; filename=Draft.rdfa`, "Draft.rdfa"],
        [
            `attachment; filename="Entw_rf.rdfa"; filename*=UTF-8''Entw%C3%BCrf.rdfa`,
            "Entwürf.rdfa",
        ],
        ["", null],
        [null, null],
    ])("fileNameOfContentDisposition_%s_returnsTheName", (header, expected) => {
        expect(fileNameOfContentDisposition(header)).toBe(expected);
    });
});

describe("desktop API", () => {
    beforeEach(() => {
        vi.useRealTimers();
        Object.values(api).forEach(fn => fn.mockReset?.());
    });

    test("fetchWorkspaceFile_answer_returnsBlobVersionIdAndName", async () => {
        const blob = new Blob(["zip"]);
        vi.mocked(api.getWorkspaceFile).mockResolvedValue(
            answer(200, {
                data: blob,
                headers: {
                    "X-Workspace-Version": "v-1:editable",
                    "X-Workspace-Id": "id-1",
                    "Content-Disposition": `attachment; filename="A B.rdfa"`,
                },
            }),
        );

        const file = await fetchWorkspaceFile("A B");

        expect(api.getWorkspaceFile).toHaveBeenCalledWith({
            path: { workspaceName: "A B" },
            parseAs: "blob",
        });
        expect(file).toEqual({
            blob,
            version: "v-1:editable",
            workspaceId: "id-1",
            fileName: "A B.rdfa",
        });
    });

    test("fetchWorkspaceFile_noFileName_namesItAfterTheWorkspace", async () => {
        vi.mocked(api.getWorkspaceFile).mockResolvedValue(
            answer(200, { data: new Blob(["zip"]) }),
        );

        const file = await fetchWorkspaceFile("Draft");

        expect(file.fileName).toBe("Draft.rdfa");
        expect(file.version).toBeNull();
    });

    test("markWorkspaceSaved_version_sendsIt", async () => {
        vi.mocked(api.markWorkspaceSaved).mockResolvedValue(answer(204));

        await markWorkspaceSaved("Draft", "v-2:editable");

        expect(api.markWorkspaceSaved).toHaveBeenCalledWith({
            path: { workspaceName: "Draft" },
            body: { version: "v-2:editable" },
        });
    });

    test("markWorkspaceSaved_workspaceBeingWritten_asksAgain", async () => {
        vi.useFakeTimers();
        vi.mocked(api.markWorkspaceSaved)
            .mockResolvedValueOnce(answer(409))
            .mockResolvedValueOnce(answer(204));

        const saved = markWorkspaceSaved("Draft");
        await vi.runAllTimersAsync();
        await saved;

        expect(api.markWorkspaceSaved).toHaveBeenCalledTimes(2);
    });

    test("saveWorkspaceToItsFile_answer_tellsWhetherThereWasAFile", async () => {
        vi.mocked(api.saveWorkspaceToFile)
            .mockResolvedValueOnce(answer(204))
            .mockResolvedValueOnce(answer(409, { error: "none" }));

        expect(await saveWorkspaceToItsFile("Draft")).toBe(true);
        expect(await saveWorkspaceToItsFile("Draft")).toBe(false);
    });

    test("fetchOpenRequest_answer_returnsANamedFile", async () => {
        vi.mocked(api.getOpenRequestFile).mockResolvedValue(
            answer(200, {
                data: new Blob(["zip"]),
                headers: {
                    "Content-Disposition": `attachment; filename="Model.rdfa"`,
                },
            }),
        );

        const file = await fetchOpenRequest("request-1");

        expect(api.getOpenRequestFile).toHaveBeenCalledWith({
            path: { requestId: "request-1" },
            parseAs: "blob",
        });
        expect(file.name).toBe("Model.rdfa");
    });

    test("markOpenedFrom_workspaceBeingWritten_asksAgainWithTheSameRequest", async () => {
        vi.useFakeTimers();
        vi.mocked(api.markOpenedFromRequest)
            .mockResolvedValueOnce(answer(409))
            .mockResolvedValueOnce(answer(204));

        const marked = markOpenedFrom("My Draft", "request-1");
        await vi.runAllTimersAsync();
        await marked;

        expect(api.markOpenedFromRequest).toHaveBeenCalledTimes(2);
        expect(api.markOpenedFromRequest).toHaveBeenLastCalledWith({
            path: { workspaceName: "My Draft", requestId: "request-1" },
        });
    });

    test("sendHeartbeat_errorAnswer_throwsWithTheStatus", async () => {
        vi.mocked(api.reportWindowOpen).mockResolvedValue(
            answer(503, { error: "busy" }),
        );

        await expect(sendHeartbeat("w")).rejects.toMatchObject({
            status: 503,
        });
    });

    test("sendHeartbeat_noBackend_throwsTheNetworkError", async () => {
        const failure = new TypeError("Failed to fetch");
        vi.mocked(api.reportWindowOpen).mockResolvedValue({ error: failure });

        await expect(sendHeartbeat("w")).rejects.toBe(failure);
    });

    test("fetchRecoveredWorkspace_gone_returnsNull", async () => {
        vi.mocked(api.getRecoveredWorkspace).mockResolvedValue(answer(404));

        expect(await fetchRecoveredWorkspace("Draft-1.rdfa")).toBeNull();
    });

    test("discardRecoveredWorkspace_goneAlready_doesNotComplain", async () => {
        vi.mocked(api.discardRecoveredWorkspace).mockResolvedValue(answer(404));

        await expect(
            discardRecoveredWorkspace("Draft-1.rdfa"),
        ).resolves.toBeUndefined();
    });
});
