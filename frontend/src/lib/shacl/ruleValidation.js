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

/**
 * What the form refuses to send, and says why on the field.
 *
 * The server writes whatever it is handed as long as Turtle can hold it, so a minimum above the
 * maximum or a pattern that is not a regular expression reached the document silently — and a
 * rule that can never be met reads exactly like one that can. Kept out of the cards so it can be
 * tested without a DOM.
 */

/**
 * A number Turtle could read, and nothing else — the same test the backend writer makes.
 *
 * Deliberately stricter than `Number()`, which takes `"1."`, `" 2 "` and `"0x10"`: the digits go
 * back between the quotes of the literal the document already has, so they have to be digits.
 */

/** The pairs of fields where the first must not exceed the second. */
const BOUNDS = [
    ["minCount", "maxCount", "Less than the minimum."],
    ["minLength", "maxLength", "Less than the shortest length."],
    ["minInclusive", "maxInclusive", "Below the lowest value allowed."],
    ["minExclusive", "maxExclusive", "Below the lowest value allowed."],
];

/** The flags `sh:flags` takes: those of XPath's `fn:matches`. */
const FLAGS = /^[smixq]*$/;
export function isLexicalNumber(text) {
    return (
        /^[+-]?\d+$/.test(text) ||
        /^[+-]?\d*\.\d+$/.test(text) ||
        /^[+-]?(\d+\.\d*|\.\d+|\d+)[eE][+-]?\d+$/.test(text)
    );
}

/** A count — of values, of characters — is a whole number, zero or more. */
export function countProblem(text) {
    return text === "" || /^\+?\d+$/.test(text)
        ? null
        : "A whole number, 0 or more.";
}

/** A bound of a value range is a number, written the way the document will hold it. */
export function numberProblem(text) {
    return text === "" || isLexicalNumber(text)
        ? null
        : "A number, such as 10 or 0.5.";
}

/**
 * What is wrong with a rule as the form now holds it, as `{ field: message }`.
 *
 * A pair of bounds is reported on the upper one. Either could be the one to change, but a
 * message on both would read as two problems.
 */
export function ruleProblems(rule) {
    const problems = {};
    for (const [low, high, message] of BOUNDS) {
        if (!present(rule[low]) || !present(rule[high])) {
            continue;
        }
        const min = Number(rule[low]);
        const max = Number(rule[high]);
        if (Number.isFinite(min) && Number.isFinite(max) && min > max) {
            problems[high] = message;
        }
    }
    if (present(rule.pattern)) {
        try {
            new RegExp(rule.pattern);
        } catch (error) {
            problems.pattern = `Not a regular expression: ${error.message}`;
        }
    }
    if (present(rule.flags) && !FLAGS.test(rule.flags)) {
        problems.flags = "Only the flags s, m, i, x and q are allowed.";
    }
    return problems;
}

function present(value) {
    return value !== null && value !== undefined && value !== "";
}
