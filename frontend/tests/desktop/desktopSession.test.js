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
    refreshDesktopStatus,
    registerUnappliedEdits,
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

    test("startDesktopSession_running_reportsTheWindowAtOnceAndPeriodically", async () => {
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

    test("heartbeat_answer_learnsUnsavedWorkspacesAndIds", async () => {
        vi.mocked(sendHeartbeat).mockResolvedValue({
            instanceId: "run-1",
            unsavedWorkspaces: ["Draft"],
            workspaceIds: { Draft: "id-1" },
        });

        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);

        expect(desktopSession.unsavedWorkspaces).toEqual(["Draft"]);
        expect(desktopSession.workspaceIds).toEqual({ Draft: "id-1" });
    });

    test("warnBeforeUnload_unsavedWork_asksOnlyThen", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);
        expect(beforeUnload().defaultPrevented).toBe(false);

        vi.mocked(sendHeartbeat).mockResolvedValue({
            unsavedWorkspaces: ["Draft"],
        });
        await refreshDesktopStatus();
        expect(beforeUnload().defaultPrevented).toBe(true);
    });

    test("warnBeforeUnload_unappliedEdits_asks", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);
        let edited = true;
        const unregister = registerUnappliedEdits(() => edited);

        expect(beforeUnload().defaultPrevented).toBe(true);
        edited = false;
        expect(beforeUnload().defaultPrevented).toBe(false);
        edited = true;
        unregister();
        expect(beforeUnload().defaultPrevented).toBe(false);
    });

    test("sayGoodbye_pageHidden_tellsTheBackend", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);

        window.dispatchEvent(new Event("pagehide"));

        expect(sendGoodbye).toHaveBeenCalledWith(
            vi.mocked(sendHeartbeat).mock.calls[0][0],
        );
    });

    test("heartbeat_noAnswerTwice_countsTheBackendAsStopped", async () => {
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

    test("heartbeat_answerAfterAShortOutage_countsTheBackendAsRunningAgain", async () => {
        vi.mocked(sendHeartbeat).mockResolvedValue({
            instanceId: "run-1",
            unsavedWorkspaces: ["Draft"],
        });
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);
        vi.mocked(sendHeartbeat).mockRejectedValue(
            new TypeError("Failed to fetch"),
        );
        await vi.advanceTimersByTimeAsync(20_000);
        expect(desktopSession.stopped).toBe(true);

        vi.mocked(sendHeartbeat).mockResolvedValue({
            instanceId: "run-1",
            unsavedWorkspaces: ["Draft"],
        });
        await vi.advanceTimersByTimeAsync(10_000);

        expect(desktopSession.stopped).toBe(false);
        expect(beforeUnload().defaultPrevented).toBe(true);
    });

    test("heartbeat_backendStartedAnew_staysStopped", async () => {
        vi.mocked(sendHeartbeat).mockResolvedValue({
            instanceId: "run-1",
            unsavedWorkspaces: ["Draft"],
        });
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);

        vi.mocked(sendHeartbeat).mockResolvedValue({
            instanceId: "run-2",
            unsavedWorkspaces: [],
        });
        await vi.advanceTimersByTimeAsync(10_000);

        expect(desktopSession.stopped).toBe(true);
        expect(desktopSession.unsavedWorkspaces).toEqual(["Draft"]);
    });

    test("heartbeat_accessTokenRefused_countsTheBackendAsStopped", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);
        vi.mocked(sendHeartbeat).mockRejectedValue(
            Object.assign(new Error("401 Unauthorized"), { status: 401 }),
        );

        await vi.advanceTimersByTimeAsync(10_000);

        expect(desktopSession.stopped).toBe(true);
    });

    test("heartbeat_errorAnswer_doesNotCountAsStopped", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);
        vi.mocked(sendHeartbeat).mockRejectedValue(new Error("500"));
        vi.spyOn(console, "warn").mockImplementation(() => {});

        await vi.advanceTimersByTimeAsync(60_000);

        expect(desktopSession.stopped).toBe(false);
    });

    test("scheduleRefresh_writeElsewhere_asksAgainButNotForItsOwnRequests", async () => {
        startDesktopSession();
        await vi.advanceTimersByTimeAsync(0);
        const listener = vi.mocked(onBackendWrite).mock.calls[0][0];
        vi.mocked(sendHeartbeat).mockClear();

        listener("/api/desktop/workspaces/Draft/saved");
        await vi.advanceTimersByTimeAsync(500);
        expect(sendHeartbeat).not.toHaveBeenCalled();

        listener("/api/workspaces/Draft/schemas/g/classes");
        listener("/api/workspaces/Draft/schemas/g/classes");
        await vi.advanceTimersByTimeAsync(500);
        expect(sendHeartbeat).toHaveBeenCalledTimes(1);
    });

    test("refreshDesktopStatus_duringAHeartbeat_foldsIntoOneMore", async () => {
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

    test("refreshDesktopStatus_duringAHeartbeat_resolvesWithTheNextAnswer", async () => {
        let answerFirst;
        vi.mocked(sendHeartbeat).mockImplementationOnce(
            () => new Promise(resolve => (answerFirst = resolve)),
        );
        startDesktopSession();
        vi.mocked(sendHeartbeat).mockResolvedValue({
            unsavedWorkspaces: [],
            workspacesWithFile: ["Handed"],
        });

        const refreshed = refreshDesktopStatus();
        answerFirst({ unsavedWorkspaces: [], workspacesWithFile: [] });
        await refreshed;

        expect(desktopSession.workspacesWithFile).toEqual(["Handed"]);
    });
});
