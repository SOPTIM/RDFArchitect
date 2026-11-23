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

import {
    createShapesDocument,
    createShapesDocumentFromFile,
    deleteShapesDocument,
    getGeneratedShaclasString,
    getShapesDocumentText,
    listShapesDocuments,
    replaceShapesDocumentText,
    updateShapesDocument,
} from "$lib/api/generated/index.ts";
import { uniqueDocumentName } from "$lib/shacl/documentNames.js";
import { graphStore } from "$lib/stores/graphStore.ts";
import { workspaceStore } from "$lib/stores/workspaceStore.ts";
import { graphLabeller, schemaName as nameOf } from "$lib/utils/graph-label.js";
import { compareGraphs } from "$lib/utils/graph-order.js";

/** Said instead of a server error when the workspace is the reason nothing can be written. */
const READ_ONLY =
    "This workspace is read-only, so its constraints cannot be changed.";

/**
 * What opens the shapes RDFArchitect derives from a schema.
 *
 * Not a document: nothing stores them, and they are rebuilt from the classes every time they are
 * asked for. They sit in the tree anyway because that is where someone looks for "what constrains
 * this schema", and the generated shapes are most of the answer. A plain string, so it can never
 * collide with a document's UUID.
 */
const GENERATED_PREFIX = "generated:";

/** What the generated entry is called in the list. */
export const GENERATED_NAME = "Generated shapes";

const UNICODE_SPACE = /[\p{Zs}\u2028\u2029]/u;

export function generatedId(schemaName) {
    return `${GENERATED_PREFIX}${schemaName}`;
}

function isGenerated(id) {
    return typeof id === "string" && id.startsWith(GENERATED_PREFIX);
}

/**
 * Whether Java's `String.isBlank` — what the server tests — calls `text` blank. Not `trim`'s idea
 * of it: Java's `Character.isWhitespace` keeps the no-break spaces as content, and counts the
 * information separators U+001C–U+001F as space.
 */
function isJavaBlank(text) {
    for (const character of text) {
        const code = character.codePointAt(0);
        if ((code >= 0x09 && code <= 0x0d) || (code >= 0x1c && code <= 0x1f)) {
            continue;
        }
        if (
            UNICODE_SPACE.test(character) &&
            code !== 0x00a0 &&
            code !== 0x2007 &&
            code !== 0x202f
        ) {
            continue;
        }
        return false;
    }
    return true;
}

/**
 * Names one version of a document's text the way the backend does: the lowercase hex SHA-256 of
 * its UTF-8 bytes. Sent with a save so the server can refuse one made to text it no longer holds.
 */
export async function revisionOf(text) {
    const digest = await crypto.subtle.digest(
        "SHA-256",
        new TextEncoder().encode(text),
    );
    return Array.from(new Uint8Array(digest), byte =>
        byte.toString(16).padStart(2, "0"),
    ).join("");
}

/**
 * Text as the server records it: whitespace-only Turtle is kept as the empty document it means.
 * A revision has to name that, or a save after emptying a document reads as made to stale text.
 */
function storedForm(text) {
    return isJavaBlank(text) ? "" : text;
}

/**
 * The part of a failed response worth showing someone.
 *
 * Spring answers with RFC 9457 problem details, whose `detail` is the actual reason — a parser's
 * line and column, say. Everything else in the body is about the request, not about what to fix.
 */
export function reasonFrom(error) {
    if (!error) {
        return null;
    }
    if (typeof error === "string") {
        return error;
    }
    return error.detail ?? error.message ?? null;
}

/**
 * A workspace's constraints documents and the text being edited.
 *
 * All of the workbench's behaviour lives here rather than in the page so it can be tested without
 * a DOM: the components below it render this and call into it, and hold no state of their own.
 */
