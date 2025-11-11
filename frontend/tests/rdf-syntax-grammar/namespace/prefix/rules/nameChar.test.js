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

import { isNameChar } from "$lib/rdf-syntax-grammar/namespace/prefix/rules/nameChar.js";

describe("isNameChar", () => {
    describe("valid NameChar characters", () => {
        test("should accept all NameStartChar characters - uppercase letters", () => {
            expect(isNameChar(0x41)).toBe(true); // 'A'
            expect(isNameChar(0x5a)).toBe(true); // 'Z'
        });

        test("should accept all NameStartChar characters - lowercase letters", () => {
            expect(isNameChar(0x61)).toBe(true); // 'a'
            expect(isNameChar(0x7a)).toBe(true); // 'z'
        });

        test("should accept all NameStartChar characters - underscore", () => {
            expect(isNameChar(0x5f)).toBe(true); // '_'
        });

        test("should accept all NameStartChar characters - colon", () => {
            expect(isNameChar(0x3a)).toBe(true); // ':'
        });

        test("should accept hyphen '-'", () => {
            expect(isNameChar(0x2d)).toBe(true); // '-'
        });

        test("should accept period '.'", () => {
            expect(isNameChar(0x2e)).toBe(true); // '.'
        });

        test("should accept digits 0-9", () => {
            expect(isNameChar(0x30)).toBe(true); // '0'
            expect(isNameChar(0x39)).toBe(true); // '9'
            expect(isNameChar(0x35)).toBe(true); // '5'
        });

        test("should accept #xB7", () => {
            expect(isNameChar(0x00b7)).toBe(true);
        });

        test("should accept range [#x0300-#x036F]", () => {
            expect(isNameChar(0x0300)).toBe(true); // start
            expect(isNameChar(0x036f)).toBe(true); // end
            expect(isNameChar(0x0330)).toBe(true); // middle
        });

        test("should accept range [#x203F-#x2040]", () => {
            expect(isNameChar(0x203f)).toBe(true); // start
            expect(isNameChar(0x2040)).toBe(true); // end
        });

        test("should accept characters from NameStartChar ranges", () => {
            expect(isNameChar(0x00c0)).toBe(true); // [#xC0-#xD6]
            expect(isNameChar(0x00d8)).toBe(true); // [#xD8-#xF6]
            expect(isNameChar(0x00f8)).toBe(true); // [#xF8-#x2FF]
            expect(isNameChar(0x0370)).toBe(true); // [#x370-#x37D]
            expect(isNameChar(0x037f)).toBe(true); // [#x37F-#x1FFF]
            expect(isNameChar(0x200c)).toBe(true); // [#x200C-#x200D]
            expect(isNameChar(0x2070)).toBe(true); // [#x2070-#x218F]
            expect(isNameChar(0x2c00)).toBe(true); // [#x2C00-#x2FEF]
            expect(isNameChar(0x3001)).toBe(true); // [#x3001-#xD7FF]
            expect(isNameChar(0xf900)).toBe(true); // [#xF900-#xFDCF]
            expect(isNameChar(0xfdf0)).toBe(true); // [#xFDF0-#xFFFD]
            expect(isNameChar(0x10000)).toBe(true); // [#x10000-#xEFFFF]
        });
    });

    describe("invalid NameChar characters", () => {
        test("should reject special characters not in allowed ranges", () => {
            expect(isNameChar(0x0020)).toBe(false); // space
            expect(isNameChar(0x0021)).toBe(false); // '!'
            expect(isNameChar(0x0040)).toBe(false); // '@'
            expect(isNameChar(0x0023)).toBe(false); // '#'
            expect(isNameChar(0x0024)).toBe(false); // '$'
            expect(isNameChar(0x0025)).toBe(false); // '%'
        });

        test("should reject characters just outside valid ranges", () => {
            expect(isNameChar(0x00bf)).toBe(false); // just before [#xC0-#xD6]
            expect(isNameChar(0x00d7)).toBe(false); // between ranges
            expect(isNameChar(0x00f7)).toBe(false); // between ranges
            expect(isNameChar(0x0370)).toBe(true); // 0x0300 is in NameStartChar range [#x370-#x37D]
        });

        test("should reject control characters", () => {
            expect(isNameChar(0x0000)).toBe(false); // null
            expect(isNameChar(0x0001)).toBe(false);
            expect(isNameChar(0x001f)).toBe(false);
        });

        test("should reject characters between the combining mark ranges", () => {
            expect(isNameChar(0x02ff)).toBe(true); // valid - in [#xF8-#x2FF]
            expect(isNameChar(0x036f)).toBe(true); // valid - in combining marks [#x0300-#x036F]
            expect(isNameChar(0x037e)).toBe(false); // invalid - between [#x37D] and [#x37F]
        });

        test("should reject brackets and braces", () => {
            expect(isNameChar(0x005b)).toBe(false); // '['
            expect(isNameChar(0x005d)).toBe(false); // ']'
            expect(isNameChar(0x007b)).toBe(false); // '{'
            expect(isNameChar(0x007d)).toBe(false); // '}'
        });
    });

    describe("edge cases", () => {
        test("should handle boundary values correctly", () => {
            // Test boundaries for digits
            expect(isNameChar(0x2f)).toBe(false); // '/' just before '0'
            expect(isNameChar(0x30)).toBe(true); // '0'
            expect(isNameChar(0x39)).toBe(true); // '9'
            expect(isNameChar(0x3a)).toBe(true); // ':' just after '9' (but valid as NameStartChar)

            // Test boundaries for hyphen and period
            expect(isNameChar(0x2c)).toBe(false); // ',' just before '-'
            expect(isNameChar(0x2d)).toBe(true); // '-'
            expect(isNameChar(0x2e)).toBe(true); // '.'
            expect(isNameChar(0x2f)).toBe(false); // '/' just after '.'
        });

        test("should handle #xB7 specifically", () => {
            expect(isNameChar(0x00b6)).toBe(false); // just before
            expect(isNameChar(0x00b7)).toBe(true); // #xB7
            expect(isNameChar(0x00b8)).toBe(false); // just after
        });
    });
});
