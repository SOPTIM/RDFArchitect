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

import { STANDARD_TERMS, standardDetailOf } from "./standardVocabulary.js";

/**
 * The schema terms of one workspace, held for as long as an editor is open on it.
 *
 * The whole term list is fetched once and filtered in the browser, so completion appears as the
 * user types instead of after a round trip. That is affordable because the list is small and
 * changes only when the schema does — a full CGMES release is a few thousand names.
 *
 * Details are fetched per term and kept, because a hover is asked for the same handful of terms
 * over and over while someone reads a file.
 *
 * SHACL's own vocabulary is added on top of what the workspace declares. It is in no profile, so
 * the schema index does not know it exists, and a constraints file is written mostly in `sh:`
 * terms.
 */
export class SchemaTermSource {
    /** @type {import("$lib/api/generated").SchemaTerm[]} */
    terms = $state([]);
    profiles = $state([]);
    loaded = $state(false);

    #workspaceName;
    #requestOptions;
    /** Where a class several schemas hold is opened, first to last. */
    #preferredSchemas = [];
    #details = new Map();
    #pending = new Map();
    #loading = null;
    /** Bumped by `invalidate`, so an answer to a question asked before it is dropped. */
    #generation = 0;

    constructor({ workspaceName, requestOptions = {} }) {
        this.#workspaceName = workspaceName;
        this.#requestOptions = requestOptions;
    }

    get #path() {
        return { workspaceName: this.#workspaceName };
    }

    /**
     * What the editor offers: the workspace's terms and the vocabularies they are written in.
     *
     * Separate from `terms` because the vocabularies are not the workspace's: `sh:minCount` is
     * something to write, and not a class or property of a schema.
     */
    get completionTerms() {
        return [...this.terms, ...STANDARD_TERMS];
    }

    /**
     * Says which schemas a class is opened in first when several hold it: those of the document
     * being edited, so following `cim:Terminal` from Equipment constraints opens Equipment's.
     */
    preferSchemas(schemaNames) {
        this.#preferredSchemas = [...(schemaNames ?? [])];
    }

    /** Fetches the term list once. Safe to call from every provider on every keystroke. */
    load() {
        const generation = this.#generation;
        this.#loading ??= listTerms({
            ...this.#requestOptions,
            path: this.#path,
        })
            .then(({ data, error }) => {
                if (generation !== this.#generation) {
                    return;
                }
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
     *
     * The standard vocabularies are answered here without a request. Their namespaces are disjoint
     * from any CIM profile, so a hit is definitive — and in a constraints file most of what the
     * cursor passes over is `sh:`, which would otherwise be one round trip each to be told the
     * schema has never heard of it.
     */
    async detailOf(iri) {
        const standard = standardDetailOf(iri);
        if (standard) {
            return standard;
        }
        // The class a detail leads to depends on the schemas asked for first, so a detail is
        // kept per term and preference.
        const schemas = this.#preferredSchemas;
        const key = [iri, ...schemas].join("\n");
        if (this.#details.has(key)) {
            return this.#details.get(key);
        }
        if (this.#pending.has(key)) {
            return this.#pending.get(key);
        }
        const generation = this.#generation;
        const request = describeTerm({
            ...this.#requestOptions,
            path: this.#path,
            query: schemas.length > 0 ? { iri, schema: schemas } : { iri },
        })
            .then(({ data, error, response }) => {
                // 204 is the server's "no profile declares this"; the client reads it as `{}`.
                const detail =
                    error || response?.status === 204 ? null : (data ?? null);
                if (generation === this.#generation) {
                    this.#details.set(key, detail);
                }
                return detail;
            })
            .catch(() => null)
            .finally(() => {
                if (this.#pending.get(key) === request) {
                    this.#pending.delete(key);
                }
            });
        this.#pending.set(key, request);
        return request;
    }

    /**
     * Drops everything, so the next question re-reads a schema that has since been edited.
     *
     * Called when the schema may have changed under the page, by an undo or an import from the
     * menu bar: the term list and every cached hover would still describe the schema before it.
     */
    invalidate() {
        this.#generation += 1;
        this.#details.clear();
        this.#pending.clear();
        this.#loading = null;
        this.loaded = false;
    }
}
