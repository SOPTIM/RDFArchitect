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

import { compare } from "$lib/api/generated/index.ts";

/** How each kind of finding reads, worst first, with an example for whoever has not seen one. */
export const CONFORMANCE_KIND = {
    CONTRADICTED: {
        label: "Contradiction",
        order: 0,
        explanation: "The schema and a document cannot both be satisfied.",
        example:
            "For example: the schema says a property holds an xsd:float, and a document says sh:datatype xsd:integer.",
    },
    DIFFERENT: {
        label: "Difference",
        order: 1,
        explanation:
            "Both can be satisfied, but they do not say the same thing.",
        example:
            "For example: the schema allows 0..1 values, and a document says sh:minCount 1. Often this is the profile doing its job and narrowing what the schema allows.",
    },
    MISSING_IN_DOCUMENT: {
        label: "Not covered",
        order: 2,
        explanation:
            "The schema implies this; none of the documents applying to it state it.",
        example:
            "For example: the schema gives a property a multiplicity of 1..1, and no document says anything about it. Not a disagreement: official files split their rules on purpose.",
    },
    NOT_IN_SCHEMA: {
        label: "Not in the schema",
        order: 3,
        explanation:
            "A document constrains something no schema in the workspace has.",
        example:
            "For example: a document has a rule on cim:Terminal.foo, and no schema of this workspace declares that property.",
    },
};

/**
 * How a report reads at a glance: `ok`, `gaps` or `drift`.
 *
 * Coverage and agreement are separate questions, and conflating them is what made a 55-line
 * cross-profile constraints file report "0 of 49 property constraints agree". A file that says
 * nothing about a property does not disagree with the schema about it.
 */
export function conformanceVerdict(report) {
    if (!report) {
        return null;
    }
    if (report.contradictedCount > 0) {
        return "drift";
    }
    if (
        report.differentCount > 0 ||
        report.notInSchemaCount > 0 ||
        report.missingInDocumentCount > 0
    ) {
        return "gaps";
    }
    return "ok";
}

export function conformanceKind(kind) {
    return CONFORMANCE_KIND[kind] ?? CONFORMANCE_KIND.DIFFERENT;
}

/**
 * Folds findings that say the same thing about the same property into one, naming every class.
 *
 * Official files state an inherited property again on every concrete subclass, so one rule the
 * schema puts on `AsynchronousMachineDynamics` came back as 22 identical findings, one per subclass,
 * burying the other findings under a single question. They are kept apart by kind, property,
 * both readings and the documents stating them: only findings that would read alike are merged.
 *
 * @returns {Array<object>} findings in their first-seen order, each with `targetClasses`
 */
export function foldFindings(findings) {
    const folded = new Map();
    for (const finding of findings ?? []) {
        const key = JSON.stringify([
            finding.kind,
            finding.path,
            finding.message,
            finding.schemaSays,
            finding.documentSays,
            finding.statedIn ?? [],
        ]);
        const existing = folded.get(key);
        if (existing) {
            existing.targetClasses.push(finding.targetClass);
        } else {
            folded.set(key, {
                ...finding,
                targetClasses: [finding.targetClass],
            });
        }
    }
    return [...folded.values()];
}

/**
 * Whether a schema and the documents applying to it still agree.
 *
 * Official constraints arrive split across several documents, so the comparison reads every enabled
 * one applying to the schema.
 *
 * Answered when asked rather than continuously: the comparison generates shapes for the whole
 * schema, which is far more work than validating a document, so it is kept until something could
 * change the answer.
 */
export class ConformanceView {
    /** The last report per schema, so coming back shows it instead of asking again. */
    reports = $state({});
    running = $state(false);
    error = $state(null);
    /** The schema a failed comparison was about, so its message stays with it. */
    failedOn = $state(null);

    #datasetName;
    #requestOptions;

    constructor({ datasetName, requestOptions = {} }) {
        this.#datasetName = datasetName;
        this.#requestOptions = requestOptions;
    }

    /** The comparison last made for `schemaUri`, or null when there is none. */
    reportFor(schemaUri) {
        return schemaUri ? (this.reports[schemaUri] ?? null) : null;
    }

    async run(schemaUri) {
        if (!schemaUri || this.running) {
            return null;
        }
        this.running = true;
        try {
            const { data, error } = await compare({
                ...this.#requestOptions,
                path: { datasetName: this.#datasetName },
                query: { schema: schemaUri },
            });
            if (error) {
                this.error = "The schema could not be compared.";
                this.failedOn = schemaUri;
                return null;
            }
            this.reports = { ...this.reports, [schemaUri]: data };
            this.error = null;
            this.failedOn = null;
            return data;
        } finally {
            this.running = false;
        }
    }

    /** Drops every report, for when a save or a change to a schema can change the answers. */
    forget() {
        this.reports = {};
        this.error = null;
        this.failedOn = null;
    }
}
