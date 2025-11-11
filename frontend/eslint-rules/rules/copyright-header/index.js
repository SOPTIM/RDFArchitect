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
    buildCopyrightComment,
    getCopyrightNotice,
    normalizeCommentBody,
} from "../../utils/copyright.js";

const BLOCK_COMMENT_REGEX = /^(\s*?)\/\*([\s\S]*?)\*\//;

const copyrightHeaderRule = {
    meta: {
        type: "problem",
        docs: {
            description:
                "Require Apache 2.0 copyright header at the top of JS, TS, and CSS files.",
            recommended: false,
        },
        fixable: "code",
        schema: [],
    },
    create(context) {
        const filename = context.getFilename();
        if (!needsCheck(filename)) return {};

        const expectedNormalized = getCopyrightNotice().normalized;
        const expectedComment = buildCopyrightComment("block");

        return {
            Program() {
                const sourceCode = context.getSourceCode();
                const text = sourceCode.getText();
                const leading = findLeadingBlockComment(text);

                if (!leading) {
                    context.report({
                        loc: { line: 1, column: 0 },
                        message:
                            "Add the Apache 2.0 copyright header (block comment) at the top of the file.",
                        fix(fixer) {
                            const insertText =
                                expectedComment +
                                (text.startsWith("\n") ? "" : "\n\n");
                            return fixer.insertTextBeforeRange(
                                [0, 0],
                                insertText,
                            );
                        },
                    });
                    return;
                }

                const normalized = normalizeCommentBody(leading.body, "block");
                if (normalized === expectedNormalized) return;

                context.report({
                    loc: { line: 1, column: 0 },
                    message:
                        "Update the leading block comment to match the Apache 2.0 copyright header.",
                    fix(fixer) {
                        const replacement = expectedComment;
                        return fixer.replaceTextRange(
                            [leading.range[0], leading.range[1]],
                            replacement,
                        );
                    },
                });
            },
        };
    },
};

function findLeadingBlockComment(text) {
    const sanitized = text.replace(/^\uFEFF/, "");
    const match = sanitized.match(BLOCK_COMMENT_REGEX);
    if (!match) return null;
    const [fullMatch, leadingWhitespace, body] = match;
    const start = (match.index ?? 0) + leadingWhitespace.length;
    const end = (match.index ?? 0) + fullMatch.length;
    return { body, range: [match.index ?? 0, end], start, end };
}

function needsCheck(filename) {
    return (
        filename.endsWith(".js") ||
        filename.endsWith(".ts") ||
        filename.endsWith(".css") ||
        filename.endsWith(".svelte.js") ||
        filename.endsWith(".svelte.ts")
    );
}

export default copyrightHeaderRule;
