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

import { editorState } from "$lib/sharedState.svelte.js";

/** How long a revealed attribute/association/enum entry row stays highlighted, in ms. */
export const PROPERTY_HIGHLIGHT_MS = 2500;

/**
 * Takes the pending "reveal this property" request if it names `uuid`.
 *
 * The request is consumed by the first row that claims it, so a class editor rendering the same
 * property twice (once inherited) highlights it once, and a later class editor does not pick up a
 * stale request.
 *
 * @param {string | null | undefined} uuid the row's attribute/association/enum entry uuid — the
 *     plain value, not the row's `ReactiveValueWrapper` (pass `attribute.uuid.value`)
 * @returns {boolean} whether this row was the one asked for
 */
export function claimPropertyFocus(uuid) {
    if (!uuid || editorState.focusedPropertyUUID.getValue() !== uuid) {
        return false;
    }
    editorState.focusedPropertyUUID.updateValue(null);
    return true;
}

/**
 * Scrolls a just-revealed row into view, after the class editor has finished laying out.
 *
 * @param {HTMLElement | null | undefined} node
 */
export function scrollRowIntoView(node) {
    queueMicrotask(() =>
        node?.scrollIntoView({ block: "center", behavior: "smooth" }),
    );
}
