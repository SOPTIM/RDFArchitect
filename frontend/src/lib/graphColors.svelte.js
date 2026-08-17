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
    getCrossProfileColors,
    updateCrossProfileColors,
} from "$lib/api/generated/index.ts";
import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import {
    DiagramType,
    editorState,
    forceReloadTrigger,
} from "$lib/sharedState.svelte.js";
import { normalizeHex } from "$lib/utils/color.js";

/** Fallback colors for schemas that never got one assigned. */
const DEFAULT_COLORS = [
    "#2563eb",
    "#dc2626",
    "#16a34a",
    "#d97706",
    "#9333ea",
    "#0891b2",
    "#db2777",
    "#65a30d",
];

export const graphColors = createGraphColors();

function defaultColorFor(index) {
    return DEFAULT_COLORS[index % DEFAULT_COLORS.length];
}

function createGraphColors() {
    let byDataset = $state({});
    const pendingLoads = new Map();

    async function load(datasetName) {
        const { data, error } = await getCrossProfileColors({
            path: { datasetName: datasetName },
        });
        if (error) {
            console.error(
                `Failed to load schema colors for "${datasetName}"`,
                error,
            );
            return {};
        }

        const stored = data.graphColors ?? {};

        const resolved = {};
        const seeded = {};
        Object.keys(stored)
            .sort((a, b) => a.localeCompare(b))
            .forEach((graphUri, index) => {
                const existing = normalizeHex(stored[graphUri]);
                resolved[graphUri] = existing ?? defaultColorFor(index);
                if (!existing) {
                    seeded[graphUri] = resolved[graphUri];
                }
            });

        byDataset[datasetName] = resolved;
        await put(datasetName, seeded);
        return resolved;
    }

    function startLoad(datasetName) {
        const running = load(datasetName)
            .catch(err => {
                console.error(
                    `Error fetching schema colors for dataset "${datasetName}"`,
                    err,
                );
                return {};
            })
            .finally(() => pendingLoads.delete(datasetName));
        pendingLoads.set(datasetName, running);
        return running;
    }

    return {
        get(datasetName, graphUri) {
            return byDataset[datasetName]?.[graphUri] ?? null;
        },

        reload(datasetName) {
            if (!datasetName) {
                return Promise.resolve({});
            }
            return pendingLoads.get(datasetName) ?? startLoad(datasetName);
        },

        async set(datasetName, graphUri, color) {
            const hex = normalizeHex(color);
            if (!hex) {
                return false;
            }
            const previous = byDataset[datasetName] ?? {};
            byDataset[datasetName] = { ...previous, [graphUri]: hex };

            const saved = await put(datasetName, { [graphUri]: hex });
            if (!saved) {
                byDataset[datasetName] = previous;
                toastStore.error(
                    "Save failed",
                    `Could not save the color for "${graphUri}".`,
                );
                return false;
            }
            refreshMergedViewIfVisible();
            return true;
        },

        async replaceAll(datasetName, colorsByGraph) {
            const previous = byDataset[datasetName] ?? {};
            byDataset[datasetName] = {
                ...previous,
                ...normalizeColors(colorsByGraph),
            };

            const saved = await put(datasetName, colorsByGraph);
            if (!saved) {
                byDataset[datasetName] = previous;
                return false;
            }
            refreshMergedViewIfVisible();
            return true;
        },
    };
}

function normalizeColors(colorsByGraph) {
    return Object.fromEntries(
        Object.entries(colorsByGraph)
            .map(([graphUri, color]) => [graphUri, normalizeHex(color)])
            .filter(([, color]) => color !== null),
    );
}

/**
 * Only the merged view renders the colors server-side, so a reload is
 * pointless - and disruptive, since it rebuilds the navigation - unless it
 * is the diagram currently on screen.
 */
function refreshMergedViewIfVisible() {
    if (
        editorState.selectedDiagram.getProperty("type") ===
        DiagramType.CROSS_PROFILE
    ) {
        forceReloadTrigger.trigger();
    }
}

async function put(datasetName, colorsByGraph) {
    const payload = normalizeColors(colorsByGraph);
    if (Object.keys(payload).length === 0) {
        return Object.keys(colorsByGraph).length === 0;
    }
    try {
        const { error } = await updateCrossProfileColors({
            path: { datasetName: datasetName },
            body: { graphColors: payload },
        });
        if (error) {
            console.error(`Failed to save schema colors for "${datasetName}"`);
        }
        return true;
    } catch (err) {
        console.error(`Failed to save schema colors for "${datasetName}"`, err);
        return false;
    }
}
