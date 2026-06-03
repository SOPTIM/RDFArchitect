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

import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import { editorState, forceReloadTrigger } from "$lib/sharedState.svelte.js";

function resolveTargets(datasetName, graphURI) {
    const dataset = datasetName ?? editorState.selectedDataset.getValue();
    const graph = graphURI ?? editorState.selectedGraph.getValue();

    if (!dataset || !graph) return null;

    return {
        dataset,
        graph,
        encodedDataset: encodeURIComponent(dataset),
        encodedGraph: encodeURIComponent(graph),
    };
}

export async function fetchCanUndo(datasetName, graphURI) {
    const targets = resolveTargets(datasetName, graphURI);
    if (!targets) return false;

    const res = await fetch(
        `${PUBLIC_BACKEND_URL}/datasets/${targets.encodedDataset}/graphs/${targets.encodedGraph}/canUndo`,
        {
            method: "POST",
            credentials: "include",
        },
    );

    if (res.ok) {
        const text = await res.text();
        return text === "true";
    }

    console.log("Failed to fetch canUndo status.");
    return false;
}

export function undo(datasetName, graphURI) {
    return eventStack.guardAction(async () => {
        const targets = resolveTargets(datasetName, graphURI);
        if (!targets) return false;

        const res = await fetch(
            `${PUBLIC_BACKEND_URL}/datasets/${targets.encodedDataset}/graphs/${targets.encodedGraph}/undo`,
            {
                method: "POST",
                credentials: "include",
            },
        );

        if (res.ok) {
            console.log("Undo successful.");
            forceReloadTrigger.trigger();
            toastStore.info("Undone");
            return true;
        } else {
            console.log("Undo failed.");
            toastStore.error("Undo failed", "Could not undo the last change.");
            return false;
        }
    });
}

export async function fetchCanRedo(datasetName, graphURI) {
    const targets = resolveTargets(datasetName, graphURI);
    if (!targets) return false;

    const res = await fetch(
        `${PUBLIC_BACKEND_URL}/datasets/${targets.encodedDataset}/graphs/${targets.encodedGraph}/canRedo`,
        {
            method: "POST",
            credentials: "include",
        },
    );

    if (res.ok) {
        const text = await res.text();
        return text === "true";
    }

    console.log("Failed to fetch canRedo status.");
    return false;
}

export function redo(datasetName, graphURI) {
    return eventStack.guardAction(async () => {
        const targets = resolveTargets(datasetName, graphURI);
        if (!targets) return false;

        const res = await fetch(
            `${PUBLIC_BACKEND_URL}/datasets/${targets.encodedDataset}/graphs/${targets.encodedGraph}/redo`,
            {
                method: "POST",
                credentials: "include",
            },
        );

        if (res.ok) {
            console.log("Redo successful.");
            forceReloadTrigger.trigger();
            toastStore.info("Redone");
            return true;
        } else {
            console.log("Redo failed.");
            toastStore.error("Redo failed", "Could not redo the change.");
            return false;
        }
    });
}
