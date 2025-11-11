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
    import { faBoxOpen } from "@fortawesome/free-solid-svg-icons";
    import { onMount } from "svelte";

    import { BackendConnection } from "$lib/api/backend.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import ZoomAndDraggableMermaid from "$lib/mermaid/zoomAndDraggableMermaid.svelte";
    import {
        editorState,
        forceReloadTrigger,
        graphViewState,
    } from "$lib/sharedState.svelte.js";

    import FilterViewDialog from "../FilterViewDialog.svelte";

    /** @type {{ rightInsetPercent?: number }} */
    let { rightInsetPercent = 0 } = $props();

    let mermaidString = $state();
    let showFilterDialog = $state(false);
    let zoomAndDraggableMermaid = $state();

    let displayDiagram = $derived(
        !mermaidString || mermaidString.includes("class "),
    );

    $effect(async () => {
        editorState.selectedDataset.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedPackageUUID.subscribe();
        forceReloadTrigger.subscribe();

        if (!editorState.selectedPackageUUID.getValue()) return;

        let graphFilter = {
            packageUUID: editorState.selectedPackageUUID.getValue(),
            includeEnumEntries:
                graphViewState.filter.getValue().includeEnumEntries,
            includeAttributes:
                graphViewState.filter.getValue().includeAttributes,
            includeAssociations:
                graphViewState.filter.getValue().includeAssociations,
            includeInheritance:
                graphViewState.filter.getValue().includeInheritance,
            includeRelationsToExternalPackages:
                graphViewState.filter.getValue()
                    .includeRelationsToExternalPackages,
        };
        new BackendConnection(fetch, PUBLIC_BACKEND_URL)
            .fetchMermaidUMLFiltered(
                editorState.selectedDataset.getValue(),
                editorState.selectedGraph.getValue(),
                graphFilter,
            )
            .then(res => res.text())
            .then(newMMString => (mermaidString = newMMString));
    });

    onMount(() => {
        /*
			getClassInformation is the name of the function that is called, when clicking a class in the mermaid diagram.
			The name callbackFunction is defined when creating the mermaidString (in our case in the backend).
			So we have to coordinate the function names manually
		 */
        window.getClassInformation = nodeId => {
            console.log("selecting class: ", nodeId);
            if (!editorState.selectedClassUUID.getValue()) {
                eventStack.executeNewestEvent(nodeId);
                editorState.selectedClassDataset.updateValue(
                    editorState.selectedDataset.getValue(),
                );
                editorState.selectedClassGraph.updateValue(
                    editorState.selectedGraph.getValue(),
                );
                editorState.selectedClassUUID.updateValue(nodeId);
                return;
            }
            eventStack.executeNewestEvent({
                datasetName: editorState.selectedDataset.getValue(),
                graphUri: editorState.selectedGraph.getValue(),
                classUuid: nodeId,
            });
        };
    });
</script>

{#if editorState.selectedPackageUUID.getValue()}
    <div class="bg-window-background flex h-full flex-col justify-between">
        <div class="relative h-full overflow-hidden">
            {#if displayDiagram}
                <div class="relative z-1 mt-1 ml-1 flex space-x-0.5">
                    <div class="h-9 w-28">
                        <ButtonControl
                            callOnClick={() =>
                                zoomAndDraggableMermaid.resetTransform()}
                        >
                            reset view
                        </ButtonControl>
                    </div>
                    <div class="h-9 w-28">
                        <ButtonControl
                            callOnClick={() => (showFilterDialog = true)}
                        >
                            filter view
                        </ButtonControl>
                    </div>
                </div>
                <ZoomAndDraggableMermaid
                    bind:this={zoomAndDraggableMermaid}
                    {mermaidString}
                    {rightInsetPercent}
                />
            {:else}
                <div
                    class="absolute top-0 bottom-0 left-0 flex items-center justify-center"
                    style="width: calc(100% - {rightInsetPercent}%);"
                >
                    <EmptyStateCard
                        title="No classes in this package"
                        description="Select another package to load a different diagram."
                        icon={faBoxOpen}
                    />
                </div>
            {/if}
        </div>
    </div>
{:else}
    <div class="bg-window-background flex h-full flex-col justify-between">
        <div class="relative h-full overflow-hidden">
            <div
                class="absolute top-0 bottom-0 left-0 flex items-center justify-center"
                style="width: calc(100% - {rightInsetPercent}%);"
            >
                <EmptyStateCard
                    title="No diagram requested yet"
                    description="Select a package to load and render its diagram."
                />
            </div>
        </div>
    </div>
{/if}
<FilterViewDialog bind:showDialog={showFilterDialog} />
