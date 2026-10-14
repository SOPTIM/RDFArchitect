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

import { isInvalidRangeValue } from "$lib/models/reactive/validity-rules/validityFunctions.js";
import {
    innerTickFractions,
    parseRangeInput,
    roundToDecimals,
    scaleLabels,
    snapToStep,
    valueAfterKey,
} from "$lib/utils/rangeValue.js";

describe("snapToStep", () => {
    test("keeps any value within the bounds without a step", () => {
        expect(snapToStep(1.547592, 1, 5)).toBe(1.547592);
    });

    test("snaps to the nearest step counted from the lower bound", () => {
        expect(snapToStep(1.547592, 1, 5, 1)).toBe(2);
        expect(snapToStep(2.4, 1, 5, 1)).toBe(2);
        expect(snapToStep(7, 0, 100, 5)).toBe(5);
        expect(snapToStep(0.37, 0, 1, 0.1)).toBe(0.4);
        expect(snapToStep(2.4, 0.5, 5, 1)).toBe(2.5);
    });

    test("never snaps beyond the upper bound", () => {
        expect(snapToStep(9.9, 0, 10, 4)).toBe(8);
    });

    test("leaves values outside of the bounds for the validation", () => {
        expect(snapToStep(7, 1, 5, 1)).toBe(7);
        expect(snapToStep(-1, 0, 100, 1)).toBe(-1);
    });
});

describe("roundToDecimals", () => {
    test("rounds to the given number of decimals", () => {
        expect(roundToDecimals(25.456, 2)).toBe(25.46);
        expect(roundToDecimals(33.33333, 2)).toBe(33.33);
        expect(roundToDecimals(12.5, 0)).toBe(13);
    });
});

describe("valueAfterKey", () => {
    test("moves one step with the arrow keys", () => {
        expect(valueAfterKey("ArrowRight", 60, 0, 100, 10, 2)).toBe(70);
        expect(valueAfterKey("ArrowDown", 60, 0, 100, 10, 2)).toBe(50);
        expect(valueAfterKey("ArrowUp", 0.3, 0, 1, 0.1, 2)).toBe(0.4);
    });

    test("moves from a value between two steps to the next step in the key direction", () => {
        expect(valueAfterKey("ArrowRight", 25, 0, 100, 10, 2)).toBe(30);
        expect(valueAfterKey("ArrowLeft", 25, 0, 100, 10, 2)).toBe(20);
    });

    test("moves a tenth of the range with the page keys and to the bounds with Home and End", () => {
        expect(valueAfterKey("PageUp", 40, 0, 100, 1, 2)).toBe(50);
        expect(valueAfterKey("PageDown", 40, 0, 100, 10, 2)).toBe(30);
        expect(valueAfterKey("Home", 40, 0, 100, 10, 2)).toBe(0);
        expect(valueAfterKey("End", 40, 0, 100, 10, 2)).toBe(100);
    });

    test("moves a hundredth of the range without a step and stays within the bounds", () => {
        expect(valueAfterKey("ArrowRight", 12.5, 0, 100, undefined, 2)).toBe(
            13.5,
        );
        expect(valueAfterKey("ArrowRight", 99.5, 0, 100, 10, 2)).toBe(100);
        expect(valueAfterKey("ArrowLeft", 150, 0, 100, 10, 2)).toBe(90);
    });

    test("ignores other keys", () => {
        expect(valueAfterKey("a", 40, 0, 100, 10, 2)).toBeNull();
    });
});

describe("innerTickFractions", () => {
    test("has a tick for every step between the two ends", () => {
        expect(innerTickFractions(0, 100, 25)).toEqual([0.25, 0.5, 0.75]);
        expect(innerTickFractions(0, 10, 4)).toEqual([0.4, 0.8]);
    });

    test("has no ticks without a step or with more than 30 ticks", () => {
        expect(innerTickFractions(0, 100)).toEqual([]);
        expect(innerTickFractions(0, 29, 1)).toHaveLength(28);
        expect(innerTickFractions(0, 30, 1)).toEqual([]);
    });
});

describe("scaleLabels", () => {
    const valuesOf = labels => labels.map(label => label.value);

    test("labels the two ends and evenly spaced ticks in between", () => {
        expect(valuesOf(scaleLabels(0, 100, 20))).toEqual([
            0, 20, 40, 60, 80, 100,
        ]);
        expect(valuesOf(scaleLabels(0, 100, 10))).toEqual([
            0, 20, 40, 60, 80, 100,
        ]);
        expect(valuesOf(scaleLabels(0, 100, 4))).toEqual([
            0, 20, 40, 60, 80, 100,
        ]);
        expect(scaleLabels(0, 100, 25)[1]).toEqual({
            value: 25,
            fraction: 0.25,
        });
    });

    test("labels only the two ends without ticks", () => {
        expect(valuesOf(scaleLabels(0, 100))).toEqual([0, 100]);
        expect(valuesOf(scaleLabels(0, 100, 1))).toEqual([0, 100]);
        expect(valuesOf(scaleLabels(0, 29, 1))).toEqual([0, 29]);
    });
});

describe("parseRangeInput", () => {
    test("parses numbers and their text", () => {
        expect(parseRangeInput("42.5")).toBe(42.5);
        expect(parseRangeInput(3)).toBe(3);
    });

    test("is null for empty and non-numeric input", () => {
        expect(parseRangeInput("")).toBeNull();
        expect(parseRangeInput("  ")).toBeNull();
        expect(parseRangeInput(undefined)).toBeNull();
        expect(parseRangeInput("5a")).toBeNull();
    });
});

describe("isInvalidRangeValue", () => {
    test("accepts numbers within the bounds", () => {
        expect(isInvalidRangeValue("3", 1, 5)).toEqual([]);
        expect(isInvalidRangeValue(5, 1, 5)).toEqual([]);
    });

    test("reports empty, non-numeric and out of bounds input", () => {
        expect(isInvalidRangeValue("", 1, 5)).toEqual(["must not be empty"]);
        expect(isInvalidRangeValue("x", 1, 5)).toEqual(["must be a number"]);
        expect(isInvalidRangeValue(6, 1, 5)).toEqual([
            "must be between 1 and 5",
        ]);
    });
});
