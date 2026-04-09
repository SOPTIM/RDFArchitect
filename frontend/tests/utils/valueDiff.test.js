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

import { buildInlineValueDiff } from "$lib/utils/valueDiff.js";

function renderSegments(segments) {
    return segments.map(segment => segment.text).join("");
}

describe("buildInlineValueDiff", () => {
    test("highlights a single-character change", () => {
        const result = buildInlineValueDiff("abc", "adc");

        expect(result.fromSegments).toEqual([
            { text: "a", kind: "unchanged" },
            { text: "b", kind: "removed" },
            { text: "c", kind: "unchanged" },
        ]);
        expect(result.toSegments).toEqual([
            { text: "a", kind: "unchanged" },
            { text: "d", kind: "added" },
            { text: "c", kind: "unchanged" },
        ]);
    });

    test("handles token replacement with shared sentence prefix and suffix", () => {
        const result = buildInlineValueDiff(
            "prefix old suffix",
            "prefix new suffix",
        );

        expect(result.fromSegments).toEqual([
            { text: "prefix ", kind: "unchanged" },
            { text: "old", kind: "removed" },
            { text: " suffix", kind: "unchanged" },
        ]);
        expect(result.toSegments).toEqual([
            { text: "prefix ", kind: "unchanged" },
            { text: "new", kind: "added" },
            { text: " suffix", kind: "unchanged" },
        ]);
    });

    test("highlights punctuation-heavy URI changes precisely", () => {
        const result = buildInlineValueDiff(
            "http://example.org/ns#ClassA",
            "http://example.org/ns#ClassB",
        );

        expect(result.fromSegments).toEqual([
            { text: "http://example.org/ns#Class", kind: "unchanged" },
            { text: "A", kind: "removed" },
        ]);
        expect(result.toSegments).toEqual([
            { text: "http://example.org/ns#Class", kind: "unchanged" },
            { text: "B", kind: "added" },
        ]);
    });

    test("preserves whitespace-only changes and merges adjacent unchanged segments", () => {
        const fromText = "line1\nline2";
        const toText = "line1\n  line2";
        const result = buildInlineValueDiff(fromText, toText);

        expect(renderSegments(result.fromSegments)).toBe(fromText);
        expect(renderSegments(result.toSegments)).toBe(toText);
        expect(result.fromSegments).toEqual([
            { text: "line1\nline2", kind: "unchanged" },
        ]);
        expect(result.toSegments).toEqual([
            { text: "line1\n", kind: "unchanged" },
            { text: "  ", kind: "added" },
            { text: "line2", kind: "unchanged" },
        ]);
    });

    test("supports pure add and remove blocks when one side is empty", () => {
        const added = buildInlineValueDiff("", "new value");
        expect(added.fromSegments).toEqual([]);
        expect(added.toSegments).toEqual([
            { text: "new value", kind: "added" },
        ]);

        const removed = buildInlineValueDiff("old value", "");
        expect(removed.fromSegments).toEqual([
            { text: "old value", kind: "removed" },
        ]);
        expect(removed.toSegments).toEqual([]);
    });

    test("falls back for very large token and char inputs while keeping output stable", () => {
        const prefix = Array.from({ length: 60 }, (_, i) => `p${i}`).join(" ");
        const fromMiddle = Array.from({ length: 40 }, (_, i) => `a${i}`).join(
            " ",
        );
        const toMiddle = Array.from({ length: 40 }, (_, i) => `b${i}`).join(
            " ",
        );
        const suffix = Array.from({ length: 60 }, (_, i) => `s${i}`).join(" ");

        const fromText = `${prefix} ${fromMiddle} ${suffix}`;
        const toText = `${prefix} ${toMiddle} ${suffix}`;

        const result = buildInlineValueDiff(fromText, toText);

        expect(renderSegments(result.fromSegments)).toBe(fromText);
        expect(renderSegments(result.toSegments)).toBe(toText);
        expect(
            result.fromSegments.some(segment => segment.kind === "removed"),
        ).toBe(true);
        expect(
            result.toSegments.some(segment => segment.kind === "added"),
        ).toBe(true);
        expect(result.fromSegments.length).toBeLessThanOrEqual(5);
        expect(result.toSegments.length).toBeLessThanOrEqual(5);
    });

    test("merges adjacent unchanged segments after refined replacement diff", () => {
        const result = buildInlineValueDiff("hello world", "hello  world");

        expect(result.fromSegments).toEqual([
            { text: "hello world", kind: "unchanged" },
        ]);
        expect(result.toSegments).toEqual([
            { text: "hello ", kind: "unchanged" },
            { text: " ", kind: "added" },
            { text: "world", kind: "unchanged" },
        ]);
    });
});