export class ShapesWorkbench {
    /** @type {import("$lib/api/generated").ShapesDocumentInfo[]} */
    documents = $state([]);
    /** The workspace's schemas, as `{ name, label }`, in the order the navigation shows them. */
    schemas = $state([]);
    selectedId = $state(null);
    /**
     * The open document as it was listed, while it is gone from the workspace — undone, or deleted
     * in another tab — and the buffer still holds unsaved edits to it. Null otherwise.
     *
     * The edits stay in the buffer rather than move on with the selection: under another document,
     * the next save would write them there. Saving them makes a new document.
     */
    detached = $state(null);
    /** Moves with every reading of the documents, for what is derived from them elsewhere. */
    documentsRevision = $state(0);
    text = $state("");
    savedText = $state("");
    /**
     * Whether the workspace forbids changes.
     *
     * Read-only is a property of the workspace, and it is advisory: the backend reports it but does
     * not enforce it, so every part of this feature that writes has to check. Defaults to true so a
     * workbench that has not finished loading cannot be used to change anything.
     */
    readOnly = $state(true);
    /** Why the last create, import or update was refused, as the server put it. */
    lastError = $state(null);
    loading = $state(false);
    saving = $state(false);
    /** True while the generated shapes are being fetched for the first time. */
    generating = $state(false);
    /** Last failure, shown in place of the editor rather than swallowed. */
    error = $state(null);
    /**
     * Changes whenever the buffer is replaced by something other than an edit — another document
     * opened, or the same one read again. The editor starts a fresh history on each change, so
     * undo cannot step from one document into another.
     */
    bufferKey = $state(0);

    /**
     * Whether the buffer holds the selected document's text. False while it is being read and
     * after a read failed, when the buffer describes nothing and must neither be edited nor saved.
     */
    #textReady = $state(true);
    /** Bumped by every `select`, so an answer for a document no longer selected is dropped. */
    #selection = 0;
    #saveInFlight = null;
    /** The last change to which schemas a document applies, which the next one waits for. */
    #schemaChanges = Promise.resolve();
    #unwatchReadOnly = null;

    #workspaceName;
    /** The schema selected in the rest of the app, which decides what opens first. */
    #preferredSchema;
    #requestOptions;
    /** Generated Turtle per schema. Cleared when a schema may have changed elsewhere. */
    #generated = new Map();

    constructor({
        workspaceName,
        preferredSchema = null,
        requestOptions = {},
    }) {
        this.#workspaceName = workspaceName;
        this.#preferredSchema = preferredSchema;
        this.#requestOptions = requestOptions;
    }

