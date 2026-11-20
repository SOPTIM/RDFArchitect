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

import { onBackendWrite } from "$lib/api/backendConnectionMonitor.svelte.js";
import {
    PUBLIC_DESKTOP,
    PUBLIC_DESKTOP_HEARTBEAT_SECONDS,
} from "$lib/config/runtime";

import { sendGoodbye, sendHeartbeat } from "./desktopApi.js";

/** Heartbeats that may fail in a row before the backend counts as stopped. */
const MISSED_HEARTBEATS_UNTIL_STOPPED = 2;

/** How long to wait after a change before asking again, so a burst of requests asks once. */
const REFRESH_DELAY_MS = 200;

/**
 * What this window of the desktop app knows about its backend.
 *
 * The desktop backend keeps every workspace in memory and stops when its last window closes (see
 * `ClientPresence`), so this window reports that it is open, and learns in return which
 * workspaces hold changes no file has — the ones closing the window would lose.
 */
export const desktopSession = $state({
    /** Whether this window takes part, i.e. this is the desktop app. */
    active: false,
    /** The workspaces with unsaved changes, as of the last heartbeat. */
    unsavedWorkspaces: [],
    /** Whether the backend has stopped answering, which leaves this window without its data. */
    stopped: false,
});

let windowId = null;
let timer = null;
let inFlight = null;
let refreshQueued = false;
let missedHeartbeats = 0;
let refreshTimer = null;
let stopListeningToWrites = () => {};

export function isWorkspaceUnsaved(workspaceName) {
    return desktopSession.unsavedWorkspaces.includes(workspaceName);
}

/**
 * Starts reporting this window to the desktop backend. Does nothing outside the desktop app.
 *
 * @returns {() => void} stops reporting again
 */
export function startDesktopSession() {
    if (!PUBLIC_DESKTOP || desktopSession.active) {
        return () => {};
    }
    desktopSession.active = true;
    windowId = crypto.randomUUID();
    window.addEventListener("beforeunload", warnBeforeUnload);
    window.addEventListener("pagehide", sayGoodbye);
    document.addEventListener("visibilitychange", heartbeatWhenShown);
    stopListeningToWrites = onBackendWrite(url => {
        if (!url.includes("/api/desktop/")) {
            scheduleRefresh();
        }
    });
    void heartbeat();
    timer = setInterval(heartbeat, PUBLIC_DESKTOP_HEARTBEAT_SECONDS * 1000);
    return stopDesktopSession;
}

export function stopDesktopSession() {
    if (!desktopSession.active) {
        return;
    }
    clearInterval(timer);
    timer = null;
    clearTimeout(refreshTimer);
    refreshTimer = null;
    stopListeningToWrites();
    window.removeEventListener("beforeunload", warnBeforeUnload);
    window.removeEventListener("pagehide", sayGoodbye);
    document.removeEventListener("visibilitychange", heartbeatWhenShown);
    sayGoodbye();
    desktopSession.active = false;
    desktopSession.unsavedWorkspaces = [];
    desktopSession.stopped = false;
    missedHeartbeats = 0;
}

/**
 * Asks the backend again which workspaces are unsaved, after something may have changed one.
 * Calls arriving while a heartbeat is under way are folded into one more after it.
 */
export function refreshDesktopStatus() {
    if (!desktopSession.active) {
        return Promise.resolve();
    }
    return heartbeat();
}

function scheduleRefresh() {
    clearTimeout(refreshTimer);
    refreshTimer = setTimeout(() => {
        refreshTimer = null;
        void refreshDesktopStatus();
    }, REFRESH_DELAY_MS);
}

function heartbeat() {
    if (inFlight) {
        refreshQueued = true;
        return inFlight;
    }
    inFlight = sendHeartbeat(windowId)
        .then(status => {
            missedHeartbeats = 0;
            desktopSession.unsavedWorkspaces = status?.unsavedWorkspaces ?? [];
        })
        .catch(error => {
            // A network error means nobody listens any more; an error status is a backend
            // that is still there.
            if (error instanceof TypeError) {
                missedHeartbeats += 1;
                if (missedHeartbeats >= MISSED_HEARTBEATS_UNTIL_STOPPED) {
                    desktopSession.stopped = true;
                }
            } else {
                console.warn("[desktopSession] Heartbeat failed", error);
            }
        })
        .finally(() => {
            inFlight = null;
            if (refreshQueued && desktopSession.active) {
                refreshQueued = false;
                void heartbeat();
            }
        });
    return inFlight;
}

function heartbeatWhenShown() {
    if (document.visibilityState === "visible") {
        void heartbeat();
    }
}

function sayGoodbye() {
    if (windowId && !desktopSession.stopped) {
        sendGoodbye(windowId);
    }
}

/**
 * Asks before closing a window that would take unsaved work with it. Browsers show a text of
 * their own; the page only decides whether to ask.
 */
function warnBeforeUnload(event) {
    if (
        desktopSession.stopped ||
        desktopSession.unsavedWorkspaces.length === 0
    ) {
        return;
    }
    event.preventDefault();
    // Still needed by some browsers to show the question at all.
    event.returnValue = "";
}
