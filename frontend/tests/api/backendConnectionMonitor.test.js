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
    backendConnection,
    installBackendFetchInterceptor,
    onBackendWrite,
} from "$lib/api/backendConnectionMonitor.svelte.js";

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));
vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { error: vi.fn(() => 1), success: vi.fn(), dismiss: vi.fn() },
}));

describe("backendConnectionMonitor on the backend's own origin", () => {
    let uninstall;
    let fetchMock;
    let removeListener;
    const writes = [];

    beforeEach(() => {
        fetchMock = vi.fn(async () => new Response("{}"));
        window.fetch = fetchMock;
        uninstall = installBackendFetchInterceptor();
        removeListener = onBackendWrite(url => writes.push(url));
    });

    afterEach(() => {
        removeListener();
        uninstall();
        writes.length = 0;
        backendConnection.isOffline = false;
    });

    test("reports requests that may change something", async () => {
        await window.fetch("/api/workspaces/Draft/undo", { method: "POST" });
        await window.fetch("/api/workspaces/Draft", {
            method: "delete",
        });
        await window.fetch(
            new Request(`${window.location.origin}/api/workspaces/Draft`, {
                method: "PUT",
            }),
        );

        expect(writes).toEqual([
            "/api/workspaces/Draft/undo",
            "/api/workspaces/Draft",
            `${window.location.origin}/api/workspaces/Draft`,
        ]);
    });

    test("does not report reads or requests elsewhere", async () => {
        await window.fetch("/api/workspaces");
        await window.fetch("/env.js", { method: "POST" });
        await window.fetch("https://example.org/api/x", { method: "POST" });

        expect(writes).toEqual([]);
    });

    test("notices a backend that does not answer", async () => {
        fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));

        await expect(window.fetch("/api/workspaces")).rejects.toThrow();

        expect(backendConnection.isOffline).toBe(true);
    });
});
