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

import { ReactiveValueWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-value-wrapper.svelte.js";

describe("ReactiveValueWrapper", () => {
    describe("constructor()", () => {
        test.each([
            { input: "test", description: "a string value" },
            { input: 42, description: "a number value" },
            { input: 0, description: "zero" },
            { input: null, description: "null" },
            { input: undefined, description: "undefined" },
        ])("should initialize with $description", ({ input }) => {
            const wrapper = new ReactiveValueWrapper(input);
            expect(wrapper.value).toBe(input);
            expect(wrapper.backup).toBe(input);
        });

        test("should unwrap another ReactiveValueWrapper", () => {
            const originalWrapper = new ReactiveValueWrapper("test");
            const newWrapper = new ReactiveValueWrapper(originalWrapper);
            expect(newWrapper.value).toBe("test");
            expect(newWrapper.backup).toBe("test");
        });

        test("should accept a single violation function", () => {
            const check = value => (!value ? ["Value is required"] : []);
            const wrapper = new ReactiveValueWrapper("x", check);
            expect(wrapper.isValid).toBe(true);

            const wrapper2 = new ReactiveValueWrapper("", check);
            expect(wrapper2.isValid).toBe(false);
            expect(wrapper2.violations).toEqual(["Value is required"]);
        });

        test("should accept an array of violation functions", () => {
            const checks = [
                value => (!value ? ["Value is required"] : []),
                value => (value?.length < 3 ? ["Value too short"] : []),
            ];
            const wrapper = new ReactiveValueWrapper("ab", checks);
            expect(wrapper.isValid).toBe(false);
            expect(wrapper.violations).toEqual(["Value too short"]);
        });
    });

    describe("isModified", () => {
        test("should return false when value equals backup", () => {
            const wrapper = new ReactiveValueWrapper("test");
            expect(wrapper.isModified).toBe(false);
        });

        test("should return true when value differs from backup", () => {
            const wrapper = new ReactiveValueWrapper("test");
            wrapper.value = "changed";
            expect(wrapper.isModified).toBe(true);
        });

        test("should return false after reset", () => {
            const wrapper = new ReactiveValueWrapper("test");
            wrapper.value = "changed";
            wrapper.reset();
            expect(wrapper.isModified).toBe(false);
        });

        test("should return false after save", () => {
            const wrapper = new ReactiveValueWrapper("test");
            wrapper.value = "changed";
            wrapper.save();
            expect(wrapper.isModified).toBe(false);
        });
    });

    describe("violations and isValid", () => {
        test("should have no violations by default", () => {
            const wrapper = new ReactiveValueWrapper("test");
            expect(wrapper.violations).toEqual([]);
        });

        test("should be valid by default", () => {
            const wrapper = new ReactiveValueWrapper("test");
            expect(wrapper.isValid).toBe(true);
        });

        test("should return custom violations when checks are passed via constructor", () => {
            const wrapper = new ReactiveValueWrapper("test", value => {
                if (!value) return ["Value is required"];
                if (value.length < 3) return ["Value too short"];
                return [];
            });

            expect(wrapper.isValid).toBe(true);
            expect(wrapper.violations).toEqual([]);
        });

        test("should be invalid when violations exist", () => {
            const wrapper = new ReactiveValueWrapper("", value =>
                !value ? ["Value is required"] : [],
            );

            expect(wrapper.isValid).toBe(false);
            expect(wrapper.violations).toEqual(["Value is required"]);
        });

        test("should update violations when value changes", () => {
            const wrapper = new ReactiveValueWrapper("test", value =>
                value.length < 5 ? ["Too short"] : [],
            );

            expect(wrapper.violations).toEqual(["Too short"]);

            wrapper.value = "long enough";
            expect(wrapper.isValid).toBe(true);
            expect(wrapper.violations).toEqual([]);
        });

        test("should handle multiple violations (single check returning multiple messages)", () => {
            const wrapper = new ReactiveValueWrapper("a", value => {
                const violations = [];
                if (value.length < 3) violations.push("Too short");
                if (!value.includes("@")) violations.push("Must include @");
                return violations;
            });

            expect(wrapper.isValid).toBe(false);
            expect(wrapper.violations).toEqual(["Too short", "Must include @"]);
        });

        test("should handle multiple violation checks (array of checks)", () => {
            const wrapper = new ReactiveValueWrapper("ab", [
                value => (value.length < 3 ? ["Minimum 3 characters"] : []),
                value => (value.length > 10 ? ["Maximum 10 characters"] : []),
            ]);

            expect(wrapper.isValid).toBe(false);
            expect(wrapper.violations).toEqual(["Minimum 3 characters"]);

            wrapper.value = "valid";
            expect(wrapper.isValid).toBe(true);
            expect(wrapper.violations).toEqual([]);

            wrapper.value = "this is too long";
            expect(wrapper.isValid).toBe(false);
            expect(wrapper.violations).toEqual(["Maximum 10 characters"]);
        });
    });

    describe("reset()", () => {
        test.each([
            {
                initial: "initial",
                changed: "changed",
                description: "string value to backup",
            },
            { initial: null, changed: "changed", description: "to null" },
            { initial: 0, changed: 42, description: "to zero" },
        ])("should reset $description", ({ initial, changed }) => {
            const wrapper = new ReactiveValueWrapper(initial);
            wrapper.value = changed;
            wrapper.reset();
            expect(wrapper.value).toBe(initial);
        });

        test("should reset to original value multiple times", () => {
            const wrapper = new ReactiveValueWrapper("initial");
            wrapper.value = "changed1";
            wrapper.reset();
            expect(wrapper.value).toBe("initial");

            wrapper.value = "changed2";
            wrapper.reset();
            expect(wrapper.value).toBe("initial");
        });
    });

    describe("save()", () => {
        test.each([
            { initial: "initial", changed: "changed", description: "string" },
            { initial: "initial", changed: null, description: "null value" },
            { initial: 42, changed: 0, description: "zero value" },
        ])("should save $description as new backup", ({ initial, changed }) => {
            const wrapper = new ReactiveValueWrapper(initial);
            wrapper.value = changed;
            wrapper.save();
            expect(wrapper.backup).toBe(changed);
            expect(wrapper.value).toBe(changed);
        });

        test("should allow reset to new backup after save", () => {
            const wrapper = new ReactiveValueWrapper("initial");
            wrapper.value = "changed";
            wrapper.save();
            wrapper.value = "another change";
            wrapper.reset();
            expect(wrapper.value).toBe("changed");
        });
    });

    describe("equals()", () => {
        describe("comparing with raw values", () => {
            test.each([
                {
                    value: "test",
                    compareWith: "test",
                    expected: true,
                    description: "equal string values",
                },
                {
                    value: "test",
                    compareWith: "other",
                    expected: false,
                    description: "different string values",
                },
                {
                    value: 42,
                    compareWith: 42,
                    expected: true,
                    description: "equal number values",
                },
            ])(
                "should return $expected for $description",
                ({ value, compareWith, expected }) => {
                    const wrapper = new ReactiveValueWrapper(value);
                    expect(wrapper.equals(compareWith)).toBe(expected);
                },
            );

            test.each([
                { compareWith: 0, expected: true, description: "0" },
                { compareWith: null, expected: false, description: "null" },
                {
                    compareWith: undefined,
                    expected: false,
                    description: "undefined",
                },
            ])(
                "should handle zero correctly when comparing with $description",
                ({ compareWith, expected }) => {
                    const wrapper = new ReactiveValueWrapper(0);
                    expect(wrapper.equals(compareWith)).toBe(expected);
                },
            );

            test.each([
                {
                    value: null,
                    compareWith: null,
                    expected: true,
                    description: "null with null",
                },
                {
                    value: null,
                    compareWith: undefined,
                    expected: true,
                    description: "null with undefined",
                },
                {
                    value: null,
                    compareWith: "",
                    expected: true,
                    description: "null with empty string",
                },
                {
                    value: undefined,
                    compareWith: undefined,
                    expected: true,
                    description: "undefined with undefined",
                },
                {
                    value: undefined,
                    compareWith: null,
                    expected: true,
                    description: "undefined with null",
                },
                {
                    value: undefined,
                    compareWith: "",
                    expected: true,
                    description: "undefined with empty string",
                },
                {
                    value: "",
                    compareWith: "",
                    expected: true,
                    description: "empty string with empty string",
                },
                {
                    value: "",
                    compareWith: null,
                    expected: true,
                    description: "empty string with null",
                },
                {
                    value: "",
                    compareWith: undefined,
                    expected: true,
                    description: "empty string with undefined",
                },
            ])(
                "should handle $description",
                ({ value, compareWith, expected }) => {
                    const wrapper = new ReactiveValueWrapper(value);
                    expect(wrapper.equals(compareWith)).toBe(expected);
                },
            );
        });

        describe("comparing with ReactiveValueWrapper objects", () => {
            test.each([
                {
                    value1: "test",
                    value2: "test",
                    expected: true,
                    description: "equal string values",
                },
                {
                    value1: "test",
                    value2: "other",
                    expected: false,
                    description: "different string values",
                },
            ])(
                "should return $expected for $description",
                ({ value1, value2, expected }) => {
                    const wrapper1 = new ReactiveValueWrapper(value1);
                    const wrapper2 = new ReactiveValueWrapper(value2);
                    expect(wrapper1.equals(wrapper2)).toBe(expected);
                },
            );

            test("should handle zero correctly", () => {
                const wrapper1 = new ReactiveValueWrapper(0);
                const wrapper2 = new ReactiveValueWrapper(0);
                const wrapper3 = new ReactiveValueWrapper(null);
                expect(wrapper1.equals(wrapper2)).toBe(true);
                expect(wrapper1.equals(wrapper3)).toBe(false);
            });

            test.each([
                {
                    value1: null,
                    value2: null,
                    description: "null values",
                },
                {
                    value1: null,
                    value2: undefined,
                    description: "null with undefined",
                },
                {
                    value1: undefined,
                    value2: undefined,
                    description: "undefined values",
                },
                {
                    value1: undefined,
                    value2: null,
                    description: "undefined with null",
                },
            ])("should handle $description", ({ value1, value2 }) => {
                const wrapper1 = new ReactiveValueWrapper(value1);
                const wrapper2 = new ReactiveValueWrapper(value2);
                expect(wrapper1.equals(wrapper2)).toBe(true);
            });
        });
    });

    describe("getPlainObject()", () => {
        test.each([
            {
                input: "test",
                expected: "test",
                description: "non-empty string",
            },
            { input: "", expected: null, description: "empty string" },
            { input: undefined, expected: null, description: "undefined" },
            { input: 42, expected: 42, description: "number" },
            { input: 0, expected: 0, description: "zero value" },
            { input: null, expected: null, description: "null" },
        ])(
            "should return $expected for $description",
            ({ input, expected }) => {
                const wrapper = new ReactiveValueWrapper(input);
                expect(wrapper.getPlainObject()).toBe(expected);
            },
        );

        test("should return object for object value", () => {
            const obj = { key: "value" };
            const wrapper = new ReactiveValueWrapper(obj);
            expect(wrapper.getPlainObject()).toStrictEqual(obj);
        });

        test("should return array for array value", () => {
            const arr = [1, 2, 3];
            const wrapper = new ReactiveValueWrapper(arr);
            expect(wrapper.getPlainObject()).toStrictEqual(arr);
        });
    });

    describe("integration scenarios", () => {
        test("should handle complete workflow: create, change, reset, change, save", () => {
            const wrapper = new ReactiveValueWrapper("initial");

            // Initial state
            expect(wrapper.value).toBe("initial");
            expect(wrapper.backup).toBe("initial");
            expect(wrapper.isModified).toBe(false);

            // Change value
            wrapper.value = "changed";
            expect(wrapper.isModified).toBe(true);

            // Reset
            wrapper.reset();
            expect(wrapper.value).toStrictEqual("initial");
            expect(wrapper.isModified).toBe(false);

            // Change again
            wrapper.value = "new value";
            expect(wrapper.isModified).toBe(true);

            // Save
            wrapper.save();
            expect(wrapper.backup).toBe("new value");
            expect(wrapper.isModified).toBe(false);
        });

        test("should handle validation workflow", () => {
            const wrapper = new ReactiveValueWrapper("ab", value => {
                if (value.length < 3) return ["Minimum 3 characters"];
                if (value.length > 10) return ["Maximum 10 characters"];
                return [];
            });

            // Initial state - invalid
            expect(wrapper.isValid).toBe(false);
            expect(wrapper.violations).toEqual(["Minimum 3 characters"]);

            // Make valid
            wrapper.value = "valid";
            expect(wrapper.isValid).toBe(true);
            expect(wrapper.violations).toEqual([]);

            // Make invalid again
            wrapper.value = "this is too long";
            expect(wrapper.isValid).toBe(false);
            expect(wrapper.violations).toEqual(["Maximum 10 characters"]);
        });

        test("should maintain independence between multiple instances", () => {
            const wrapper1 = new ReactiveValueWrapper("value1");
            const wrapper2 = new ReactiveValueWrapper("value2");

            wrapper1.value = "changed1";
            expect(wrapper2.value).toBe("value2");

            wrapper2.save();
            expect(wrapper1.backup).toBe("value1");
            expect(wrapper2.backup).toBe("value2");
        });
    });
});
