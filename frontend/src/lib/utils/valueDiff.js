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
 * @param {string} fromValue The original value shown on the left side.
 * @param {string} toValue The updated value shown on the right side.
 * @returns {{ fromSegments: FromSegment[], toSegments: ToSegment[] }} Matching
 * segment lists for the removed/original and added/updated views.
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
 * Refine a token-level replacement with a character-level diff.
 *
 * @param {string} fromText The original replacement text.
 * @param {string} toText The updated replacement text.
 * @param {FromSegment[]} fromSegments The left-side segment accumulator.
 * @param {ToSegment[]} toSegments The right-side segment accumulator.
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
 * Split a string into diff-friendly tokens while preserving whitespace and
 * punctuation as standalone entries.
 *
 * @param {string} text The text to split into tokens.
 * @returns {string[]} Tokenized text segments.
 */
function tokenize(text) {
    return text.match(/(\s+|[\p{L}\p{N}_]+|[^\s\p{L}\p{N}_]+)/gu) ?? [];
}

/**
 * Create contiguous diff blocks from two item sequences.
 *
 * @template T
 * @param {T[]} fromItems Items from the original value.
 * @param {T[]} toItems Items from the updated value.
 * @param {number} lcsProductLimit Maximum matrix size before using the
 * cheaper prefix/suffix fallback.
 * @returns {{ type: "equal" | "delete" | "insert", items: T[] }[]} Diff
 * blocks describing unchanged, removed, and added runs.
 */
function buildDiffBlocks(fromItems, toItems, lcsProductLimit) {
    const trivialBlocks = buildTrivialDiffBlocks(fromItems, toItems);
    if (trivialBlocks) {
        return trivialBlocks;
    }

    if (fromItems.length * toItems.length > lcsProductLimit) {
        return buildPrefixSuffixDiffBlocks(fromItems, toItems);
    }

    return buildLcsDiffBlocks(fromItems, toItems);
}

/**
 * Handle degenerate diff inputs without building a comparison matrix.
 *
 * @template T
 * @param {T[]} fromItems Items from the original value.
 * @param {T[]} toItems Items from the updated value.
 * @returns {{ type: "equal" | "delete" | "insert", items: T[] }[] | null}
 * Ready-made diff blocks, or `null` when full diffing is still needed.
 */
function buildTrivialDiffBlocks(fromItems, toItems) {
    if (fromItems.length === 0 && toItems.length === 0) {
        return [];
    }

    if (fromItems.length === 0) {
        return [{ type: "insert", items: [...toItems] }];
    }

    if (toItems.length === 0) {
        return [{ type: "delete", items: [...fromItems] }];
    }

    return null;
}

/**
 * Build a full longest-common-subsequence matrix for two item sequences.
 *
 * @template T
 * @param {T[]} fromItems Items from the original value.
 * @param {T[]} toItems Items from the updated value.
 * @returns {Uint32Array[]} LCS lengths for each source/target position pair.
 */
function buildLcsLengths(fromItems, toItems) {
    const fromLength = fromItems.length;
    const toLength = toItems.length;

    const lcsLengths = Array.from(
        { length: fromLength + 1 },
        () => new Uint32Array(toLength + 1),
    );

    for (let fromIndex = fromLength - 1; fromIndex >= 0; fromIndex--) {
        for (let toIndex = toLength - 1; toIndex >= 0; toIndex--) {
            if (fromItems[fromIndex] === toItems[toIndex]) {
                lcsLengths[fromIndex][toIndex] =
                    lcsLengths[fromIndex + 1][toIndex + 1] + 1;
                continue;
            }

            lcsLengths[fromIndex][toIndex] = Math.max(
                lcsLengths[fromIndex + 1][toIndex],
                lcsLengths[fromIndex][toIndex + 1],
            );
        }
    }

    return lcsLengths;
}

/**
 * Use an LCS matrix to produce grouped diff blocks for two item sequences.
 *
 * @template T
 * @param {T[]} fromItems Items from the original value.
 * @param {T[]} toItems Items from the updated value.
 * @returns {{ type: "equal" | "delete" | "insert", items: T[] }[]} Diff
 * blocks produced from the LCS walk.
 */
function buildLcsDiffBlocks(fromItems, toItems) {
    const fromLength = fromItems.length;
    const toLength = toItems.length;
    const lcsLengths = buildLcsLengths(fromItems, toItems);
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
 * Append a single item to the current diff block, creating a new block when
 * the diff operation changes.
 *
 * @template T
 * @param {{ type: "equal" | "delete" | "insert", items: T[] }[]} blocks Diff
 * blocks built so far.
 * @param {"equal" | "delete" | "insert"} type The diff operation to append.
 * @param {T} item The item to append.
 */
function appendBlockItem(blocks, type, item) {
    const previous = blocks.at(-1);
    if (previous?.type === type) {
        previous.items.push(item);
        return;
    }
    blocks.push({ type, items: [item] });
}

/**
 * @template T
 * Build a coarse diff by preserving the shared prefix and suffix and marking
 * the middle spans as a delete/insert pair.
 *
 * @param {T[]} fromItems Items from the original value.
 * @param {T[]} toItems Items from the updated value.
 * @returns {{ type: "equal" | "delete" | "insert", items: T[] }[]} Diff
 * blocks using the prefix/suffix fallback strategy.
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
 * Append text to the current segment list while merging adjacent segments of
 * the same kind.
 *
 * @param {Array<{ text: string, kind: string }>} segments Diff segments built
 * so far.
 * @param {string} text The text content to append.
 * @param {string} kind The segment kind for the appended text.
 */
function appendSegment(segments, text, kind) {
    if (!text) {
        return;
    }

    const previous = segments.at(-1);
    if (previous?.kind === kind) {
        previous.text += text;
        return;
    }

    segments.push({ text, kind });
}
