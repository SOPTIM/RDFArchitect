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

import { SvelteSet } from "svelte/reactivity";

import { applyEdit, readForm } from "$lib/api/generated/index.ts";
import { STANDARD_NAMESPACES } from "$lib/shacl/standardVocabulary.js";
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
 * The fields of a shape or rule that the server states and the form never edits.
 *
 * Everything else on a model is what the user typed, which is what a re-read must not take away.
 */
const SERVER_OWNED = ["line", "editable", "readOnlyReason", "usedBy"];

/**
 * What an edit is about, as something that survives a re-read.
 *
 * Every read hands back new objects, so the object a field was typed into is not the one on
 * screen a moment later; the IRI is. A rule written inside a shape has no IRI of its own and is
 * edited as part of that shape.
 */
function keyOf(body) {
    if (body.shape?.iri) {
        return `shape:${body.shape.iri}`;
    }
    if (body.propertyShape?.iri) {
        return `rule:${body.propertyShape.iri}`;
    }
    return body.removeShapeIri ? `shape:${body.removeShapeIri}` : null;
}

/** The server's answer for a shape or rule, with what the user has typed laid over it. */
function overlay(fresh, local) {
    const merged = { ...fresh, ...local };
    for (const field of SERVER_OWNED) {
        merged[field] = fresh[field];
    }
    return merged;
}

/**
 * A blank shape, for adding one through the form. `newShapeIri` suggests what to call it.
 */
export function newShape(iri, targetClass) {
    return {
        iri,
        targetClasses: targetClass ? [targetClass] : [],
        properties: [],
        retained: [],
        editable: true,
    };
}

/**
 * The namespace a document's existing shapes live in, for naming a new one.
 *
 * Falls back to the document's default prefix, then to one of its own prefixes that is neither a
 * W3C vocabulary nor one of the schema's (`avoid`) — a shape called `sh:BreakerShape` or
 * `cim:BreakerShape` claims to be part of a vocabulary it is not — and finally to a generic
 * namespace: a new shape has to be called something, and the name can still be corrected.
 */
export function shapeNamespaceOf(shapes, prefixes, avoid = []) {
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
    const taken = new Set([...STANDARD_NAMESPACES, ...avoid]);
    return (
        prefixes[""] ??
        Object.values(prefixes).find(namespace => !taken.has(namespace)) ??
        "urn:rdfa:shapes#"
    );
}

/**
 * A name for a new shape: `<Class>Shape` in the shapes' namespace, numbered if it is taken.
 *
 * Named after the class it targets, in the namespace the document already uses for its shapes, so
 * a new shape reads like the ones around it.
 *
 * @param taken every IRI the document already names a shape or rule with
 */
export function newShapeIri(namespace, targetClass, taken) {
    const cut = targetClass
        ? Math.max(targetClass.lastIndexOf("#"), targetClass.lastIndexOf("/"))
        : -1;
    const local = (targetClass ? targetClass.slice(cut + 1) : "") || "New";
    let iri = `${namespace}${local}Shape`;
    for (let suffix = 2; taken.has(iri); suffix += 1) {
        iri = `${namespace}${local}Shape${suffix}`;
    }
    return iri;
}

/**
 * The shapes, with each shared rule shown under them knowing who else uses it.
 *
 * The backend lists a shared rule's users once, on the rule itself, rather than under every shape
 * referencing it — on a `-Con-Simple-` profile repeating it was most of the payload. The cards
 * under a shape ask the same question, so the list is filled in from the rule it copies.
 */
