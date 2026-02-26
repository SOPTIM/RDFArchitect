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

import { isNameChar } from "./nameChar.js";
import { isNameStartChar } from "./nameStartChar.js";

/**
 * Returns UNIQUE characters that violate the XML NCName rule.
 *
 * NCName ::= Name - (Char* ':' Char*)
 *
 * Spec:
 * https://www.w3.org/TR/xml-names/#NT-NCName
 *
 * @param {string} str
 * @returns {string[]} unique violating characters
 */
export function getNCNameViolations(str) {
    const violations = new Set();
    if (str.length === 0) return [];

    let index = 0;
    for (let i = 0; i < str.length; ) {
        const cp = str.codePointAt(i);
        const ch = String.fromCodePoint(cp);
        i += cp > 0xffff ? 2 : 1;

        if (cp === 0x3a) {
            violations.add(ch);
            index++;
            continue;
        }

        if (index === 0) {
            if (!isNameStartChar(cp)) violations.add(ch);
        } else if (!isNameChar(cp)) violations.add(ch);
        index++;
    }
    return [...violations];
}
