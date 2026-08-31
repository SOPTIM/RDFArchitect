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

import {
    completionEntries,
    hoverMarkdown,
    parsePrefixes,
    termAt,
    tokenAt,
} from "$lib/shacl/turtleTerms.js";

const CLASS_SCHEME = "rdfa-class";

/** Model → the schema it should be completed against. Weak, so a closed editor is forgotten. */
const sources = new WeakMap();

/** Model → its prefixes, remembered per content version rather than re-scanned per keystroke. */
const prefixCache = new WeakMap();

/** Where following a term should go. Set by the workbench; without it, definitions do nothing. */
let openClass = null;

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

export function registerTurtleLanguageFeatures(monaco, languageId) {
    const kinds = {
        CLASS: monaco.languages.CompletionItemKind.Class,
        PROPERTY: monaco.languages.CompletionItemKind.Property,
        ENUM_MEMBER: monaco.languages.CompletionItemKind.EnumMember,
    };

    monaco.languages.registerCompletionItemProvider(languageId, {
        // ":" is what turns "cim" into a term being written; the rest is Monaco's own filtering.
        triggerCharacters: [":"],
        async provideCompletionItems(model, position) {
            const source = sourceFor(model);
            if (!source) {
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
                await source.detailOf(term.iri),
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
            if (!source || !openClass) {
                return null;
            }
            const term = termAt(
                model.getLineContent(position.lineNumber),
                position.column,
                prefixesOf(model),
            );
            if (!term) {
                return null;
            }
            const detail = await source.detailOf(term.iri);
            if (!detail?.classUUID) {
                return null;
            }
            // Monaco wants a location to open. The uri is a handle rather than a document: the
            // opener below recognises the scheme and navigates the app instead of opening a model.
            // The package rides along in the query because it is what says which diagram to put on
            // screen, and a class that belongs to none simply leaves it empty.
            return {
                uri: monaco.Uri.from({
                    scheme: CLASS_SCHEME,
                    path: `/${encodeURIComponent(detail.graphUri)}/${detail.classUUID}`,
                    query: detail.packageUUID ?? "",
                }),
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
