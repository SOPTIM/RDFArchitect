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

const TOKEN_LCS_LIMIT = 20_000;
const CHAR_LCS_LIMIT = 10_000;

/**
 * @typedef {{ text: string, kind: "unchanged" | "removed" }} FromSegment
 * @typedef {{ text: string, kind: "unchanged" | "added" }} ToSegment
 */

/**
 * Build a git-like inline diff between two strings.
 *
 * @param {string} fromValue
 * @param {string} toValue
 * @returns {{ fromSegments: FromSegment[], toSegments: ToSegment[] }}
 */
export function buildInlineValueDiff(fromValue, toValue) {
    const fromText = String(fromValue ?? "");
    const toText = String(toValue ?? "");

    if (fromText === toText) {
        const unchanged = fromText
            ? [{ text: fromText, kind: "unchanged" }]
            : [];
        return { fromSegments: unchanged, toSegments: [...unchanged] };
    }

    const fromSegments = [];
    const toSegments = [];

    const tokenBlocks = buildDiffBlocks(
        tokenize(fromText),
        tokenize(toText),
        TOKEN_LCS_LIMIT,
    );

    for (let i = 0; i < tokenBlocks.length; i++) {
        const current = tokenBlocks[i];
        const next = tokenBlocks[i + 1];

        if (current.type === "equal") {
            const text = current.items.join("");
            appendSegment(fromSegments, text, "unchanged");
            appendSegment(toSegments, text, "unchanged");
            continue;
        }

        if (current.type === "delete" && next?.type === "insert") {
            appendReplacementDiff(
                current.items.join(""),
                next.items.join(""),
                fromSegments,
                toSegments,
            );
            i++;
            continue;
        }

        if (current.type === "insert" && next?.type === "delete") {
            appendReplacementDiff(
                next.items.join(""),
                current.items.join(""),
                fromSegments,
                toSegments,
            );
            i++;
            continue;
        }

        if (current.type === "delete") {
            appendSegment(fromSegments, current.items.join(""), "removed");
            continue;
        }

        appendSegment(toSegments, current.items.join(""), "added");
    }

    return { fromSegments, toSegments };
}

/**
 * @param {string} fromText
 * @param {string} toText
 * @param {FromSegment[]} fromSegments
 * @param {ToSegment[]} toSegments
 */
function appendReplacementDiff(fromText, toText, fromSegments, toSegments) {
    const charBlocks = buildDiffBlocks(
        Array.from(fromText),
        Array.from(toText),
        CHAR_LCS_LIMIT,
    );

    for (const block of charBlocks) {
        const text = block.items.join("");

        if (block.type === "equal") {
            appendSegment(fromSegments, text, "unchanged");
            appendSegment(toSegments, text, "unchanged");
            continue;
        }

        if (block.type === "delete") {
            appendSegment(fromSegments, text, "removed");
            continue;
        }

        appendSegment(toSegments, text, "added");
    }
}

/**
 * @param {string} text
 * @returns {string[]}
 */
function tokenize(text) {
    return text.match(/(\s+|[\p{L}\p{N}_]+|[^\s\p{L}\p{N}_]+)/gu) ?? [];
}

/**
 * @template T
 * @param {T[]} fromItems
 * @param {T[]} toItems
 * @param {number} lcsProductLimit
 * @returns {{ type: "equal" | "delete" | "insert", items: T[] }[]}
 */
