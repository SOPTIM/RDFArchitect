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
 * What the backend calls a kind of data, in the words the editor uses for it, and what a change of
 * that kind actually is — a filter that only says "DL" leaves the user guessing what it hides.
 */
const KINDS = {
    rdf: {
        label: "Schema",
        description:
            "Classes, attributes, associations, packages and enumerations",
    },
    shacl: {
        label: "Constraints (SHACL)",
        description:
            "Hand-written constraints on top of the ones generated from the schema",
    },
    dl: {
        label: "Layout",
        description: "Where classes and edge labels sit on a diagram",
    },
    diagrams: {
        label: "Diagrams",
        description: "Custom diagrams and which classes they contain",
    },
    prefixes: {
        label: "Namespaces",
        description:
            "The namespace prefixes shared by every schema in the workspace",
    },
    graphs: {
        label: "Schema list",
        description: "Schemas being created, deleted or renamed",
    },
    colors: {
        label: "Colours",
        description:
            "The colours schemas are drawn in on the cross-profile diagram",
    },
};

/**
 * Returns what to call a kind of data in the changelog.
 *
 * @param {string} kind the kind as the backend names it
 * @returns {string} the label to show
 */
export function labelOf(kind) {
    return KINDS[kind]?.label ?? kind;
}

/**
 * Returns what a change of this kind is, for a filter that would otherwise only show a word.
 *
 * @param {string} kind the kind as the backend names it
 * @returns {string} the description to show on hover
 */
export function descriptionOf(kind) {
    return KINDS[kind]?.description ?? "";
}

/**
 * Returns the kinds a filter can offer, in a stable order.
 *
 * Every kind the editor knows is offered whether or not the changes at hand have any, so that the
 * filter does not rearrange itself as the user moves between schemas; a kind with nothing behind
 * it is shown as having nothing. A kind the editor does not know is added if it occurs, so that a
 * changelog never silently drops a change.
 *
 * @param {Array<{affectedKinds?: string[]}>} changes the changes the filter applies to
 * @returns {string[]} the kinds to offer, known ones first and in the editor's order
 */
export function filterableKinds(changes) {
    const present = new Set(
        (changes ?? []).flatMap(change => change.affectedKinds ?? []),
    );
    const unknown = [...present].filter(kind => !(kind in KINDS)).sort();
    return [...Object.keys(KINDS), ...unknown];
}

/**
 * Returns how many of the given changes touched each kind of data.
 *
 * A change is counted under every kind it touched, because that is how the filter treats it: it
 * stays visible as long as one of them is still showing.
 *
 * @param {Array<{affectedKinds?: string[]}>} changes the changes to count
 * @returns {Map<string, number>} how many changes each kind has
 */
export function countByKind(changes) {
    const counts = new Map();
    for (const change of changes ?? []) {
        for (const kind of new Set(change.affectedKinds ?? [])) {
            counts.set(kind, (counts.get(kind) ?? 0) + 1);
        }
    }
    return counts;
}
