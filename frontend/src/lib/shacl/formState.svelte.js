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

import { applyEdit, readForm } from "$lib/api/generated/index.ts";

/**
 * A blank shape, for adding one through the form.
 *
 * Named after the class it targets, in the namespace the document already uses for its shapes, so
 * a new shape reads like the ones around it.
 */
export function newShape(shapeNamespace, targetClass, localName) {
    return {
        iri: `${shapeNamespace}${localName}Shape`,
        targetClass,
        properties: [],
        unsupported: [],
        editable: true,
    };
}

/**
 * The namespace a document's existing shapes live in, for naming a new one.
 *
 * Falls back to the document's default or first prefix, and finally to a generic namespace — a new
 * shape has to be called something, and anything is better than refusing to add one.
 */
export function shapeNamespaceOf(shapes, prefixes) {
    const existing = shapes.find(shape => shape.iri);
    if (existing) {
        const cut = Math.max(
            existing.iri.lastIndexOf("#"),
            existing.iri.lastIndexOf("/"),
        );
        if (cut >= 0) {
            return existing.iri.slice(0, cut + 1);
        }
    }
    return prefixes[""] ?? Object.values(prefixes)[0] ?? "urn:rdfa:shapes#";
}

/**
 * The form view of the document currently in the editor.
 *
 * Reads and writes the same buffer the Turtle view shows, never the stored document, so switching
 * views loses nothing and saving stays one explicit step. Every edit goes to the backend and comes
 * back as new text: only the edited shape's statement is rewritten, so the rest of the file keeps
 * the bytes its author gave it.
 */
export class ShapesFormView {
    /** @type {import("$lib/api/generated").NodeShapeModel[]} */
    shapes = $state([]);
    /** A syntax error in the buffer; the form has nothing to show until it is fixed. */
    parseError = $state(null);
    loading = $state(false);
    applying = $state(false);
    error = $state(null);

    #datasetName;
    #graphUri;
    #requestOptions;
    /** The text the shapes were read from, so a stale read is not shown as current. */
    #readFrom = null;

    constructor({ datasetName, graphUri, requestOptions = {} }) {
        this.#datasetName = datasetName;
        this.#graphUri = graphUri;
        this.#requestOptions = requestOptions;
    }

    get #path() {
        return { datasetName: this.#datasetName, graphURI: this.#graphUri };
    }

    /** Whether the shapes on screen describe this text. */
    describes(turtle) {
        return this.#readFrom === turtle;
    }

    /** Reads the buffer into shapes. Does nothing when the shapes already describe it. */
    async read(turtle) {
        if (this.describes(turtle)) {
            return;
        }
        this.loading = true;
        try {
            const { data, error } = await readForm({
                ...this.#requestOptions,
                path: this.#path,
                body: turtle === "" ? " " : turtle,
                bodySerializer: null,
                headers: { "Content-Type": "text/plain" },
            });
            if (error) {
                this.error = "The constraints could not be read as a form.";
                return;
            }
            this.shapes = data?.shapes ?? [];
            this.parseError = data?.parseError ?? null;
            this.error = null;
            this.#readFrom = turtle;
        } finally {
            this.loading = false;
        }
    }

    /**
     * Writes a shape back and returns the new document text, or `null` when nothing changed.
     *
     * The caller puts the returned text into the buffer, which is what makes the change visible in
     * both views at once and leaves it unsaved until the user says so.
     */
    async applyShape(turtle, shape) {
        return this.#apply(turtle, { turtle, shape });
    }

    /** Removes a shape from the document. */
    async removeShape(turtle, shapeIri) {
        return this.#apply(turtle, { turtle, removeShapeIri: shapeIri });
    }

    async #apply(turtle, body) {
        this.applying = true;
        try {
            const { data, error } = await applyEdit({
                ...this.#requestOptions,
                path: this.#path,
                body,
            });
            if (error || !data) {
                this.error = "The change could not be applied.";
                return null;
            }
            this.error = null;
            this.#readFrom = null;
            return { turtle: data.turtle, warnings: data.warnings ?? [] };
        } finally {
            this.applying = false;
        }
    }
}
