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
    getShapesDocumentText,
    listShapesDocuments,
    replaceShapesDocumentText,
    updateShapesDocument,
    validateShapes,
    validateShapesText,
} from "$lib/api/generated/index.ts";

/**
 * How long typing has to pause before the buffer is sent for validation.
 *
 * Long enough that a normal typing burst produces one request rather than one per keystroke,
 * short enough that the squiggles feel like they belong to what is on screen. Phase 4 replaces
 * this round trip with a language server, at which point the delay can go.
 */
export const VALIDATION_DEBOUNCE_MS = 500;

/** An empty per-document result, so a badge has something to render before the first report. */
const NO_FINDINGS = {
    valid: true,
    errorCount: 0,
    warningCount: 0,
    infoCount: 0,
    findings: [],
};

/**
 * One graph's constraints documents, the text being edited, and what validation says about them.
 *
 * All of the workbench's behaviour lives here rather than in the page so it can be tested without
 * a DOM: the components below it render this and call into it, and hold no state of their own.
 *
 * Two validations are in play and they answer different questions. `validateAll` asks about what
 * is *stored*, which is what the document list's badges are about; `validateBuffer` asks about the
 * text in the editor, which is what the squiggles are about. The buffer result is preferred for
 * the open document whenever it is current, so the editor never shows findings for a version of
 * the text the user has already changed.
 */
export class ShapesWorkbench {
    /** @type {import("$lib/api/generated").ShapesDocumentInfo[]} */
    documents = $state([]);
    selectedId = $state(null);
    text = $state("");
    savedText = $state("");
    /** @type {import("$lib/api/generated").ShapesValidationReport | null} */
    report = $state(null);
    /** @type {import("$lib/api/generated").ShapesValidationReport | null} */
    bufferReport = $state(null);
    loading = $state(false);
    saving = $state(false);
    validating = $state(false);
    /** Last failure, shown in place of the editor rather than swallowed. */
    error = $state(null);

    /** The text and document the buffer report describes, so a stale one is not displayed. */
    #validated = $state({ id: null, text: null });

    #datasetName;
    #graphUri;
    #requestOptions;
    #timer = null;

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

    get dirty() {
        return this.selectedId !== null && this.text !== this.savedText;
    }

    /** The profiles the shapes were checked against, for the inspector. */
    get profiles() {
        return this.report?.profiles ?? [];
    }

    /**
     * Validation result per document, with the open document's replaced by the buffer's whenever
     * that is current. This is the single list the badges, the problems panel and the editor
     * markers all read, so they cannot disagree with each other.
     */
    get results() {
        return this.documents.map(document => ({
            documentId: document.id,
            documentName: document.name,
            ...this.resultFor(document.id),
        }));
    }

    resultFor(documentId) {
        if (documentId === this.selectedId && this.#bufferIsCurrent()) {
            return this.bufferReport?.documents?.[0] ?? NO_FINDINGS;
        }
        return (
            this.report?.documents?.find(
                result => result.documentId === documentId,
            ) ?? NO_FINDINGS
        );
    }

    /** Findings for the open document — what the editor shows as squiggles. */
    get findings() {
        return this.resultFor(this.selectedId)?.findings ?? [];
    }

    get totals() {
        return this.results.reduce(
            (totals, result) => ({
                errorCount: totals.errorCount + (result.errorCount ?? 0),
                warningCount: totals.warningCount + (result.warningCount ?? 0),
                infoCount: totals.infoCount + (result.infoCount ?? 0),
            }),
            { errorCount: 0, warningCount: 0, infoCount: 0 },
        );
    }

