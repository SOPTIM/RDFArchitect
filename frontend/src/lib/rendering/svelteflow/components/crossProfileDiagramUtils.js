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

export function mapCrossProfileDiagramToFlow(dto) {
    const uuidByClassUri = new Map(dto.classes.map(c => [c.classUri, c.uuid]));

    const nodes = dto.classes.map(mergedClass => {
        console.log("attributes raw:", mergedClass.attributes);
        console.log("enumEntries raw:", mergedClass.enumEntries);
        return {
            id: mergedClass.uuid,
            type: "class",
            position: { x: 0, y: 0 },
            data: {
                label: extractLabel(mergedClass.classUri),
                stereotypes: [],
                attributes: mergedClass.attributes.map(graphSourced => ({
                    label: graphSourced.value.label ?? "",
                    type: graphSourced.value.dataType
                        ? (graphSourced.value.dataType.label ?? "")
                        : "",
                    multiplicity: graphSourced.value.multiplicity ?? "",
                })),
                enumEntries: mergedClass.enumEntries.map(
                    graphSourced => graphSourced.value.label ?? "",
                ),
                belongsToCategory: null,
                graphUri: mergedClass.sources[0]?.graphUri ?? null,
            },
        };
    });

    const edges = buildEdges(dto.classes, uuidByClassUri);

    return { nodes, edges };
}

function extractLabel(classUri) {
    const hash = classUri.lastIndexOf("#");
    const slash = classUri.lastIndexOf("/");
    const idx = Math.max(hash, slash);
    return idx >= 0 ? classUri.substring(idx + 1) : classUri;
}

function buildEdges(classes, uuidByClassUri) {
    const edges = [];
    const edgeIds = new Set();

    for (const mergedClass of classes) {
        for (const graphSourced of mergedClass.associationPairs) {
            const pair = graphSourced.value;
            const targetPrefix = pair.to?.range?.prefix ?? "";
            const targetLabel = pair.to?.range?.label ?? "";
            const targetClassUri = targetPrefix + targetLabel;
            const targetUuid = uuidByClassUri.get(targetClassUri);

            if (!targetUuid) continue;

            const edgeId = `${mergedClass.uuid}→${pair.from?.uuid ?? pair.to?.uuid}→${targetUuid}`;
            if (edgeIds.has(edgeId)) continue;
            edgeIds.add(edgeId);

            edges.push({
                id: edgeId,
                source: mergedClass.uuid,
                target: targetUuid,
                type: "association",
                data: {
                    label: pair.from?.label ?? "",
                    multiplicity: pair.to?.multiplicity ?? "",
                },
            });
        }
    }

    return edges;
}