    get path() {
        return { workspaceName: this.#workspaceName };
    }

    get selected() {
        return (
            this.documents.find(document => document.id === this.selectedId) ??
            null
        );
    }

    /** The documents applying to a schema, in reading order. */
    documentsOf(schemaName) {
        return this.documents.filter(document =>
            document.schemas?.includes(schemaName),
        );
    }

    /**
     * The documents applying to no schema of this workspace.
     *
     * A document can name a schema that is not here: deleting a schema leaves its documents as they
     * are, and a schema imported under that name again finds them.
     */
    get unassigned() {
        const known = new Set(this.schemas.map(schema => schema.name));
        return this.documents.filter(
            document => !document.schemas?.some(name => known.has(name)),
        );
    }

    /** Whether what is open is generated shapes rather than a document. */
    get showingGenerated() {
        return isGenerated(this.selectedId);
    }

    /** The schema whose generated shapes are open, or null. */
    get generatedSchema() {
        return this.showingGenerated
            ? this.selectedId.slice(GENERATED_PREFIX.length)
            : null;
    }

    /** The schemas of what is being looked at, which is where a new document most likely goes. */
    get contextSchemas() {
        const known = name => this.schemas.some(schema => schema.name === name);
        const inView =
            this.generatedSchema ??
            (this.selected ? null : this.#preferredSchema);
        if (inView) {
            return known(inView) ? [inView] : [];
        }
        return (this.selected?.schemas ?? []).filter(known);
    }

    /**
     * Whether the editor must refuse edits.
     *
     * Two unrelated reasons, and the editor only needs the answer: the workspace may be read-only,
     * and the generated shapes are derived from the schema — editing them would mean editing a view.
     */
    get editorReadOnly() {
        return this.readOnly || this.showingGenerated || !this.#textReady;
    }

    /** Whether the buffer is the selected document's text, rather than loading or failed. */
    get textReady() {
        return this.#textReady;
    }

    get dirty() {
        return (
            this.selectedId !== null &&
            !this.showingGenerated &&
            this.#textReady &&
            this.text !== this.savedText
        );
    }

    // -------------------------------------------------------------------------
    // Loading and editing
    // -------------------------------------------------------------------------

    async load() {
        this.loading = true;
        try {
            await this.refreshReadOnly();
            await this.#refreshSchemas();
            const gone = await this.#refreshDocuments();
            if (this.error) {
                return;
            }
            await this.select(gone ? this.#firstToOpen() : this.selectedId);
        } finally {
            this.loading = false;
        }
    }

    /**
     * Reads everything again after something outside the workbench changed the schema — an undo,
     * an import, the workspace becoming read-only.
     *
     * An unsaved buffer is kept: the caller has either asked about it already, or changed nothing
     * the buffer depends on. A clean buffer is re-read, and only replaced when the stored text
     * actually differs, so the editor keeps its place otherwise.
     */
    async reload() {
        await this.refreshReadOnly();
        this.#generated.clear();
        await this.#refreshSchemas(true);
        const gone = await this.#refreshDocuments();
        if (this.error) {
            return;
        }
        if (gone) {
            await this.select(this.#firstToOpen());
        } else if (!this.dirty) {
            await this.#reread();
        }
    }

    /** Re-reads the workspace's read-only flag. An unknown answer counts as read-only. */
    async refreshReadOnly() {
        this.readOnly =
            (await workspaceStore.isReadOnly(this.#workspaceName)) !== false;
        return this.readOnly;
    }

    /**
     * Follows the workspace's read-only flag while the workbench is open.
     *
     * The flag can be flipped from the menu bar at any time, and only the workspace list knows;
     * reading it once on load left a workspace made read-only with an editable workbench.
     */
    watchReadOnly() {
        this.#unwatchReadOnly?.();
        this.#unwatchReadOnly =
            workspaceStore.subscribe?.(state => {
                if (!state?.data) {
                    return;
                }
                const workspace = state.data.find(
                    candidate => candidate.label === this.#workspaceName,
                );
                this.readOnly = workspace?.readOnly !== false;
            }) ?? null;
    }

    /** Stops everything that would otherwise outlive the workbench. */
    dispose() {
        this.#unwatchReadOnly?.();
        this.#unwatchReadOnly = null;
    }

    /**
     * Opens a document, or the generated shapes of a schema.
     *
     * One that is not there — a link to a document deleted since, a schema gone — opens what
     * would open on loading instead.
     */
    async select(documentId) {
        if (documentId !== null && !this.#isListed(documentId)) {
            documentId = this.#firstToOpen();
        }
        const selection = ++this.#selection;
        this.selectedId = documentId;
        this.detached = null;
        if (documentId === null) {
            this.#replaceBuffer("");
            this.#textReady = true;
            return;
        }
        if (isGenerated(documentId)) {
            await this.#showGenerated(selection);
            return;
        }
        this.#textReady = false;
        this.#replaceBuffer("");
        const text = await this.#readText(documentId);
        if (selection !== this.#selection) {
            return;
        }
        if (text === null) {
            // The buffer stays empty and locked: showing the previous document's text under this
            // one's name would let the next save write it here.
            this.error = "The document could not be loaded.";
            return;
        }
        this.#replaceBuffer(text);
        this.#textReady = true;
        this.error = null;
    }

    #isListed(documentId) {
        return isGenerated(documentId)
            ? this.schemas.some(
                  schema => generatedId(schema.name) === documentId,
              )
            : this.documents.some(document => document.id === documentId);
    }

    /** Throws the unsaved edits away, as an undoable edit back to what was last saved. */
    revert() {
        if (this.dirty) {
            this.text = this.savedText;
        }
    }

    /** Throws away the unsaved edits to a document that is gone, and opens what is left. */
    async discardDetached() {
        if (this.detached) {
            await this.select(this.#firstToOpen());
        }
    }

    /**
     * Replaces the buffer with the open document as it is stored now, throwing the unsaved edits
     * away. The answer to a save refused because the document changed elsewhere.
     *
     * @returns whether the stored text was read
     */
    async loadStored() {
        const documentId = this.selectedId;
        if (documentId === null || isGenerated(documentId)) {
            return false;
        }
        const selection = this.#selection;
        const text = await this.#readText(documentId);
        if (selection !== this.#selection || text === null) {
            return false;
        }
        this.#replaceBuffer(text);
        this.#textReady = true;
        return true;
    }

    /** The stored text of any of the documents, or null when it could not be read. */
    async textOf(documentId) {
        return this.#readText(documentId);
    }

    async #readText(documentId) {
        const { data, error } = await getShapesDocumentText({
            ...this.#requestOptions,
            path: { ...this.path, documentId },
        });
        return error ? null : (data ?? "");
    }

    async #reread() {
        const documentId = this.selectedId;
        if (documentId === null || isGenerated(documentId)) {
            await this.select(documentId);
            return;
        }
        const selection = this.#selection;
        const text = await this.#readText(documentId);
        if (selection !== this.#selection || this.dirty || text === null) {
            return;
        }
        if (text !== this.text || !this.#textReady) {
            this.#replaceBuffer(text);
        }
        this.#textReady = true;
    }

    #replaceBuffer(text) {
        this.text = text;
        this.savedText = text;
        this.bufferKey += 1;
    }

    /**
     * Writes the buffer back to the document.
     *
     * @param {{ overwrite?: boolean }} [options] `overwrite` writes even when the document has
     *     changed since it was read — the user's answer to a refused save, never the default.
     * @returns `{ saved, reason }` — on failure `reason` is what the server said went wrong, which
     *     for the usual case is the line and column where the Turtle stopped parsing. A message
     *     that only says "could not be saved" leaves the user with nowhere to look.
     */
    async save({ overwrite = false } = {}) {
        if (this.showingGenerated) {
            return {
                saved: false,
                reason:
                    "The generated shapes come from the schema itself. Change the classes, or" +
                    " copy what you need into a document of your own.",
            };
        }
        // A second Save while the first is on its way is not a failure: it waits, and only writes
        // again if something was typed in the meantime.
        if (this.#saveInFlight) {
            while (this.#saveInFlight) {
                await this.#saveInFlight;
            }
            if (!this.dirty) {
                return { saved: true, reason: null };
            }
        } else if (this.selectedId && this.#textReady && !this.dirty) {
            // Nothing to write. Sending it anyway would announce a save that changed nothing,
            // and on a large file make the user wait for one.
            return { saved: true, reason: null, unchanged: true };
        }
        const run = this.detached
            ? this.#writeDetached()
            : this.#write(overwrite);
        this.#saveInFlight = run;
        try {
            return await run;
        } finally {
            this.#saveInFlight = null;
        }
    }

    async #write(overwrite) {
        if (!this.selectedId || !this.#textReady) {
            return { saved: false, reason: null };
        }
        // What is written is decided when Save is pressed: the read-only check below waits for
        // the server, and another document can be opened meanwhile.
        const documentId = this.selectedId;
        const bufferKey = this.bufferKey;
        // What is sent is what becomes saved. Reading the buffer again after the request would
        // mark whatever was typed while it was on its way as saved, though it never was.
        const sent = this.text;
        const savedText = this.savedText;
        if (await this.refreshReadOnly()) {
            return { saved: false, reason: READ_ONLY };
        }
        this.saving = true;
        try {
            // Names the text this edit was made to, so a document saved meanwhile — in another
            // tab, or rewound by an undo while this buffer held edits — is not overwritten unseen.
            const expectedRevision = overwrite
                ? undefined
                : await revisionOf(storedForm(savedText));
            // A document that has been emptied still has to be sent as something, or the
            // endpoint's plain-string body arrives absent and Spring rejects the request.
            const { error, response } = await replaceShapesDocumentText({
                ...this.#requestOptions,
                path: { ...this.path, documentId },
                query: expectedRevision ? { expectedRevision } : undefined,
                body: sent === "" ? " " : sent,
                bodySerializer: null,
                headers: { "Content-Type": "text/plain" },
            });
            if (error) {
                if (response?.status === 404) {
                    // Gone since it was last listed: listing again keeps the edits as `detached`.
                    await this.#refreshDocuments();
                }
                return {
                    saved: false,
                    reason: reasonFrom(error),
                    stale: response?.status === 412,
                };
            }
            if (this.bufferKey === bufferKey) {
                this.savedText = sent;
            }
            await this.#relist();
            return { saved: true, reason: null };
        } finally {
            this.saving = false;
        }
    }

    /**
     * Saves the edits to a document that is gone as a new document, under the same name when that
     * is free and applying to the same schemas where they are still there.
     *
     * The buffer stays as it is, with its history: it now holds the new document's text.
     */
    async #writeDetached() {
        const { name, schemas } = this.detached;
        const bufferKey = this.bufferKey;
        const sent = this.text;
        if (await this.refreshReadOnly()) {
            return { saved: false, reason: READ_ONLY };
        }
        const known = new Set(this.schemas.map(schema => schema.name));
        this.saving = true;
        try {
            const { data, error } = await createShapesDocument({
                ...this.#requestOptions,
                path: this.path,
                query: {
                    name: uniqueDocumentName(
                        this.documents.map(document => document.name),
                        name,
                    ),
                    schema: schemas.filter(schema => known.has(schema)),
                },
                body: sent === "" ? " " : sent,
                bodySerializer: null,
                headers: { "Content-Type": "text/plain" },
            });
            if (error) {
                return { saved: false, reason: reasonFrom(error) };
            }
            if (this.bufferKey === bufferKey && this.detached) {
                this.#selection += 1;
                this.selectedId = data.id;
                this.detached = null;
                this.savedText = sent;
            }
            await this.#relist();
            return { saved: true, reason: null };
        } finally {
            this.saving = false;
        }
    }

    // -------------------------------------------------------------------------
    // The document list
    // -------------------------------------------------------------------------

    /**
     * Adds a document and opens it.
     *
     * @param text what the new document starts with — normally its prefixes, so the first thing
     *     written into it is a shape rather than boilerplate
     * @param schemas the names of the schemas it applies to
     */
    async create(name, text = "", schemas = []) {
        if (await this.refreshReadOnly()) {
            this.lastError = READ_ONLY;
            return null;
        }
        const { data, error } = await createShapesDocument({
            ...this.#requestOptions,
            path: this.path,
            query: { name, schema: schemas },
            body: text === "" ? " " : text,
            bodySerializer: null,
            headers: { "Content-Type": "text/plain" },
        });
        this.lastError = error ? (error.detail ?? null) : null;
        if (error) {
            return null;
        }
        await this.#refreshDocuments();
        await this.select(data.id);
        return data;
    }

    async importFile(file, name, schemas = []) {
        if (await this.refreshReadOnly()) {
            this.lastError = READ_ONLY;
            return null;
        }
        const { data, error } = await createShapesDocumentFromFile({
            ...this.#requestOptions,
            path: this.path,
            query: { ...(name ? { name } : {}), schema: schemas },
            body: { file },
        });
        this.lastError = error ? (error.detail ?? null) : null;
        if (error) {
            return null;
        }
        await this.#refreshDocuments();
        await this.select(data.id);
        return data;
    }

    async rename(documentId, name) {
        return this.#update(documentId, { name });
    }

    async setEnabled(documentId, enabled) {
        return this.#update(documentId, { enabled });
    }

    /**
     * Says which schemas a document applies to.
     *
     * Sent as one empty value for none: leaving the parameter out means "leave them as they are".
     */
    async setSchemas(documentId, schemas) {
        return this.#update(documentId, {
            schema: schemas.length > 0 ? schemas : [""],
        });
    }

    /**
     * Says whether a document applies to one schema, leaving the others as they are.
     *
     * One change after the other, each starting from the set the one before left: ticking two
     * schemas in quick succession would otherwise send the second with the set from before the
     * first, and undo it.
     */
    async setApplies(documentId, schemaName, applies) {
        const change = this.#schemaChanges.then(() => {
            const current =
                this.documents.find(document => document.id === documentId)
                    ?.schemas ?? [];
            const others = current.filter(name => name !== schemaName);
            return this.setSchemas(
                documentId,
                applies ? [...others, schemaName] : others,
            );
        });
        this.#schemaChanges = change.catch(() => {});
        return change;
    }

    /** The documents listed next to one another, under `schemaName` or else under no schema. */
    #peersIn(schemaName) {
        return schemaName ? this.documentsOf(schemaName) : this.unassigned;
    }

    /** Whether the document has a neighbour in that direction, where it is listed. */
    canMove(documentId, offset, schemaName = null) {
        const peers = this.#peersIn(schemaName);
        const from = peers.findIndex(document => document.id === documentId);
        return from >= 0 && peers[from + offset] !== undefined;
    }

    /**
     * Moves a document past its neighbour where it is listed.
     *
     * There is one reading order, over the workspace. The order matters for the combined export and
     * for reading, not for which constraints apply — every enabled document applies, and none
     * overrides another.
     */
    async move(documentId, offset, schemaName = null) {
        const peers = this.#peersIn(schemaName);
        const from = peers.findIndex(document => document.id === documentId);
        const neighbour = from < 0 ? undefined : peers[from + offset];
        if (!neighbour) {
            return false;
        }
        return this.#update(documentId, {
            order: this.documents.findIndex(
                document => document.id === neighbour.id,
            ),
        });
    }

    async remove(documentId) {
        if (await this.refreshReadOnly()) {
            return false;
        }
        const { error } = await deleteShapesDocument({
            ...this.#requestOptions,
            path: { ...this.path, documentId },
        });
        if (error) {
            return false;
        }
        // The user deleted it, so unsaved edits to it go with it rather than being kept.
        const wasOpen = this.selectedId === documentId;
        const gone = await this.#refreshDocuments();
        if (wasOpen || gone) {
            await this.select(this.#firstToOpen());
        }
        return true;
    }

    async #update(documentId, query) {
        if (await this.refreshReadOnly()) {
            this.lastError = READ_ONLY;
            return false;
        }
        const { error } = await updateShapesDocument({
            ...this.#requestOptions,
            path: { ...this.path, documentId },
            query,
        });
        this.lastError = error ? (error.detail ?? null) : null;
        if (error) {
            return false;
        }
        await this.#relist();
        return true;
    }

    async #refreshSchemas(force = false) {
        const graphs =
            (await graphStore.getGraphs(this.#workspaceName, force)) ?? [];
        const labelOf = graphLabeller(graphs);
        this.schemas = graphs
            .map(graph => ({ name: nameOf(graph), label: labelOf(graph) }))
            .sort(compareGraphs);
    }

    /**
     * Reads the documents again.
     *
     * @returns whether what was open is gone with nothing unsaved in the buffer, so something else
     *     has to be opened. A document gone while the buffer holds edits to it stays selected and
     *     becomes `detached`.
     */
    async #refreshDocuments() {
        const listed = this.selected ?? this.detached;
        const { data, error } = await listShapesDocuments({
            ...this.#requestOptions,
            path: this.path,
        });
        if (error) {
            this.error = "The constraints documents could not be listed.";
            return false;
        }
        this.documents = [...(data ?? [])].sort(
            (a, b) => (a.order ?? 0) - (b.order ?? 0),
        );
        this.documentsRevision += 1;
        this.error = null;
        const stillThere = this.showingGenerated
            ? this.schemas.some(schema => schema.name === this.generatedSchema)
            : this.documents.some(document => document.id === this.selectedId);
        if (stillThere) {
            this.detached = null;
            return false;
        }
        if (this.dirty) {
            this.detached = {
                id: this.selectedId,
                name: listed?.name ?? "constraints.ttl",
                schemas: [...(listed?.schemas ?? [])],
            };
            return false;
        }
        this.detached = null;
        return true;
    }

    /** Reads the documents again, and opens what is left when what was open is gone. */
    async #relist() {
        if (await this.#refreshDocuments()) {
            await this.select(this.#firstToOpen());
        }
    }

    /**
     * What opens when nothing is chosen: the selected schema's first document that holds anything,
     * and its generated shapes when it has no document — which is how every schema starts. With no
     * schema selected, the first document of the workspace.
     */
    #firstToOpen() {
        const holdsSomething = document => (document.tripleCount ?? 0) > 0;
        const preferred = this.schemas.find(
            schema => schema.name === this.#preferredSchema,
        );
        if (preferred) {
            const own = this.documentsOf(preferred.name);
            return (
                own.find(holdsSomething)?.id ??
                own[0]?.id ??
                generatedId(preferred.name)
            );
        }
        return (
            this.documents.find(holdsSomething)?.id ??
            this.documents[0]?.id ??
            (this.schemas[0] ? generatedId(this.schemas[0].name) : null)
        );
    }

    /**
     * Puts the shapes the schema implies in the editor, read-only.
     *
     * Fetched rather than derived here: the same generator answers the SHACL export, and a second
     * implementation in the browser would be a second thing to keep true.
     */
    async #showGenerated(selection) {
        const schemaName = this.generatedSchema;
        this.#textReady = true;
        if (this.#generated.has(schemaName)) {
            this.#replaceBuffer(this.#generated.get(schemaName));
            this.error = null;
            return;
        }
        this.generating = true;
        try {
            const { data, error } = await getGeneratedShaclasString({
                ...this.#requestOptions,
                path: { ...this.path, schemaName },
            });
            if (selection !== this.#selection) {
                return;
            }
            if (error) {
                this.error = "The generated shapes could not be read.";
                this.#replaceBuffer("");
                return;
            }
            this.#generated.set(schemaName, data ?? "");
            this.#replaceBuffer(data ?? "");
            this.error = null;
        } finally {
            this.generating = false;
        }
    }
}
