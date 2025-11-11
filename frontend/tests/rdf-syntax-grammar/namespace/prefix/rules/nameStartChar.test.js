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

import { isNameStartChar } from "$lib/rdf-syntax-grammar/namespace/prefix/rules/nameStartChar.js";

describe("isNameStartChar", () => {
    describe("valid NameStartChar characters", () => {
        test("should accept colon ':'", () => {
            expect(isNameStartChar(0x3a)).toBe(true); // ':'
        });

        test("should accept uppercase letters A-Z", () => {
            expect(isNameStartChar(0x41)).toBe(true); // 'A'
            expect(isNameStartChar(0x5a)).toBe(true); // 'Z'
            expect(isNameStartChar(0x4d)).toBe(true); // 'M'
        });

        test("should accept lowercase letters a-z", () => {
            expect(isNameStartChar(0x61)).toBe(true); // 'a'
            expect(isNameStartChar(0x7a)).toBe(true); // 'z'
            expect(isNameStartChar(0x6d)).toBe(true); // 'm'
        });

        test("should accept underscore '_'", () => {
            expect(isNameStartChar(0x5f)).toBe(true); // '_'
        });

        test("should accept range [#xC0-#xD6]", () => {
            expect(isNameStartChar(0x00c0)).toBe(true); // start
            expect(isNameStartChar(0x00d6)).toBe(true); // end
            expect(isNameStartChar(0x00cb)).toBe(true); // middle
        });

        test("should accept range [#xD8-#xF6]", () => {
            expect(isNameStartChar(0x00d8)).toBe(true); // start
            expect(isNameStartChar(0x00f6)).toBe(true); // end
            expect(isNameStartChar(0x00e5)).toBe(true); // middle
        });

        test("should accept range [#xF8-#x2FF]", () => {
            expect(isNameStartChar(0x00f8)).toBe(true); // start
            expect(isNameStartChar(0x02ff)).toBe(true); // end
            expect(isNameStartChar(0x01ff)).toBe(true); // middle
        });

        test("should accept range [#x370-#x37D]", () => {
            expect(isNameStartChar(0x0370)).toBe(true); // start
            expect(isNameStartChar(0x037d)).toBe(true); // end
            expect(isNameStartChar(0x0375)).toBe(true); // middle
        });

        test("should accept range [#x37F-#x1FFF]", () => {
            expect(isNameStartChar(0x037f)).toBe(true); // start
            expect(isNameStartChar(0x1fff)).toBe(true); // end
            expect(isNameStartChar(0x1000)).toBe(true); // middle
        });

        test("should accept range [#x200C-#x200D]", () => {
            expect(isNameStartChar(0x200c)).toBe(true); // start
            expect(isNameStartChar(0x200d)).toBe(true); // end
        });

        test("should accept range [#x2070-#x218F]", () => {
            expect(isNameStartChar(0x2070)).toBe(true); // start
            expect(isNameStartChar(0x218f)).toBe(true); // end
            expect(isNameStartChar(0x2100)).toBe(true); // middle
        });

        test("should accept range [#x2C00-#x2FEF]", () => {
            expect(isNameStartChar(0x2c00)).toBe(true); // start
            expect(isNameStartChar(0x2fef)).toBe(true); // end
            expect(isNameStartChar(0x2e00)).toBe(true); // middle
        });

        test("should accept range [#x3001-#xD7FF]", () => {
            expect(isNameStartChar(0x3001)).toBe(true); // start
            expect(isNameStartChar(0xd7ff)).toBe(true); // end
            expect(isNameStartChar(0x8000)).toBe(true); // middle
        });

        test("should accept range [#xF900-#xFDCF]", () => {
            expect(isNameStartChar(0xf900)).toBe(true); // start
            expect(isNameStartChar(0xfdcf)).toBe(true); // end
            expect(isNameStartChar(0xfb00)).toBe(true); // middle
        });

        test("should accept range [#xFDF0-#xFFFD]", () => {
            expect(isNameStartChar(0xfdf0)).toBe(true); // start
            expect(isNameStartChar(0xfffd)).toBe(true); // end
            expect(isNameStartChar(0xff00)).toBe(true); // middle
        });

        test("should accept range [#x10000-#xEFFFF]", () => {
            expect(isNameStartChar(0x10000)).toBe(true); // start
            expect(isNameStartChar(0xeffff)).toBe(true); // end
            expect(isNameStartChar(0x20000)).toBe(true); // middle
        });
    });

    describe("invalid NameStartChar characters", () => {
        test("should reject digits 0-9", () => {
            expect(isNameStartChar(0x30)).toBe(false); // '0'
            expect(isNameStartChar(0x39)).toBe(false); // '9'
            expect(isNameStartChar(0x35)).toBe(false); // '5'
        });

        test("should reject hyphen '-'", () => {
            expect(isNameStartChar(0x2d)).toBe(false); // '-'
        });

        test("should reject period '.'", () => {
            expect(isNameStartChar(0x2e)).toBe(false); // '.'
        });

        test("should reject #xB7", () => {
            expect(isNameStartChar(0x00b7)).toBe(false);
        });

        test("should reject range [#x0300-#x036F]", () => {
            expect(isNameStartChar(0x0300)).toBe(false); // start
            expect(isNameStartChar(0x036f)).toBe(false); // end
            expect(isNameStartChar(0x0330)).toBe(false); // middle
        });

        test("should reject range [#x203F-#x2040]", () => {
            expect(isNameStartChar(0x203f)).toBe(false); // start
            expect(isNameStartChar(0x2040)).toBe(false); // end
        });

        test("should reject characters just outside valid ranges", () => {
            expect(isNameStartChar(0x00bf)).toBe(false); // just before [#xC0-#xD6]
            expect(isNameStartChar(0x00d7)).toBe(false); // between ranges
            expect(isNameStartChar(0x00f7)).toBe(false); // between ranges
            expect(isNameStartChar(0x036f)).toBe(false); // just before [#x370-#x37D]
            expect(isNameStartChar(0x037e)).toBe(false); // between ranges
        });

        test("should reject special characters", () => {
            expect(isNameStartChar(0x0020)).toBe(false); // space
            expect(isNameStartChar(0x0021)).toBe(false); // '!'
            expect(isNameStartChar(0x0040)).toBe(false); // '@'
            expect(isNameStartChar(0x005b)).toBe(false); // '['
            expect(isNameStartChar(0x0060)).toBe(false); // '`'
            expect(isNameStartChar(0x007b)).toBe(false); // '{'
        });

        test("should reject control characters", () => {
            expect(isNameStartChar(0x0000)).toBe(false); // null
            expect(isNameStartChar(0x0001)).toBe(false);
            expect(isNameStartChar(0x001f)).toBe(false);
        });
    });
});
