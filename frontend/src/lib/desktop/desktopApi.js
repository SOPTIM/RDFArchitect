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

import {
    discardRecoveredWorkspace as requestDiscardRecoveredWorkspace,
    getOpenRequestFile,
    getRecoveredWorkspace,
    getWorkspaceFile,
    listRecoveredWorkspaces as requestRecoveredWorkspaces,
    markOpenedFromRequest,
    markWorkspaceSaved as requestMarkWorkspaceSaved,
    reportWindowClosed,
    reportWindowOpen,
    saveWorkspaceToFile,
} from "$lib/api/generated/index.ts";

/**
 * The part of the API only the desktop app has (tag `desktop`), through the generated client.
 * Each function throws on failure: a `TypeError` when the backend cannot be reached, a
 * {@link DesktopApiError} when it answers with an error.
 */

/** Names the version of the workspace a workspace file holds. */
export const WORKSPACE_VERSION_HEADER = "X-Workspace-Version";

/** Names the workspace a workspace file holds, by an id that stays through renames. */
export const WORKSPACE_ID_HEADER = "X-Workspace-Id";

/** How often to ask again while the backend answers that the workspace is being written to. */
const BUSY_RETRIES = 10;
const BUSY_RETRY_DELAY_MS = 200;

async function call(request) {
    const { data, error, response } = await request;
    if (response?.ok) {
        return { data, response };
    }
    if (!response) {
        throw error;
    }
    throw new DesktopApiError(response, error);
}

async function retryWhileBusy(send) {
    for (let attempt = 0; ; attempt++) {
        try {
            return await send();
        } catch (error) {
            if (error?.status !== 409 || attempt >= BUSY_RETRIES) {
                throw error;
            }
            await new Promise(resolve =>
                setTimeout(resolve, BUSY_RETRY_DELAY_MS),
            );
        }
    }
}

/**
 * Tells the backend the window is still open.
 *
 * @param {string} windowId
 * @returns {Promise<{instanceId?: string, unsavedWorkspaces?: string[], workspacesWithFile?: string[], workspaceIds?: Record<string, string>}>}
 */
export async function sendHeartbeat(windowId) {
    const { data } = await call(reportWindowOpen({ path: { windowId } }));
    return data;
}

/**
 * Tells the backend the window is closing. The request outlives the page, which is unloading.
 *
 * @param {string} windowId
 */
export function sendGoodbye(windowId) {
    reportWindowClosed({ path: { windowId }, keepalive: true }).catch(() => {});
}

/**
 * Reads the workspace as a workspace file.
 *
 * @param {string} workspaceName
 * @returns {Promise<{blob: Blob, version: string | null, workspaceId: string | null, fileName: string}>}
 */
export async function fetchWorkspaceFile(workspaceName) {
    const { data, response } = await call(
        getWorkspaceFile({ path: { workspaceName }, parseAs: "blob" }),
    );
    return {
        blob: data,
        version: response.headers.get(WORKSPACE_VERSION_HEADER),
        workspaceId: response.headers.get(WORKSPACE_ID_HEADER),
        fileName:
            fileNameOfContentDisposition(
                response.headers.get("Content-Disposition"),
            ) ?? `${workspaceName}.rdfa`,
    };
}

/**
 * Records that the workspace was written to a file. Without a version, asks again for a moment
 * while the workspace is being written to.
 *
 * @param {string} workspaceName
 * @param {string | null} [version] the version the file holds, `null` for the one it stands on
 */
export async function markWorkspaceSaved(workspaceName, version = null) {
    await retryWhileBusy(() =>
        call(
            requestMarkWorkspaceSaved({
                path: { workspaceName },
                body: { version },
            }),
        ),
    );
}

/**
 * Saves the workspace to the file the backend knows for it, one the operating system handed over.
 *
 * @param {string} workspaceName
 * @returns {Promise<boolean>} whether it had such a file
 */
export async function saveWorkspaceToItsFile(workspaceName) {
    try {
        await call(saveWorkspaceToFile({ path: { workspaceName } }));
        return true;
    } catch (error) {
        if (error?.status === 409) {
            return false;
        }
        throw error;
    }
}

/**
 * Reads a workspace file the operating system asked the app to open.
 *
 * @param {string} requestId
 * @returns {Promise<File>}
 */
export async function fetchOpenRequest(requestId) {
    const { data, response } = await call(
        getOpenRequestFile({ path: { requestId }, parseAs: "blob" }),
    );
    const fileName =
        fileNameOfContentDisposition(
            response.headers.get("Content-Disposition"),
        ) ?? "workspace.rdfa";
    return new File([data], fileName, { type: "application/zip" });
}

/**
 * Records that the workspace was opened from the file of an open request. Asks again for a
 * moment while the workspace is being written to.
 *
 * @param {string} workspaceName
 * @param {string} requestId
 */
export async function markOpenedFrom(workspaceName, requestId) {
    await retryWhileBusy(() =>
        call(markOpenedFromRequest({ path: { workspaceName, requestId } })),
    );
}

/**
 * The workspaces kept from the previous run, newest first.
 *
 * @returns {Promise<{id: string, workspaceName: string, savedAt: string}[]>}
 */
export async function listRecoveredWorkspaces() {
    const { data } = await call(requestRecoveredWorkspaces());
    return data;
}

/**
 * Reads the workspace file of a workspace kept from the previous run.
 *
 * @param {string} id
 * @returns {Promise<Blob | null>} `null` if there is no such copy any more
 */
export async function fetchRecoveredWorkspace(id) {
    try {
        const { data } = await call(
            getRecoveredWorkspace({ path: { id }, parseAs: "blob" }),
        );
        return data;
    } catch (error) {
        if (error?.status === 404) {
            return null;
        }
        throw error;
    }
}

/**
 * Removes a workspace kept from the previous run for good. One that is gone already counts as
 * removed.
 *
 * @param {string} id
 */
export async function discardRecoveredWorkspace(id) {
    try {
        await call(requestDiscardRecoveredWorkspace({ path: { id } }));
    } catch (error) {
        if (error?.status !== 404) {
            throw error;
        }
    }
}

/**
 * The file name a `Content-Disposition` header names, preferring its UTF-8 form.
 *
 * @param {string | null} header
 * @returns {string | null}
 */
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

/** An error answer of the desktop backend. */
export class DesktopApiError extends Error {
    /**
     * @param {Response | undefined} response
     * @param {unknown} detail the body of the answer
     */
    constructor(response, detail) {
        const text =
            typeof detail === "string"
                ? detail
                : (detail?.message ?? detail?.error ?? "");
        super(
            `${response?.status ?? ""} ${response?.statusText ?? ""}${text ? `: ${text}` : ""}`.trim(),
        );
        this.name = "DesktopApiError";
        /** @type {number | undefined} */
        this.status = response?.status;
    }
}
