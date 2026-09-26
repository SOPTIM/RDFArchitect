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

/**
 * Completion, hover and go-to-definition for Turtle, answered from the workspace's CIM schema.
 *
 * These are the parts of the editor a language server would normally provide. There is no language
 * server because there would be nothing for the protocol to carry: the schema index lives in the
 * backend that already serves this app, and two plain endpoints reach it.
 *
 * Providers are registered once for the language, but a provider only answers for a model that has
 * been given a source — so the workbench's editor completes against its workspace, and the small
 * read-only editors in the class-editor dialogs stay quiet.
 */

import { extractSubjects } from "$lib/shacl/outline.js";
import {
    completionEntries,
    hoverMarkdown,
    parsePrefixes,
    termAt,
    tokenAt,
} from "$lib/shacl/turtleTerms.js";

const CLASS_SCHEME = "rdfa-class";

/**
 * How many go-to-definition placeholder models to keep before disposing the oldest.
 *
 * Each Ctrl+hover on a new term mints one, and nothing else ever disposes them, so a long editing
 * session would leak one model per term followed. Only the model being previewed right now is
 * actually needed; a couple of spares cover a preview that is still on screen when the next one is
 * resolved.
 */
const MAX_PREVIEW_MODELS = 4;

/** The placeholder models minted above, oldest first. */
const previewModels = [];

/** Model → the schema it should be completed against. Weak, so a closed editor is forgotten. */
const sources = new WeakMap();

/** Model → its prefixes, remembered per content version rather than re-scanned per keystroke. */
const prefixCache = new WeakMap();

/** A directive line, where a `:` belongs to a prefix being declared rather than to a term. */
const DIRECTIVE = /^\s*(?:@prefix|@base|prefix|base)\b/i;

/** Where following a term should go. Set by the workbench; without it, definitions do nothing. */
let openClass = null;

/** Keeps `model` and disposes whichever placeholder has gone longest without being needed. */
function trackPreviewModel(model) {
    previewModels.push(model);
    while (previewModels.length > MAX_PREVIEW_MODELS) {
        previewModels.shift()?.dispose();
    }
}

export function attachTermSource(model, source) {
    sources.set(model, source);
    source?.load();
}

export function detachTermSource(model) {
    sources.delete(model);
}

/**
 * Registers what happens when the user follows a term to the class it belongs to.
 *
 * The handler is given `(graphUri, classUUID, packageUUID)`. The package is part of the
 * destination, not decoration: opening the class editor without it leaves the diagram showing
 * wherever the user last was, with the class they asked for nowhere in sight.
 */
export function onOpenClass(handler) {
    openClass = handler;
}

function sourceFor(model) {
    return model ? (sources.get(model) ?? null) : null;
}

function prefixesOf(model) {
    const cached = prefixCache.get(model);
    if (cached && cached.version === model.getVersionId()) {
        return cached.prefixes;
    }
    const prefixes = parsePrefixes(model.getValue());
    prefixCache.set(model, { version: model.getVersionId(), prefixes });
    return prefixes;
}

/**
 * The line this document defines `iri` on, or null when it does not.
 *
 * Checked before the schema is asked: a constraints file refers to its own shapes and rules far
 * more often than to anything the schema could explain, and the schema has never heard of them.
 */
export function localDefinitionLine(text, iri, prefixes) {
    const lines = (text ?? "").split("\n");
    for (const subject of extractSubjects(text)) {
        if (termAt(lines[subject.line - 1], 1, prefixes)?.iri === iri) {
            return subject.line;
        }
    }
    return null;
}

/**
 * Where the text just before the cursor stands: in `code`, a `comment`, a short `string`, a
 * triple-quoted `longString` or an `iri`.
 *
 * Only in code does a `:` start a term. The SPARQL of a `sh:select` is a long string that uses
 * the document's prefixes, so it counts as a place to complete in as well.
 */
export function lexicalContext(textBefore) {
    const text = textBefore ?? "";
    let state = "code";
    let quote = "";
    let index = 0;
    while (index < text.length) {
        const char = text[index];
        if (state === "code") {
            const triple = text.slice(index, index + 3);
            if (char === "#") {
                state = "comment";
            } else if (triple === '"""' || triple === "'''") {
                quote = triple;
                state = "longString";
                index += 3;
                continue;
            } else if (char === '"' || char === "'") {
                quote = char;
                state = "string";
            } else if (char === "<") {
                state = "iri";
            }
        } else if (state === "comment") {
            if (char === "\n") {
                state = "code";
            }
        } else if (state === "iri") {
            if (char === ">" || /\s/.test(char)) {
                state = "code";
            }
        } else if (char === "\\") {
            index += 2;
            continue;
        } else if (state === "string") {
            if (char === quote || char === "\n") {
                state = "code";
            }
        } else if (text.startsWith(quote, index)) {
            state = "code";
            index += 3;
            continue;
        }
        index += 1;
    }
    return state;
}

/** Whether a `:` typed at this position is part of a term being written. */
function writesTermAt(model, position) {
    if (DIRECTIVE.test(model.getLineContent(position.lineNumber))) {
        return false;
    }
    const context = lexicalContext(
        model.getValueInRange({
            startLineNumber: 1,
            startColumn: 1,
            endLineNumber: position.lineNumber,
            endColumn: position.column,
        }),
    );
    return context === "code" || context === "longString";
}

