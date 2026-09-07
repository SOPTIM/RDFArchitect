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

import { uniqueDocumentName } from "$lib/shacl/documentNames.js";

describe("uniqueDocumentName", () => {
    test("keeps the file's own name when nothing has taken it", () => {
        // The name is the point: a modeller refers to "the DiagramLayout simple constraints" by
        // its file name, and importing used to rename everything to custom.ttl.
        expect(
            uniqueDocumentName(
                ["eq.ttl"],
                "61970-600-2_DiagramLayout-AP-Con-Simple-SHACL.ttl",
            ),
        ).toBe("61970-600-2_DiagramLayout-AP-Con-Simple-SHACL.ttl");
    });

    test("distinguishes a second copy rather than colliding", () => {
        expect(uniqueDocumentName(["eq.ttl"], "eq.ttl")).toBe("eq.ttl (2)");
    });

    test("keeps counting past a suffix that is also taken", () => {
        expect(
            uniqueDocumentName(
                ["eq.ttl", "eq.ttl (2)", "eq.ttl (3)"],
                "eq.ttl",
            ),
        ).toBe("eq.ttl (4)");
    });

    test("copes with no documents at all", () => {
        expect(uniqueDocumentName(undefined, "eq.ttl")).toBe("eq.ttl");
    });
});
