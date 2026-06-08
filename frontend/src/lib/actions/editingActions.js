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
 * Shared actions for toggling the read-only state of a dataset.
 *
 * Each function performs the API call, surfaces a success or error toast,
 * and returns `true` when the write succeeded. Reactive state updates that
 * are local to a call site (refreshing local `readonly` flags, triggering
 * editor state, etc.) remain the caller's responsibility.
 */

import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import { datasetStore } from "$lib/stores/DatasetStore.ts";

/**
 * Make the dataset editable. Toasts on success and failure.
 *
 * @param {string} datasetName
 * @returns {Promise<boolean>} `true` when the dataset is now editable.
 */
export async function enableEditing(datasetName) {
    if (!datasetName) return false;
    const { error } = datasetStore.updateReadonly(datasetName, false);
    if (error) {
        toastStore.error(
            "Could not enable editing",
            `Dataset "${datasetName}" remains read-only.`,
        );
        return false;
    }
    toastStore.success(
        "Editing enabled",
        `Dataset "${datasetName}" is now editable.`,
    );
    return true;
}

/**
 * Mark the dataset read-only. Toasts on success and failure.
 *
 * @param {string} datasetName
 * @returns {Promise<boolean>} `true` when the dataset is now read-only.
 */
export async function disableEditing(datasetName) {
    if (!datasetName) return false;
    const { error } = await datasetStore.updateReadonly(datasetName, true);
    if (error) {
        toastStore.error(
            "Could not disable editing",
            `Dataset "${datasetName}" remains editable.`,
        );
        return false;
    }
    toastStore.success(
        "Editing disabled",
        `Dataset "${datasetName}" is now read-only.`,
    );
    return true;
}
