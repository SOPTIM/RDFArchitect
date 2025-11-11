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

const HTML_COMMENT_REGEX = /^(\s*?)<!--([\s\S]*?)-->/;

const svelteFileStructureRule = {
    meta: {
        type: "problem",
        docs: {
            description:
                "Enforce copyright header and <script>/<markup>/<style> order in Svelte components.",
            recommended: false,
        },
        fixable: "code",
        schema: [],
    },
    create(context) {
        const filename = context.getFilename();
        if (!filename.endsWith(".svelte")) return {};

        const expectedCopyright = getCopyrightNotice();
        const expectedComment = buildCopyrightComment("html");

        return {
            Program(node) {
                const sourceCode = context.getSourceCode();
                const text = sourceCode.getText();
                const leading = findLeadingHtmlComment(text);

                const normalized = leading
                    ? normalizeCommentBody(leading.body, "html")
                    : null;
                const hasValidCopyright =
                    normalized === expectedCopyright.normalized;

                if (!hasValidCopyright) {
                    context.report({
                        loc: { line: 1, column: 0 },
                        message:
                            "Add the Apache 2.0 copyright header (HTML comment) at the top of the Svelte file.",
                        fix(fixer) {
                            if (leading) {
                                return fixer.replaceTextRange(
                                    [leading.range[0], leading.range[1]],
                                    expectedComment,
                                );
                            }
                            const prefix = text.startsWith("\n") ? "" : "\n\n";
                            return fixer.insertTextBeforeRange(
                                [0, 0],
                                expectedComment + prefix,
                            );
                        },
                    });
                    return;
                }

                const orderedNodes = filterNodesForOrder(
                    node.body ?? [],
                    sourceCode,
                    expectedCopyright.normalized,
                );
                const violation = findFirstOutOfOrder(orderedNodes);
                if (!violation) return;

                const message = violation.previousNode
                    ? `Place ${sectionLabel(violation.nodeIdx)} before ${sectionLabel(violation.previousIdx)} to keep scripts, markup, then styles.`
                    : `Move ${sectionLabel(violation.nodeIdx)} before markup and style content.`;

                context.report({
                    node: violation.node,
                    message,
                    fix(fixer) {
                        const start = orderedNodes[0].range[0];
                        const end =
                            orderedNodes[orderedNodes.length - 1].range[1];
                        const segments = buildSegments(orderedNodes);
                        const sorted = sortNodes(orderedNodes);
                        const parts = sorted.map(n => {
                            const [s, e] = segments.get(n);
                            return sourceCode.text.slice(s, e);
                        });
                        return fixer.replaceTextRange(
                            [start, end],
                            parts.join(""),
                        );
                    },
                });
            },
        };
    },
};

function findLeadingHtmlComment(text) {
    const sanitized = text.replace(/^\uFEFF/, "");
    const match = sanitized.match(HTML_COMMENT_REGEX);
    if (!match) return null;
    const [fullMatch, leadingWhitespace, body] = match;
    const start = (match.index ?? 0) + leadingWhitespace.length;
    const end = (match.index ?? 0) + fullMatch.length;
    return { body, range: [match.index ?? 0, end], start, end };
}

function extractHtmlCommentBody(raw) {
    const match = raw.match(/^<!--([\s\S]*?)-->$/);
    return match ? match[1] : raw;
}

function sectionIndex(node) {
    if (node.type === "SvelteOptions") return 0;
    if (node.type === "SvelteScriptElement") return 1;
    if (node.type === "SvelteStyleElement") return 3;
    return 2; // markup and everything else
}

function sectionLabel(idx) {
    if (idx === 0) return "<svelte:options>";
    if (idx === 1) return "<script>";
    if (idx === 3) return "<style>";
    return "markup";
}

function isWhitespaceText(node, sourceCode) {
    if (node.type !== "SvelteText") return false;
    const text = sourceCode.getText(node);
    return text.trim() === "";
}

function filterNodesForOrder(nodes, sourceCode, expectedNormalized) {
    return nodes.filter(node => {
        if (node.type === "SvelteHTMLComment") {
            const body = extractHtmlCommentBody(sourceCode.getText(node));
            const normalized = normalizeCommentBody(body, "html");
            return normalized !== expectedNormalized;
        }

        if (isWhitespaceText(node, sourceCode)) return false;

        return true;
    });
}

function findFirstOutOfOrder(nodes) {
    let lastIdx = -1;
    let lastNode = null;
    for (const node of nodes) {
        const idx = sectionIndex(node);
        if (idx < lastIdx) {
            return {
                node,
                nodeIdx: idx,
                previousNode: lastNode,
                previousIdx: lastIdx,
            };
        }
        lastIdx = idx;
        lastNode = node;
    }
    return null;
}

function buildSegments(nodes) {
    const segments = new Map();
    for (let i = 0; i < nodes.length; i++) {
        const node = nodes[i];
        const prevEnd = i === 0 ? node.range[0] : nodes[i - 1].range[1];
        segments.set(node, [prevEnd, node.range[1]]);
    }
    return segments;
}

function sortNodes(nodes) {
    return nodes
        .map((node, i) => ({ node, i, idx: sectionIndex(node) }))
        .sort((a, b) => (a.idx === b.idx ? a.i - b.i : a.idx - b.idx))
        .map(entry => entry.node);
}

export default svelteFileStructureRule;
