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

import { describe, expect, it } from "vitest";

import {
    hasInvalidCharacters,
    workspaceNameFrom,
} from "../../src/lib/utils/workspaceName.js";

describe("hasInvalidCharacters", () => {
    it("holds for the characters that would end a url early", () => {
        for (const name of ["a/b", "a\\b", "a?b", "a#b"]) {
            expect(hasInvalidCharacters(name)).toBe(true);
        }
    });

    it("does not hold for an ordinary name", () => {
        expect(hasInvalidCharacters("CGMES 3.0")).toBe(false);
    });

    it("does not hold for nothing at all", () => {
        expect(hasInvalidCharacters(undefined)).toBe(false);
    });
});

describe("workspaceNameFrom", () => {
    it("drops the path and the extension", () => {
        expect(workspaceNameFrom("C:\\exports\\cgmes-3.0.xml")).toBe(
            "cgmes-3.0",
        );
        expect(workspaceNameFrom("/tmp/cgmes-3.0.xmi")).toBe("cgmes-3.0");
    });

    it("keeps a name that has no extension", () => {
        expect(workspaceNameFrom("export")).toBe("export");
    });

    it("keeps a leading dot, which names no extension", () => {
        expect(workspaceNameFrom(".hidden")).toBe(".hidden");
    });

    it("replaces what a workspace cannot be called by", () => {
        expect(workspaceNameFrom("a#b?c.xml")).toBe("a_b_c");
    });

    it("is empty for nothing at all", () => {
        expect(workspaceNameFrom(undefined)).toBe("");
    });
});
