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

import { ReactiveObjectsArrayWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-objects-array-wrapper.svelte.js";
import { ReactiveValueWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-value-wrapper.svelte.js";

describe("ReactiveObjectsArrayWrapper", () => {
    describe("constructor()", () => {
        test("should initialize with empty array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [],
                ReactiveValueWrapper,
            );
            expect(wrapper.values).toEqual([]);
            expect(wrapper.backup).toEqual([]);
        });

        test("should initialize with array of values", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2", "test3"],
                ReactiveValueWrapper,
            );
            expect(wrapper.values).toHaveLength(3);
            expect(wrapper.backup).toHaveLength(3);
            expect(wrapper.values[0].value).toBe("test1");
            expect(wrapper.values[1].value).toBe("test2");
            expect(wrapper.values[2].value).toBe("test3");
        });

        test("should wrap each value with EntryClass", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
            );
            expect(wrapper.values[0]).toBeInstanceOf(ReactiveValueWrapper);
            expect(wrapper.values[1]).toBeInstanceOf(ReactiveValueWrapper);
        });

        test("should keep same instances in backend", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test"],
                ReactiveValueWrapper,
            );
            expect(wrapper.values[0]).toBe(wrapper.backup[0]);
            expect(wrapper.values[0].value).toBe(wrapper.backup[0].value);
        });
    });

    describe("isModified", () => {
        test("should return false when array is unchanged", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
            );
            expect(wrapper.isModified).toBe(false);
        });

        test("should return true when entry is added", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.append("test2");
            expect(wrapper.isModified).toBe(true);
        });

        test("should return true when entry is removed", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
            );
            wrapper.remove(wrapper.values[0]);
            expect(wrapper.isModified).toBe(true);
        });

        test("should return true when entry value is modified", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
            );
            wrapper.values[0].value = "modified";
            expect(wrapper.isModified).toBe(true);
        });

        test("should return false when original entry is replaced with equal value", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            const originalEntry = wrapper.values[0];
            wrapper.remove(originalEntry);
            wrapper.append("test1"); // Same value, different instance
            expect(wrapper.isModified).toBe(false);
        });

        test("should return false after reset", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.append("test2");
            wrapper.reset();
            expect(wrapper.isModified).toBe(false);
        });

        test("should return false after save", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.append("test2");
            wrapper.save();
            expect(wrapper.isModified).toBe(false);
        });

        test("should return true when entry is prepended", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.prepend("test0");
            expect(wrapper.isModified).toBe(true);
        });

        test("should return false when original entry is replaced with equal value via prepend", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            const originalEntry = wrapper.values[0];
            wrapper.remove(originalEntry);
            wrapper.prepend("test1"); // Same value, different instance
            expect(wrapper.isModified).toBe(false);
        });
    });

    describe("isValid", () => {
        test("should return true when all entries are valid", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
                entry => entry.violationChecks.push(() => []),
            );
            expect(wrapper.isValid).toBe(true);
        });

        test("should return true for empty array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [],
                ReactiveValueWrapper,
                entry => entry.violationChecks.push(() => []),
            );
            expect(wrapper.isValid).toBe(true);
        });

        test("should return false when one entry is invalid", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
                entry => entry.violationChecks.push(() => ["Error"]),
            );
            expect(wrapper.isValid).toBe(false);
        });

        test("should return false when multiple entries are invalid", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2", "test3"],
                ReactiveValueWrapper,
                entry => {
                    // Make only specific entries invalid by value to keep tests deterministic
                    entry.violationChecks.push(value => {
                        if (value === "test1") return ["Error 1"];
                        if (value === "test3") return ["Error 2"];
                        return [];
                    });
                },
            );
            expect(wrapper.isValid).toBe(false);
        });

        test("should update when entry validity changes", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test"],
                ReactiveValueWrapper,
                entry =>
                    entry.violationChecks.push(value => {
                        if (value.length < 5) return ["Too short"];
                        return [];
                    }),
            );
            expect(wrapper.isValid).toBe(false);

            wrapper.values[0].value = "long enough";
            expect(wrapper.isValid).toBe(true);
        });

        test("should update validity when invalid entry is prepended and then fixed/removed", () => {
            const min3chars = entry => {
                entry.violationChecks.push(value => {
                    if (value.length < 3) return ["Minimum 3 characters"];
                    return [];
                });
            };

            const wrapper = new ReactiveObjectsArrayWrapper(
                ["valid"],
                ReactiveValueWrapper,
                min3chars,
            );

            // Baseline valid
            expect(wrapper.isValid).toBe(true);

            // Prepend invalid entry
            wrapper.prepend("x");
            min3chars(wrapper.values[0]); // apply checks to the newly created entry
            expect(wrapper.isValid).toBe(false);

            // Fix invalid entry
            wrapper.values[0].value = "fixed";
            expect(wrapper.isValid).toBe(true);

            // Prepend another invalid entry and remove it
            wrapper.prepend("y");
            min3chars(wrapper.values[0]); // apply checks to the newly created entry
            expect(wrapper.isValid).toBe(false);

            wrapper.remove(wrapper.values[0]);
            expect(wrapper.isValid).toBe(true);
        });
    });

    describe("contains()", () => {
        test("should return true when value exists in array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
            );
            expect(wrapper.contains(wrapper.values[0])).toBe(true);
        });

        test("should return false when value does not exist in array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
            );
            const otherValue = new ReactiveValueWrapper("test3");
            expect(wrapper.contains(otherValue)).toBe(false);
        });

        test("should return true for equal but different instance", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            const equalValue = new ReactiveValueWrapper("test1");
            expect(wrapper.contains(equalValue)).toBe(true);
        });

        test("should return false for empty array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [],
                ReactiveValueWrapper,
            );
            const value = new ReactiveValueWrapper("test");
            expect(wrapper.contains(value)).toBe(false);
        });
    });

    describe("add()", () => {
        test("should add value to empty array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [],
                ReactiveValueWrapper,
            );
            wrapper.append("test");
            expect(wrapper.values).toHaveLength(1);
            expect(wrapper.values[0].value).toBe("test");
        });

        test("should add value to existing array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.append("test2");
            expect(wrapper.values).toHaveLength(2);
            expect(wrapper.values[1].value).toBe("test2");
        });

        test("should wrap added value with EntryClass", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [],
                ReactiveValueWrapper,
            );
            wrapper.append("test");
            expect(wrapper.values[0]).toBeInstanceOf(ReactiveValueWrapper);
        });

        test("should add multiple values", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [],
                ReactiveValueWrapper,
            );
            wrapper.append("test1");
            wrapper.append("test2");
            wrapper.append("test3");
            expect(wrapper.values).toHaveLength(3);
            expect(wrapper.values[0].value).toBe("test1");
            expect(wrapper.values[1].value).toBe("test2");
            expect(wrapper.values[2].value).toBe("test3");
        });

        test("should allow adding duplicate values", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test"],
                ReactiveValueWrapper,
            );
            wrapper.append("test");
            expect(wrapper.values).toHaveLength(2);
            expect(wrapper.values[0].value).toBe("test");
            expect(wrapper.values[1].value).toBe("test");
        });
    });

    describe("prepend()", () => {
        test("should prepend value to empty array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [],
                ReactiveValueWrapper,
            );
            wrapper.prepend("test");
            expect(wrapper.values).toHaveLength(1);
            expect(wrapper.values[0].value).toBe("test");
        });

        test("should prepend value to existing array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.prepend("test0");
            expect(wrapper.values).toHaveLength(2);
            expect(wrapper.values[0].value).toBe("test0");
            expect(wrapper.values[1].value).toBe("test1");
        });

        test("should wrap prepended value with EntryClass", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [],
                ReactiveValueWrapper,
            );
            wrapper.prepend("test");
            expect(wrapper.values[0]).toBeInstanceOf(ReactiveValueWrapper);
        });

        test("should prepend multiple values in correct order", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [],
                ReactiveValueWrapper,
            );
            wrapper.prepend("c");
            wrapper.prepend("b");
            wrapper.prepend("a");
            expect(wrapper.values).toHaveLength(3);
            expect(wrapper.values[0].value).toBe("a");
            expect(wrapper.values[1].value).toBe("b");
            expect(wrapper.values[2].value).toBe("c");
        });

        test("should allow prepending duplicate values", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test"],
                ReactiveValueWrapper,
            );
            wrapper.prepend("test");
            expect(wrapper.values).toHaveLength(2);
            expect(wrapper.values[0].value).toBe("test");
            expect(wrapper.values[1].value).toBe("test");
        });
    });

    describe("remove()", () => {
        test("should remove value from array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
            );
            const toRemove = wrapper.values[0];
            wrapper.remove(toRemove);
            expect(wrapper.values).toHaveLength(1);
            expect(wrapper.values[0].value).toBe("test2");
        });

        test("should remove last value from array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test"],
                ReactiveValueWrapper,
            );
            wrapper.remove(wrapper.values[0]);
            expect(wrapper.values).toHaveLength(0);
        });

        test("should do nothing when removing non-existent value", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            const otherValue = new ReactiveValueWrapper("test2");
            wrapper.remove(otherValue);
            expect(wrapper.values).toHaveLength(1);
            expect(wrapper.values[0].value).toBe("test1");
        });

        test("should remove by equality, not reference", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            const equalValue = new ReactiveValueWrapper("test1");
            wrapper.remove(equalValue);
            expect(wrapper.values).toHaveLength(0);
        });

        test("should remove only matching entries", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2", "test1"],
                ReactiveValueWrapper,
            );
            const toRemove = new ReactiveValueWrapper("test1");
            wrapper.remove(toRemove);
            expect(wrapper.values).toHaveLength(1);
            expect(wrapper.values[0].value).toBe("test2");
        });
    });

    describe("reset()", () => {
        test("should reset to original values after additions", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.append("test2");
            wrapper.append("test3");
            wrapper.reset();
            expect(wrapper.values).toHaveLength(1);
            expect(wrapper.values[0].value).toBe("test1");
        });

        test("should reset to original values after prepends", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.prepend("test0");
            wrapper.prepend("test-1");
            wrapper.reset();
            expect(wrapper.values).toHaveLength(1);
            expect(wrapper.values[0].value).toBe("test1");
        });

        test("should reset to original values after removals", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
            );
            wrapper.remove(wrapper.values[0]);
            wrapper.reset();
            expect(wrapper.values).toHaveLength(2);
            expect(wrapper.values[0].value).toBe("test1");
            expect(wrapper.values[1].value).toBe("test2");
        });

        test("should reset modified entry values", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
            );
            wrapper.values[0].value = "modified";
            wrapper.reset();
            expect(wrapper.values[0].value).toBe("test1");
        });

        test("should reset empty array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [],
                ReactiveValueWrapper,
            );
            wrapper.append("test");
            wrapper.reset();
            expect(wrapper.values).toHaveLength(0);
        });
    });

    describe("save()", () => {
        test("should save added values as new baseline", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.append("test2");
            wrapper.save();
            expect(wrapper.backup).toHaveLength(2);
            expect(wrapper.isModified).toBe(false);
        });

        test("should save prepended values as new baseline", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.prepend("test0");
            wrapper.save();
            expect(wrapper.backup).toHaveLength(2);
            expect(wrapper.backup[0].value).toBe("test0");
            expect(wrapper.backup[1].value).toBe("test1");
            expect(wrapper.isModified).toBe(false);
        });

        test("should save removed values as new baseline", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2"],
                ReactiveValueWrapper,
            );
            wrapper.remove(wrapper.values[0]);
            wrapper.save();
            expect(wrapper.backup).toHaveLength(1);
            expect(wrapper.backup[0].value).toBe("test2");
            expect(wrapper.isModified).toBe(false);
        });

        test("should save modified entry values", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.values[0].value = "modified";
            wrapper.save();
            expect(wrapper.backup[0].value).toBe("modified");
            expect(wrapper.isModified).toBe(false);
        });

        test("should allow reset to new baseline after save (prepend path)", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.prepend("test0");
            wrapper.save();

            wrapper.prepend("test-1");
            wrapper.reset();

            expect(wrapper.values).toHaveLength(2);
            expect(wrapper.values[0].value).toBe("test0");
            expect(wrapper.values[1].value).toBe("test1");
        });

        test("should save empty array", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test"],
                ReactiveValueWrapper,
            );
            wrapper.remove(wrapper.values[0]);
            wrapper.save();
            expect(wrapper.backup).toHaveLength(0);
            expect(wrapper.isModified).toBe(false);
        });
    });

    describe("getPlainObject()", () => {
        test("should return plain array of values", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1", "test2", "test3"],
                ReactiveValueWrapper,
            );
            const result = wrapper.getPlainObject();
            expect(result).toEqual(["test1", "test2", "test3"]);
        });

        test("should return empty array for empty wrapper", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [],
                ReactiveValueWrapper,
            );
            expect(wrapper.getPlainObject()).toEqual([]);
        });

        test("should handle null and undefined values", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                [null, undefined, "test"],
                ReactiveValueWrapper,
            );
            const result = wrapper.getPlainObject();
            expect(result).toEqual([null, null, "test"]);
        });

        test("should reflect current values, not backup", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            wrapper.append("test2");
            wrapper.values[0].value = "modified";
            const result = wrapper.getPlainObject();
            expect(result).toEqual(["modified", "test2"]);
        });

        test("should reflect prepended values in correct order", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["b", "c"],
                ReactiveValueWrapper,
            );
            wrapper.prepend("a");
            const result = wrapper.getPlainObject();
            expect(result).toEqual(["a", "b", "c"]);
        });
    });

    describe("integration scenarios", () => {
        test("should handle complete workflow: create, add, modify, reset, save", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["initial"],
                ReactiveValueWrapper,
            );

            // Initial state
            expect(wrapper.values).toHaveLength(1);
            expect(wrapper.isModified).toBe(false);

            // Add entry
            wrapper.append("new");
            expect(wrapper.values).toHaveLength(2);
            expect(wrapper.isModified).toBe(true);

            // Modify entry
            wrapper.values[0].value = "modified";
            expect(wrapper.isModified).toBe(true);

            // Reset
            wrapper.reset();
            expect(wrapper.values).toHaveLength(1);
            expect(wrapper.values[0].value).toBe("initial");
            expect(wrapper.isModified).toBe(false);

            // Make changes and save
            wrapper.append("new entry");
            wrapper.values[0].value = "changed";
            wrapper.save();
            expect(wrapper.isModified).toBe(false);
            expect(wrapper.backup).toHaveLength(2);
            expect(wrapper.backup[0].value).toBe("changed");
        });

        test("should handle workflow using prepend: create, prepend, modify, reset, save", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["initial"],
                ReactiveValueWrapper,
            );

            // Initial state
            expect(wrapper.values).toHaveLength(1);
            expect(wrapper.isModified).toBe(false);

            // Prepend entry
            wrapper.prepend("new");
            expect(wrapper.values).toHaveLength(2);
            expect(wrapper.values[0].value).toBe("new");
            expect(wrapper.isModified).toBe(true);

            // Modify original entry (now at index 1)
            wrapper.values[1].value = "modified";
            expect(wrapper.isModified).toBe(true);

            // Reset
            wrapper.reset();
            expect(wrapper.values).toHaveLength(1);
            expect(wrapper.values[0].value).toBe("initial");
            expect(wrapper.isModified).toBe(false);

            // Make changes and save (prepend path)
            wrapper.prepend("new entry");
            wrapper.values[1].value = "changed";
            wrapper.save();
            expect(wrapper.isModified).toBe(false);
            expect(wrapper.backup).toHaveLength(2);
            expect(wrapper.backup[0].value).toBe("new entry");
            expect(wrapper.backup[1].value).toBe("changed");
        });

        test("should handle validation workflow", () => {
            const min3chars = entry => {
                entry.violationChecks.push(value => {
                    if (value.length < 3) return ["Minimum 3 characters"];
                    return [];
                });
            };

            const wrapper = new ReactiveObjectsArrayWrapper(
                ["ab", "abc"],
                ReactiveValueWrapper,
                min3chars,
            );

            // Initial state - one invalid
            expect(wrapper.isValid).toBe(false);

            // Fix invalid entry
            wrapper.values[0].value = "valid";
            expect(wrapper.isValid).toBe(true);

            // Add invalid entry
            wrapper.append("x");
            min3chars(wrapper.values[2]); // apply checks to the newly created entry
            expect(wrapper.isValid).toBe(false);

            // Remove invalid entry
            wrapper.remove(wrapper.values[2]);
            expect(wrapper.isValid).toBe(true);
        });

        test("should maintain independence between multiple instances", () => {
            const wrapper1 = new ReactiveObjectsArrayWrapper(
                ["test1"],
                ReactiveValueWrapper,
            );
            const wrapper2 = new ReactiveObjectsArrayWrapper(
                ["test2"],
                ReactiveValueWrapper,
            );

            wrapper1.append("new1");
            expect(wrapper2.values).toHaveLength(1);

            wrapper2.save();
            expect(wrapper1.backup).toHaveLength(1);
            expect(wrapper2.backup).toHaveLength(1);
        });

        test("should handle modification sequence", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["a", "b", "c"],
                ReactiveValueWrapper,
            );

            // Remove middle entry
            wrapper.remove(wrapper.values[1]);
            expect(wrapper.values).toHaveLength(2);

            // Add two new entries
            wrapper.append("d");
            wrapper.append("e");
            expect(wrapper.values).toHaveLength(4);

            // Modify first entry
            wrapper.values[0].value = "modified";

            // Save
            wrapper.save();
            expect(wrapper.backup).toHaveLength(4);
            expect(wrapper.backup[0].value).toBe("modified");

            // Further changes after save
            wrapper.remove(wrapper.values[0]);
            expect(wrapper.isModified).toBe(true);

            // Reset to saved state
            wrapper.reset();
            expect(wrapper.values).toHaveLength(4);
            expect(wrapper.values[0].value).toBe("modified");
        });

        test("should handle modification sequence including prepend", () => {
            const wrapper = new ReactiveObjectsArrayWrapper(
                ["b", "c"],
                ReactiveValueWrapper,
            );

            // Prepend a
            wrapper.prepend("a");
            expect(wrapper.values).toHaveLength(3);
            expect(wrapper.values[0].value).toBe("a");

            // Remove middle entry (b)
            wrapper.remove(wrapper.values[1]);
            expect(wrapper.getPlainObject()).toEqual(["a", "c"]);

            // Save baseline
            wrapper.save();
            expect(wrapper.isModified).toBe(false);

            // Further changes
            wrapper.prepend("x");
            expect(wrapper.getPlainObject()).toEqual(["x", "a", "c"]);
            expect(wrapper.isModified).toBe(true);

            // Reset to saved state
            wrapper.reset();
            expect(wrapper.getPlainObject()).toEqual(["a", "c"]);
            expect(wrapper.isModified).toBe(false);
        });
    });
});
