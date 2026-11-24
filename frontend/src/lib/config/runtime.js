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

import { env as publicEnv } from "$env/dynamic/public";

import { browser } from "$app/environment";

const runtimeConfig = browser ? window.__RDFARCHITECT_CONFIG__ : {};

export const PUBLIC_BACKEND_URL = readPublicValue("PUBLIC_BACKEND_URL");

export const PUBLIC_APP_VERSION = readPublicValue("PUBLIC_APP_VERSION");
export const PUBLIC_COMMIT_SHA = readPublicValue("PUBLIC_COMMIT_SHA");
export const PUBLIC_REPOSITORY_URL = readPublicValue("PUBLIC_REPOSITORY_URL");
export const PUBLIC_DEPLOYMENT_ENVIRONMENT = readPublicValue(
    "PUBLIC_DEPLOYMENT_ENVIRONMENT",
);

/**
 * Whether an embedding host may ask this app which backend session it is using
 * (see `$lib/embedding/session-handshake.js`). Off unless the deployment sets it,
 * because the session id grants access to the session's datasets.
 */
export const PUBLIC_EMBED_SESSION_HANDSHAKE =
    readPublicValue("PUBLIC_EMBED_SESSION_HANDSHAKE").toLowerCase() === "true";

/**
 * Whether this is the desktop app: the backend runs on the user's own machine, serves this page
 * and stops when its last window closes, so unsaved work is lost on closing. Set by the backend's
 * own `/env.js` (see `RuntimeConfigController`).
 */
export const PUBLIC_DESKTOP =
    readPublicValue("PUBLIC_DESKTOP").toLowerCase() === "true";

/** How often a desktop window tells the backend it is still open, in seconds. */
export const PUBLIC_DESKTOP_HEARTBEAT_SECONDS = readPositiveNumber(
    "PUBLIC_DESKTOP_HEARTBEAT_SECONDS",
    10,
);

/**
 * Whether Share Snapshot is offered. On unless the deployment switches it off, which it does
 * together with the backend's `snapshots.enabled`.
 */
export const PUBLIC_SHARE_SNAPSHOT_ENABLED =
    readPublicValue("PUBLIC_SHARE_SNAPSHOT_ENABLED").toLowerCase() !== "false";

function readPositiveNumber(key, fallback) {
    const value = Number(readPublicValue(key));
    return Number.isFinite(value) && value > 0 ? value : fallback;
}

function readPublicValue(key) {
    const runtimeValue = runtimeConfig?.[key];
    if (typeof runtimeValue === "string" && runtimeValue.trim()) {
        return runtimeValue.trim();
    }

    const envValue = publicEnv[key];
    return typeof envValue === "string" ? envValue.trim() : "";
}
