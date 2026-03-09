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
    import { SvelteFlowProvider } from "@xyflow/svelte";

    import { BackendConnection } from "$lib/api/backend.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import MermaidWrapper from "$lib/rendering/mermaid/mermaidWrapper.svelte";
    import SvelteFlowWrapper from "$lib/rendering/svelteflow/svelteFlowWrapper.svelte";
    import { editorState, graphViewState, forceReloadTrigger } from "$lib/sharedState.svelte.js";

    import FilterViewDialog from "../FilterViewDialog.svelte";

    /** @type {{ rightInsetPercent?: number }} */
    let { rightInsetPercent = 0 } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const MERMAID_FORMAT = "MERMAID";
    const SVELTEFLOW_FORMAT = "SVELTEFLOW";

    let isLoading = $state(false);

    let svelteFlowAPI = $state({});

    let showFilterDialog = $state(false);
    let response = $state(null);
    let isDatasetReadOnly = $state();
    let renderingFormat = $state(null);
    let mermaidWrapper = $state();
    let svelteFlowWrapper = $state();

    let displayDiagram = $derived(true);

    $effect(async () => {
        const dataset = editorState.selectedDataset.getValue();
        isDatasetReadOnly = dataset ? await isReadOnly(dataset) : false;
    });

    $effect(async () => {
        isLoading = true;
        forceReloadTrigger.subscribe();
        editorState.selectedDataset.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedPackageUUID.subscribe();

        if (!editorState.selectedPackageUUID.getValue()) {
            response = null;
            renderingFormat = null;
            return;
        }

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

        try {
            const res = await bec.fetchFilteredRenderingData(
                editorState.selectedDataset.getValue(),
                editorState.selectedGraph.getValue(),
                graphFilter,
            );

            const responseText = await res.text();
            if (!responseText) {
                displayDiagram = false;
            } else {
                response = JSON.parse(responseText);
                renderingFormat = response.format;
                displayDiagram = true;
            }
        } catch (error) {
            console.error("Error fetching diagram data:", error);
            response = null;
            renderingFormat = null;
        }
    });

    async function isReadOnly(datasetName) {
        const res = await bec.isReadOnly(datasetName);
        return await res.json();
    }

    function handleResetView() {
        if (renderingFormat === MERMAID_FORMAT) {
            mermaidWrapper.resetTransform();
        } else if (renderingFormat === SVELTEFLOW_FORMAT) {
            svelteFlowAPI.svelteFlow.fitView();
        }
    }
</script>

{#if editorState.selectedPackageUUID.getValue()}
    <div class="bg-window-background flex h-full flex-col justify-between">
        <div class="relative h-full">
            {#if displayDiagram}
                <div
                    class="absolute top-1 left-1 z-1 flex flex-col space-y-0.5"
                >
                    <div class="h-9 w-28">
                        <ButtonControl
                            variant="default"
                            callOnClick={() => handleResetView()}
                        >
                            reset view
                        </ButtonControl>
                    </div>
                    <div class="h-9 w-28">
                        <ButtonControl
                            variant="default"
                            callOnClick={() => (showFilterDialog = true)}
                        >
                            filter view
                        </ButtonControl>
                    </div>
                    {#if !isDatasetReadOnly && renderingFormat === SVELTEFLOW_FORMAT}
                        <div class="h-9 w-28">
                            <ButtonControl
                                variant="default"
                                callOnClick={async () =>
                                    await svelteFlowWrapper.applyELKLayout()}
                            >
                                <span class="text-sm">reset layout</span>
                            </ButtonControl>
                        </div>
                    {/if}
                </div>
                {#if isLoading}
                    <div
                        class="bg-window-background absolute inset-0 z-10 flex items-center justify-center"
                        style="width: calc(100% - {rightInsetPercent}%);"
                    >
                        <LoadingSpinner ariaLabel="Loading diagram" />
                    </div>
                {/if}
                {#if renderingFormat === MERMAID_FORMAT}
                    <MermaidWrapper
                        bind:isLoading
                        bind:this={mermaidWrapper}
                        mermaidString={response.mermaidString}
                    />
                {:else if renderingFormat === SVELTEFLOW_FORMAT}
                    <SvelteFlowProvider>
                        <SvelteFlowWrapper
                            bind:isLoading
                            bind:svelteFlowAPI
                            bind:this={svelteFlowWrapper}
                            nodes={JSON.parse(
                                JSON.stringify(response.nodes || []),
                            )}
                            edges={JSON.parse(
                                JSON.stringify(response.edges || []),
                            )}
                        />
                    </SvelteFlowProvider>
                {/if}
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
