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

import { describe, expect, test } from "vitest";

import {
    leavingNeedsConfirmation,
    WORKBENCH_PATH,
} from "$lib/shacl/workbenchLink.js";

describe("leavingNeedsConfirmation", () => {
    test("asks before unsaved changes are carried out of the workbench", () => {
        // Switching document was already guarded; anything that navigated — the menu bar, the
        // back button, the editor's own Ctrl+click — threw the buffer away in silence.
        expect(
            leavingNeedsConfirmation({ dirty: true, toPathname: "/mainpage" }),
        ).toBe(true);
    });

    test("says nothing when there is nothing to lose", () => {
        expect(
            leavingNeedsConfirmation({ dirty: false, toPathname: "/mainpage" }),
        ).toBe(false);
    });

    test("treats staying on the workbench as not leaving it", () => {
        // Following a deep link clears its query this way, and the document has not been left.
        expect(
            leavingNeedsConfirmation({
                dirty: true,
                toPathname: WORKBENCH_PATH,
            }),
        ).toBe(false);
    });

    test("does not ask twice about a navigation already agreed to", () => {
        // The answer is acted on by re-issuing the navigation, which would otherwise be caught by
        // the same guard and cancel the thing the user just agreed to.
        expect(
            leavingNeedsConfirmation({
                dirty: true,
                toPathname: "/mainpage",
                alreadyConfirmed: true,
            }),
        ).toBe(false);
    });

    test("asks when the navigation leaves the app entirely", () => {
        // A reload or a closed tab has no destination of ours; the browser asks its own question,
        // but only if we say there is something to ask about.
        expect(leavingNeedsConfirmation({ dirty: true })).toBe(true);
    });
});
