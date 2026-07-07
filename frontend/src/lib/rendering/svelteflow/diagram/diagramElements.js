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

export const EDGE_Z_INDEX = -1;

const offsetEdgeIdCache = new WeakMap();

export function hasDefaultNodeLayout(diagramNodes) {
    return (
        diagramNodes.length > 0 &&
        diagramNodes.every(
            node => node.position.x === 0 && node.position.y === 0,
        )
    );
}

function offsetEdgeIds(edges) {
    let ids = offsetEdgeIdCache.get(edges);
    if (!ids) {
        ids = new Set();
        const associationPairs = new Set();
        for (const edge of edges) {
            if (edge.type === "association") {
                associationPairs.add(`${edge.source}|${edge.target}`);
                associationPairs.add(`${edge.target}|${edge.source}`);
            }
        }
        for (const edge of edges) {
            if (
                edge.type === "inheritance" &&
                associationPairs.has(`${edge.source}|${edge.target}`)
            ) {
                ids.add(edge.id);
            }
        }
        offsetEdgeIdCache.set(edges, ids);
    }
    return ids;
}

export function decorateEdges(edges) {
    const offsetIds = offsetEdgeIds(edges);
    return edges.map(edge => decorateEdge(edge, offsetIds));
}

function decorateEdge(edge, offsetIds) {
    const decorated = { ...edge, zIndex: EDGE_Z_INDEX };

    if (!offsetIds.has(edge.id)) {
        return decorated;
    }

    return {
        ...decorated,
        data: {
            ...(edge.data || {}),
            offsetEdge: true,
        },
    };
}
