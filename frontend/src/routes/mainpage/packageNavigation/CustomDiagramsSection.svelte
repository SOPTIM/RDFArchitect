<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -        http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -
  -->

<script>
    import { faObjectGroup, faPlus } from "@fortawesome/free-solid-svg-icons";
    import { onMount } from "svelte";

    import { BackendConnection } from "$lib/api/backend.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu/index.js";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    import CustomDiagramDialog from "./custom-diagram-dialogs/CustomDiagramDialog.svelte";
    import CustomDiagramButton from "./CustomDiagramButton.svelte";
    import { getUri, isSelectedDataset, isSelectedGraph } from "./packageNavigationUtils.svelte.js";

    let {
        dataset,
        graph,
        readOnly,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    
    let diagramsExpanded = $state(false);
    let diagrams = $state([]);
    let classesByDiagram = $state({});
    
    let showNewDiagramDialog = $state(false);

    onMount(() => {
        fetchDiagrams();
    });

    async function fetchDiagrams() {
        try {
            let diagramList;
            if (graph) {
                diagramList = await getGraphDiagrams(dataset.label, getUri(graph));
            } else {
                diagramList = await getDatasetDiagrams(dataset.label);
            }
            const previous = diagrams ?? [];
            const selectedDiagramId = editorState.selectedCustomDiagramUUID.getValue();

            diagrams = diagramList.map(diagram => {
                const prev = previous.find(p => diagram.diagramId === p.diagramId);
                const keepExpanded = prev?.showContents ?? false;
                const userCollapsed = prev?.userCollapsed ?? !keepExpanded;
                const isSelected =
                    (isSelectedGraph(dataset, graph) || !graph && isSelectedDataset(dataset)) &&
                    selectedDiagramId === diagram.diagramId;

                return {
                    ...diagram,
                    userCollapsed,
                    showContents: userCollapsed
                        ? false
                        : keepExpanded || isSelected,
                };
            });
        } catch (err) {
            console.error("Failed to load diagrams:", err);
        }
    }

    async function ensureClassesLoaded(diagram) {
        if (classesByDiagram[diagram.diagramId]) {
            return;
        }

        const classUuids = diagram.classes.map(c => c.uuid);
        const loaded = graph
            ? await bec.getClassesByUuids(dataset.label, getUri(graph), classUuids)
            : await bec.getGlobalClassesByUuids(dataset.label, classUuids);

        classesByDiagram[diagram.diagramId] = loaded;
    }

    async function getGraphDiagrams(datasetName, graphURI) {
        const res = await bec.getCustomDiagramsForGraph(datasetName, graphURI);
        return await res.json();
    }

    async function getDatasetDiagrams(datasetName) {
        const res = await bec.getCustomDiagramsForDataset(datasetName);
        return await res.json();
    }
</script>

<div class="h-0.5 bg-border my-1 ml-14"></div>
<ContextMenu.Root>
    <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
        <NavigationEntry
            level={3}
            label="Custom Diagrams"
            icon={faObjectGroup}
            hasChildren={diagrams.length > 0}
            expanded={diagramsExpanded}
            isSelected={
                isSelectedGraph(dataset, graph) &&
                editorState.selectedCustomDiagramUUID.getValue() !== null
            }
            onToggle={() => diagramsExpanded = !diagramsExpanded}
        />
    </ContextMenu.TriggerArea>
    <ContextMenu.Content>
        <ContextMenu.Item.Button
            onSelect={() => {
                showNewDiagramDialog = true;
            }}
            faIcon={faPlus}
        >
            New Diagram
        </ContextMenu.Item.Button>
    </ContextMenu.Content>
</ContextMenu.Root>
{#if diagramsExpanded && diagrams.length > 0}
    {#each diagrams as diagram (diagram.diagramId)}
        <CustomDiagramButton
            {dataset}
            {graph}
            {diagram}
            {readOnly}
        />
    {/each}
{/if}

<CustomDiagramDialog
    bind:showDialog={showNewDiagramDialog}
    lockedDatasetName={dataset.label}
    lockedGraphUri={getUri(graph)}
/>
