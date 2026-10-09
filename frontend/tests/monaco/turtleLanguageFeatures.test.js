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

import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import {
    attachTermSource,
    lexicalContext,
    localDefinitionLine,
    onOpenClass,
    registerTurtleLanguageFeatures,
} from "$lib/monaco/turtleLanguageFeatures.js";

const CIM = "http://iec.ch/TC57/CIM100#";
const GRAPH = "http://ex.org/EQ";
const CLASS_UUID = "11111111-2222-3333-4444-555555555555";
const PACKAGE_UUID = "99999999-8888-7777-6666-555555555555";

const TURTLE = `@prefix cim: <${CIM}> .
cim:ACLineSegment a cim:Class .
`;

/** The column of a term on line 2 of TURTLE. */
const ON_THE_CLASS = { lineNumber: 2, column: 6 };

let registered;

/** Just enough of the Monaco namespace for the providers to be registered and then called. */
function fakeMonaco() {
    registered = { models: new Map() };
    return {
        languages: {
            CompletionTriggerKind: { Invoke: 0, TriggerCharacter: 1 },
            CompletionItemKind: {
                Class: 1,
                Property: 2,
                EnumMember: 3,
                Value: 4,
            },
            registerCompletionItemProvider: (language, provider) => {
                registered.completion = provider;
            },
            registerHoverProvider: (language, provider) => {
                registered.hover = provider;
            },
            registerDefinitionProvider: (language, provider) => {
                registered.definition = provider;
            },
        },
        editor: {
            registerEditorOpener: opener => {
                registered.opener = opener;
            },
            // Go-to-definition backs its target uri with a model, because Monaco's own
            // Ctrl+hover preview resolves the uri before the opener ever runs.
            getModel: uri => registered.models.get(String(uri.path)) ?? null,
            createModel: (value, language, uri) => {
                const model = { value, language, uri, disposed: false };
                model.dispose = () => (model.disposed = true);
                registered.models.set(String(uri.path), model);
                return model;
            },
        },
        Uri: { from: parts => ({ ...parts }) },
    };
}

/** A model over one document. Only what the providers actually read. */
function fakeModel(text = TURTLE) {
    const lines = text.split("\n");
    return {
        uri: { path: "/document.ttl" },
        getValue: () => text,
        getValueInRange: range =>
            [
                ...lines.slice(
                    range.startLineNumber - 1,
                    range.endLineNumber - 1,
                ),
                lines[range.endLineNumber - 1].slice(0, range.endColumn - 1),
            ].join("\n"),
        getVersionId: () => 1,
        getLineContent: lineNumber => lines[lineNumber - 1] ?? "",
    };
}

function fakeSource(detail, terms = []) {
    return {
        terms,
        completionTerms: terms,
        load: vi.fn().mockResolvedValue(undefined),
        detailOf: vi.fn().mockResolvedValue(detail),
    };
}

beforeEach(() => {
    registerTurtleLanguageFeatures(fakeMonaco(), "turtle");
});

afterEach(() => {
    onOpenClass(null);
});

describe("following a term", () => {
    test("carries the package as well as the class", async () => {
        const model = fakeModel();
        attachTermSource(
            model,
            fakeSource({
                iri: `${CIM}ACLineSegment`,
                graphUri: GRAPH,
                classUUID: CLASS_UUID,
                packageUUID: PACKAGE_UUID,
            }),
        );
        onOpenClass(vi.fn());

        const definition = await registered.definition.provideDefinition(
            model,
            ON_THE_CLASS,
        );

        expect(definition.uri.scheme).toBe("rdfa-class");
        expect(definition.uri.path).toContain(CLASS_UUID);
        expect(definition.uri.query).toBe(PACKAGE_UUID);
    });

    test("hands the graph, the class and the package to the handler", async () => {
        const model = fakeModel();
        attachTermSource(
            model,
            fakeSource({
                iri: `${CIM}ACLineSegment`,
                graphUri: GRAPH,
                classUUID: CLASS_UUID,
                packageUUID: PACKAGE_UUID,
            }),
        );
        const handler = vi.fn();
        onOpenClass(handler);

        const definition = await registered.definition.provideDefinition(
            model,
            ON_THE_CLASS,
        );
        const opened = registered.opener.openCodeEditor(null, definition.uri);

        // Without the package the class editor fills but the diagram stays wherever it was, so the
        // class the user asked for is nowhere on screen.
        expect(opened).toBe(true);
        expect(handler).toHaveBeenCalledWith(GRAPH, CLASS_UUID, PACKAGE_UUID);
    });

    test("a class in no package is still followable", async () => {
        const model = fakeModel();
        attachTermSource(
            model,
            fakeSource({
                iri: `${CIM}ACLineSegment`,
                graphUri: GRAPH,
                classUUID: CLASS_UUID,
                packageUUID: null,
            }),
        );
        const handler = vi.fn();
        onOpenClass(handler);

        const definition = await registered.definition.provideDefinition(
            model,
            ON_THE_CLASS,
        );
        registered.opener.openCodeEditor(null, definition.uri);

        expect(handler).toHaveBeenCalledWith(GRAPH, CLASS_UUID, null);
    });

    test("a term the workspace holds no class for is not followable", async () => {
        const model = fakeModel();
        attachTermSource(model, fakeSource({ iri: `${CIM}ACLineSegment` }));
        onOpenClass(vi.fn());

        expect(
            await registered.definition.provideDefinition(model, ON_THE_CLASS),
        ).toBeNull();
    });

    test("the opener leaves other schemes alone", () => {
        onOpenClass(vi.fn());

        expect(
            registered.opener.openCodeEditor(null, {
                scheme: "file",
                path: "/somewhere",
            }),
        ).toBe(false);
    });
});

