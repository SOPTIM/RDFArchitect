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

import { categoryIndex, categoryLabel } from "./categorize.js";

function findFirstViolation(nodes) {
    let prev = -1;
    let prevNode = null;
    for (const n of nodes) {
        const idx = categoryIndex(n);
        if (idx < prev) {
            return {
                node: n,
                nodeCategory: idx,
                previousNode: prevNode,
                previousCategory: prev,
            };
        }
        if (idx > prev) {
            prev = idx;
            prevNode = n;
        }
    }
    return null;
}

function buildSegments(nodes) {
    const segments = new Map();
    for (let i = 0; i < nodes.length; i++) {
        const node = nodes[i];
        const prevEnd = i === 0 ? node.range[0] : nodes[i - 1].range[1];
        const seg = [prevEnd, node.range[1]];
        segments.set(node, seg);
    }
    return segments;
}

function sortedNodes(nodes) {
    return nodes
        .map((n, i) => ({ n, i, c: categoryIndex(n) }))
        .sort((a, b) => (a.c === b.c ? a.i - b.i : a.c - b.c))
        .map(x => x.n);
}

function applyFix(fixer, nodes, sourceCode) {
    if (!nodes.length) return null;
    const start = nodes[0].range[0];
    const end = nodes[nodes.length - 1].range[1];
    const segments = buildSegments(nodes);
    const order = sortedNodes(nodes);
    const parts = order.map(n => {
        const [s, e] = segments.get(n);
        return sourceCode.text.slice(s, e);
    });
    const replacement = parts.join("");
    return fixer.replaceTextRange([start, end], replacement);
}

export function processNodes(context, nodes) {
    const sourceCode = context.sourceCode || context.getSourceCode();
    if (!Array.isArray(nodes) || nodes.length < 2) return;
    const violation = findFirstViolation(nodes);
    if (!violation) return;

    const offendingRaw = sourceCode
        .getText(violation.node)
        .split("\n")[0]
        .trim();
    const offending =
        offendingRaw.length > 80
            ? `${offendingRaw.slice(0, 77)}…`
            : offendingRaw;
    let anchor = null;
    if (violation.previousNode) {
        const anchorRaw = sourceCode
            .getText(violation.previousNode)
            .split("\n")[0]
            .trim();
        anchor =
            anchorRaw.length > 80 ? `${anchorRaw.slice(0, 77)}…` : anchorRaw;
    }
    context.report({
        node: violation.node,
        message: anchor
            ? `Move "${offending}" into the ${categoryLabel(violation.nodeCategory)} section (before ${categoryLabel(violation.previousCategory)} such as "${anchor}").`
            : `Move "${offending}" into the ${categoryLabel(violation.nodeCategory)} section.`,
        fix(fixer) {
            return applyFix(fixer, nodes, sourceCode);
        },
    });
}
