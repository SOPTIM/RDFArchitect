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

const START_RANGES = [
    [0x00c0, 0x00d6],
    [0x00d8, 0x00f6],
    [0x00f8, 0x02ff],
    [0x0370, 0x037d],
    [0x037f, 0x1fff],
    [0x200c, 0x200d],
    [0x2070, 0x218f],
    [0x2c00, 0x2fef],
    [0x3001, 0xd7ff],
    [0xf900, 0xfdcf],
    [0xfdf0, 0xfffd],
    [0x10000, 0xeffff],
];

function inRanges(cp, ranges) {
    for (const [min, max] of ranges) {
        if (cp >= min && cp <= max) return true;
    }
    return false;
}

/**
 * Checks whether a Unicode code point is a valid XML NameStartChar.
 *
 * NameStartChar ::= ":"
 *                 | [A-Z] | "_"
 *                 | [a-z]
 *                 | [#xC0-#xD6]
 *                 | [#xD8-#xF6]
 *                 | [#xF8-#x2FF]
 *                 | [#x370-#x37D]
 *                 | [#x37F-#x1FFF]
 *                 | [#x200C-#x200D]
 *                 | [#x2070-#x218F]
 *                 | [#x2C00-#x2FEF]
 *                 | [#x3001-#xD7FF]
 *                 | [#xF900-#xFDCF]
 *                 | [#xFDF0-#xFFFD]
 *                 | [#x10000-#xEFFFF]
 *
 * Spec:
 * https://www.w3.org/TR/xml-names/#NT-NameStartChar
 *
 * @param {number} cp - Unicode code point
 * @returns {boolean} true if the code point is a NameStartChar
 */
export function isNameStartChar(cp) {
    if (cp === 0x3a) return true; // ':'
    if ((cp >= 0x41 && cp <= 0x5a) || (cp >= 0x61 && cp <= 0x7a)) return true;
    if (cp === 0x5f) return true; // '_'
    return inRanges(cp, START_RANGES);
}
