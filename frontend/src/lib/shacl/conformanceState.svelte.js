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
 * Whether the graph's constraints still agree with the schema they describe.
 *
 * The question is about the graph, not about one file. Official constraints arrive split across
 * several documents, so the comparison reads every enabled one; the open document is only what
 * decides when the question gets asked.
 *
 * Answered on demand rather than continuously: the comparison generates shapes for the whole
 * schema, which is far more work than validating a document, and the question is one a modeller
 * asks at a moment they choose.
 */
export class ConformanceView {
    /** @type {import("$lib/api/generated").ConformanceReport | null} */
    report = $state(null);
    running = $state(false);
    error = $state(null);
    /** The document the report describes, so a stale one is not shown for another. */
    reportedOn = $state(null);

    #datasetName;
    #graphUri;
    #requestOptions;

    constructor({ datasetName, graphUri, requestOptions = {} }) {
        this.#datasetName = datasetName;
        this.#graphUri = graphUri;
        this.#requestOptions = requestOptions;
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
                return null;
            }
            this.report = data;
            this.reportedOn = documentId;
            this.error = null;
            return data;
        } finally {
            this.running = false;
        }
    }

    /** Drops a report that no longer describes what is on screen. */
    forget() {
        this.report = null;
        this.reportedOn = null;
        this.error = null;
    }
}
