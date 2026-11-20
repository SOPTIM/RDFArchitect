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
    fetchOpenRequest,
    fetchWorkspaceFile,
    fileNameOfContentDisposition,
    markOpenedFrom,
    markWorkspaceSaved,
    saveWorkspaceToItsFile,
    sendHeartbeat,
} from "$lib/desktop/desktopApi.js";

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));

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
    ])("reads %s", (header, expected) => {
        expect(fileNameOfContentDisposition(header)).toBe(expected);
    });
});

describe("desktop API", () => {
    let fetchMock;

    beforeEach(() => {
        fetchMock = vi.fn();
        vi.stubGlobal("fetch", fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    test("reads the workspace file with the version it holds", async () => {
        fetchMock.mockResolvedValue(
            new Response("zip", {
                status: 200,
                headers: {
                    "X-Workspace-Version": "v-1",
                    "Content-Disposition": `attachment; filename="A B.rdfa"`,
                },
            }),
        );

        const file = await fetchWorkspaceFile("A B");

        expect(fetchMock).toHaveBeenCalledWith(
            "/api/desktop/workspaces/A%20B/file",
            { credentials: "include" },
        );
        expect(file.version).toBe("v-1");
        expect(file.fileName).toBe("A B.rdfa");
        expect(await file.blob.text()).toBe("zip");
    });

    test("names a file the backend did not name after the workspace", async () => {
        fetchMock.mockResolvedValue(new Response("zip", { status: 200 }));

        const file = await fetchWorkspaceFile("Draft");

        expect(file.fileName).toBe("Draft.rdfa");
        expect(file.version).toBeNull();
    });

    test("records the version that was saved", async () => {
        fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

        await markWorkspaceSaved("Draft", "v-2");

        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe("/api/desktop/workspaces/Draft/saved");
        expect(init.method).toBe("PUT");
        expect(JSON.parse(init.body)).toEqual({ version: "v-2" });
    });

    test("tells whether the backend had a file to save to", async () => {
        fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
        fetchMock.mockResolvedValueOnce(new Response("none", { status: 409 }));

        expect(await saveWorkspaceToItsFile("Draft")).toBe(true);
        expect(await saveWorkspaceToItsFile("Draft")).toBe(false);
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/desktop/workspaces/Draft/file",
            { method: "POST", credentials: "include" },
        );
    });

    test("reads a file the operating system handed over", async () => {
        fetchMock.mockResolvedValue(
            new Response("zip", {
                headers: {
                    "Content-Disposition": `attachment; filename="Model.rdfa"`,
                },
            }),
        );

        const file = await fetchOpenRequest("request-1");

        expect(fetchMock).toHaveBeenCalledWith(
            "/api/desktop/open-requests/request-1",
            { credentials: "include" },
        );
        expect(file.name).toBe("Model.rdfa");
    });

    test("records where an opened workspace came from", async () => {
        fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

        await markOpenedFrom("My Draft", "request-1");

        expect(fetchMock).toHaveBeenCalledWith(
            "/api/desktop/workspaces/My%20Draft/opened-from/request-1",
            { method: "PUT", credentials: "include" },
        );
    });

    test("reports a failed heartbeat with its status", async () => {
        fetchMock.mockResolvedValue(
            new Response("busy", { status: 503, statusText: "Unavailable" }),
        );

        await expect(sendHeartbeat("w")).rejects.toThrow("503");
    });
});
