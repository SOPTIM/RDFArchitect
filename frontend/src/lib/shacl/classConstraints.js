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
 * What is enforced on one class, arranged the way the question gets asked.
 *
 * The endpoint answers in two halves — what the schema generates and what the documents add — and
 * the old dialog put them behind two buttons, leaving the reader to merge them in their head.
 * Nobody wants "the custom shapes"; they want to know what applies to this property. So the halves
 * are folded into one row per property, and where a rule came from becomes a label on the answer
 * rather than the question.
 *
 * Free of Svelte so it can be tested as plain data handling.
 */

/** Which half of the answer to show. "Both" first, because it is the question people ask. */
export const SCOPES = [
    { id: "both", label: "Both" },
    { id: "generated", label: "Generated" },
    { id: "custom", label: "Custom" },
];

export const GENERATED = "generated";
export const CUSTOM = "custom";

/**
 * An empty answer, for before the request lands and after the dialog closes.
 *
 * A factory rather than a constant: the caller holds it as mutable state, and handing out one
 * shared object would let two dialogs write into each other's.
 */
export function emptyRelations() {
    return {
        namespaces: "",
        nodeShapes: [],
        propertyShapes: [],
        derivedPropertyShapes: [],
    };
}

/**
 * One row per property, each carrying the shapes that constrain it and where they came from.
 *
 * Rows are keyed by the property's name because that is the only thing the two halves share:
 * generated and official shapes have no naming convention in common, which is the same reason the
 * conformance check matches on class and path rather than on shape names.
 */
export function constraintRows({
    custom,
    generated,
    scope = "both",
    filter = "",
} = {}) {
    const rows = new Map();

    for (const side of sidesFor(scope)) {
        const relations = side === GENERATED ? generated : custom;
        collect(rows, relations?.propertyShapes, side, false);
        collect(rows, relations?.derivedPropertyShapes, side, true);
    }

    return [...rows.values()]
        .filter(row => matches(row, filter))
        .sort(
            (a, b) =>
                Number(a.inherited) - Number(b.inherited) ||
                a.label.localeCompare(b.label),
        );
}

/** The class-level shapes — `sh:closed`, node-level messages — which belong to no property. */
export function classRules({ custom, generated, scope = "both" } = {}) {
    return sidesFor(scope).flatMap(side => {
        const relations = side === GENERATED ? generated : custom;
        return (relations?.nodeShapes ?? []).map(shape => ({
            ...shape,
            side,
        }));
    });
}

/**
 * How a row's rule reads.
 *
 * Normally one line. When both halves state something and they disagree, both are shown with their
 * source, because that disagreement is the single most interesting thing the dialog can surface —
 * and hiding one of them behind a tab is what made it invisible before.
 */
export function summaryOf(row) {
    const stated = row.sources.filter(source => source.summary);
    if (stated.length === 0) {
        return [];
    }
    const distinct = [...new Set(stated.map(source => source.summary))];
    if (distinct.length === 1) {
        return [{ text: distinct[0], side: null }];
    }
    return stated.map(source => ({ text: source.summary, side: source.side }));
}

/** Every place a row's rules come from: "generated", and the documents that state them. */
export function originsOf(row) {
    const origins = [];
    for (const source of row.sources) {
        if (source.side === GENERATED) {
            origins.push({ label: "generated", generated: true });
            continue;
        }
        for (const shape of source.shapes) {
            for (const origin of shape.origins ?? []) {
                origins.push({
                    label: origin.documentName,
                    documentId: origin.documentId,
                    line: origin.line,
                    generated: false,
                });
            }
        }
    }
    return origins.filter(
        (origin, index) =>
            origins.findIndex(other => other.label === origin.label) === index,
    );
}

/** How many rules a row holds, across every source. */
export function ruleCount(row) {
    return row.sources.reduce(
        (total, source) => total + source.shapes.length,
        0,
    );
}

// -------------------------------------------------------------------------

function sidesFor(scope) {
    if (scope === GENERATED) {
        return [GENERATED];
    }
    if (scope === CUSTOM) {
        return [CUSTOM];
    }
    return [GENERATED, CUSTOM];
}

function collect(rows, wrappers, side, inherited) {
    for (const wrapper of wrappers ?? []) {
        const key = `${inherited ? "inherited:" : ""}${wrapper.label}`;
        if (!rows.has(key)) {
            rows.set(key, {
                key,
                label: wrapper.label,
                propertyType: wrapper.propertyType,
                inherited,
                sources: [],
            });
        }
        rows.get(key).sources.push({
            side,
            summary: wrapper.summary || null,
            shapes: wrapper.propertyShapes ?? [],
        });
    }
}

/** Matches the property's name or the rule as it reads, so "float" finds every float rule. */
function matches(row, filter) {
    const needle = filter.trim().toLowerCase();
    if (needle === "") {
        return true;
    }
    return (
        row.label.toLowerCase().includes(needle) ||
        row.sources.some(source =>
            (source.summary ?? "").toLowerCase().includes(needle),
        )
    );
}
