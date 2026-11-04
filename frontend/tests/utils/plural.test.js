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

import { counted, plural } from "../../src/lib/utils/plural.js";

describe("plural", () => {
    test("adds an s to a regular noun", () => {
        expect(plural("change")).toBe("changes");
        expect(plural("schema")).toBe("schemas");
    });

    test("adds an es to a noun already ending in s", () => {
        expect(plural("class")).toBe("classes");
    });
});

describe("counted", () => {
    test("keeps the singular for one", () => {
        expect(counted(1, "change")).toBe("1 change");
    });

    test("uses the plural for none and for several", () => {
        expect(counted(0, "change")).toBe("0 changes");
        expect(counted(3, "change")).toBe("3 changes");
    });
});
