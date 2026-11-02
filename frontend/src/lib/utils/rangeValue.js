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
 * Snaps a value to the nearest step counted from the lower bound, never beyond the last step
 * within the upper bound. Without a step any value is kept as it is. A value outside of the bounds
 * is kept as well, so it can be reported as invalid instead of being changed silently.
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
    const lastStep = Math.floor((max - min) / step + 1e-9);
    return gridValue(
        Math.min(lastStep, Math.round((value - min) / step)),
        min,
        step,
    );
}

/**
 * Decides which ticks of a slider are drawn and labelled. The two ends always are. The ticks in
 * between are drawn if the slider has steps and at most {@link MAX_TICKS} ticks; then evenly spaced
 * ones of them are labelled as well, at most {@link MAX_SCALE_LABELS} labels in total, so the
 * distance of the steps can be read off.
 *
 * @param {number} tickCount the number of ticks, one for every step including the two ends
 * @param {boolean} stepped whether the slider moves in visible steps
 * @returns {{innerTicks: boolean, labelIndices: number[]}} whether the ticks in between are drawn,
 *     and the indices of the labelled ticks
 */
export function sliderScale(tickCount, stepped) {
    if (tickCount <= 1) {
        return { innerTicks: false, labelIndices: tickCount === 1 ? [0] : [] };
    }
    const lastIndex = tickCount - 1;
    if (!stepped || tickCount > MAX_TICKS) {
        return { innerTicks: false, labelIndices: [0, lastIndex] };
    }
    const stride = labelStride(lastIndex);
    const labelIndices = [0];
    for (let index = stride; index < lastIndex; index += stride) {
        labelIndices.push(index);
    }
    labelIndices.push(lastIndex);
    return { innerTicks: true, labelIndices };
}

function labelStride(stepCount) {
    for (let stride = 1; stride < stepCount; stride++) {
        if (stepCount % stride === 0 && stepCount / stride < MAX_SCALE_LABELS) {
            return stride;
        }
    }
    return stepCount;
}

function gridValue(stepCount, min, step) {
    const decimals = Math.max(decimalsOf(min), decimalsOf(step));
    return Number((min + stepCount * step).toFixed(decimals));
}

function decimalsOf(number) {
    const [, decimals = ""] = String(number).split(".");
    return decimals.length;
}
