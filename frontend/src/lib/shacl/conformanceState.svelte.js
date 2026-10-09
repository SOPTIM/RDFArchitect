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

/** How each kind of disagreement reads, worst first. */
export const CONFORMANCE_KIND = {
    CONTRADICTED: {
        label: "Contradiction",
        order: 0,
        explanation: "The schema and the document cannot both be satisfied.",
    },
    DIFFERENT: {
        label: "Difference",
        order: 1,
        explanation:
            "Both can be satisfied, but they do not say the same thing.",
    },
    MISSING_IN_DOCUMENT: {
        label: "Not covered",
        order: 2,
        explanation:
            "The schema implies this; none of the graph's documents state it.",
    },
    NOT_IN_SCHEMA: {
        label: "Not in the schema",
        order: 3,
        explanation:
            "The documents constrain something the schema does not have.",
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
 * Whether the graph's constraints still agree with the schema they describe.
 *
 * The question is about the graph, not about one file. Official constraints arrive split across
 * several documents, so the comparison reads every enabled one; the open document is only what
 * decides when the question gets asked.
 *
 * Answered when the schema check is opened rather than continuously: the comparison generates
 * shapes for the whole schema, which is far more work than validating a document, so it is asked
 * once per document and kept until a save could change the answer.
 */
export class ConformanceView {
    /**
     * The last report per document, so switching to another document and back shows its result
     * again instead of asking for a comparison that was already made.
     */
    reports = $state({});
    running = $state(false);
    error = $state(null);
    /** The document a failed comparison was about, so showing it again does not retry in a loop. */
    failedOn = $state(null);

    #datasetName;
    #graphUri;
    #requestOptions;

    constructor({ datasetName, graphUri, requestOptions = {} }) {
        this.#datasetName = datasetName;
        this.#graphUri = graphUri;
        this.#requestOptions = requestOptions;
    }

    /** The comparison last made for `documentId`, or null when there is none. */
    reportFor(documentId) {
        return documentId ? (this.reports[documentId] ?? null) : null;
    }

    /**
     * Compares `documentId` unless its result is already known — what opening the schema check
     * does, so the answer is there without a click. A comparison that failed is not retried until
     * asked for again.
     */
    ensure(documentId) {
        if (
            !documentId ||
            this.running ||
            this.reports[documentId] ||
            this.failedOn === documentId
        ) {
            return null;
        }
        return this.run(documentId);
    }

    async run(documentId) {
        if (!documentId || this.running) {
            return null;
        }
        this.running = true;
        try {
            const { data, error } = await compare({
                ...this.#requestOptions,
                path: {
                    datasetName: this.#datasetName,
                    graphURI: this.#graphUri,
                },
                query: { documentId },
            });
            if (error) {
                this.error =
                    "The document could not be compared with the schema.";
                this.failedOn = documentId;
                return null;
            }
            this.reports = { ...this.reports, [documentId]: data };
            this.error = null;
            this.failedOn = null;
            return data;
        } finally {
            this.running = false;
        }
    }

    /**
     * Drops every report. Each comparison reads all of the graph's enabled documents, so a save
     * to any of them, or a change to the schema, can change the answer for every one.
     */
    forget() {
        this.reports = {};
        this.error = null;
        this.failedOn = null;
    }
}
