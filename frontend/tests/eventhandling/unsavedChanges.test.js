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

import { afterEach, describe, expect, test, vi } from "vitest";

import {
    confirmUnsavedChanges,
    guardUnsavedChanges,
} from "$lib/eventhandling/unsavedChanges.js";

let release = () => {};

afterEach(() => release());

describe("confirmUnsavedChanges", () => {
    test("goes ahead when no page has anything to lose", async () => {
        expect(await confirmUnsavedChanges()).toBe(true);
    });

    test("asks the registered page and follows its answer", async () => {
        const ask = vi.fn().mockResolvedValue(false);
        release = guardUnsavedChanges(ask);

        expect(await confirmUnsavedChanges()).toBe(false);
        expect(ask).toHaveBeenCalledOnce();

        ask.mockResolvedValue(true);
        expect(await confirmUnsavedChanges()).toBe(true);
    });

    test("stops asking a page once it has gone", async () => {
        const ask = vi.fn().mockResolvedValue(false);
        guardUnsavedChanges(ask)();

        expect(await confirmUnsavedChanges()).toBe(true);
        expect(ask).not.toHaveBeenCalled();
    });

    test("a page leaving late does not remove the guard of the page after it", async () => {
        const first = guardUnsavedChanges(() => Promise.resolve(true));
        release = guardUnsavedChanges(() => Promise.resolve(false));

        first();

        expect(await confirmUnsavedChanges()).toBe(false);
    });
});
