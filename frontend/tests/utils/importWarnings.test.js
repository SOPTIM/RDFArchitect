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

import { describe, expect, it } from "vitest";

import {
    GroupBy,
    groupIssues,
} from "../../src/lib/components/validation/validationIssues.js";
import {
    asValidationResult,
    countUncarried,
} from "../../src/lib/utils/importWarnings.js";

/** Two profiles, three items, two of them for the same reason. */
const WARNINGS = [
    {
        fileName: "TopologyProfile",
        graphUri: "http://graph#TopologyProfile",
        items: [
            { resource: "license", reason: "no ontology field of that name" },
            { resource: "Terminal", reason: "declared more than once" },
        ],
    },
    {
        fileName: "CoreEquipmentProfile",
        graphUri: "http://graph#CoreEquipmentProfile",
        items: [
            { resource: "shortName", reason: "no ontology field of that name" },
        ],
    },
];

describe("asValidationResult", () => {
    it("makes one finding of each item, never one of each file", () => {
        expect(asValidationResult(WARNINGS).issues).toHaveLength(3);
    });

    it("reports every one of them as a warning, the import having gone through", () => {
        expect(
            asValidationResult(WARNINGS).issues.map(issue => issue.severity),
        ).toEqual(["WARNING", "WARNING", "WARNING"]);
    });

    it("takes the reason as the finding and the thing as the resource", () => {
        const [first] = asValidationResult(WARNINGS).issues;

        expect(first.resourceUri).toBe("license");
        expect(first.message).toBe("no ontology field of that name");
    });

    it("names where it was found, which is a column and a grouping of its own", () => {
        const [first] = asValidationResult(WARNINGS).issues;

        expect(first.ruleLabel).toBe("TopologyProfile");
        expect(first.ruleId).toBe("http://graph#TopologyProfile");
    });

    it("reports no occurrences, an import warning having no value to show per schema", () => {
        // Given one, the table would offer to open a row with nothing behind it.
        expect(
            asValidationResult(WARNINGS).issues[0].occurrences,
        ).toBeUndefined();
    });

    it("is valid where the import carried everything over", () => {
        expect(asValidationResult([])).toEqual({ valid: true, issues: [] });
    });

    it("groups by the reason, which is what the items have in common", () => {
        const groups = groupIssues(
            asValidationResult(WARNINGS).issues,
            GroupBy.MESSAGE,
        );

        expect(
            groups.map(group => [group.label, group.issues.length]),
        ).toContainEqual(["no ontology field of that name", 2]);
    });

    it("groups by where the items were found", () => {
        const groups = groupIssues(
            asValidationResult(WARNINGS).issues,
            GroupBy.RULE,
        );

        expect(groups.map(group => group.label).sort()).toEqual([
            "CoreEquipmentProfile",
            "TopologyProfile",
        ]);
    });
});

describe("countUncarried", () => {
    it("counts the items rather than the files holding them", () => {
        expect(countUncarried(WARNINGS)).toBe(3);
    });

    it("counts nothing before an import has run", () => {
        expect(countUncarried(undefined)).toBe(0);
    });
});
