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

import fs from "node:fs";
import path from "node:path";
import { beforeAll, describe, expect, test } from "vitest";
import { INITIAL } from "vscode-textmate";

import {
    createRegistry,
    loadGrammar,
    tokenNameFor,
} from "$lib/monaco/textmate.js";

/**
 * The grammars are only worth having because Monarch cannot express one language inside another.
 * These tests are therefore mostly about the embedded SPARQL: that a `sh:select` block really is
 * tokenized by the SPARQL grammar, and that the surrounding Turtle picks up again afterwards.
 */

const SHAPE = `@prefix sh:   <http://www.w3.org/ns/shacl#> .
@prefix cim:  <http://iec.ch/TC57/CIM100#> .

cim:ACLineSegmentShape
        a            sh:NodeShape ;
        sh:targetClass cim:ACLineSegment ;
        sh:sparql [
            sh:select """
                SELECT $this WHERE { $this cim:Conductor.length ?length . }
            """ ;
        ] ;
        sh:property [
            sh:path     cim:IdentifiedObject.name ;
            sh:minCount 1 ;
        ] .`;

let grammar;

/** Every token of the source, as `{ text, scopes, token }`. */
function tokenize(source) {
    let state = INITIAL;
    const all = [];
    for (const line of source.split("\n")) {
        const result = grammar.tokenizeLine(line, state);
        state = result.ruleStack;
        for (const token of result.tokens) {
            all.push({
                text: line.slice(token.startIndex, token.endIndex),
                scopes: token.scopes,
                token: tokenNameFor(token.scopes),
            });
        }
    }
    return all;
}

function find(tokens, text) {
    return tokens.find(token => token.text.trim() === text);
}

beforeAll(async () => {
    const oniguruma = await import("vscode-oniguruma");
    const wasm = fs.readFileSync(
        path.resolve("node_modules/vscode-oniguruma/release/onig.wasm"),
    );
    await oniguruma.loadWASM(wasm);
    grammar = await loadGrammar(
        createRegistry(
            Promise.resolve({
                createOnigScanner: oniguruma.createOnigScanner,
                createOnigString: oniguruma.createOnigString,
            }),
        ),
    );
});

describe("Turtle highlighting", () => {
    test("names the Turtle constructs the theme colours", () => {
        const tokens = tokenize(SHAPE);

        // A prefixed name is three tokens — namespace, colon, local name — so the assertions
        // below name the parts rather than "sh:NodeShape".
        expect(find(tokens, "@prefix").token).toBe("directive");
        expect(find(tokens, "sh").token).toBe("prefix");
        expect(find(tokens, "NodeShape").token).toBe("localName");
        expect(find(tokens, "a").token).toBe("typeKeyword");
        expect(find(tokens, "1").token).toBe("number");
        expect(find(tokens, "<http://www.w3.org/ns/shacl#>").token).toBe("iri");
    });

    test("tokenizes the SPARQL inside sh:select with the SPARQL grammar", () => {
        const tokens = tokenize(SHAPE);

        const select = find(tokens, "SELECT");
        expect(select).toBeDefined();
        expect(select.scopes).toContain("meta.embedded.block.sparql");
        expect(select.token).toBe("sparqlKeyword");

        expect(find(tokens, "$this").token).toBe("sparqlVariable");
        expect(find(tokens, "sh:select").token).toBe("shaclQuery");
    });

    test("returns to Turtle after the embedded block ends", () => {
        const tokens = tokenize(SHAPE);

        // "path" only occurs in the property shape after the embedded block.
        expect(find(tokens, "path").scopes).not.toContain(
            "meta.embedded.block.sparql",
        );
        expect(find(tokens, "minCount").token).toBe("localName");
    });

    test("carries a comment across the whole line", () => {
        const tokens = tokenize(
            "# a note about the shape\ncim:X a sh:NodeShape .",
        );

        expect(tokens[0].token).toBe("comment");
        expect(tokens[0].text).toBe("# a note about the shape");
        expect(find(tokens, "NodeShape").token).toBe("localName");
    });
});

describe("tokenNameFor", () => {
    test("prefers the innermost scope that has a colour", () => {
        expect(
            tokenNameFor([
                "source.turtle",
                "meta.embedded.block.sparql",
                "source.sparql",
                "keyword.control.sparql",
            ]),
        ).toBe("sparqlKeyword");
    });

    test("falls back to an enclosing scope when the innermost has no colour", () => {
        expect(
            tokenNameFor([
                "source.turtle",
                "string.quoted.triple.turtle",
                "meta.embedded.block.sparql",
            ]),
        ).toBe("string");
    });

    test("leaves text unstyled when nothing in the stack maps", () => {
        expect(tokenNameFor(["source.turtle"])).toBe("");
        expect(tokenNameFor([])).toBe("");
    });
});
