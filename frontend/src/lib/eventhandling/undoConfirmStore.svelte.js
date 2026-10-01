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
 * Asks the user to confirm an undo that would make something disappear.
 *
 * Undo is triggered from three places — the keyboard handler, the edit menu and the graph context
 * menu — but the question is the same everywhere, so it is asked in one place: the store holds the
 * request, UndoConfirmDialog.svelte (mounted once in `+layout.svelte`) shows it, and the caller
 * simply awaits an answer.
 *
 * @typedef {{ message: string, removed: string[] }} UndoConfirmRequest
 */

/** Singleton facade; the dialog reads `getRequest()` inside a `$derived`. */
export const undoConfirmStore = {
    /**
     * Asks whether an undo that removes something should go ahead.
     *
     * @param {string} message what the change to be undone did
     * @param {string[]} removed what the undo would take away
     * @returns {Promise<boolean>} whether the user confirmed
     */
    confirm(message, removed) {
        // A replaced question was not answered; its caller is still waiting on one.
        answer?.(false);
        state.request = { message, removed };
        return new Promise(resolve => {
            answer = resolve;
        });
    },

    /** The question currently on screen, or `null`. */
    getRequest() {
        return state.request;
    },

    /**
     * Answers the open question and closes the dialog.
     *
     * @param {boolean} confirmed whether the undo should go ahead
     */
    respond(confirmed) {
        state.request = null;
        const resolve = answer;
        answer = null;
        resolve?.(confirmed);
    },
};
/** @type {{ request: UndoConfirmRequest | null }} */
const state = $state({ request: null });

/** Resolver of the promise the open question handed out, or `null` when none is open. */
let answer = null;
