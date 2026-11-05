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
 * Editor colours, taken from the palette the rest of the app is built from.
 *
 * The values duplicate the `@theme` block in `src/app.css` rather than reading the custom
 * properties off the document: Monaco needs plain hex strings at theme-registration time, before
 * an editor exists to resolve `var(--color-blue)` against. Keep the light theme in step with
 * `app.css` when that palette changes.
 *
 * The app itself is light-only today. The dark theme is here because a code editor is where a
 * light background hurts first, and because `resolveThemeName` is the single place app-wide dark
 * mode would have to hook into once it exists.
 */

import { userSettings } from "$lib/userSettings.svelte.js";

export const LIGHT_THEME = "rdfa-turtle-light";

export const DARK_THEME = "rdfa-turtle-dark";

/** The user setting that chooses between them; `system` follows the OS preference. */
export const THEME_SETTING = "editorTheme";

export const THEME_OPTIONS = [
    { value: "light", name: "Light" },
    { value: "dark", name: "Dark" },
    { value: "system", name: "Follow system" },
];

/**
 * The colours of everything the editor draws outside the text: its context menu, the completion
 * list, the hover. Monaco resolves these into the CSS variables its own widgets are painted with,
 * which is why they are set here rather than in a stylesheet — `monaco-menus.css` only has the
 * shape of those menus to answer for.
 *
 * A menu's selected row is `list.hover*` rather than `menu.selection*`: that is the pair Monaco's
 * menu styles actually read.
 */
const MENU_AND_WIDGETS_LIGHT = {
    "menu.background": "#f9f9f9",
    "menu.foreground": "#303030",
    "menu.border": "#e0e0e0",
    "menu.separatorBackground": "#e0e0e0",
    "list.hoverBackground": "#90c1f1",
    "list.hoverForeground": "#ffffff",
    "widget.border": "#e0e0e0",
    "widget.shadow": "#0000001f",
    "editorWidget.background": "#f9f9f9",
    "editorWidget.foreground": "#303030",
    "editorWidget.border": "#e0e0e0",
    "editorSuggestWidget.background": "#f9f9f9",
    "editorSuggestWidget.foreground": "#303030",
    "editorSuggestWidget.border": "#e0e0e0",
    "editorSuggestWidget.selectedBackground": "#90c1f1",
    "editorSuggestWidget.selectedForeground": "#ffffff",
    "editorSuggestWidget.highlightForeground": "#1f75cb",
    "editorHoverWidget.background": "#f9f9f9",
    "editorHoverWidget.foreground": "#303030",
    "editorHoverWidget.border": "#e0e0e0",
    focusBorder: "#1f75cb",
};

const MENU_AND_WIDGETS_DARK = {
    "menu.background": "#1e2140",
    "menu.foreground": "#e0e0e0",
    "menu.border": "#2b2f4d",
    "menu.separatorBackground": "#2b2f4d",
    "list.hoverBackground": "#2c3a63",
    "list.hoverForeground": "#ffffff",
    "widget.border": "#2b2f4d",
    "widget.shadow": "#00000066",
    "editorWidget.background": "#1e2140",
    "editorWidget.foreground": "#e0e0e0",
    "editorWidget.border": "#2b2f4d",
    "editorSuggestWidget.background": "#1e2140",
    "editorSuggestWidget.foreground": "#e0e0e0",
    "editorSuggestWidget.border": "#2b2f4d",
    "editorSuggestWidget.selectedBackground": "#2c3a63",
    "editorSuggestWidget.selectedForeground": "#ffffff",
    "editorSuggestWidget.highlightForeground": "#6fb8f5",
    "editorHoverWidget.background": "#1e2140",
    "editorHoverWidget.foreground": "#e0e0e0",
    "editorHoverWidget.border": "#2b2f4d",
    focusBorder: "#6fb8f5",
};

