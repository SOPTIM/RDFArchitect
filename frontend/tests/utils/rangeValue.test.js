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
    parseRangeInput,
    roundToDecimals,
    sliderScale,
    snapToStep,
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

    test("never snaps beyond the last step within the upper bound", () => {
        expect(snapToStep(9.9, 0, 10, 4)).toBe(8);
        expect(snapToStep(95, 0, 95, 10)).toBe(90);
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

describe("sliderScale", () => {
    test("draws every tick of a stepped slider and labels evenly spaced ones", () => {
        expect(sliderScale(11, true)).toEqual({
            innerTicks: true,
            labelIndices: [0, 2, 4, 6, 8, 10],
        });
        expect(sliderScale(6, true)).toEqual({
            innerTicks: true,
            labelIndices: [0, 1, 2, 3, 4, 5],
        });
    });

    test("draws and labels only the two ends without steps or with more than 30 ticks", () => {
        expect(sliderScale(30, true).innerTicks).toBe(true);
        expect(sliderScale(31, true)).toEqual({
            innerTicks: false,
            labelIndices: [0, 30],
        });
        expect(sliderScale(10001, false)).toEqual({
            innerTicks: false,
            labelIndices: [0, 10000],
        });
    });

    test("handles a slider with a single step or none", () => {
        expect(sliderScale(1, true).labelIndices).toEqual([0]);
        expect(sliderScale(0, true).labelIndices).toEqual([]);
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
