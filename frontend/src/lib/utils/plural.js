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
 * Returns the plural of a word.
 *
 * Covers the regular English nouns the editor names things with — a change,
 * a class, a schema. A word with an irregular plural has to be written out
 * where it is used.
 *
 * @param {string} word the singular
 * @returns {string} the plural
 */
export function plural(word) {
    return word.endsWith("s") ? `${word}es` : `${word}s`;
}

/**
 * Returns a count together with what is being counted, in the right number.
 *
 * @param {number} count how many there are
 * @param {string} noun what they are, in the singular
 * @returns {string} e.g. {@code "1 change"} or {@code "3 changes"}
 */
export function counted(count, noun) {
    return `${count} ${count === 1 ? noun : plural(noun)}`;
}
