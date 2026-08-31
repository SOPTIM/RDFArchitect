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
 * Loads Monaco and teaches it Turtle.
 *
 * The import paths look short because monaco-editor's `exports` map already rewrites `./x.js` to
 * `./esm/vs/x.js`. Spelling the real path out — `monaco-editor/esm/vs/editor/editor.api.js`, as
 * every older example does — is resolved through that same map a second time and fails.
 *
 * Everything is behind one lazily-called function for two reasons. Monaco touches `self`,
 * `document` and `window` while it is being imported, so it cannot be evaluated during
 * server-side rendering; and it is by far the largest thing the app pulls in, so the pages that
 * do not edit constraints should not pay for it.
 *
 * The editor is assembled from `editor.api` plus a chosen list of contributions rather than
 * imported from `editor.main`, which registers Monarch grammars for around eighty languages this
 * app will never open a file in.
 */

import { THEMES } from "./theme.js";

export const TURTLE_LANGUAGE_ID = "turtle";

let monacoPromise;

/**
 * The Monaco namespace, with the Turtle language and this app's themes registered.
 *
 * Safe to call from anywhere and as often as convenient: the work happens once and every caller
 * awaits the same promise.
 */
export function loadMonaco() {
    monacoPromise ??= initialise();
    return monacoPromise;
}

async function initialise() {
    // The grammars pull in a TextMate engine of their own, so they are loaded here rather than
    // imported by the component: a page that only shows a few lines of Turtle in a dialog should
    // not download them until an editor is actually opened.
    const [monaco, { createTurtleTokensProvider }] = await Promise.all([
        import("monaco-editor/editor/editor.api.js"),
        import("./textmate.js"),
    ]);
    await Promise.all([importContributions(), configureWorkers()]);

    monaco.languages.register({
        id: TURTLE_LANGUAGE_ID,
        extensions: [".ttl", ".shacl"],
        aliases: ["Turtle", "SHACL", "turtle"],
        mimetypes: ["text/turtle"],
    });
    monaco.languages.setLanguageConfiguration(TURTLE_LANGUAGE_ID, {
        comments: { lineComment: "#" },
        brackets: [
            ["[", "]"],
            ["(", ")"],
        ],
        autoClosingPairs: [
            { open: "[", close: "]" },
            { open: "(", close: ")" },
            { open: "<", close: ">" },
            { open: '"', close: '"', notIn: ["string"] },
            { open: "'", close: "'", notIn: ["string"] },
        ],
        surroundingPairs: [
            { open: "[", close: "]" },
            { open: "(", close: ")" },
            { open: "<", close: ">" },
            { open: '"', close: '"' },
        ],
        // A Turtle statement runs until its ".", so indenting after "[" or "(" is the only
        // structure there is to follow.
        indentationRules: {
            increaseIndentPattern: /[[(]\s*$/,
            decreaseIndentPattern: /^\s*[\])]/,
        },
    });
    monaco.languages.setTokensProvider(
        TURTLE_LANGUAGE_ID,
        await createTurtleTokensProvider(),
    );

    for (const [name, theme] of Object.entries(THEMES)) {
        monaco.editor.defineTheme(name, theme);
    }
    return monaco;
}

/**
 * The editor features worth having in a constraints editor.
 *
 * `hover` and `gotoError` are the two that matter most: without them a validation marker is a
 * squiggle with no way to read the message, and F8 is how you walk a file's findings.
 */
function importContributions() {
    return Promise.all([
        import("monaco-editor/editor/contrib/bracketMatching/browser/bracketMatching.js"),
        import("monaco-editor/editor/contrib/clipboard/browser/clipboard.js"),
        import("monaco-editor/editor/contrib/comment/browser/comment.js"),
        import("monaco-editor/editor/contrib/contextmenu/browser/contextmenu.js"),
        import("monaco-editor/editor/contrib/cursorUndo/browser/cursorUndo.js"),
        import("monaco-editor/editor/contrib/find/browser/findController.js"),
        import("monaco-editor/editor/contrib/folding/browser/folding.js"),
        import("monaco-editor/editor/contrib/gotoError/browser/gotoError.js"),
        import("monaco-editor/editor/contrib/hover/browser/hoverContribution.js"),
        import("monaco-editor/editor/contrib/indentation/browser/indentation.js"),
        import("monaco-editor/editor/contrib/lineSelection/browser/lineSelection.js"),
        import("monaco-editor/editor/contrib/linesOperations/browser/linesOperations.js"),
        import("monaco-editor/editor/contrib/multicursor/browser/multicursor.js"),
        import("monaco-editor/editor/contrib/smartSelect/browser/smartSelect.js"),
        import("monaco-editor/editor/contrib/suggest/browser/suggestController.js"),
        import("monaco-editor/editor/contrib/tokenization/browser/tokenization.js"),
        import("monaco-editor/editor/contrib/wordHighlighter/browser/wordHighlighter.js"),
        import("monaco-editor/editor/contrib/wordOperations/browser/wordOperations.js"),
    ]);
}

/**
 * Points Monaco at its web worker.
 *
 * Only the base editor worker is needed — it backs the word-based suggestions and link detection
 * that come with the contributions above. Monaco falls back to the main thread without one, but
 * warns on every editor it creates.
 */
async function configureWorkers() {
    if (self.MonacoEnvironment?.getWorker) {
        return;
    }
    const { default: EditorWorker } =
        await import("monaco-editor/editor/editor.worker.js?worker");
    self.MonacoEnvironment = { getWorker: () => new EditorWorker() };
}
