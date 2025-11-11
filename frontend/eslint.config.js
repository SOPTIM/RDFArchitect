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

import { includeIgnoreFile } from "@eslint/compat";
import js from "@eslint/js";
import * as typescriptParser from "@typescript-eslint/parser";
import prettier from "eslint-config-prettier";
import importPlugin from "eslint-plugin-import";
import svelte from "eslint-plugin-svelte";
import * as espree from "espree";
import globals from "globals";
import { fileURLToPath } from "node:url";
import * as svelteParser from "svelte-eslint-parser";
import ts from "typescript-eslint";

import rdfaOrder from "./eslint-rules/index.js";
import plainTextParser from "./eslint-rules/parsers/plaintext.js";
import svelteConfig from "./svelte.config.js";

const gitignorePath = fileURLToPath(new URL("./.gitignore", import.meta.url));

const baseGlobals = {
    ...globals.browser,
    ...globals.node,
};

const svelteParserOptions = {
    parser: {
        ts: typescriptParser,
        js: espree,
    },
    svelteConfig,
};

const createImportOrderRule = () => [
    "error",
    {
        groups: [
            ["builtin", "external"],
            ["internal", "parent", "sibling", "index"],
            ["type"],
        ],
        pathGroups: [
            {
                pattern: "$lib/**",
                group: "internal",
                position: "before",
            },
            {
                pattern: "$env/**",
                group: "external",
                position: "after",
            },
        ],
        pathGroupsExcludedImportTypes: ["builtin"],
        alphabetize: { order: "asc", caseInsensitive: true },
        "newlines-between": "always",
    },
];

/** @type {import('eslint').Linter.Config[]} */
export default [
    includeIgnoreFile(gitignorePath),

    js.configs.recommended,
    ...ts.configs.recommended,
    prettier,
    ...svelte.configs["flat/prettier"],

    {
        files: ["**/*.js"],
        languageOptions: {
            parser: espree,
            globals: baseGlobals,
        },
        plugins: {
            "rdfa-order": rdfaOrder,
            import: importPlugin,
        },
        rules: {
            "rdfa-order/copyright-header": "error",
            "rdfa-order/svelte-script-sections": "error",
            "import/order": createImportOrderRule(),
        },
    },
    {
        files: ["**/*.svelte.js"],
        languageOptions: {
            parser: svelteParser,
            parserOptions: {
                parser: espree,
                svelteConfig,
            },
            globals: baseGlobals,
        },
        plugins: {
            "rdfa-order": rdfaOrder,
            import: importPlugin,
        },
        rules: {
            "rdfa-order/copyright-header": "error",
            "rdfa-order/svelte-script-sections": "error",
            "import/order": createImportOrderRule(),
        },
    },
    {
        files: ["**/*.ts"],
        languageOptions: {
            parser: typescriptParser,
            globals: baseGlobals,
        },
        plugins: {
            "rdfa-order": rdfaOrder,
            import: importPlugin,
        },
        rules: {
            "rdfa-order/copyright-header": "error",
            "rdfa-order/svelte-script-sections": "error",
            "import/order": createImportOrderRule(),
        },
    },
    {
        files: ["**/*.css"],
        languageOptions: {
            parser: plainTextParser,
            globals: baseGlobals,
        },
        plugins: {
            "rdfa-order": rdfaOrder,
        },
        rules: {
            "rdfa-order/copyright-header": "error",
        },
    },
    {
        files: ["**/*.svelte"],
        languageOptions: {
            parser: svelteParser,
            parserOptions: svelteParserOptions,
            globals: baseGlobals,
        },
        plugins: {
            "rdfa-order": rdfaOrder,
            import: importPlugin,
        },
        rules: {
            "svelte/no-at-html-tags": "warn",
            "rdfa-order/svelte-file-structure": "error",
            "rdfa-order/svelte-script-sections": "error",
            "import/order": createImportOrderRule(),
        },
    },
];