/**
 * A term's detail, or null when the answer describes nothing.
 *
 * "Not a schema term" arrives as an empty body (204) or as an error (404). Either way there is
 * nothing to show, and an empty object would otherwise be rendered as a hover about `undefined`.
 */
function describedTerm(detail) {
    return detail?.iri ? detail : null;
}

export function registerTurtleLanguageFeatures(monaco, languageId) {
    const kinds = {
        CLASS: monaco.languages.CompletionItemKind.Class,
        PROPERTY: monaco.languages.CompletionItemKind.Property,
        ENUM_MEMBER: monaco.languages.CompletionItemKind.EnumMember,
    };

    monaco.languages.registerCompletionItemProvider(languageId, {
        // ":" is what turns "cim" into a term being written; the rest is Monaco's own filtering.
        triggerCharacters: [":"],
        async provideCompletionItems(model, position, context) {
            const source = sourceFor(model);
            if (!source) {
                return { suggestions: [] };
            }
            // Asked for explicitly, completion answers anywhere; but a `:` typed into a comment,
            // a string, an IRI or a prefix declaration is not a term being written.
            if (
                context?.triggerKind ===
                    monaco.languages.CompletionTriggerKind?.TriggerCharacter &&
                !writesTermAt(model, position)
            ) {
                return { suggestions: [] };
            }
            await source.load();

            const line = model.getLineContent(position.lineNumber);
            const token = tokenAt(line, position.column);
            const range = {
                startLineNumber: position.lineNumber,
                endLineNumber: position.lineNumber,
                startColumn: token?.startColumn ?? position.column,
                endColumn: token?.endColumn ?? position.column,
            };
            return {
                suggestions: completionEntries(
                    source.completionTerms ?? source.terms,
                    prefixesOf(model),
                ).map(entry => ({
                    label: entry.label,
                    insertText: entry.insertText,
                    detail: entry.detail,
                    sortText: entry.sortText,
                    kind:
                        kinds[entry.kind] ??
                        monaco.languages.CompletionItemKind.Value,
                    range,
                })),
            };
        },
    });

    monaco.languages.registerHoverProvider(languageId, {
        async provideHover(model, position) {
            const source = sourceFor(model);
            if (!source) {
                return null;
            }
            const prefixes = prefixesOf(model);
            const term = termAt(
                model.getLineContent(position.lineNumber),
                position.column,
                prefixes,
            );
            if (!term) {
                return null;
            }
            const markdown = hoverMarkdown(
                describedTerm(await source.detailOf(term.iri)),
                prefixes,
            );
            if (!markdown) {
                return null;
            }
            return {
                contents: [{ value: markdown }],
                range: {
                    startLineNumber: position.lineNumber,
                    endLineNumber: position.lineNumber,
                    startColumn: term.startColumn,
                    endColumn: term.endColumn,
                },
            };
        },
    });

    monaco.languages.registerDefinitionProvider(languageId, {
        async provideDefinition(model, position) {
            const source = sourceFor(model);
            if (!source) {
                return null;
            }
            const prefixes = prefixesOf(model);
            const term = termAt(
                model.getLineContent(position.lineNumber),
                position.column,
                prefixes,
            );
            if (!term) {
                return null;
            }
            const local = localDefinitionLine(
                model.getValue(),
                term.iri,
                prefixes,
            );
            // On the definition itself there is nowhere to go in the document; the schema may
            // still know the term, as it does when a class is described where it is declared.
            if (local !== null && local !== position.lineNumber) {
                return {
                    uri: model.uri,
                    range: {
                        startLineNumber: local,
                        endLineNumber: local,
                        startColumn: 1,
                        endColumn: 1,
                    },
                };
            }
            if (!openClass) {
                return null;
            }
            const detail = describedTerm(await source.detailOf(term.iri));
            if (!detail?.classUUID) {
                return null;
            }
            // Monaco wants a location to open. The uri is a handle rather than a document: the
            // opener below recognises the scheme and navigates the app instead of opening a model.
            // The package rides along in the query because it is what says which diagram to put on
            // screen, and a class that belongs to none simply leaves it empty.
            const uri = monaco.Uri.from({
                scheme: CLASS_SCHEME,
                path: `/${encodeURIComponent(detail.graphUri)}/${detail.classUUID}`,
                query: detail.packageUUID ?? "",
            });
            // Ctrl+hover asks Monaco's own preview widget to resolve this uri to a model before the
            // opener above ever runs, so one has to exist even though nothing will display it.
            if (!monaco.editor.getModel(uri)) {
                trackPreviewModel(
                    monaco.editor.createModel(
                        detail.label ?? term.text,
                        undefined,
                        uri,
                    ),
                );
            }
            return {
                uri,
                range: {
                    startLineNumber: 1,
                    endLineNumber: 1,
                    startColumn: 1,
                    endColumn: 1,
                },
            };
        },
    });

    monaco.editor.registerEditorOpener({
        openCodeEditor(_source, resource) {
            if (resource.scheme !== CLASS_SCHEME || !openClass) {
                return false;
            }
            const [graphUri, classUUID] = resource.path
                .replace(/^\//, "")
                .split("/");
            openClass(
                decodeURIComponent(graphUri),
                classUUID,
                resource.query || null,
            );
            return true;
        },
    });
}
