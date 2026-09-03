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
import { reasonFrom } from "$lib/shacl/workbenchState.svelte.js";

/**
 * How long typing is collected before it is sent.
 *
 * Long enough that a word is one request rather than five, short enough that nobody notices the
 * form is behind. A field that is left — blurred, tabbed out of, or committed with Enter — is sent
 * at once and does not wait for this.
 */
const TYPING_PAUSE_MS = 400;

/**
 * A blank shape, for adding one through the form.
 *
 * Named after the class it targets, in the namespace the document already uses for its shapes, so
 * a new shape reads like the ones around it.
 */
export function newShape(shapeNamespace, targetClass, localName) {
    return {
        iri: `${shapeNamespace}${localName}Shape`,
        targetClasses: targetClass ? [targetClass] : [],
        properties: [],
        retained: [],
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
    /**
     * The rules the document writes as shapes of their own, shared by the shapes referencing them.
     *
     * @type {import("$lib/api/generated").PropertyShapeModel[]}
     */
    propertyShapes = $state([]);
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
    /** Counts reads so a slower earlier one cannot land on top of a newer one. */
    #reads = 0;
    /**
     * The text the last applied edit produced, which the next edit is applied to.
     *
     * The caller hands its buffer to every edit, but a buffer set from an edit that is still in
     * flight has not reached it yet. Without this, two quick edits would both be applied to the
     * text as it stood before either of them and the first would be lost.
     */
    #applied = null;
    /** Edits run one at a time and in order; a form edit is a read-modify-write on one document. */
    #queue = Promise.resolve();
    /** An edit typed but not yet sent, as `{ target, body, turtle, handler }`. */
    #pending = null;
    #timer = null;

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
        if (turtle !== this.#applied) {
            // The buffer moved for a reason that is not one of our edits — someone typed in the
            // Turtle view, or another document was opened. An edit still waiting to be sent
            // describes text that no longer exists, and sending it would overwrite the change that
            // replaced it, so it is dropped rather than applied to the wrong document.
            this.#discardPending();
            this.#applied = null;
        }
        const read = ++this.#reads;
        this.loading = true;
        try {
            const { data, error } = await readForm({
                ...this.#requestOptions,
                path: this.#path,
                body: turtle === "" ? " " : turtle,
                bodySerializer: null,
                headers: { "Content-Type": "text/plain" },
            });
            // A newer read started while this one was in flight, so this answer describes text the
            // buffer has moved past. Landing it would leave the cards showing one document's
            // shapes over another's text, and applying an edit from one would rewrite the wrong
            // statement — `describes` would say so, but nothing re-reads until the text changes.
            if (read !== this.#reads) {
                return;
            }
            if (error) {
                this.error = "The constraints could not be read as a form.";
                return;
            }
            this.shapes = data?.shapes ?? [];
            this.propertyShapes = data?.propertyShapes ?? [];
            this.parseError = data?.parseError ?? null;
            this.error = null;
            this.#readFrom = turtle;
        } finally {
            if (read === this.#reads) {
                this.loading = false;
            }
        }
    }

    /**
     * Writes a shape back and returns the new document text, or `null` when nothing changed.
     *
     * The caller puts the returned text into the buffer, which is what makes the change visible in
     * both views at once and leaves it unsaved until the user says so.
     */
    async applyShape(turtle, shape) {
        await this.#clearPendingFor(shape);
        return this.#apply(turtle, { shape });
    }

    /**
     * Writes back a rule the document holds as a shape of its own.
     *
     * Its own request rather than part of the shape it was edited under, because the rule is where
     * the change belongs: every shape referencing it is meant to see it. `split` is the way out of
     * that when the user wants one — the rule is copied first and only the copy is changed.
     */
    async applyRule(turtle, rule, split = null) {
        await this.#clearPendingFor(rule);
        return this.#apply(turtle, { propertyShape: rule, split });
    }

    /** Removes a shape from the document. */
    async removeShape(turtle, shapeIri) {
        await this.flush();
        return this.#apply(turtle, { removeShapeIri: shapeIri });
    }

    /** Reads the buffer again even though the shapes already describe it, undoing a local edit. */
    async reload(turtle) {
        this.#discardPending();
        this.#readFrom = null;
        await this.read(turtle);
    }

    /**
     * Applies a shape once typing pauses, calling `handler` with the result.
     *
     * For fields that change as they are typed. Every keystroke used to be its own request, each
     * one built on the text as it stood before the request before it, so a typed word arrived
     * partly or not at all. The handler is held with the edit rather than taken from the caller
     * later, so it still runs if the form view is switched away before the pause is over.
     */
    schedule(turtle, shape, handler) {
        this.#scheduleEdit(turtle, shape, { shape }, handler);
    }

    /** The same, for a rule written as a shape of its own. */
    scheduleRule(turtle, rule, handler) {
        this.#scheduleEdit(turtle, rule, { propertyShape: rule }, handler);
    }

    #scheduleEdit(turtle, target, body, handler) {
        if (this.#pending && this.#pending.target !== target) {
            // Two shapes edited within one pause: the earlier edit goes first, because the later
            // one has to be applied to the text the earlier one produces.
            this.flush();
        }
        this.#pending = { target, body, turtle, handler };
        clearTimeout(this.#timer);
        this.#timer = setTimeout(() => this.flush(), TYPING_PAUSE_MS);
    }

    /** Sends a scheduled edit now, if there is one. */
    async flush() {
        const pending = this.#pending;
        this.#discardPending();
        if (!pending) {
            return;
        }
        pending.handler(await this.#apply(pending.turtle, pending.body));
    }

    /**
     * Waits until every edit has been applied and its text handed back.
     *
     * What a save has to await: a document written while an edit was still on its way would be the
     * document without that edit, and the form would show it coming back.
     */
    async settle() {
        await this.flush();
        await this.#queue;
    }

    /** Drops a scheduled edit for this shape or rule; sends one for any other target first. */
    async #clearPendingFor(target) {
        if (!this.#pending) {
            return;
        }
        if (this.#pending.target === target) {
            // The same object, so whatever was typed is already part of what is about to be sent.
            this.#discardPending();
            return;
        }
        await this.flush();
    }

    #discardPending() {
        clearTimeout(this.#timer);
        this.#timer = null;
        this.#pending = null;
    }

    #apply(turtle, body) {
        const run = this.#queue.then(() => this.#send(turtle, body));
        // The queue survives a failed edit: chaining the run itself would leave every later edit
        // rejected with the same error.
        this.#queue = run.then(
            () => {},
            () => {},
        );
        return run;
    }

    async #send(turtle, body) {
        this.applying = true;
        try {
            const { data, error } = await applyEdit({
                ...this.#requestOptions,
                path: this.#path,
                body: { ...body, turtle: this.#applied ?? turtle },
            });
            if (error || !data) {
                // The server says why — the shape spans two statements, a rule has no property.
                // Replacing that with "could not be applied" throws away the only part of the
                // answer the user can act on.
                this.error =
                    reasonFrom(error) ?? "The change could not be applied.";
                return null;
            }
            this.error = null;
            this.#readFrom = null;
            this.#applied = data.turtle;
            return { turtle: data.turtle, warnings: data.warnings ?? [] };
        } finally {
            this.applying = false;
        }
    }
}
