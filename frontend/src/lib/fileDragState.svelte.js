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
 * Whether files from outside the browser are currently being dragged over the window, and how many
 * of them. Drop targets announce themselves from this, so that they are found before the pointer
 * has to go looking for them.
 */
export const fileDragState = $state({
    active: false,
    fileCount: 0,
});

let enteredElements = 0;

/**
 * Starts the drag mode, unless what is dragged are not files.
 *
 * @param {DragEvent} event the drag event that entered an element
 */
export function enterFileDrag(event) {
    if (!dragHasFiles(event)) {
        return;
    }
    enteredElements += 1;
    fileDragState.active = true;
    fileDragState.fileCount = countDraggedFiles(event);
}

/**
 * Ends the drag mode once every element that was entered has been left again. `dragleave` fires on
 * every element boundary the pointer crosses, not only when it leaves the window, which is why the
 * leaves are counted against the enters.
 */
export function leaveFileDrag() {
    if (enteredElements === 0) {
        return;
    }
    enteredElements -= 1;
    if (enteredElements === 0) {
        endFileDrag();
    }
}

/** Ends the drag mode outright, for a drop as well as for a drag that was cancelled. */
export function endFileDrag() {
    enteredElements = 0;
    fileDragState.active = false;
    fileDragState.fileCount = 0;
}

/**
 * Whether a drag event carries files, as opposed to something dragged within the page.
 *
 * @param {DragEvent} event the drag event to read
 * @returns {boolean} true when the payload are files
 */
export function dragHasFiles(event) {
    return Array.from(event.dataTransfer?.types ?? []).includes("Files");
}

/**
 * The files of a drop, and the names of the folders that were dropped along with them.
 *
 * `dataTransfer.files` lists a folder as if it were a file, so each item is asked what it is and
 * taken from there. Sorting them out by name instead would drop a file that happens to be named
 * like a folder beside it. A browser without the entry API reports no folders rather than
 * mistaking them for files.
 *
 * @param {DataTransfer} dataTransfer what was dropped
 * @returns {{files: File[], directoryNames: string[]}} the dropped files and folder names
 */
export function extractDroppedItems(dataTransfer) {
    const items = Array.from(dataTransfer?.items ?? []);
    if (!items.some(item => item.webkitGetAsEntry)) {
        return {
            files: Array.from(dataTransfer?.files ?? []),
            directoryNames: [],
        };
    }

    const files = [];
    const directoryNames = [];
    for (const item of items) {
        const entry = item.webkitGetAsEntry();
        if (entry?.isDirectory) {
            directoryNames.push(entry.name);
            continue;
        }
        const file = item.getAsFile?.();
        if (file) {
            files.push(file);
        }
    }

    return { files, directoryNames };
}

function countDraggedFiles(event) {
    return Array.from(event.dataTransfer?.items ?? []).filter(
        item => item.kind === "file",
    ).length;
}
