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
 * Asking a page with unsaved work before something outside it throws that work away.
 *
 * A navigation guard only sees navigations. Much of the app changes what a page shows by changing
 * the shared selection instead — the logo resets it, following a term selects another schema, a
 * rename or an undo changes the schema under the page — and by the time a guard runs, a page that
 * derives its state from the selection has already rebuilt itself without the edits. So whoever is
 * about to do that asks here first, and the page with the work answers.
 */

let guard = null;

/**
 * Registers the question to ask. `ask` resolves true when it is fine to go ahead — nothing was
 * unsaved, it was saved, or the user chose to discard it.
 *
 * @returns a function that removes the registration
 */
export function guardUnsavedChanges(ask) {
    guard = ask;
    return () => {
        if (guard === ask) {
            guard = null;
        }
    };
}

/** Whether the caller may go ahead and replace what the current page shows. */
export async function confirmUnsavedChanges() {
    return guard ? (await guard()) === true : true;
}
