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
import { workspaceStore } from "$lib/stores/workspaceStore.ts";

/** Said instead of a server error when the workspace is the reason nothing can be written. */
const READ_ONLY =
    "This workspace is read-only, so its constraints cannot be changed.";

/**
 * The id of the shapes RDFArchitect derives from the schema itself.
 *
 * Not a document: nothing stores it, and it is rebuilt from the classes every time it is asked
 * for. It sits in the list anyway because the list is where someone looks for "what constrains
 * this schema", and the generated shapes are most of the answer — reading them beside an imported
 * file is what makes a conformance report legible. A plain string, so it can never collide with a
 * document's UUID.
 */
export const GENERATED_ID = "generated";

/** What the generated entry is called in the list. */
export const GENERATED_NAME = "Generated shapes";

const UNICODE_SPACE = /[\p{Zs}\u2028\u2029]/u;

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
 * The document the workbench opens when none is chosen: the first that holds anything, and the
 * generated shapes when the graph has no document at all — which is how every graph starts.
 */
function firstToOpen(documents) {
    return (
        documents.find(document => (document.tripleCount ?? 0) > 0)?.id ??
        documents[0]?.id ??
        GENERATED_ID
    );
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
 * One graph's constraints documents and the text being edited.
 *
 * All of the workbench's behaviour lives here rather than in the page so it can be tested without
 * a DOM: the components below it render this and call into it, and hold no state of their own.
 */
export class ShapesWorkbench {
    /** @type {import("$lib/api/generated").ShapesDocumentInfo[]} */
    documents = $state([]);
    selectedId = $state(null);
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
    #unwatchReadOnly = null;

    #datasetName;
    #graphUri;
    #requestOptions;
    /** The generated Turtle, fetched once. Cleared when the schema may have changed elsewhere. */
    #generated = null;

    constructor({ datasetName, graphUri, requestOptions = {} }) {
        this.#datasetName = datasetName;
        this.#graphUri = graphUri;
        this.#requestOptions = requestOptions;
    }

    get path() {
        return { datasetName: this.#datasetName, graphURI: this.#graphUri };
    }

    get selected() {
        return (
            this.documents.find(document => document.id === this.selectedId) ??
            null
        );
    }

    /** The document list as the UI shows it: the generated shapes, then the graph's documents. */
    get entries() {
        return [
            {
                id: GENERATED_ID,
                name: GENERATED_NAME,
                generated: true,
                enabled: true,
            },
            ...this.documents,
        ];
    }

    /** Whether what is open is the generated shapes rather than a document. */
    get showingGenerated() {
        return this.selectedId === GENERATED_ID;
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
            await this.#refreshDocuments();
            if (this.error) {
                return;
            }
            await this.select(this.selectedId);
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
        this.#generated = null;
        await this.#refreshDocuments();
        if (this.error) {
            return;
        }
        if (!this.dirty) {
            await this.#reread();
        }
    }

    /** Re-reads the workspace's read-only flag. An unknown answer counts as read-only. */
    async refreshReadOnly() {
        this.readOnly =
            (await workspaceStore.isReadOnly(this.#datasetName)) !== false;
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
                    candidate => candidate.label === this.#datasetName,
                );
                this.readOnly = workspace?.readOnly !== false;
            }) ?? null;
    }

    /** Stops everything that would otherwise outlive the workbench. */
    dispose() {
        this.#unwatchReadOnly?.();
        this.#unwatchReadOnly = null;
    }

    async select(documentId) {
        const selection = ++this.#selection;
        this.selectedId = documentId;
        if (documentId === null) {
            this.#replaceBuffer("");
            this.#textReady = true;
            return;
        }
        if (documentId === GENERATED_ID) {
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
    }

    /** Throws the unsaved edits away, as an undoable edit back to what was last saved. */
    revert() {
        if (this.dirty) {
            this.text = this.savedText;
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
        if (documentId === null || documentId === GENERATED_ID) {
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

    /** The stored text of any of the graph's documents, or null when it could not be read. */
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
        if (documentId === null || documentId === GENERATED_ID) {
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
        const run = this.#write(overwrite);
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
        if (await this.refreshReadOnly()) {
            return { saved: false, reason: READ_ONLY };
        }
        this.saving = true;
        try {
            const documentId = this.selectedId;
            const bufferKey = this.bufferKey;
            // What is sent is what becomes saved. Reading the buffer again after the request would
            // mark whatever was typed while it was on its way as saved, though it never was.
            const sent = this.text;
            // Names the text this edit was made to, so a document saved meanwhile — in another
            // tab, or rewound by an undo while this buffer held edits — is not overwritten unseen.
            const expectedRevision = overwrite
                ? undefined
                : await revisionOf(storedForm(this.savedText));
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
                return {
                    saved: false,
                    reason: reasonFrom(error),
                    stale: response?.status === 412,
                };
            }
            if (this.bufferKey === bufferKey) {
                this.savedText = sent;
            }
            await this.#refreshDocuments();
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
     */
    async create(name, text = "") {
        if (await this.refreshReadOnly()) {
            return null;
        }
        const { data, error } = await createShapesDocument({
            ...this.#requestOptions,
            path: this.path,
            query: { name },
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

    async importFile(file, name) {
        if (await this.refreshReadOnly()) {
            return null;
        }
        const { data, error } = await createShapesDocumentFromFile({
            ...this.#requestOptions,
            path: this.path,
            query: name ? { name } : undefined,
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
     * Moves a document by `offset` places in the list.
     *
     * The order matters for the combined export and for reading, not for which constraints
     * apply — every enabled document applies, and none overrides another.
     */
    async move(documentId, offset) {
        const from = this.documents.findIndex(
            document => document.id === documentId,
        );
        const to = from + offset;
        if (from < 0 || to < 0 || to >= this.documents.length) {
            return false;
        }
        return this.#update(documentId, { order: to });
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
        // Asked before the relisting, which moves the selection off a document that is gone: asked
        // after, this is always false, the editor keeps the deleted document's text, and the next
        // save writes that text into whichever document the list moved to.
        const wasOpen = this.selectedId === documentId;
        await this.#refreshDocuments();
        if (wasOpen) {
            await this.select(this.selectedId);
        }
        return true;
    }

    async #update(documentId, query) {
        if (await this.refreshReadOnly()) {
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
        await this.#refreshDocuments();
        return true;
    }

    async #refreshDocuments() {
        const { data, error } = await listShapesDocuments({
            ...this.#requestOptions,
            path: this.path,
        });
        if (error) {
            this.error = "The constraints documents could not be listed.";
            return;
        }
        this.documents = [...(data ?? [])].sort(
            (a, b) => (a.order ?? 0) - (b.order ?? 0),
        );
        this.error = null;
        if (
            !this.showingGenerated &&
            !this.documents.some(document => document.id === this.selectedId)
        ) {
            this.selectedId = firstToOpen(this.documents);
        }
    }

    /**
     * Puts the shapes the schema implies in the editor, read-only.
     *
     * Fetched rather than derived here: the same generator answers the export and the conformance
     * check, and a second implementation in the browser would be a second thing to keep true.
     */
    async #showGenerated(selection) {
        this.#textReady = true;
        if (this.#generated !== null) {
            this.#replaceBuffer(this.#generated);
            return;
        }
        this.generating = true;
        try {
            const { data, error } = await getGeneratedShaclasString({
                ...this.#requestOptions,
                path: this.path,
            });
            if (selection !== this.#selection) {
                return;
            }
            if (error) {
                this.error = "The generated shapes could not be read.";
                this.#replaceBuffer("");
                return;
            }
            this.#generated = data ?? "";
            this.#replaceBuffer(this.#generated);
            this.error = null;
        } finally {
            this.generating = false;
        }
    }
}
