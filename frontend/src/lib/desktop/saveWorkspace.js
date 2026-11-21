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

import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import { saveFile, workspaceArchiveMediaType } from "$lib/utils/fileUtils";

import {
    fetchWorkspaceFile,
    markOpenedFrom,
    markWorkspaceSaved,
    saveWorkspaceToItsFile,
} from "./desktopApi.js";
import {
    desktopSession,
    refreshDesktopStatus,
} from "./desktopSession.svelte.js";
import { workspaceFiles } from "./workspaceFiles.js";

/** What the file pickers offer, and the directory they remember between uses. */
const PICKER_OPTIONS = {
    id: "rdfarchitect-workspace",
    types: [
        {
            description: "RDFArchitect workspace",
            accept: {
                "application/zip": [workspaceArchiveMediaType.fileExtension],
            },
        },
    ],
};

const UNSAFE_FILE_NAME_CHARACTERS = '\\/:*?"<>|';

/**
 * Whether the browser lets the page choose a file and write to it again later (the File System
 * Access API, Chromium only). Elsewhere a save is a download.
 */
export function canChooseFiles() {
    return (
        typeof window !== "undefined" &&
        typeof window.showSaveFilePicker === "function" &&
        typeof window.showOpenFilePicker === "function"
    );
}

/** The file name the backend suggests for a workspace, see `WorkspaceArchiveFormat.fileNameFor`. */
export function suggestedFileName(workspaceName) {
    const safe = Array.from(workspaceName ?? "", character =>
        UNSAFE_FILE_NAME_CHARACTERS.includes(character) ||
        character < " " ||
        character === "\u007f"
            ? "_"
            : character,
    )
        .join("")
        .trim();
    return `${safe || "workspace"}${workspaceArchiveMediaType.fileExtension}`;
}

/**
 * Saves the workspace to a workspace file: to the file it was opened from or last saved to, or
 * to one the user chooses first for Save As and for a workspace that has none. A file the
 * operating system handed over (a double-clicked one) is written by the backend, which knows where
 * it is; the browser only can write files it chose itself.
 *
 * The picker has to open first, while the click or key press that asked for it still counts as
 * the user's: browsers refuse it otherwise.
 *
 * @param {string} workspaceName
 * @param {{saveAs?: boolean}} [options]
 * @returns {Promise<"saved" | "cancelled" | "failed">}
 */
export async function saveWorkspace(workspaceName, { saveAs = false } = {}) {
    if (!workspaceName) {
        return "failed";
    }
    try {
        let handle = saveAs
            ? null
            : (workspaceFiles.get(workspaceName) ?? null);
        if (
            !saveAs &&
            !handle &&
            desktopSession.workspacesWithFile.includes(workspaceName) &&
            (await saveWorkspaceToItsFile(workspaceName))
        ) {
            await refreshDesktopStatus();
            toastStore.success(
                "Workspace saved",
                `"${workspaceName}" was saved to the file it was opened from.`,
            );
            return "saved";
        }
        if (canChooseFiles()) {
            if (!handle) {
                handle = await window.showSaveFilePicker({
                    ...PICKER_OPTIONS,
                    suggestedName: suggestedFileName(workspaceName),
                });
            }
        }
        const file = await fetchWorkspaceFile(workspaceName);
        if (handle) {
            await writeTo(handle, file.blob);
            workspaceFiles.remember(workspaceName, handle);
        } else {
            saveFile(file.blob, file.fileName, workspaceArchiveMediaType);
        }
        await markWorkspaceSaved(workspaceName, file.version);
        await refreshDesktopStatus();
        toastStore.success(
            "Workspace saved",
            handle
                ? `"${workspaceName}" was saved to ${handle.name}.`
                : `"${workspaceName}" was downloaded as ${file.fileName}.`,
        );
        return "saved";
    } catch (error) {
        if (error?.name === "AbortError") {
            return "cancelled";
        }
        console.error(
            `[saveWorkspace] Could not save "${workspaceName}"`,
            error,
        );
        toastStore.error(
            "Save failed",
            `Could not save workspace "${workspaceName}".${error?.message ? ` ${error.message}` : ""}`,
        );
        return "failed";
    }
}

/**
 * Lets the user choose a workspace file to open, keeping hold of it so Save can write to it.
 *
 * @returns {Promise<{file: File, handle: FileSystemFileHandle} | null>} `null` if cancelled
 */
export async function chooseWorkspaceFile() {
    try {
        const [handle] = await window.showOpenFilePicker({
            ...PICKER_OPTIONS,
            multiple: false,
        });
        return { file: await handle.getFile(), handle };
    } catch (error) {
        if (error?.name === "AbortError") {
            return null;
        }
        throw error;
    }
}

/**
 * Records that a workspace was just opened from a file: it holds nothing the file does not, and
 * Save writes back to that file when there is a handle on it.
 *
 * @param {string} workspaceName
 * @param {FileSystemFileHandle | null} handle
 */
export async function workspaceOpenedFromFile(workspaceName, handle) {
    workspaceFiles.forget(workspaceName);
    workspaceFiles.remember(workspaceName, handle);
    try {
        await markWorkspaceSaved(workspaceName);
    } catch (error) {
        console.warn(
            `[saveWorkspace] Could not mark "${workspaceName}" as saved`,
            error,
        );
    }
    await refreshDesktopStatus();
}

/**
 * Records that a workspace was just opened from a file the operating system handed over: it holds
 * nothing the file does not, and Save writes back to that file.
 */
export async function workspaceOpenedFromRequest(workspaceName, requestId) {
    workspaceFiles.forget(workspaceName);
    try {
        await markOpenedFrom(workspaceName, requestId);
    } catch (error) {
        console.warn(
            `[saveWorkspace] Could not record where "${workspaceName}" came from`,
            error,
        );
    }
    await refreshDesktopStatus();
}

async function writeTo(handle, blob) {
    if (
        typeof handle.queryPermission === "function" &&
        (await handle.queryPermission({ mode: "readwrite" })) !== "granted" &&
        (await handle.requestPermission({ mode: "readwrite" })) !== "granted"
    ) {
        throw new DOMException(
            "Writing to the file was not allowed.",
            "NotAllowedError",
        );
    }
    const writable = await handle.createWritable();
    try {
        await writable.write(blob);
        await writable.close();
    } catch (error) {
        await writable.abort?.().catch(() => {});
        throw error;
    }
}
