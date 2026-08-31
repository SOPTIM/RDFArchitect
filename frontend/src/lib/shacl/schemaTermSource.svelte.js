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

import { describeTerm, listTerms } from "$lib/api/generated/index.ts";

/**
 * The schema terms of one graph's workspace, held for as long as an editor is open on it.
 *
 * The whole term list is fetched once and filtered in the browser, so completion appears as the
 * user types instead of after a round trip. That is affordable because the list is small and
 * changes only when the schema does — a full CGMES release is a few thousand names.
 *
 * Details are fetched per term and kept, because a hover is asked for the same handful of terms
 * over and over while someone reads a file.
 */
export class SchemaTermSource {
    /** @type {import("$lib/api/generated").SchemaTerm[]} */
    terms = $state([]);
    profiles = $state([]);
    loaded = $state(false);

    #datasetName;
    #graphUri;
    #requestOptions;
    #details = new Map();
    #pending = new Map();
    #loading = null;

    constructor({ datasetName, graphUri, requestOptions = {} }) {
        this.#datasetName = datasetName;
        this.#graphUri = graphUri;
        this.#requestOptions = requestOptions;
    }

    get #path() {
        return { datasetName: this.#datasetName, graphURI: this.#graphUri };
    }

    /** Fetches the term list once. Safe to call from every provider on every keystroke. */
    load() {
        this.#loading ??= listTerms({
            ...this.#requestOptions,
            path: this.#path,
        })
            .then(({ data, error }) => {
                if (error) {
                    // Completion simply stays empty; the editor is still perfectly usable.
                    console.warn("Failed to load the schema terms:", error);
                    return;
                }
                this.terms = data?.terms ?? [];
                this.profiles = data?.profiles ?? [];
                this.loaded = true;
            })
            .catch(error => {
                console.warn("Failed to load the schema terms:", error);
            });
        return this.#loading;
    }

    /**
     * What the schema says about one term, or `null` when it declares none.
     *
     * A miss is cached as well as a hit: an editor asks about whatever is under the cursor, and
     * most of what a Turtle file contains is not a schema term.
     */
    async detailOf(iri) {
        if (this.#details.has(iri)) {
            return this.#details.get(iri);
        }
        if (this.#pending.has(iri)) {
            return this.#pending.get(iri);
        }
        const request = describeTerm({
            ...this.#requestOptions,
            path: this.#path,
            query: { iri },
        })
            .then(({ data, error }) => {
                const detail = error ? null : (data ?? null);
                this.#details.set(iri, detail);
                return detail;
            })
            .catch(() => null)
            .finally(() => this.#pending.delete(iri));
        this.#pending.set(iri, request);
        return request;
    }

    /** Drops everything, so the next question re-reads a schema that has since been edited. */
    invalidate() {
        this.#details.clear();
        this.#pending.clear();
        this.#loading = null;
        this.loaded = false;
    }
}