describe("completion", () => {
    test("offers the vocabularies a file is written in, not only the schema", async () => {
        const model = fakeModel();
        const source = fakeSource(null, []);
        source.completionTerms = [
            {
                kind: "PROPERTY",
                iri: "http://www.w3.org/ns/shacl#minCount",
                namespace: "http://www.w3.org/ns/shacl#",
                localName: "minCount",
            },
        ];
        attachTermSource(model, source);

        const { suggestions } =
            await registered.completion.provideCompletionItems(
                model,
                ON_THE_CLASS,
            );

        expect(suggestions.map(entry => entry.label)).toContain(
            "<http://www.w3.org/ns/shacl#minCount>",
        );
    });

    test("stays quiet for a model with no schema behind it", async () => {
        const { suggestions } =
            await registered.completion.provideCompletionItems(
                fakeModel(),
                ON_THE_CLASS,
            );

        expect(suggestions).toEqual([]);
    });
});

describe("following a shape the document defines itself", () => {
    const RULES = `@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix ex: <http://example.org/shapes#> .

ex:Line a sh:NodeShape ;
    sh:property ex:Line.length-cardinality .

ex:Line.length-cardinality a sh:PropertyShape ;
    sh:minCount 1 .
`;

    test("jumps to it without asking the schema", async () => {
        const model = fakeModel(RULES);
        const source = fakeSource(null);
        attachTermSource(model, source);

        const definition = await registered.definition.provideDefinition(
            model,
            { lineNumber: 5, column: 22 },
        );

        expect(definition.uri).toBe(model.uri);
        expect(definition.range.startLineNumber).toBe(7);
        expect(source.detailOf).not.toHaveBeenCalled();
    });

    test("finds the line whichever way the name is written", () => {
        const prefixes = { ex: "http://example.org/shapes#" };
        const text = `<http://example.org/shapes#A> a sh:NodeShape .
ex:B a sh:NodeShape .`;

        expect(
            localDefinitionLine(text, "http://example.org/shapes#A", prefixes),
        ).toBe(1);
        expect(
            localDefinitionLine(text, "http://example.org/shapes#B", prefixes),
        ).toBe(2);
        expect(
            localDefinitionLine(text, "http://example.org/shapes#C", prefixes),
        ).toBeNull();
    });
});

describe("a term the schema has nothing to say about", () => {
    // The backend answers 204 with no body, which the client turns into an empty object.
    test("has no hover", async () => {
        const model = fakeModel();
        attachTermSource(model, fakeSource({}));

        expect(
            await registered.hover.provideHover(model, ON_THE_CLASS),
        ).toBeNull();
    });

    test("is not followable", async () => {
        const model = fakeModel();
        attachTermSource(model, fakeSource({}));
        onOpenClass(vi.fn());

        expect(
            await registered.definition.provideDefinition(model, ON_THE_CLASS),
        ).toBeNull();
    });
});

describe("completion on a typed colon", () => {
    const TYPED = { triggerKind: 1, triggerCharacter: ":" };

    async function suggestionsAt(text, position, context = TYPED) {
        const model = fakeModel(text);
        attachTermSource(
            model,
            fakeSource(null, [
                {
                    kind: "CLASS",
                    iri: `${CIM}ACLineSegment`,
                    namespace: CIM,
                    localName: "ACLineSegment",
                },
            ]),
        );
        const { suggestions } =
            await registered.completion.provideCompletionItems(
                model,
                position,
                context,
            );
        return suggestions;
    }

    test("completes a term being written", async () => {
        const text = `@prefix cim: <${CIM}> .\nex:S sh:targetClass cim:`;

        expect(
            await suggestionsAt(text, { lineNumber: 2, column: 25 }),
        ).not.toEqual([]);
    });

    test("stays quiet in a comment, a string, an IRI or a prefix declaration", async () => {
        const cases = [
            ["# see cim:", 11],
            ['ex:S sh:message "use cim:', 26],
            ["ex:S sh:path <http:", 20],
            ["@prefix cim:", 13],
        ];
        for (const [line, column] of cases) {
            expect(
                await suggestionsAt(`@prefix cim: <${CIM}> .\n${line}`, {
                    lineNumber: 2,
                    column,
                }),
            ).toEqual([]);
        }
    });

    test("still answers when asked explicitly", async () => {
        expect(
            await suggestionsAt(
                `@prefix cim: <${CIM}> .\n# cim:`,
                { lineNumber: 2, column: 7 },
                { triggerKind: 0 },
            ),
        ).not.toEqual([]);
    });
});

describe("lexicalContext", () => {
    test("tells code from comments, strings and IRIs", () => {
        expect(lexicalContext("ex:S sh:path ex:")).toBe("code");
        expect(lexicalContext("ex:S # a comment")).toBe("comment");
        expect(lexicalContext("# a comment\nex:S ")).toBe("code");
        expect(lexicalContext('ex:S sh:name "open')).toBe("string");
        expect(lexicalContext('ex:S sh:name "with \\" inside')).toBe("string");
        expect(lexicalContext('ex:S sh:name "closed" ; ')).toBe("code");
        expect(lexicalContext("ex:S sh:path <http:")).toBe("iri");
        expect(lexicalContext("ex:S sh:path <http://x> ")).toBe("code");
    });

    test("follows a triple-quoted string across lines", () => {
        expect(
            lexicalContext(
                'ex:S sh:select """\nSELECT # not a comment\n?x cim:',
            ),
        ).toBe("longString");
        expect(lexicalContext('ex:S sh:select """\nSELECT\n""" ;\n  ')).toBe(
            "code",
        );
    });
});
