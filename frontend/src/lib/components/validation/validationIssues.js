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

import {
    faCircleExclamation,
    faCircleInfo,
    faTriangleExclamation,
} from "@fortawesome/free-solid-svg-icons";

export const SEVERITY = {
    ERROR: {
        label: "Error",
        pluralLabel: "Errors",
        order: 0,
        icon: faCircleExclamation,
        card: "bg-red-background border-red-border",
        text: "text-red-text",
        iconClass: "text-red-text",
    },
    WARNING: {
        label: "Warning",
        pluralLabel: "Warnings",
        order: 1,
        icon: faTriangleExclamation,
        card: "bg-orange/10 border-orange",
        text: "text-orange",
        iconClass: "text-orange",
    },
    INFO: {
        label: "Info",
        pluralLabel: "Infos",
        order: 2,
        icon: faCircleInfo,
        card: "bg-lightblue border-blue",
        text: "text-blue",
        iconClass: "text-blue",
    },
    UNKNOWN_SEVERITY: {
        label: "Unknown",
        pluralLabel: "Unknown",
        order: 3,
        icon: faCircleInfo,
        card: "bg-background-subtle border-border",
        text: "text-default-text",
        iconClass: "text-default-text",
    },
};

export const GroupBy = Object.freeze({
    NONE: "none",
    SEVERITY: "severity",
    RULE: "rule",
    CLASS: "class",
    MESSAGE: "message",
    SCHEMA: "schema",
});

const RULE_LABELS = {
    "inheritance-consistency": "Inheritance",
    "datatype-consistency": "Datatype kind",
    "cim-datatype-definition": "CIMDatatype definition",
    "duplicate-property": "Duplicate property",
    "cim-version-consistency": "CIM version",
    "unique-profile-header": "Profile header",
    "inverse-association-consistency": "Inverse association",
    "enum-entry-consistency": "Enum entry",
    "label-consistency": "Label",
    "workspace-index": "Unreadable resource",
};

const NO_KEY = "";
const NO_CLASS_LABEL = "Not related to a class";
const NO_SCHEMA_LABEL = "Not related to a schema";
const NO_RULE_LABEL = "Other";

export function severityMeta(severity) {
    return SEVERITY[severity] ?? SEVERITY.UNKNOWN_SEVERITY;
}

export function ruleLabel(ruleId) {
    return RULE_LABELS[ruleId] ?? ruleId;
}

export function countBySeverity(issues) {
    const counts = { ERROR: 0, WARNING: 0, INFO: 0 };
    for (const issue of issues) {
        if (issue.severity in counts) {
            counts[issue.severity] += 1;
        }
    }
    return counts;
}

export function sortIssues(issues) {
    return [...issues].sort(
        (a, b) =>
            severityMeta(a.severity).order - severityMeta(b.severity).order ||
            (a.resourceUri ?? "").localeCompare(b.resourceUri ?? ""),
    );
}

/**
 * The class a finding is about, derived from its resource URI: CIM names
 * properties and enum entries `Class.member`, so the part before the first dot
 * of the local name is the class.
 */
export function classOfIssue(issue) {
    const uri = issue.resourceUri;
    if (!uri) {
        return { key: NO_KEY, label: NO_CLASS_LABEL };
    }
    const separator = Math.max(uri.lastIndexOf("#"), uri.lastIndexOf("/"));
    const namespace = uri.substring(0, separator + 1);
    const className = uri.substring(separator + 1).split(".")[0];
    return { key: namespace + className, label: className };
}

export function groupIssues(issues, groupBy) {
    const sorted = sortIssues(issues);
    switch (groupBy) {
        case GroupBy.SEVERITY:
            return collect(sorted, issue => {
                const meta = severityMeta(issue.severity);
                return {
                    key: issue.severity,
                    label: meta.pluralLabel,
                    order: meta.order,
                };
            });
        case GroupBy.RULE:
            return collect(sorted, issue => ({
                key: issue.ruleId ?? "",
                label: issue.ruleId ? ruleLabel(issue.ruleId) : NO_RULE_LABEL,
            }));
        case GroupBy.CLASS:
            return collect(sorted, issue => classOfIssue(issue));
        case GroupBy.MESSAGE:
            return collect(sorted, issue => ({
                key: issue.message,
                label: issue.message,
            }));
        case GroupBy.SCHEMA:
            return collectBySchema(sorted);
        default:
            return [{ key: GroupBy.NONE, label: null, issues: sorted }];
    }
}

/**
 * Groups by schema. A finding that involves several schemas shows up under each
 * of them, so every group lists everything that concerns that schema.
 */
function collectBySchema(sortedIssues) {
    const groups = new Map();
    for (const issue of sortedIssues) {
        const occurrences = issue.occurrences ?? [];
        if (occurrences.length === 0) {
            addToGroup(groups, NO_KEY, NO_SCHEMA_LABEL, issue);
            continue;
        }
        for (const occurrence of occurrences) {
            addToGroup(
                groups,
                occurrence.graphUri ?? NO_KEY,
                schemaLabelOf(occurrence),
                issue,
            );
        }
    }
    return [...groups.values()].sort(compareGroups);
}

function schemaLabelOf(occurrence) {
    if (occurrence.keyword) {
        return occurrence.keyword;
    }
    const uri = occurrence.graphUri ?? "";
    return uri.substring(
        Math.max(uri.lastIndexOf("#"), uri.lastIndexOf("/")) + 1,
    );
}

function addToGroup(groups, key, label, issue) {
    if (!groups.has(key)) {
        groups.set(key, { key, label, issues: [] });
    }
    const group = groups.get(key);
    if (!group.issues.includes(issue)) {
        group.issues.push(issue);
    }
}

function collect(sortedIssues, groupOf) {
    const groups = new Map();
    for (const issue of sortedIssues) {
        const group = groupOf(issue);
        if (!groups.has(group.key)) {
            groups.set(group.key, { ...group, issues: [] });
        }
        groups.get(group.key).issues.push(issue);
    }
    return [...groups.values()].sort(compareGroups);
}

function compareGroups(a, b) {
    if (a.order !== undefined && b.order !== undefined) {
        return a.order - b.order;
    }
    if (a.key === "" || b.key === "") {
        return a.key === "" ? 1 : -1;
    }
    return (
        worstSeverity(a) - worstSeverity(b) || a.label.localeCompare(b.label)
    );
}

function worstSeverity(group) {
    return Math.min(
        ...group.issues.map(issue => severityMeta(issue.severity).order),
    );
}