function buildDiffBlocks(fromItems, toItems, lcsProductLimit) {
    const fromLength = fromItems.length;
    const toLength = toItems.length;

    if (fromLength === 0 && toLength === 0) {
        return [];
    }

    if (fromLength === 0) {
        return [{ type: "insert", items: [...toItems] }];
    }

    if (toLength === 0) {
        return [{ type: "delete", items: [...fromItems] }];
    }

    if (fromLength * toLength > lcsProductLimit) {
        return buildPrefixSuffixDiffBlocks(fromItems, toItems);
    }

    const lcsLengths = Array.from(
        { length: fromLength + 1 },
        () => new Uint32Array(toLength + 1),
    );

    for (let fromIndex = fromLength - 1; fromIndex >= 0; fromIndex--) {
        for (let toIndex = toLength - 1; toIndex >= 0; toIndex--) {
            if (fromItems[fromIndex] === toItems[toIndex]) {
                lcsLengths[fromIndex][toIndex] =
                    lcsLengths[fromIndex + 1][toIndex + 1] + 1;
            } else {
                lcsLengths[fromIndex][toIndex] = Math.max(
                    lcsLengths[fromIndex + 1][toIndex],
                    lcsLengths[fromIndex][toIndex + 1],
                );
            }
        }
    }

    const blocks = [];

    let fromIndex = 0;
    let toIndex = 0;

    while (fromIndex < fromLength && toIndex < toLength) {
        if (fromItems[fromIndex] === toItems[toIndex]) {
            appendBlockItem(blocks, "equal", fromItems[fromIndex]);
            fromIndex++;
            toIndex++;
            continue;
        }

        if (
            lcsLengths[fromIndex + 1][toIndex] >=
            lcsLengths[fromIndex][toIndex + 1]
        ) {
            appendBlockItem(blocks, "delete", fromItems[fromIndex]);
            fromIndex++;
            continue;
        }

        appendBlockItem(blocks, "insert", toItems[toIndex]);
        toIndex++;
    }

    while (fromIndex < fromLength) {
        appendBlockItem(blocks, "delete", fromItems[fromIndex]);
        fromIndex++;
    }

    while (toIndex < toLength) {
        appendBlockItem(blocks, "insert", toItems[toIndex]);
        toIndex++;
    }

    return blocks;
}

/**
 * @template T
 * @param {{ type: "equal" | "delete" | "insert", items: T[] }[]} blocks
 * @param {"equal" | "delete" | "insert"} type
 * @param {T} item
 */
function appendBlockItem(blocks, type, item) {
    const previous = blocks.at(-1);
    if (previous && previous.type === type) {
        previous.items.push(item);
        return;
    }
    blocks.push({ type, items: [item] });
}

/**
 * @template T
 * @param {T[]} fromItems
 * @param {T[]} toItems
 * @returns {{ type: "equal" | "delete" | "insert", items: T[] }[]}
 */
function buildPrefixSuffixDiffBlocks(fromItems, toItems) {
    let prefixLength = 0;
    while (
        prefixLength < fromItems.length &&
        prefixLength < toItems.length &&
        fromItems[prefixLength] === toItems[prefixLength]
    ) {
        prefixLength++;
    }

    let fromEnd = fromItems.length - 1;
    let toEnd = toItems.length - 1;
    while (
        fromEnd >= prefixLength &&
        toEnd >= prefixLength &&
        fromItems[fromEnd] === toItems[toEnd]
    ) {
        fromEnd--;
        toEnd--;
    }

    const blocks = [];

    if (prefixLength > 0) {
        blocks.push({
            type: "equal",
            items: fromItems.slice(0, prefixLength),
        });
    }

    if (fromEnd >= prefixLength) {
        blocks.push({
            type: "delete",
            items: fromItems.slice(prefixLength, fromEnd + 1),
        });
    }

    if (toEnd >= prefixLength) {
        blocks.push({
            type: "insert",
            items: toItems.slice(prefixLength, toEnd + 1),
        });
    }

    if (fromEnd + 1 < fromItems.length) {
        blocks.push({
            type: "equal",
            items: fromItems.slice(fromEnd + 1),
        });
    }

    return blocks;
}

/**
 * @param {Array<{ text: string, kind: string }>} segments
 * @param {string} text
 * @param {string} kind
 */
function appendSegment(segments, text, kind) {
    if (!text) {
        return;
    }

    const previous = segments.at(-1);
    if (previous && previous.kind === kind) {
        previous.text += text;
        return;
    }

    segments.push({ text, kind });
}
