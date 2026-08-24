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

    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import {
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
    import { customDiagramStore } from "$lib/stores/diagramStore.ts";

    import CustomDiagramButton from "./CustomDiagramButton.svelte";
    import {
        isSelectedWorkspace,
        isSelectedGraph,
    } from "./packageNavigationUtils.svelte.js";

    let { workspaceNavEntry, graphNavEntry, readonly } = $props();

    let diagramsExpanded = $state(false);
    let diagrams = $state([]);
    let classesByDiagram = $state({});
    let mergedClassLookup = null;

    let isSelected = $derived(
        graphNavEntry
            ? isSelectedGraph(workspaceNavEntry.id, graphNavEntry.id) &&
                  editorState.selectedDiagram.getProperty("type") ===
                      DiagramType.CUSTOM_GRAPH_DIAGRAM
            : !editorState.selectedGraph.getValue() &&
                  isSelectedWorkspace(workspaceNavEntry.id) &&
                  editorState.selectedDiagram.getProperty("type") ===
                      DiagramType.CUSTOM_WORKSPACE_DIAGRAM,
    );
    let level = $derived(graphNavEntry ? 2 : 1);
    let label = $derived(
        graphNavEntry ? "Schema Diagrams" : "Workspace Diagrams",
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
                diagramList = await customDiagramStore.getGraphDiagrams(
                    workspaceNavEntry.id,
                    graphNavEntry.id,
                );
            } else {
                diagramList = await customDiagramStore.getWorkspaceDiagrams(
                    workspaceNavEntry.id,
                );
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
            mergedClassLookup = null;
            const reloaded = await Promise.all(
                diagrams
                    .filter(d => d.showContents)
                    .map(async d => [d.diagramId, await classesOf(d)]),
            );
            classesByDiagram = Object.fromEntries(reloaded);
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
            const userCollapsed = prev?.userCollapsed ?? false;
            const isSelected = graphNavEntry
                ? isSelectedGraph(workspaceNavEntry.id, graphNavEntry.id) &&
                  selectedDiagramId === diagram.diagramId
                : isSelectedWorkspace(workspaceNavEntry.id) &&
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
        try {
            const classes = await classesOf(diagram);
            classesByDiagram = {
                ...classesByDiagram,
                [diagram.diagramId]: classes,
            };
        } catch (err) {
            console.error("Failed to load diagram classes:", err);
        }
    }

    async function classesOf(diagram) {
        return graphNavEntry
            ? classesOfSchema(diagram)
            : mergedClassesOf(diagram, await mergedClassBySourceUuid());
    }

    function classesOfSchema(diagram) {
        const parent = {
            id: diagram.diagramId,
            open: () => selectDiagram(diagram),
        };
        return graphNavEntry.children
            .map(pack =>
                pack.children.filter(cls =>
                    diagram.classes.some(dc => dc.uuid === cls.id),
                ),
            )
            .flat()
            .map(cls => ({
                id: cls.id,
                label: cls.label,
                tooltip: cls.tooltip,
                parent,
            }));
    }

    function mergedClassBySourceUuid() {
        if (!mergedClassLookup) {
            const request = crossProfileStore
                .getDiagram(workspaceNavEntry.id)
                .then(crossProfileDiagram => {
                    const bySourceUuid = new Map();
                    for (const merged of crossProfileDiagram?.classes ?? []) {
                        for (const source of merged.sources ?? []) {
                            bySourceUuid.set(source.classUUID, merged);
                        }
                    }
                    return bySourceUuid;
                });
            mergedClassLookup = request;
            request.catch(() => {
                if (mergedClassLookup === request) {
                    mergedClassLookup = null;
                }
            });
        }
        return mergedClassLookup;
    }

    function mergedClassesOf(diagram, bySourceUuid) {
        const parent = {
            id: diagram.diagramId,
            open: () => selectDiagram(diagram),
        };
        const classes = [];
        const seen = new Set();
        for (const diagramClass of diagram.classes) {
            const merged = bySourceUuid.get(diagramClass.uuid);
            if (!merged || seen.has(merged.uuid)) {
                continue;
            }
            seen.add(merged.uuid);
            classes.push({
                id: merged.uuid,
                label: merged.label,
                tooltip: merged.classUri,
                parent,
            });
        }
        return classes;
    }

    function handleClick() {
        selectDiagram(null);
    }

    function selectDiagram(diagram) {
        const diagramType = graphNavEntry
            ? DiagramType.CUSTOM_GRAPH_DIAGRAM
            : DiagramType.CUSTOM_WORKSPACE_DIAGRAM;
        editorState.selectCustomDiagram(
            workspaceNavEntry.id,
            graphNavEntry?.id,
            diagram?.diagramId ?? null,
            diagramType,
        );
    }
</script>

{#if diagrams.length > 0}
    <div
        class="bg-border my-1 ml-10 h-0.5"
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
                {workspaceNavEntry}
                {graphNavEntry}
                bind:diagram={diagrams[index]}
                classes={classesByDiagram[diagram.diagramId]}
                {readonly}
                level={graphNavEntry ? 3 : 2}
                onToggle={() => ensureClassesLoaded(diagram)}
            />
        {/each}
    {/if}
{/if}