const LIGHT = {
    base: "vs",
    inherit: true,
    rules: [
        { token: "comment", foreground: "787878", fontStyle: "italic" },
        { token: "directive", foreground: "6e49cb", fontStyle: "bold" },
        { token: "prefix", foreground: "1f75cb" },
        { token: "localName", foreground: "2e2e2e" },
        { token: "iri", foreground: "1f75cb", fontStyle: "underline" },
        { token: "blankNode", foreground: "6e49cb" },
        { token: "string", foreground: "fc6d26" },
        { token: "escape", foreground: "db3b21" },
        { token: "number", foreground: "6e49cb" },
        { token: "boolean", foreground: "6e49cb" },
        { token: "typeKeyword", foreground: "1f75cb", fontStyle: "bold" },
        { token: "typeMarker", foreground: "787878" },
        { token: "shaclQuery", foreground: "e74890", fontStyle: "bold" },
        { token: "sparqlKeyword", foreground: "1f75cb", fontStyle: "bold" },
        { token: "sparqlFunction", foreground: "6e49cb" },
        { token: "sparqlVariable", foreground: "f0881a" },
        { token: "sparqlOperator", foreground: "2e2e2e" },
        { token: "invalid", foreground: "db3b21", fontStyle: "underline" },
    ],
    colors: {
        "editor.background": "#ffffff",
        "editor.foreground": "#303030",
        "editorLineNumber.foreground": "#a8a8a8",
        "editorLineNumber.activeForeground": "#1f75cb",
        "editor.lineHighlightBackground": "#f1f1f1",
        "editor.selectionBackground": "#cfe3f8",
        "editorIndentGuide.background1": "#e0e0e0",
        "editorGutter.background": "#f9f9f9",
        ...MENU_AND_WIDGETS_LIGHT,
    },
};

const DARK = {
    base: "vs-dark",
    inherit: true,
    rules: [
        { token: "comment", foreground: "8f8f8f", fontStyle: "italic" },
        { token: "directive", foreground: "b39ddb", fontStyle: "bold" },
        { token: "prefix", foreground: "6fb8f5" },
        { token: "localName", foreground: "e0e0e0" },
        { token: "iri", foreground: "6fb8f5", fontStyle: "underline" },
        { token: "blankNode", foreground: "b39ddb" },
        { token: "string", foreground: "ff9a63" },
        { token: "escape", foreground: "ef7360" },
        { token: "number", foreground: "b39ddb" },
        { token: "boolean", foreground: "b39ddb" },
        { token: "typeKeyword", foreground: "6fb8f5", fontStyle: "bold" },
        { token: "typeMarker", foreground: "8f8f8f" },
        { token: "shaclQuery", foreground: "f489b4", fontStyle: "bold" },
        { token: "sparqlKeyword", foreground: "6fb8f5", fontStyle: "bold" },
        { token: "sparqlFunction", foreground: "b39ddb" },
        { token: "sparqlVariable", foreground: "f7b45c" },
        { token: "sparqlOperator", foreground: "e0e0e0" },
        { token: "invalid", foreground: "ef7360", fontStyle: "underline" },
    ],
    colors: {
        "editor.background": "#14162b",
        "editor.foreground": "#e0e0e0",
        "editorLineNumber.foreground": "#5c6676",
        "editorLineNumber.activeForeground": "#6fb8f5",
        "editor.lineHighlightBackground": "#1e2140",
        "editor.selectionBackground": "#2c3a63",
        "editorIndentGuide.background1": "#2b2f4d",
        "editorGutter.background": "#14162b",
        ...MENU_AND_WIDGETS_DARK,
    },
};

export const THEMES = { [LIGHT_THEME]: LIGHT, [DARK_THEME]: DARK };

/**
 * Which theme to use, honouring the user setting and falling back to the light palette the rest
 * of the app uses.
 */
export function resolveThemeName() {
    const preference = userSettings.get(THEME_SETTING, "light");
    if (preference === "dark") {
        return DARK_THEME;
    }
    if (preference === "system" && prefersDark()) {
        return DARK_THEME;
    }
    return LIGHT_THEME;
}

function prefersDark() {
    return (
        typeof window !== "undefined" &&
        typeof window.matchMedia === "function" &&
        window.matchMedia("(prefers-color-scheme: dark)").matches
    );
}
