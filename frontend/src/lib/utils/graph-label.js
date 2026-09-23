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
 * What a schema is called on screen.
 *
 * The navigation tree, the changelog, the schema colors, the schema lists of a class and every
 * schema picker name a graph the same way, so the rule lives here rather than in each of them: a
 * profile's own name first, its keyword when it has no name, and the tail of its URI when it is
 * not a CIM profile at all.
 */

import { uriSuffix } from "./iri.js";

/**
 * The full URI of a resource as the backend sends it: a `GraphDto` holding a URI split into
 * prefix and suffix, a bare split URI, or a string that already is the URI.
 *
 * The navigation reaches this as `getUri`, which `packageNavigationUtils` re-exports.
 */
export function graphUri(graph) {
    const uri = graph?.uri ?? graph;
    if (typeof uri === "string") {
        return uri;
    }
    return `${uri?.prefix ?? ""}${uri?.suffix ?? ""}`;
}

/** A single graph's name, without regard to the other schemas of its workspace. */
export function graphLabel(graph) {
    return graph?.label || graph?.keyword || uriSuffix(graphUri(graph));
}

/**
 * Names every schema of a workspace, telling apart those that would otherwise read alike.
 *
 * Schemas do collide: the four CGMES 2.4.15 equipment profiles share a keyword, a package label
 * and even their version IRIs, and only the graph they were imported into separates them. Where
 * that happens the tail of the URI is appended, and nowhere else — a name is more useful than a
 * name plus a URI when it is already unambiguous.
 *
 * @param {Array} graphs every schema of the workspace, as the backend listed them
 * @returns {(graph) => string}
 */
export function graphLabeller(graphs) {
    const seen = new Map();
    for (const graph of graphs ?? []) {
        const name = graphLabel(graph);
        seen.set(name, (seen.get(name) ?? 0) + 1);
    }
    return graph => {
        const name = graphLabel(graph);
        if ((seen.get(name) ?? 0) < 2) {
            return name;
        }
        const suffix = uriSuffix(graphUri(graph));
        return suffix && suffix !== name ? `${name} (${suffix})` : name;
    };
}

/**
 * What hovering a schema tells you that its name does not: which profile version it claims to
 * be, and what it is for.
 *
 * A graph URI names the graph and nothing else — it is generated, and the reader never chose it
 * — so it appears only for a schema that states nothing about itself, where it is the one thing
 * left to say.
 */
export function graphTooltip(graph) {
    const versionIris = graph?.versionIris ?? [];
    const lines = [];
    if (!versionIris.length && !graph?.versionInfo && !graph?.description) {
        lines.push(graphUri(graph));
    }
    // The version a profile states in words, which CGMES 2.4.15 has nowhere to write.
    if (graph?.versionInfo) {
        lines.push(`Version ${graph.versionInfo}`);
    }
    lines.push(...versionIris);
    if (graph?.description) {
        lines.push(graph.description);
    }
    return lines.filter(Boolean).join("\n");
}
