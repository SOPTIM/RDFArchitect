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
 * Bridges the TextMate grammars in `./syntaxes` to Monaco's tokenizer.
 *
 * Monaco's own grammar format (Monarch) cannot express one language embedded in another, so a
 * hand-written Turtle tokenizer would highlight the SPARQL inside `sh:select """..."""` as one
 * long string. TextMate can, and the grammars already exist — see `./syntaxes/README.md`.
 *
 * The bridge is small because the two tokenizer interfaces line up almost exactly: TextMate's
 * rule stack already has the `clone()` and `equals()` that Monaco's `IState` asks for, so it is
 * handed straight back to Monaco as the line state.
 */

import { INITIAL, Registry } from "vscode-textmate";

import sparqlGrammar from "./syntaxes/sparql.tmLanguage.json";
import turtleGrammar from "./syntaxes/turtle.tmLanguage.json";

const TURTLE_SCOPE = "source.turtle";

const GRAMMARS = {
    [TURTLE_SCOPE]: turtleGrammar,
    "source.sparql": sparqlGrammar,
};

/**
 * TextMate scope prefix to the token name Monaco's theme rules are written against.
 *
 * The names are flat rather than dotted so that a theme rule matches a token exactly, instead of
 * relying on Monaco resolving `keyword.control.directive.prefix.turtle` against a rule for
 * `keyword`. Longest prefix wins, so the table's order does not matter.
 */
const SCOPE_TOKENS = {
    comment: "comment",
    "constant.character.escape": "escape",
    "constant.language.boolean": "boolean",
    "constant.numeric": "number",
    "entity.name.type.iri": "iri",
    invalid: "invalid",
    "keyword.control.directive": "directive",
    "keyword.control.sparql": "sparqlKeyword",
    "keyword.operator": "sparqlOperator",
    "keyword.operator.datatype": "typeMarker",
    "keyword.other.language-tag": "typeMarker",
    "keyword.other.rdf-type": "typeKeyword",
    "keyword.other.sparql-constraint": "shaclQuery",
    "punctuation.definition.string": "string",
    "punctuation.separator.namespace": "prefix",
    "storage.modifier.namespace": "prefix",
    string: "string",
    "support.function": "sparqlFunction",
    "variable.language.blank-node": "blankNode",
    "variable.other.local-name": "localName",
    "variable.parameter.sparql": "sparqlVariable",
};

const SCOPE_PREFIXES = Object.keys(SCOPE_TOKENS).sort(
    (a, b) => b.length - a.length,
);

let grammarPromise;

/**
 * The Monaco token name for one TextMate scope stack, or `""` to leave the text unstyled.
 *
 * The stack is read from the deepest scope outwards, so the most specific rule that matched the
 * text wins. `meta.embedded.block.sparql` and the two `source.*` scopes carry no colour of their
 * own and fall through to whatever the enclosing scope says.
 */
export function tokenNameFor(scopes) {
    for (let i = scopes.length - 1; i >= 0; i--) {
        const prefix = SCOPE_PREFIXES.find(
            candidate =>
                scopes[i] === candidate ||
                scopes[i].startsWith(candidate + "."),
        );
        if (prefix) {
            return SCOPE_TOKENS[prefix];
        }
    }
    return "";
}

/**
 * Loads the WebAssembly regex engine the grammars are matched with.
 *
 * Vite rewrites the `?url` import to a hashed asset it also copies into the build, which is why
 * the wasm is fetched rather than bundled: it has to stay a separate file to be instantiated.
 */
async function createOnigLib() {
    const oniguruma = await import("vscode-oniguruma");
    const { default: wasmUrl } =
        await import("vscode-oniguruma/release/onig.wasm?url");
    const response = await fetch(wasmUrl);
    await oniguruma.loadWASM(await response.arrayBuffer());
    return {
        createOnigScanner: patterns => oniguruma.createOnigScanner(patterns),
        createOnigString: text => oniguruma.createOnigString(text),
    };
}

/**
 * A registry holding both grammars.
 *
 * The regex engine is a parameter because it is the one piece that differs between the browser,
 * where the wasm is fetched as an asset, and a test, where it is read off disk.
 */
export function createRegistry(onigLib) {
    return new Registry({
        onigLib,
        loadGrammar: async scopeName => GRAMMARS[scopeName] ?? null,
    });
}

export async function loadGrammar(registry) {
    return registry.loadGrammar(TURTLE_SCOPE);
}

/** The compiled Turtle grammar, loaded once per page. */
function loadTurtleGrammar() {
    grammarPromise ??= loadGrammar(createRegistry(createOnigLib()));
    return grammarPromise;
}

/**
 * A Monaco tokens provider driven by the Turtle grammar.
 *
 * Tokenizing is synchronous in Monaco but loading the grammar is not, so this is awaited before
 * the language is registered rather than returning a provider that starts out empty — an editor
 * created against an empty provider keeps the unstyled tokens of its first render.
 */
export async function createTurtleTokensProvider() {
    const grammar = await loadTurtleGrammar();
    return {
        getInitialState: () => INITIAL,
        tokenize(line, state) {
            const result = grammar.tokenizeLine(line, state);
            return {
                endState: result.ruleStack,
                tokens: result.tokens.map(token => ({
                    startIndex: token.startIndex,
                    scopes: tokenNameFor(token.scopes),
                })),
            };
        },
    };
}
