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
 * The navigation tree, the changelog and every schema picker name a graph the same way, so the
 * rule lives here rather than in each of them: a profile's own name first, its keyword when it
 * has no name, and the tail of its URI when it is not a CIM profile at all.
 */

import { uriSuffix } from "./iri.js";

/** The full URI of a graph as the backend sends it: a `GraphDto` split into prefix and suffix. */
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

/** The version a schema states, or "" for one that states none. CGMES 2.4.15 never does. */
export function graphVersion(graph) {
    return graph?.versionInfo || "";
}

/**
 * What hovering a schema tells you that its name does not: which graph it is, which profile
 * version it claims to be, and what it is for.
 */
export function graphTooltip(graph) {
    const lines = [graphUri(graph)];
    lines.push(...(graph?.versionIris ?? []));
    if (graph?.description) {
        lines.push(graph.description);
    }
    return lines.filter(Boolean).join("\n");
}

/**
 * The name of the graph with `uri` among `graphs`.
 *
 * Falls back to the URI's tail for a graph that is not in the list — the list is fetched, so a
 * caller can be asking before it has arrived, and a heading is better with an approximate name
 * than with an empty space that fills in later.
 */
export function graphLabelOf(graphs, uri) {
    const match = (graphs ?? []).find(graph => graphUri(graph) === uri);
    return match ? graphLabeller(graphs)(match) : uriSuffix(uri);
}
