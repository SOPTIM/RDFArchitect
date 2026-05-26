/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

/**
 * Backend connection monitor.
 *
 * Installs a wrapper around `window.fetch` that detects network-level
 * failures (TypeError from `fetch`) on requests to the backend and surfaces
 * them as a single sticky toast. HTTP error responses (4xx / 5xx) are NOT
 * intercepted — those are application-level errors and remain the
 * responsibility of the individual call site.
 *
 * On the first request that succeeds after an offline period the sticky toast
 * is dismissed and a short "connection restored" toast is shown.
 */

import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";

/** Reactive connection state — components can read `.isOffline` if needed. */
export const backendConnection = $state({ isOffline: false });

let offlineToastId = null;
let installed = false;

/**
 * Resolve the URL of a `fetch()` first argument to a string we can pattern-match.
 * `fetch` accepts string | URL | Request, so we have to handle all three.
 */
function resolveUrl(input) {
    if (typeof input === "string") return input;
    if (input instanceof URL) return input.href;
    if (typeof Request !== "undefined" && input instanceof Request) {
        return input.url;
    }
    return "";
}

function isBackendUrl(url) {
    if (!PUBLIC_BACKEND_URL) return false;
    return url.startsWith(PUBLIC_BACKEND_URL);
}

function isNetworkError(error) {
    if (!error) return false;
    if (error.name === "AbortError") return false;
    return error instanceof TypeError;
}

function markOffline() {
    backendConnection.isOffline = true;
    if (offlineToastId !== null) return;
    offlineToastId = toastStore.error(
        "Backend unreachable",
        "Could not reach the backend. Check that the server is running and try again.",
        { duration: 0 },
    );
}

function markOnline() {
    if (!backendConnection.isOffline) return;
    backendConnection.isOffline = false;
    if (offlineToastId !== null) {
        toastStore.dismiss(offlineToastId);
        offlineToastId = null;
    }
    toastStore.success(
        "Backend connection restored",
        "The connection to the backend has been restored.",
    );
}

/**
 * Install the global fetch interceptor. Safe to call multiple times.
 * Returns an `uninstall` function.
 */
export function installBackendFetchInterceptor() {
    if (typeof window === "undefined") return () => {};
    if (installed) return () => {};
    installed = true;

    const originalFetch = window.fetch.bind(window);

    window.fetch = async function interceptedFetch(input, init) {
        const url = resolveUrl(input);
        const watched = isBackendUrl(url);

        try {
            const response = await originalFetch(input, init);
            if (watched) {
                markOnline();
            }
            return response;
        } catch (error) {
            if (watched && isNetworkError(error)) {
                markOffline();
            }
            throw error;
        }
    };

    return function uninstall() {
        if (!installed) return;
        window.fetch = originalFetch;
        installed = false;
    };
}

/**
 * Active probe — call this on app start so a cold-start with the backend
 * already down surfaces the toast before the user clicks anything.
 */
export async function probeBackendConnection() {
    if (!PUBLIC_BACKEND_URL || typeof window === "undefined") return;
    try {
        await fetch(`${PUBLIC_BACKEND_URL}/datasets`, {
            method: "GET",
            credentials: "include",
        });
    } catch {
        // Ignore errors here; the interceptor will mark offline if it's a network error.
    }
}
