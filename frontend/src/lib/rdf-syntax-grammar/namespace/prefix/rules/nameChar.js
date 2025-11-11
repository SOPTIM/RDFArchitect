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

import { isNameStartChar } from "./nameStartChar.js";

const EXTRA_RANGES = [
    [0x0300, 0x036f],
    [0x203f, 0x2040],
];

function inRanges(cp, ranges) {
    for (const [min, max] of ranges) {
        if (cp >= min && cp <= max) return true;
    }
    return false;
}

/**
 * Checks whether a Unicode code point is a valid XML NameChar.
 *
 * [4a] NameChar ::= NameStartChar
 *                 | "-"
 *                 | "."
 *                 | [0-9]
 *                 | #xB7
 *                 | [#x0300-#x036F]
 *                 | [#x203F-#x2040]
 *
 * Spec:
 * https://www.w3.org/TR/xml-names/#NT-NameChar
 *
 * @param {number} cp - Unicode code point
 * @returns {boolean} true if the code point is a NameChar
 */
export function isNameChar(cp) {
    if (isNameStartChar(cp)) return true;
    if (cp === 0x2d || cp === 0x2e) return true; // '-' | '.'
    if (cp >= 0x30 && cp <= 0x39) return true; // digits
    if (cp === 0x00b7) return true; // #xB7
    return inRanges(cp, EXTRA_RANGES);
}
