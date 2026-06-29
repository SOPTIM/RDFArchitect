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
    import { faObjectGroup } from "@fortawesome/free-solid-svg-icons";
    import { onMount } from "svelte";

    import { BackendConnection } from "$lib/api/backend.js";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";
    import {
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import CustomDiagramButton from "./CustomDiagramButton.svelte";
    import {
        isSelectedDataset,
        isSelectedGraph,
    } from "./packageNavigationUtils.svelte.js";

    let { datasetNavEntry, graphNavEntry, allGraphNavEntries, readonly } =
        $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let diagramsExpanded = $state(false);
    let diagrams = $state([]);
    let classesByDiagram = $state({});

    let isSelected = $derived(
        graphNavEntry
            ? isSelectedGraph(datasetNavEntry.id, graphNavEntry.id) &&
                  editorState.selectedDiagram.getProperty("type") ===
                      DiagramType.CUSTOM_GRAPH_DIAGRAM
            : !editorState.selectedGraph.getValue() &&
                  isSelectedDataset(datasetNavEntry.id) &&
                  editorState.selectedDiagram.getProperty("type") ===
                      DiagramType.CUSTOM_DATASET_DIAGRAM,
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
        editorState.selectedDiagram.subscribe();
        const selectedDiagramId = editorState.selectedDiagram.getProperty("id");

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
                editorState.selectedDiagram.getProperty("id");

            diagrams = ensureProperDiagramExpansion(
                diagramList,
                previous,
                selectedDiagramId,
            );

            //reset classes after potential removal
            classesByDiagram = {};
            const expandedDiagrams = diagrams.filter(d => d.showContents);
            await Promise.all(
                expandedDiagrams.map(d => ensureClassesLoaded(d)),
            );
        } catch (err) {
            console.error("Failed to load diagrams:", err);
        }
    }

    function ensureProperDiagramExpansion(
        diagramList,
        previous,
        selectedDiagramId,
    ) {
        return diagramList.map(diagram => {
            const prev = previous.find(p => diagram.diagramId === p.diagramId);
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

    function handleClick() {
        const diagramType = graphNavEntry
            ? DiagramType.CUSTOM_GRAPH_DIAGRAM
            : DiagramType.CUSTOM_DATASET_DIAGRAM;
        editorState.selectCustomDiagram(
            datasetNavEntry.id,
            graphNavEntry?.id,
            null,
            diagramType,
        );
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

{#if diagrams.length > 0}
    <div
        class="bg-border my-1 ml-14 h-0.5"
        role="presentation"
        oncontextmenu={e => e.stopPropagation()}
    ></div>
    <div
        class="flex w-full flex-col items-stretch"
        role="presentation"
        oncontextmenu={e => e.stopPropagation()}
    >
        <NavigationEntry
            {level}
            {label}
            icon={faObjectGroup}
            hasChildren={diagrams.length > 0}
            expanded={diagramsExpanded}
            {isSelected}
            onToggle={() => (diagramsExpanded = !diagramsExpanded)}
            onclick={handleClick}
        />
    </div>
    {#if diagramsExpanded && diagrams.length > 0}
        {#each diagrams as diagram, index (diagram.diagramId)}
            <CustomDiagramButton
                {datasetNavEntry}
                {graphNavEntry}
                {allGraphNavEntries}
                bind:diagram={diagrams[index]}
                classes={classesByDiagram[diagram.diagramId]}
                {readonly}
                level={graphNavEntry ? 4 : 3}
                onToggle={() => ensureClassesLoaded(diagram)}
            />
        {/each}
    {/if}
{/if}
