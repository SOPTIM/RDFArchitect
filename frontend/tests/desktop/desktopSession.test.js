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

import { onBackendWrite } from "$lib/api/backendConnectionMonitor.svelte.js";
import { sendGoodbye, sendHeartbeat } from "$lib/desktop/desktopApi.js";
import {
    desktopSession,
    isWorkspaceUnsaved,
    refreshDesktopStatus,
    startDesktopSession,
    stopDesktopSession,
} from "$lib/desktop/desktopSession.svelte.js";

function beforeUnload() {
    const event = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(event);
    return event;
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "",
    PUBLIC_DESKTOP: true,
    PUBLIC_DESKTOP_HEARTBEAT_SECONDS: 10,
}));
vi.mock("$lib/desktop/desktopApi.js", () => ({
    sendHeartbeat: vi.fn(),
    sendGoodbye: vi.fn(),
}));
vi.mock("$lib/api/backendConnectionMonitor.svelte.js", () => ({
    onBackendWrite: vi.fn(() => () => {}),
}));

describe("desktopSession", () => {
    beforeEach(() => {
        vi.useFakeTimers();
        vi.mocked(sendHeartbeat).mockReset();
        vi.mocked(sendGoodbye).mockReset();
        vi.mocked(onBackendWrite).mockClear();
        vi.mocked(sendHeartbeat).mockResolvedValue({ unsavedWorkspaces: [] });
    });

    afterEach(() => {
        stopDesktopSession();
        vi.useRealTimers();
    });

    test("reports the window right away and then periodically", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);
        expect(sendHeartbeat).toHaveBeenCalledTimes(1);

        await vi.advanceTimersByTimeAsync(30_000);
        expect(sendHeartbeat).toHaveBeenCalledTimes(4);
        const windowIds = new Set(
            vi.mocked(sendHeartbeat).mock.calls.map(([id]) => id),
        );
        expect(windowIds.size).toBe(1);
    });

    test("learns which workspaces are unsaved", async () => {
        vi.mocked(sendHeartbeat).mockResolvedValue({
            unsavedWorkspaces: ["Draft"],
        });

        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);

        expect(desktopSession.unsavedWorkspaces).toEqual(["Draft"]);
        expect(isWorkspaceUnsaved("Draft")).toBe(true);
        expect(isWorkspaceUnsaved("Other")).toBe(false);
    });

    test("asks before closing a window with unsaved work only", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);
        expect(beforeUnload().defaultPrevented).toBe(false);

        vi.mocked(sendHeartbeat).mockResolvedValue({
            unsavedWorkspaces: ["Draft"],
        });
        await refreshDesktopStatus();
        expect(beforeUnload().defaultPrevented).toBe(true);
    });

    test("says goodbye when the page goes away", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);

        window.dispatchEvent(new Event("pagehide"));

        expect(sendGoodbye).toHaveBeenCalledWith(
            vi.mocked(sendHeartbeat).mock.calls[0][0],
        );
    });

    test("counts the backend as stopped once it no longer answers", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);
        vi.mocked(sendHeartbeat).mockRejectedValue(
            new TypeError("Failed to fetch"),
        );

        await vi.advanceTimersByTimeAsync(10_000);
        expect(desktopSession.stopped).toBe(false);
        await vi.advanceTimersByTimeAsync(10_000);
        expect(desktopSession.stopped).toBe(true);

        // Nothing is left to lose and nobody to say goodbye to.
        vi.mocked(sendHeartbeat).mockResolvedValue({
            unsavedWorkspaces: ["Draft"],
        });
        desktopSession.unsavedWorkspaces = ["Draft"];
        expect(beforeUnload().defaultPrevented).toBe(false);
    });

    test("does not count an error answer as a stopped backend", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);
        vi.mocked(sendHeartbeat).mockRejectedValue(new Error("500"));
        vi.spyOn(console, "warn").mockImplementation(() => {});

        await vi.advanceTimersByTimeAsync(60_000);

        expect(desktopSession.stopped).toBe(false);
    });

    test("asks again shortly after a change elsewhere, but not after its own requests", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);
        const listener = vi.mocked(onBackendWrite).mock.calls[0][0];
        vi.mocked(sendHeartbeat).mockClear();

        listener("/api/desktop/workspaces/Draft/saved");
        await vi.advanceTimersByTimeAsync(500);
        expect(sendHeartbeat).not.toHaveBeenCalled();

        listener("/api/datasets/Draft/graphs/g/classes");
        listener("/api/datasets/Draft/graphs/g/classes");
        await vi.advanceTimersByTimeAsync(500);
        expect(sendHeartbeat).toHaveBeenCalledTimes(1);
    });

    test("folds refreshes during a heartbeat into one more", async () => {
        let answer;
        vi.mocked(sendHeartbeat).mockImplementation(
            () => new Promise(resolve => (answer = resolve)),
        );
        startDesktopSession();
        refreshDesktopStatus();
        refreshDesktopStatus();
        expect(sendHeartbeat).toHaveBeenCalledTimes(1);

        vi.mocked(sendHeartbeat).mockResolvedValue({ unsavedWorkspaces: [] });
        answer({ unsavedWorkspaces: [] });
        await vi.advanceTimersByTimeAsync(0);

        expect(sendHeartbeat).toHaveBeenCalledTimes(2);
    });
});
