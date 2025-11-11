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

import { getNCNameViolations } from "$lib/rdf-syntax-grammar/namespace/prefix/rules/ncName.js";

describe("getNCNameViolations", () => {
    describe("valid NCNames", () => {
        test("should return empty array for simple valid NCName", () => {
            expect(getNCNameViolations("validName")).toEqual([]);
        });

        test("should return empty array for NCName starting with underscore", () => {
            expect(getNCNameViolations("_validName")).toEqual([]);
        });

        test("should return empty array for NCName with digits", () => {
            expect(getNCNameViolations("name123")).toEqual([]);
        });

        test("should return empty array for NCName with hyphen", () => {
            expect(getNCNameViolations("valid-name")).toEqual([]);
        });

        test("should return empty array for NCName with period", () => {
            expect(getNCNameViolations("valid.name")).toEqual([]);
        });

        test("should return empty array for NCName with combining diacritical marks", () => {
            expect(getNCNameViolations("café")).toEqual([]);
        });

        test("should return empty array for NCName with middot #xB7", () => {
            expect(getNCNameViolations("val·name")).toEqual([]);
        });

        test("should return empty array for NCName with Unicode characters", () => {
            expect(getNCNameViolations("名前")).toEqual([]);
            expect(getNCNameViolations("имя")).toEqual([]);
            expect(getNCNameViolations("όνομα")).toEqual([]);
        });

        test("should return empty array for complex valid NCName", () => {
            expect(getNCNameViolations("_validName123.test-value")).toEqual([]);
        });
    });

    describe("invalid NCNames - colon", () => {
        test("should return colon when present at start", () => {
            const result = getNCNameViolations(":invalidName");
            expect(result).toContain(":");
            expect(result.length).toBeGreaterThan(0);
        });

        test("should return colon when present in middle", () => {
            const result = getNCNameViolations("invalid:Name");
            expect(result).toContain(":");
        });

        test("should return colon when present at end", () => {
            const result = getNCNameViolations("invalidName:");
            expect(result).toContain(":");
        });

        test("should return colon only once for multiple colons", () => {
            const result = getNCNameViolations("in:valid:name");
            expect(result).toContain(":");
            expect(result.filter(v => v === ":").length).toBe(1);
        });
    });

    describe("invalid NCNames - invalid start character", () => {
        test("should return digit when NCName starts with digit", () => {
            const result = getNCNameViolations("123name");
            expect(result).toContain("1");
        });

        test("should return hyphen when NCName starts with hyphen", () => {
            const result = getNCNameViolations("-name");
            expect(result).toContain("-");
        });

        test("should return period when NCName starts with period", () => {
            const result = getNCNameViolations(".name");
            expect(result).toContain(".");
        });

        test("should return special character when NCName starts with invalid character", () => {
            expect(getNCNameViolations("@name")).toContain("@");
            expect(getNCNameViolations("#name")).toContain("#");
            expect(getNCNameViolations("$name")).toContain("$");
        });
    });

    describe("invalid NCNames - invalid middle/end characters", () => {
        test("should return space when present", () => {
            const result = getNCNameViolations("invalid name");
            expect(result).toContain(" ");
        });

        test("should return special characters in middle", () => {
            expect(getNCNameViolations("invalid@name")).toContain("@");
            expect(getNCNameViolations("invalid#name")).toContain("#");
            expect(getNCNameViolations("invalid$name")).toContain("$");
            expect(getNCNameViolations("invalid%name")).toContain("%");
        });

        test("should return brackets and braces", () => {
            expect(getNCNameViolations("name[1]")).toContain("[");
            expect(getNCNameViolations("name[1]")).toContain("]");
            expect(getNCNameViolations("name{value}")).toContain("{");
            expect(getNCNameViolations("name{value}")).toContain("}");
        });

        test("should return parentheses", () => {
            expect(getNCNameViolations("name(test)")).toContain("(");
            expect(getNCNameViolations("name(test)")).toContain(")");
        });
    });

    describe("unique violations", () => {
        test("should return each violation only once", () => {
            const result = getNCNameViolations("@@##$$");
            expect(result.length).toBe(3);
            expect(result).toContain("@");
            expect(result).toContain("#");
            expect(result).toContain("$");
        });

        test("should return unique characters for repeated violations", () => {
            const result = getNCNameViolations("name   with   spaces");
            expect(result.length).toBe(1);
            expect(result).toContain(" ");
        });

        test("should return unique violations for multiple different characters", () => {
            const result = getNCNameViolations("in:valid@name#test");
            expect(result.length).toBe(3);
            expect(result).toContain(":");
            expect(result).toContain("@");
            expect(result).toContain("#");
        });
    });

    describe("empty and edge cases", () => {
        test("should return empty array for empty string", () => {
            expect(getNCNameViolations("")).toEqual([]);
        });

        test("should handle single valid character", () => {
            expect(getNCNameViolations("a")).toEqual([]);
            expect(getNCNameViolations("_")).toEqual([]);
        });

        test("should handle single invalid character", () => {
            expect(getNCNameViolations("1")).toContain("1");
            expect(getNCNameViolations("@")).toContain("@");
            expect(getNCNameViolations(":")).toContain(":");
        });

        test("should handle Unicode surrogate pairs correctly", () => {
            // Emoji (surrogate pairs) - actually valid in the [#x10000-#xEFFFF] range
            const result = getNCNameViolations("name😀test");
            // The emoji 😀 (U+1F600) is actually in a valid range per XML spec
            expect(result.length).toBe(0);
        });
    });

    describe("real-world examples", () => {
        test("should validate common namespace prefixes", () => {
            expect(getNCNameViolations("rdf")).toEqual([]);
            expect(getNCNameViolations("rdfs")).toEqual([]);
            expect(getNCNameViolations("owl")).toEqual([]);
            expect(getNCNameViolations("xsd")).toEqual([]);
            expect(getNCNameViolations("ex")).toEqual([]);
        });

        test("should detect invalid prefixes", () => {
            expect(getNCNameViolations("rdf:")).toContain(":");
            expect(getNCNameViolations("my-ns:")).toContain(":");
            expect(getNCNameViolations("123prefix")).toContain("1");
        });

        test("should validate extended namespace prefixes", () => {
            expect(getNCNameViolations("myCompany")).toEqual([]);
            expect(getNCNameViolations("my_company")).toEqual([]);
            expect(getNCNameViolations("my-company")).toEqual([]);
            expect(getNCNameViolations("myCompany123")).toEqual([]);
        });

        test("should handle IRI fragments", () => {
            expect(getNCNameViolations("Person")).toEqual([]);
            expect(getNCNameViolations("hasProperty")).toEqual([]);
            expect(getNCNameViolations("isRelatedTo")).toEqual([]);
        });
    });

    describe("complex invalid cases", () => {
        test("should detect multiple violations at once", () => {
            const result = getNCNameViolations("123:invalid@name#with spaces");
            expect(result).toContain("1");
            expect(result).toContain(":");
            expect(result).toContain("@");
            expect(result).toContain("#");
            expect(result).toContain(" ");
        });

        test("should handle names with control characters", () => {
            const result = getNCNameViolations("name\x00test");
            expect(result.length).toBeGreaterThan(0);
        });
    });
});
