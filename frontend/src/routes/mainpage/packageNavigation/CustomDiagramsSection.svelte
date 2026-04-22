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
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import CustomDatasetDiagramDialog from "./custom-diagram-dialogs/CustomDatasetDiagramDialog.svelte";
    import CustomGraphDiagramDialog from "./custom-diagram-dialogs/CustomGraphDiagramDialog.svelte";
    import CustomDiagramButton from "./CustomDiagramButton.svelte";
    import {
        isSelectedDataset,
        isSelectedGraph,
    } from "./packageNavigationUtils.svelte.js";

    let { datasetNavEntry, graphNavEntry, allGraphNavEntries, readOnly } =
        $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let diagramsExpanded = $state(false);
    let diagrams = $state([]);
    let classesByDiagram = $state({});

    let showNewDiagramDialog = $state(false);
    let isSelected = $derived(
        graphNavEntry
            ? isSelectedGraph(datasetNavEntry.id, graphNavEntry.id) &&
                  editorState.selectedCustomDiagramUUID.getValue()
            : !editorState.selectedGraph.getValue() &&
                  isSelectedDataset(datasetNavEntry.id) &&
                  editorState.selectedCustomDiagramUUID.getValue(),
    );
    let level = $derived(graphNavEntry ? 3 : 2);
    let label = $derived(
        graphNavEntry ? "Custom Profile Diagrams" : "Custom Dataset Diagrams",
    );

    $effect(() => {
        forceReloadTrigger.subscribe();
        fetchDiagrams();
    });

    $effect(() => {
        editorState.selectedCustomDiagramUUID.subscribe();
        const selectedDiagramId =
            editorState.selectedCustomDiagramUUID.getValue();

        if (selectedDiagramId) {
            if (diagrams.some(d => d.diagramId === selectedDiagramId)) {
                diagramsExpanded = true;
            }
        }
    });

    onMount(() => {
        fetchDiagrams();
    });

    async function fetchDiagrams() {
        try {
            let diagramList;
            if (graphNavEntry) {
                diagramList = await getGraphDiagrams(
                    datasetNavEntry.id,
                    graphNavEntry.id,
                );
            } else {
                diagramList = await getDatasetDiagrams(datasetNavEntry.id);
            }
            const previous = diagrams ?? [];
            const selectedDiagramId =
                editorState.selectedCustomDiagramUUID.getValue();

            diagrams = diagramList.map(diagram => {
                const prev = previous.find(
                    p => diagram.diagramId === p.diagramId,
                );
                const keepExpanded = prev?.showContents ?? false;
                const userCollapsed = prev?.userCollapsed ?? !keepExpanded;
                const isSelected = graphNavEntry
                    ? isSelectedGraph(datasetNavEntry, graphNavEntry) &&
                      selectedDiagramId === diagram.diagramId
                    : isSelectedDataset(datasetNavEntry) &&
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

        let classes = [];
        if (graphNavEntry) {
            classes = graphNavEntry.children
                .map(pack =>
                    pack.children.filter(cls =>
                        diagram.classes.some(dc => dc.uuid === cls.id),
                    ),
                )
                .flat();
        } else {
            allGraphNavEntries.forEach(graph => {
                let classesInGraph = graph.children
                    .map(pack =>
                        pack.children.filter(cls =>
                            diagram.classes.some(dc => dc.uuid === cls.id),
                        ),
                    )
                    .flat();

                classes.push(...classesInGraph);
            });
        }
        classesByDiagram[diagram.diagramId] = classes;
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

<div class="bg-border my-1 ml-14 h-0.5"></div>
<ContextMenu.Root>
    <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
        <NavigationEntry
            {level}
            {label}
            icon={faObjectGroup}
            hasChildren={diagrams.length > 0}
            expanded={diagramsExpanded}
            {isSelected}
            onToggle={() => (diagramsExpanded = !diagramsExpanded)}
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
            {datasetNavEntry}
            {graphNavEntry}
            {allGraphNavEntries}
            {diagram}
            classes={classesByDiagram[diagram.diagramId]}
            {readOnly}
            level={graphNavEntry ? 4 : 3}
            onToggle={() => ensureClassesLoaded(diagram)}
        />
    {/each}
{/if}

{#if graphNavEntry}
    <CustomGraphDiagramDialog
        bind:showDialog={showNewDiagramDialog}
        lockedDatasetName={datasetNavEntry.id}
        lockedGraphUri={graphNavEntry.id}
    />
{:else}
    <CustomDatasetDiagramDialog
        bind:showDialog={showNewDiagramDialog}
        lockedDatasetName={datasetNavEntry.id}
    />
{/if}
