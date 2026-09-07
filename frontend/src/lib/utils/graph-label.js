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
 * The navigation tree, the changelog and every schema picker label a graph by its `dcat:keyword`
 * and fall back to the tail of its URI, so anything else that names a schema has to agree with
 * them — a heading that says `http://iec.ch/TC57/…/EquipmentProfile/1` is naming the same schema
 * the tree calls `Equipment`.
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

/** A single graph's label: its keyword, or the tail of its URI when it has none. */
export function graphLabel(graph) {
    return graph?.keyword || uriSuffix(graphUri(graph));
}

/**
 * The label of the graph with `uri` among `graphs`.
 *
 * Falls back to the URI's tail for a graph that is not in the list — the list is fetched, so a
 * caller can be asking before it has arrived, and a heading is better with an approximate name
 * than with an empty space that fills in later.
 */
export function graphLabelOf(graphs, uri) {
    const match = (graphs ?? []).find(graph => graphUri(graph) === uri);
    return match ? graphLabel(match) : uriSuffix(uri);
}
