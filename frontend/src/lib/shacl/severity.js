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
 * How a validation finding looks, matching the schema validation report so the two features do
 * not use different colours for the same word.
 */

import {
    faCircleCheck,
    faCircleExclamation,
    faCircleInfo,
    faTriangleExclamation,
} from "@fortawesome/free-solid-svg-icons";

export const FINDING_SEVERITY = {
    ERROR: {
        label: "Error",
        order: 0,
        icon: faCircleExclamation,
        card: "bg-red-background border-red-border",
        text: "text-red-text",
    },
    WARNING: {
        label: "Warning",
        order: 1,
        icon: faTriangleExclamation,
        card: "bg-orange/10 border-orange",
        text: "text-orange",
    },
    INFO: {
        label: "Info",
        order: 2,
        icon: faCircleInfo,
        card: "bg-lightblue border-blue",
        text: "text-blue",
    },
};

export const VALID_ICON = faCircleCheck;

/** Where a finding came from, spelled out for the problems panel. */
export const SOURCE_LABEL = {
    SYNTAX: "Turtle syntax",
    SHAPE: "Shape",
    SPARQL: "Embedded SPARQL",
    CONFLICT: "Between documents",
};

export function severityMeta(severity) {
    return FINDING_SEVERITY[severity] ?? FINDING_SEVERITY.INFO;
}

/**
 * A document's findings, worst first and then in reading order.
 *
 * The server already sorts each document's findings this way; sorting again here keeps the order
 * right for lists assembled from several documents, and for the buffer result that arrives
 * separately.
 */
export function bySeverityThenPosition(a, b) {
    const bySeverity =
        severityMeta(a.severity).order - severityMeta(b.severity).order;
    if (bySeverity !== 0) {
        return bySeverity;
    }
    return (
        (a.line ?? Infinity) - (b.line ?? Infinity) ||
        (a.column ?? 0) - (b.column ?? 0)
    );
}

/** Short "3 errors, 1 warning" summary, or null when there is nothing to say. */
export function summarise(counts) {
    const parts = [];
    if (counts?.errorCount) {
        parts.push(
            `${counts.errorCount} error${counts.errorCount === 1 ? "" : "s"}`,
        );
    }
    if (counts?.warningCount) {
        parts.push(
            `${counts.warningCount} warning${counts.warningCount === 1 ? "" : "s"}`,
        );
    }
    if (counts?.infoCount) {
        parts.push(`${counts.infoCount} info`);
    }
    return parts.length > 0 ? parts.join(", ") : null;
}
