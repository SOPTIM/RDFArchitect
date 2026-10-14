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
import { isImportableFile } from "$lib/utils/fileUtils";

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

    toastStore.error(
        "Nothing to import",
        items.directoryNames.length > 0
            ? "Folders cannot be imported. Pack the schemas into a ZIP archive and drop that."
            : "None of the dropped files is a schema RDFArchitect can read.",
    );
    return null;
}
