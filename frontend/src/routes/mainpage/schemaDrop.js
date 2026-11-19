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

import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import { isImportableFile, isXmiExportFile } from "$lib/utils/fileUtils";

/**
 * Decides whether a drop is worth opening the import for, and says why when it is not.
 *
 * @param {{files: File[], directoryNames: string[]}} items what was dropped
 * @returns {{files: File[], directoryNames: string[]}|null} the drop, or null when it holds nothing
 */
export function acceptSchemaDrop(items) {
    if (items.files.some(file => isImportableFile(file.name))) {
        return items;
    }
    refuse(items);
    return null;
}

/**
 * Decides what a drop on the new-workspace button is. An Enterprise Architect export is read by an
 * import of its own, and this is the one place it can be dropped: it becomes a workspace rather
 * than a schema within one. Schemas win a drop holding both, since that import has somewhere to
 * put them and the export import reads a single export into a workspace of its own.
 *
 * @param {{files: File[], directoryNames: string[]}} items what was dropped
 * @returns {{kind: "schemas", items: {files: File[], directoryNames: string[]}}
 *     |{kind: "export", file: File}|null} what to open, or null when the drop holds nothing
 */
export function acceptNewWorkspaceDrop(items) {
    if (items.files.some(file => isImportableFile(file.name))) {
        return { kind: "schemas", items };
    }
    const exportFile = items.files.find(file => isXmiExportFile(file.name));
    if (exportFile) {
        return { kind: "export", file: exportFile };
    }
    refuse(items);
    return null;
}

/** Says why a drop holds nothing to import, and where an export has to go instead. */
function refuse(items) {
    if (items.files.some(file => isXmiExportFile(file.name))) {
        toastStore.error(
            "Not a schema",
            "An Enterprise Architect XMI export becomes a workspace of its own. Drop it on the + beside the workspace tabs.",
        );
        return;
    }
    toastStore.error(
        "Nothing to import",
        items.directoryNames.length > 0
            ? "Folders cannot be imported. Pack the schemas into a ZIP archive and drop that."
            : "None of the dropped files is a schema RDFArchitect can read.",
    );
}
