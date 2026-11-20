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

import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";

/**
 * The part of the API only the desktop app has (`DesktopRESTController`). It is not in the
 * OpenAPI contract, which describes the server deployment, so it is called directly.
 */

/** Names the version of the workspace a workspace file holds. */
export const WORKSPACE_VERSION_HEADER = "X-Workspace-Version";

function desktopUrl(path) {
    return `${PUBLIC_BACKEND_URL}/api/desktop${path}`;
}

function workspaceUrl(workspaceName, path) {
    return desktopUrl(
        `/workspaces/${encodeURIComponent(workspaceName)}${path}`,
    );
}

async function failure(response) {
    const text = await response.text().catch(() => "");
    return new Error(
        `${response.status} ${response.statusText}${text ? `: ${text}` : ""}`,
    );
}

/**
 * Tells the backend the window is still open.
 *
 * @returns {Promise<{unsavedWorkspaces: string[]}>}
 */
export async function sendHeartbeat(windowId) {
    const response = await fetch(
        desktopUrl(`/windows/${encodeURIComponent(windowId)}/heartbeat`),
        { method: "POST", credentials: "include" },
    );
    if (!response.ok) {
        throw await failure(response);
    }
    return response.json();
}

/**
 * Tells the backend the window is closing. A beacon, because it has to go out while the page
 * unloads, when a `fetch` would be cancelled.
 */
export function sendGoodbye(windowId) {
    const url = desktopUrl(`/windows/${encodeURIComponent(windowId)}/goodbye`);
    if (navigator.sendBeacon?.(url)) {
        return;
    }
    fetch(url, {
        method: "POST",
        credentials: "include",
        keepalive: true,
    }).catch(() => {});
}

/**
 * Reads the workspace as a workspace file.
 *
 * @returns {Promise<{blob: Blob, version: string | null, fileName: string}>}
 */
export async function fetchWorkspaceFile(workspaceName) {
    const response = await fetch(workspaceUrl(workspaceName, "/file"), {
        credentials: "include",
    });
    if (!response.ok) {
        throw await failure(response);
    }
    return {
        blob: await response.blob(),
        version: response.headers.get(WORKSPACE_VERSION_HEADER),
        fileName:
            fileNameOfContentDisposition(
                response.headers.get("Content-Disposition"),
            ) ?? `${workspaceName}.rdfa`,
    };
}

/**
 * Records that the workspace was written to a file.
 *
 * @param {string} workspaceName
 * @param {string | null} version the version the file holds, `null` for the one it stands on
 */
export async function markWorkspaceSaved(workspaceName, version = null) {
    const response = await fetch(workspaceUrl(workspaceName, "/saved"), {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ version }),
    });
    if (!response.ok) {
        throw await failure(response);
    }
}

/**
 * Saves the workspace to the file the backend knows for it, one the operating system handed over.
 *
 * @returns {Promise<boolean>} whether it had such a file
 */
export async function saveWorkspaceToItsFile(workspaceName) {
    const response = await fetch(workspaceUrl(workspaceName, "/file"), {
        method: "POST",
        credentials: "include",
    });
    if (response.status === 409) {
        return false;
    }
    if (!response.ok) {
        throw await failure(response);
    }
    return true;
}

/**
 * Reads a workspace file the operating system asked the app to open.
 *
 * @returns {Promise<File>}
 */
export async function fetchOpenRequest(id) {
    const response = await fetch(
        desktopUrl(`/open-requests/${encodeURIComponent(id)}`),
        { credentials: "include" },
    );
    if (!response.ok) {
        throw await failure(response);
    }
    const fileName =
        fileNameOfContentDisposition(
            response.headers.get("Content-Disposition"),
        ) ?? "workspace.rdfa";
    return new File([await response.blob()], fileName, {
        type: "application/zip",
    });
}

/** Records that the workspace was opened from the file of an open request. */
export async function markOpenedFrom(workspaceName, requestId) {
    const response = await fetch(
        workspaceUrl(
            workspaceName,
            `/opened-from/${encodeURIComponent(requestId)}`,
        ),
        { method: "PUT", credentials: "include" },
    );
    if (!response.ok) {
        throw await failure(response);
    }
}

/** The file name a `Content-Disposition` header names, preferring its UTF-8 form. */
export function fileNameOfContentDisposition(header) {
    if (!header) {
        return null;
    }
    const extended = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header);
    if (extended) {
        try {
            return decodeURIComponent(extended[1].trim());
        } catch {
            // Fall back to the plain form.
        }
    }
    const plain = /filename\s*=\s*"([^"]*)"|filename\s*=\s*([^;]+)/i.exec(
        header,
    );
    if (plain) {
        return (plain[1] ?? plain[2]).trim();
    }
    return null;
}
