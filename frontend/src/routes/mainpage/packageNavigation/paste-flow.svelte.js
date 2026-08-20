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

import {
    loadPastePreview,
    PASTE_PREVIEW_FAILED,
    saveCopyClass,
} from "./save-copy-class-to-backend.js";

export const pasteFlow = $state({
    showDialog: false,
    preview: null,
    options: null,
});

let target = null;

export async function startPaste(
    workspaceName,
    graphURI,
    targetPackageUUID,
    options,
) {
    target = { workspaceName, graphURI, targetPackageUUID };
    const preview = await loadPastePreview(workspaceName, graphURI, options);
    if (preview === PASTE_PREVIEW_FAILED) {
        return;
    }
    if (!preview) {
        await confirmPaste(options);
        return;
    }
    pasteFlow.preview = preview;
    pasteFlow.options = options;
    pasteFlow.showDialog = true;
}

export async function confirmPaste(options) {
    const { workspaceName, graphURI, targetPackageUUID } = target;
    await saveCopyClass(workspaceName, graphURI, targetPackageUUID, options);
}
