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

const MAX_TICKS = 30;

const MAX_SCALE_LABELS = 6;

const KEY_DIRECTIONS = {
    ArrowRight: 1,
    ArrowUp: 1,
    PageUp: 1,
    ArrowLeft: -1,
    ArrowDown: -1,
    PageDown: -1,
};

/**
 * Parses the text of a value input into a number.
 *
 * @returns the number, or null if the text is empty or no number
 */
export function parseRangeInput(text) {
    const raw = typeof text === "number" ? String(text) : (text ?? "");
    if (raw.trim() === "") {
        return null;
    }
    const parsed = Number(raw);
    return Number.isNaN(parsed) ? null : parsed;
}

/**
 * Rounds a value to a number of decimals.
 *
 * @param {number} value the value to round
 * @param {number} decimals the number of decimals to keep
 * @returns the rounded value
 */
export function roundToDecimals(value, decimals) {
    const factor = 10 ** decimals;
    return Math.round(value * factor) / factor;
}

/**
 * Snaps a value to the nearest step counted from the lower bound. Without a step any value is
 * kept as it is. A value outside of the bounds is kept as well, so it can be reported as invalid
 * instead of being changed silently.
 *
 * @param {number} value the value to snap
 * @param {number} min the lower bound
 * @param {number} max the upper bound
 * @param {number} [step] the distance between two allowed values
 * @returns the snapped value
 */
export function snapToStep(value, min, max, step) {
    if (!step || step <= 0 || value < min || value > max) {
        return value;
    }
    return Math.min(
        max,
        gridValue(Math.round((value - min) / step), min, step),
    );
}

/**
 * Computes the value a slider moves to on a key press. The arrow keys move one step, the page
 * keys a tenth of the range and Home and End jump to the bounds. A value between two steps moves
 * to the next step in the direction of the key.
 *
 * @param {string} key the pressed key
 * @param {number} value the current value
 * @param {number} min the lower bound
 * @param {number} max the upper bound
 * @param {number} [step] the distance between two allowed values, a hundredth of the range if
 *     not set
 * @param {number} decimals the number of decimals of a value without a step
 * @returns the new value, or null if the key does not move the slider
 */
export function valueAfterKey(key, value, min, max, step, decimals) {
    if (key === "Home") {
        return min;
    }
    if (key === "End") {
        return max;
    }
    const direction = KEY_DIRECTIONS[key];
    if (!direction) {
        return null;
    }
    const distance = step > 0 ? step : (max - min) / 100;
    const count = key.startsWith("Page")
        ? Math.max(1, Math.round((max - min) / distance / 10))
        : 1;
    const target =
        Math.min(max, Math.max(min, value)) + direction * count * distance;
    if (target >= max) {
        return max;
    }
    if (target <= min) {
        return min;
    }
    if (!(step > 0)) {
        return roundToDecimals(target, decimals);
    }
    const steps = (target - min) / step;
    const nearest = Math.round(steps);
    if (Math.abs(steps - nearest) < 1e-9) {
        return gridValue(nearest, min, step);
    }
    return gridValue(
        direction > 0 ? Math.floor(steps) : Math.ceil(steps),
        min,
        step,
    );
}

/**
 * Computes where the ticks between the two ends of a slider go. There is a tick for every step,
 * but none at all if there would be more than {@link MAX_TICKS} ticks including the two ends.
 *
 * @param {number} min the lower bound
 * @param {number} max the upper bound
 * @param {number} [step] the distance between two allowed values
 * @returns the positions of the ticks as fractions of the slider length, without the two ends
 */
export function innerTickFractions(min, max, step) {
    const grid = gridOf(min, max, step);
    if (!grid) {
        return [];
    }
    const fractions = [];
    const innerEnd = grid.endsOnStep ? grid.lastStep : grid.lastStep + 1;
    for (let i = 1; i < innerEnd; i++) {
        fractions.push(fractionOf(gridValue(i, min, step), min, max));
    }
    return fractions;
}

/**
 * Computes the values written below a slider. The two ends are always labelled. If the slider
 * shows ticks, evenly spaced ticks in between are labelled as well, at most
 * {@link MAX_SCALE_LABELS} labels in total, so the distance of the steps can be read off.
 *
 * @param {number} min the lower bound
 * @param {number} max the upper bound
 * @param {number} [step] the distance between two allowed values
 * @returns the labels with their value and their position as a fraction of the slider length
 */
export function scaleLabels(min, max, step) {
    const labels = [{ value: min, fraction: 0 }];
    const grid = gridOf(min, max, step);
    if (grid) {
        const stride = labelStride(grid.lastStep);
        for (let i = stride; i < grid.lastStep; i += stride) {
            const value = gridValue(i, min, step);
            labels.push({ value, fraction: fractionOf(value, min, max) });
        }
    }
    labels.push({ value: max, fraction: 1 });
    return labels;
}

function gridOf(min, max, step) {
    if (!(step > 0) || max <= min) {
        return null;
    }
    const lastStep = Math.floor((max - min) / step + 1e-9);
    const endsOnStep = Math.abs(gridValue(lastStep, min, step) - max) < 1e-9;
    const tickCount = lastStep + (endsOnStep ? 1 : 2);
    return tickCount > MAX_TICKS ? null : { lastStep, endsOnStep };
}

function labelStride(stepCount) {
    for (let stride = 1; stride < stepCount; stride++) {
        if (stepCount % stride === 0 && stepCount / stride < MAX_SCALE_LABELS) {
            return stride;
        }
    }
    return stepCount;
}

function fractionOf(value, min, max) {
    return (value - min) / (max - min);
}

function gridValue(stepCount, min, step) {
    const decimals = Math.max(decimalsOf(min), decimalsOf(step));
    return Number((min + stepCount * step).toFixed(decimals));
}

function decimalsOf(number) {
    const [, decimals = ""] = String(number).split(".");
    return decimals.length;
}
