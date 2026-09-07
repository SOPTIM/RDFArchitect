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

import { hasPosition, toMarkers } from "$lib/monaco/markers.js";
import { bySeverityThenPosition, summarise } from "$lib/shacl/severity.js";

/** Monaco's own values, so the mapping is pinned to what the editor actually expects. */
const SEVERITIES = { ERROR: 8, WARNING: 4, INFO: 2 };

/** Stands in for the editor: the complained-about term ends five columns along. */
const extentOf = (line, column) => column + 5;

describe("toMarkers", () => {
    test("maps a finding onto the range the editor underlines", () => {
        const markers = toMarkers(
            [
                {
                    severity: "ERROR",
                    source: "SHAPE",
                    code: "UNKNOWN_CLASS",
                    message: "cim:Nonsense is not a class",
                    line: 22,
                    column: 20,
                },
            ],
            SEVERITIES,
            extentOf,
        );

        expect(markers).toEqual([
            {
                severity: 8,
                source: "SHACL",
                code: "UNKNOWN_CLASS",
                message: "cim:Nonsense is not a class",
                startLineNumber: 22,
                startColumn: 20,
                endLineNumber: 22,
                endColumn: 25,
            },
        ]);
    });

    test("translates every severity the report can carry", () => {
        const markers = toMarkers(
            [
                { severity: "ERROR", line: 1, column: 1 },
                { severity: "WARNING", line: 2, column: 1 },
                { severity: "INFO", line: 3, column: 1 },
                { severity: "SOMETHING_NEW", line: 4, column: 1 },
            ],
            SEVERITIES,
            extentOf,
        );

        // An unknown severity is shown rather than dropped, at the least alarming level.
        expect(markers.map(marker => marker.severity)).toEqual([8, 4, 2, 2]);
    });

    test("leaves out findings that point nowhere", () => {
        // A contradiction between two documents has a message but no position; a marker for it
        // would have to land somewhere, and line 1 would be a lie.
        const markers = toMarkers(
            [
                { severity: "ERROR", message: "defined in two documents" },
                {
                    severity: "ERROR",
                    message: "at a place",
                    line: 3,
                    column: 7,
                },
            ],
            SEVERITIES,
            extentOf,
        );

        expect(markers).toHaveLength(1);
        expect(markers[0].startLineNumber).toBe(3);
    });

    test("never produces an empty range", () => {
        const markers = toMarkers(
            [{ severity: "ERROR", line: 1, column: 4 }],
            SEVERITIES,
            () => 1,
        );

        expect(markers[0].endColumn).toBe(5);
    });

    test("hasPosition needs both coordinates", () => {
        expect(hasPosition({ line: 1, column: 1 })).toBe(true);
        expect(hasPosition({ line: 1 })).toBe(false);
        expect(hasPosition({ column: 1 })).toBe(false);
        expect(hasPosition(null)).toBe(false);
    });
});

describe("finding order and summary", () => {
    test("sorts errors first, then by position, with the positionless last", () => {
        const findings = [
            { severity: "WARNING", line: 2, column: 1 },
            { severity: "ERROR", line: 9, column: 1 },
            { severity: "ERROR" },
            { severity: "ERROR", line: 4, column: 8 },
            { severity: "ERROR", line: 4, column: 2 },
        ];

        expect([...findings].sort(bySeverityThenPosition)).toEqual([
            { severity: "ERROR", line: 4, column: 2 },
            { severity: "ERROR", line: 4, column: 8 },
            { severity: "ERROR", line: 9, column: 1 },
            { severity: "ERROR" },
            { severity: "WARNING", line: 2, column: 1 },
        ]);
    });

    test("summarises only the counts that are not zero", () => {
        expect(
            summarise({ errorCount: 3, warningCount: 1, infoCount: 0 }),
        ).toBe("3 errors, 1 warning");
        expect(
            summarise({ errorCount: 1, warningCount: 0, infoCount: 0 }),
        ).toBe("1 error");
        expect(
            summarise({ errorCount: 0, warningCount: 0, infoCount: 0 }),
        ).toBeNull();
        expect(summarise(undefined)).toBeNull();
    });
});
