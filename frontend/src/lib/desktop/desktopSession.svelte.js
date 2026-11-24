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

const unappliedEditChecks = new Set();

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
    /** The workspaces whose file the backend writes on Save, as of the last heartbeat. */
    workspacesWithFile: [],
    /**
     * The id of each workspace by its name, as of the last heartbeat. An id stays through renames
     * and is never given to a workspace created anew under an old name.
     *
     * @type {Record<string, string>}
     */
    workspaceIds: {},
    /**
     * Whether the backend has stopped answering, or a backend started anew answers instead, which
     * leaves this window without its data.
     */
    stopped: false,
});

let windowId = null;
let timer = null;
let inFlight = null;
let queued = null;
let missedHeartbeats = 0;
/** The run of the backend this window started with, see `DesktopStatusDTO.instanceId`. */
let instanceId = null;
let refreshTimer = null;
let stopListeningToWrites = () => {};
let releaseWindowLock = () => {};

/**
 * Registers a check for edits this window holds that no request has sent yet, such as those of
 * an open class editor. Closing the window asks first while one of them answers true.
 *
 * @param {() => boolean} check
 * @returns {() => void} removes the check again
 */
export function registerUnappliedEdits(check) {
    unappliedEditChecks.add(check);
    return () => unappliedEditChecks.delete(check);
}

function holdsUnappliedEdits() {
    return [...unappliedEditChecks].some(check => check());
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
    releaseWindowLock = holdWindowLock(windowId);
    void heartbeat();
    timer = setInterval(heartbeat, PUBLIC_DESKTOP_HEARTBEAT_SECONDS * 1000);
    return stopDesktopSession;
}

/**
 * Holds a Web Lock for as long as the window reports itself. Browsers do not freeze a hidden page
 * that holds one, and a frozen page sends no heartbeats.
 *
 * @param {string} id the window
 * @returns {() => void} releases the lock
 */
function holdWindowLock(id) {
    if (!navigator.locks) {
        return () => {};
    }
    let release;
    const released = new Promise(resolve => {
        release = resolve;
    });
    navigator.locks
        .request(`rdfarchitect-window-${id}`, () => released)
        .catch(error =>
            console.warn("[desktopSession] No lock for this window", error),
        );
    return release;
}

/** Stops reporting this window to the desktop backend and says goodbye to it. */
export function stopDesktopSession() {
    if (!desktopSession.active) {
        return;
    }
    clearInterval(timer);
    timer = null;
    clearTimeout(refreshTimer);
    refreshTimer = null;
    stopListeningToWrites();
    releaseWindowLock();
    window.removeEventListener("beforeunload", warnBeforeUnload);
    window.removeEventListener("pagehide", sayGoodbye);
    document.removeEventListener("visibilitychange", heartbeatWhenShown);
    sayGoodbye();
    desktopSession.active = false;
    desktopSession.unsavedWorkspaces = [];
    desktopSession.workspacesWithFile = [];
    desktopSession.workspaceIds = {};
    desktopSession.stopped = false;
    missedHeartbeats = 0;
    instanceId = null;
}

/**
 * Asks the backend again which workspaces are unsaved, after something may have changed one.
 * Calls arriving while a heartbeat is under way are folded into one more after it, and resolve
 * once that one has answered, so the answer covers what changed before the call.
 *
 * @returns {Promise<void>}
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
    if (!inFlight) {
        inFlight = sendOneHeartbeat().finally(() => {
            inFlight = null;
        });
        return inFlight;
    }
    queued ??= inFlight.then(() => {
        queued = null;
        return desktopSession.active ? heartbeat() : undefined;
    });
    return queued;
}

async function sendOneHeartbeat() {
    try {
        const status = await sendHeartbeat(windowId);
        missedHeartbeats = 0;
        instanceId ??= status?.instanceId ?? null;
        if (status?.instanceId && status.instanceId !== instanceId) {
            // Started anew since: none of what this window shows exists there.
            desktopSession.stopped = true;
            return;
        }
        desktopSession.stopped = false;
        desktopSession.unsavedWorkspaces = status?.unsavedWorkspaces ?? [];
        desktopSession.workspacesWithFile = status?.workspacesWithFile ?? [];
        desktopSession.workspaceIds = status?.workspaceIds ?? {};
    } catch (error) {
        // A network error means nobody listens any more, and 401 a backend that does not know
        // this window's access token, one started anew. Other error statuses come from the
        // backend this window belongs to.
        if (error?.status === 401) {
            desktopSession.stopped = true;
        } else if (error instanceof TypeError) {
            missedHeartbeats += 1;
            if (missedHeartbeats >= MISSED_HEARTBEATS_UNTIL_STOPPED) {
                desktopSession.stopped = true;
            }
        } else {
            console.warn("[desktopSession] Heartbeat failed", error);
        }
    }
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
        (desktopSession.unsavedWorkspaces.length === 0 &&
            !holdsUnappliedEdits())
    ) {
        return;
    }
    event.preventDefault();
    // Still needed by some browsers to show the question at all.
    event.returnValue = "";
}
