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

import { processNodes } from "./process-nodes.js";

const svelteScriptOrderRule = {
    meta: {
        type: "suggestion",
        docs: {
            description:
                "Enforce canonical order of Svelte <script> sections and module top-level statements.",
            recommended: false,
        },
        fixable: "code",
        schema: [],
    },
    create(context) {
        const filename = context.getFilename();
        const isSvelte = filename.endsWith(".svelte");
        const isSvelteJS = filename.endsWith(".svelte.js");

        if (isSvelte) {
            return {
                Program: node => {
                    const scriptBlocks =
                        node.body?.filter?.(
                            n => n.type === "SvelteScriptElement",
                        ) ?? [];
                    if (!scriptBlocks.length) return;
                    for (const sb of scriptBlocks) {
                        const statements = Array.isArray(sb.body?.body)
                            ? sb.body.body
                            : Array.isArray(sb.body)
                              ? sb.body
                              : null;
                        if (Array.isArray(statements)) {
                            processNodes(context, statements);
                        }
                    }
                },
            };
        }

        if (
            isSvelteJS ||
            filename.endsWith(".js") ||
            filename.endsWith(".ts")
        ) {
            return {
                Program: node => processNodes(context, node.body),
            };
        }

        return {};
    },
};

export default svelteScriptOrderRule;