export function withSharedUsage(shapes, propertyShapes) {
    const usedBy = new Map(
        propertyShapes.map(rule => [rule.iri, rule.usedBy ?? []]),
    );
    return shapes.map(shape => ({
        ...shape,
        properties: shape.properties?.map(rule =>
            rule.iri != null && rule.usedBy == null
                ? { ...rule, usedBy: usedBy.get(rule.iri) ?? [] }
                : rule,
        ),
    }));
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
    /**
     * The IRIs of the cards that are open.
     *
     * Held here rather than in the card list because the form view is unmounted when the Turtle
     * view is shown, and coming back to find every card shut is the tab forgetting what you were
     * doing. A set, because one card at a time made comparing two shapes impossible.
     *
     * @type {Set<string>}
     */
    expanded = $state(new SvelteSet());
    /** What the cards are filtered by, likewise kept across a switch to the Turtle view. */
    filter = $state("");
    /** Whether only the shapes the form will not write are listed. */
    lockedOnly = $state(false);
    /**
     * A line of the document to show the card for, set from somewhere the cards are not.
     *
     * The Turtle view and the problems panel both know a line and nothing about shapes; the form
     * knows shapes and reads them asynchronously. So the line is left here and picked up once
     * there is something to match it against.
     */
    focusLine = $state(null);
    /**
     * Shapes added through the form, listed whatever the filter says.
     *
     * A shape that vanished the moment it was added, because a filter typed earlier does not match
     * its name, looked like an add that had failed.
     *
     * @type {Set<string>}
     */
    added = $state(new SvelteSet());
    loading = $state(false);
    applying = $state(false);
    error = $state(null);
    /**
     * The last edit the server refused, as `{ key, message }`, so the card it was made on can say
     * why — a toast is gone before anyone has read it.
     */
    failure = $state(null);

    #datasetName;
    #graphUri;
    #requestOptions;
    /** The text the shapes were read from, so a stale read is not shown as current. */
    #readFrom = null;
    /**
     * The text the shapes on screen describe, sent with every edit so the server can refuse one
     * built from a document that has moved on since.
     *
     * Unlike `#readFrom` it survives an edit: the text an edit produces is what the edited model
     * says, so the model goes on describing it until a read replaces the model.
     */
    #modelFrom = null;
    /** The document the form shows, so that opening another one starts afresh. */
    #documentId;
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
    /** An edit typed but not yet sent, as `{ key, target, body, turtle, handler }`. */
    #pending = null;
    #timer = null;
    /**
     * The shapes and rules with an edit not yet written back, as `key → { target, sending }`.
     *
     * A read that lands meanwhile describes the document before that edit. Replacing the card with
     * it put the old value back into the field being typed in, so whatever was typed during the
     * round trip vanished. These are laid over the answer until their edits are through.
     */
    #local = new Map();

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
            this.#local.clear();
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
            this.propertyShapes = (data?.propertyShapes ?? []).map(rule =>
                this.#localRule(rule),
            );
            this.shapes = withSharedUsage(
                data?.shapes ?? [],
                this.propertyShapes,
            ).map(shape => this.#withLocal(shape));
            this.parseError = data?.parseError ?? null;
            this.error = null;
            this.#readFrom = turtle;
            this.#modelFrom = turtle;
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
        await this.#clearPendingFor({ shape });
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
        await this.#clearPendingFor({ propertyShape: rule });
        return this.#apply(turtle, { propertyShape: rule, split });
    }

    /** Removes a shape from the document. */
    async removeShape(turtle, shapeIri) {
        await this.flush();
        return this.#apply(turtle, { removeShapeIri: shapeIri });
    }

    /**
     * Says which document the form is showing; a different one from before starts afresh.
     *
     * The filter, the open cards and anything still waiting to be sent belong to the document they
     * were made in. Kept for the next one, a filter hid its shapes behind "Nothing matches", and an
     * edit waiting for a pause would have been sent into the wrong file.
     */
    showDocument(documentId) {
        if (documentId === this.#documentId) {
            return;
        }
        const first = this.#documentId === undefined;
        this.#documentId = documentId;
        if (first) {
            return;
        }
        this.#discardPending();
        this.#local.clear();
        this.filter = "";
        this.lockedOnly = false;
        this.expanded.clear();
        this.added.clear();
        this.failure = null;
        this.focusLine = null;
    }

    /** Why the server refused the last edit to this shape or rule, if it did. */
    failureOf(key) {
        return this.failure?.key === key ? this.failure.message : null;
    }

    /** Opens or shuts one card. */
    toggle(iri) {
        if (!this.expanded.delete(iri)) {
            this.expanded.add(iri);
        }
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
        const key = keyOf(body);
        if (this.#pending && this.#pending.key !== key) {
            // Two shapes edited within one pause: the earlier edit goes first, because the later
            // one has to be applied to the text the earlier one produces.
            this.flush();
        }
        this.#pending = { key, target, body, turtle, handler };
        this.#hold(key, target);
        clearTimeout(this.#timer);
        this.#timer = setTimeout(() => this.flush(), TYPING_PAUSE_MS);
    }

    /** Sends a scheduled edit now, if there is one. */
    async flush() {
        const pending = this.#pending;
        if (!pending) {
            return;
        }
        // Not `#discardPending`, which would let go of what was typed before `#apply` holds it.
        clearTimeout(this.#timer);
        this.#timer = null;
        this.#pending = null;
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
    async #clearPendingFor(body) {
        if (!this.#pending) {
            return;
        }
        if (this.#pending.key === keyOf(body)) {
            // The same shape, so whatever was typed is already part of what is about to be sent.
            this.#discardPending();
            return;
        }
        await this.flush();
    }

    #discardPending() {
        clearTimeout(this.#timer);
        this.#timer = null;
        const key = this.#pending?.key;
        this.#pending = null;
        this.#release(key);
    }

    #hold(key, target) {
        if (!key || !target) {
            return null;
        }
        const entry = this.#local.get(key) ?? { target, sending: 0 };
        entry.target = target;
        this.#local.set(key, entry);
        return entry;
    }

    /** Forgets a local edit once nothing of it is waiting or on its way any more. */
    #release(key) {
        const entry = this.#local.get(key);
        if (entry && entry.sending === 0 && this.#pending?.key !== key) {
            this.#local.delete(key);
        }
    }

    #withLocal(shape) {
        const local = this.#local.get(`shape:${shape.iri}`);
        const merged = local ? overlay(shape, local.target) : shape;
        return {
            ...merged,
            properties: (merged.properties ?? []).map(rule =>
                rule.iri ? this.#localRule(rule) : rule,
            ),
        };
    }

    #localRule(rule) {
        const local = this.#local.get(`rule:${rule.iri}`);
        return local ? overlay(rule, local.target) : rule;
    }

    #apply(turtle, body) {
        const key = keyOf(body);
        const entry = this.#hold(key, body.shape ?? body.propertyShape);
        if (entry) {
            entry.sending += 1;
        }
        const run = this.#queue.then(() => this.#send(turtle, body, key));
        // The queue survives a failed edit: chaining the run itself would leave every later edit
        // rejected with the same error.
        this.#queue = run.then(
            () => {},
            () => {},
        );
        if (entry) {
            // Registered before the caller's own `await`, so the edit is let go of before its
            // text reaches the buffer and is read back.
            run.then(
                () => this.#sent(key, entry),
                () => this.#sent(key, entry),
            );
        }
        return run;
    }

    #sent(key, entry) {
        entry.sending -= 1;
        if (this.#local.get(key) === entry) {
            this.#release(key);
        }
    }

    async #send(turtle, body, key) {
        this.applying = true;
        const base = this.#applied ?? turtle;
        try {
            const { data, error } = await applyEdit({
                ...this.#requestOptions,
                path: this.#path,
                body: {
                    ...body,
                    turtle: base,
                    baseTurtle: this.#modelFrom ?? base,
                },
            });
            if (error || !data) {
                // The server says why — the shape spans two statements, a rule has no property.
                // Replacing that with "could not be applied" throws away the only part of the
                // answer the user can act on.
                this.error =
                    reasonFrom(error) ?? "The change could not be applied.";
                this.failure = { key, message: this.error };
                // What was refused is not laid over the document again: the card is to go back
                // to what the document says, not keep the value the server would not write.
                this.#local.delete(key);
                return null;
            }
            this.error = null;
            if (this.failure?.key === key) {
                this.failure = null;
            }
            this.#readFrom = null;
            this.#applied = data.turtle;
            this.#modelFrom = data.turtle;
            // `base` is the text this edit was made to, so the caller can tell whether its buffer
            // still holds it — it may have been typed over in the Turtle view meanwhile.
            return { turtle: data.turtle, base, warnings: data.warnings ?? [] };
        } finally {
            this.applying = false;
        }
    }
}