    #bufferIsCurrent() {
        return (
            this.#validated.id === this.selectedId &&
            this.#validated.text === this.text
        );
    }

    // -------------------------------------------------------------------------
    // Loading and editing
    // -------------------------------------------------------------------------

    async load() {
        this.loading = true;
        try {
            await this.#refreshDocuments();
            // Validating on top of a failed listing would replace the message that says what
            // actually went wrong with a vaguer one about validation.
            if (this.error) {
                return;
            }
            await this.select(this.selectedId);
            await this.validateAll();
        } finally {
            this.loading = false;
        }
    }

    async select(documentId) {
        this.cancelPendingValidation();
        this.selectedId = documentId;
        this.bufferReport = null;
        this.#validated = { id: null, text: null };
        if (documentId === null) {
            this.text = "";
            this.savedText = "";
            return;
        }
        const { data, error } = await getShapesDocumentText({
            ...this.#requestOptions,
            path: { ...this.path, documentId },
        });
        if (error) {
            this.error = "The document could not be loaded.";
            return;
        }
        this.text = data ?? "";
        this.savedText = this.text;
    }

    async save() {
        if (!this.selectedId || this.saving) {
            return false;
        }
        this.saving = true;
        try {
            // A document that has been emptied still has to be sent as something, or the
            // endpoint's plain-string body arrives absent and Spring rejects the request.
            const { error } = await replaceShapesDocumentText({
                ...this.#requestOptions,
                path: { ...this.path, documentId: this.selectedId },
                body: this.text === "" ? " " : this.text,
                bodySerializer: null,
                headers: { "Content-Type": "text/plain" },
            });
            if (error) {
                return false;
            }
            this.savedText = this.text;
            await this.#refreshDocuments();
            await this.validateAll();
            return true;
        } finally {
            this.saving = false;
        }
    }

    // -------------------------------------------------------------------------
    // The document list
    // -------------------------------------------------------------------------

    async create(name) {
        const { data, error } = await createShapesDocument({
            ...this.#requestOptions,
            path: this.path,
            query: { name },
            body: " ",
            bodySerializer: null,
            headers: { "Content-Type": "text/plain" },
        });
        if (error) {
            return null;
        }
        await this.#refreshDocuments();
        await this.select(data.id);
        await this.validateAll();
        return data;
    }

    async importFile(file, name) {
        const { data, error } = await createShapesDocumentFromFile({
            ...this.#requestOptions,
            path: this.path,
            query: name ? { name } : undefined,
            body: { file },
        });
        if (error) {
            return null;
        }
        await this.#refreshDocuments();
        await this.select(data.id);
        await this.validateAll();
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
        const { error } = await deleteShapesDocument({
            ...this.#requestOptions,
            path: { ...this.path, documentId },
        });
        if (error) {
            return false;
        }
        await this.#refreshDocuments();
        if (this.selectedId === documentId) {
            await this.select(this.documents[0]?.id ?? null);
        }
        await this.validateAll();
        return true;
    }

    async #update(documentId, query) {
        const { error } = await updateShapesDocument({
            ...this.#requestOptions,
            path: { ...this.path, documentId },
            query,
        });
        if (error) {
            return false;
        }
        await this.#refreshDocuments();
        await this.validateAll();
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
        if (!this.documents.some(document => document.id === this.selectedId)) {
            this.selectedId = this.documents[0]?.id ?? null;
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    async validateAll() {
        const { data, error } = await validateShapes({
            ...this.#requestOptions,
            path: this.path,
        });
        if (error) {
            this.error = "The constraints could not be validated.";
            return null;
        }
        this.report = data;
        return data;
    }

    /**
     * Validates the editor's text.
     *
     * The open document's id goes with it so the report can include contradictions with the
     * graph's *other* documents while leaving the document's own stored copy out of the
     * comparison — otherwise every shape the user has not renamed reads as defined twice.
     */
    async validateBuffer() {
        if (this.selectedId === null) {
            return null;
        }
        const documentId = this.selectedId;
        const text = this.text;
        this.validating = true;
        try {
            const { data, error } = await validateShapesText({
                ...this.#requestOptions,
                path: this.path,
                query: { name: this.selected?.name ?? "unsaved", documentId },
                body: text === "" ? " " : text,
                bodySerializer: null,
                headers: { "Content-Type": "text/plain" },
            });
            if (error) {
                return null;
            }
            // Two runs can be in flight after a fast edit; the one that started last wins, and an
            // older answer is dropped rather than allowed to overwrite it.
            if (documentId === this.selectedId && text === this.text) {
                this.bufferReport = data;
                this.#validated = { id: documentId, text };
            }
            return data;
        } finally {
            this.validating = false;
        }
    }

    /** Validates the buffer once typing has paused. Safe to call on every keystroke. */
    scheduleValidation(delay = VALIDATION_DEBOUNCE_MS) {
        this.cancelPendingValidation();
        this.#timer = setTimeout(() => {
            this.#timer = null;
            this.validateBuffer();
        }, delay);
    }

    cancelPendingValidation() {
        if (this.#timer !== null) {
            clearTimeout(this.#timer);
            this.#timer = null;
        }
    }
}
