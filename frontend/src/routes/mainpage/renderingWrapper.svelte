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
    import { untrack } from "svelte";

    import {
        getCustomDatasetViewRenderingData,
        getCustomProfileViewRenderingData,
        getRenderingDataParameterized,
    } from "$lib/api/generated/index.ts";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import MermaidWrapper from "$lib/rendering/mermaid/mermaidWrapper.svelte";
    import SvelteFlowWrapper from "$lib/rendering/svelteflow/svelteFlowWrapper.svelte";
    import {
        editorState,
        graphViewState,
        forceReloadTrigger,
        DiagramType,
    } from "$lib/sharedState.svelte.js";
    import { crossProfileStore } from "$lib/stores/CrossProfileStore.ts";
    import { datasetStore } from "$lib/stores/DatasetStore.ts";

    import FilterViewDialog from "../FilterViewDialog.svelte";

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

    let displayDiagram = $state(true);
    let diagramRequestKey = null;
    let showSvelteFlowEmptyState = $derived(
        renderingFormat === SVELTEFLOW_FORMAT &&
            (response?.nodes?.length ?? 0) === 0,
    );

    $effect(async () => {
        forceReloadTrigger.subscribe();
        editorState.selectedDataset.subscribe();
        const dataset = editorState.selectedDataset.getValue();
        isDatasetReadOnly = datasetStore.isReadOnly(dataset);
    });

    $effect(async () => {
        forceReloadTrigger.subscribe();
        editorState.selectedDataset.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedDiagram.subscribe();

        const datasetName = editorState.selectedDataset.getValue();
        const graphUri = editorState.selectedGraph.getValue();
        const diagramId = editorState.selectedDiagram.getProperty("id");
        const diagramType = editorState.selectedDiagram.getProperty("type");
        const filter = graphViewState.filter.getValue();

        const nextDiagramRequestKey = getDiagramRequestKey(
            datasetName,
            graphUri,
            diagramId,
            filter,
        );
        const hasCurrentResponse = untrack(() => !!response);
        const showBlockingLoading =
            nextDiagramRequestKey !== diagramRequestKey || !hasCurrentResponse;
        diagramRequestKey = nextDiagramRequestKey;

        if (showBlockingLoading) {
            isLoading = true;
        }

        if (diagramId) {
            if (diagramType === DiagramType.CUSTOM_GRAPH_DIAGRAM) {
                await fetchGraphDiagramRenderingData(diagramId);
            } else if (diagramType === DiagramType.CUSTOM_DATASET_DIAGRAM) {
                await fetchDatasetDiagramRenderingData(diagramId);
            } else if (diagramType === DiagramType.CROSS_PROFILE) {
                await fetchCrossProfileRenderingData();
            } else {
                await fetchPackageRenderingData(
                    datasetName,
                    graphUri,
                    diagramId,
                    filter,
                );
            }
        } else {
            response = null;
            renderingFormat = null;
            displayDiagram = false;
            isLoading = false;
        }
    });

    async function fetchPackageRenderingData(
        datasetName,
        graphUri,
        packageUUID,
        filter,
    ) {
        let graphFilter = {
            packageUUID,
            includeEnumEntries: filter.includeEnumEntries,
            includeAttributes: filter.includeAttributes,
            includeAssociations: filter.includeAssociations,
            includeInheritance: filter.includeInheritance,
            includeRelationsToExternalPackages:
                filter.includeRelationsToExternalPackages,
        };

        try {
            const { data, error } = await getRenderingDataParameterized({
                path: { datasetName: datasetName, graphURI: graphUri },
                body: graphFilter,
            });

            if (error) {
                response = null;
                renderingFormat = null;
                displayDiagram = false;
                isLoading = false;
            } else {
                response = data;
                renderingFormat = data.format;
                displayDiagram = true;
            }
        } catch (error) {
            console.error("Error fetching package rendering data:", error);
            response = null;
            renderingFormat = null;
            displayDiagram = false;
        } finally {
            isLoading = false;
        }
    }

    async function fetchDatasetDiagramRenderingData(diagramId) {
        try {
            const { data, error } = await getCustomDatasetViewRenderingData({
                path: {
                    datasetName: editorState.selectedDataset.getValue(),
                    diagramId: diagramId,
                },
            });

            if (error) {
                displayDiagram = false;
            } else {
                response = data;
                renderingFormat = response.format;
                displayDiagram = true;
            }
        } catch (error) {
            console.error("Error fetching custom diagram data:", error);
            response = null;
            renderingFormat = null;
        } finally {
            isLoading = false;
        }
    }

    async function fetchGraphDiagramRenderingData(diagramId) {
        try {
            const { data, error } = await getCustomProfileViewRenderingData({
                path: {
                    datasetName: editorState.selectedDataset.getValue(),
                    graphURI: editorState.selectedGraph.getValue(),
                    diagramId: diagramId,
                },
            });

            if (error) {
                displayDiagram = false;
            } else {
                response = data;
                renderingFormat = response.format;
                displayDiagram = true;
            }
        } catch (error) {
            console.error("Error fetching custom diagram data:", error);
            response = null;
            renderingFormat = null;
        } finally {
            isLoading = false;
        }
    }

    async function fetchCrossProfileRenderingData() {
        const { error, data } = await crossProfileStore.fetchRenderingData(
            editorState.selectedDataset.getValue(),
        );

        if (error || !data) {
            displayDiagram = false;
            response = null;
            renderingFormat = null;
        } else {
            renderingFormat = response.format;
            displayDiagram = true;
        }

        isLoading = false;
    }

    function getDiagramRequestKey(datasetName, graphUri, packageUUID, filter) {
        return JSON.stringify({
            datasetName,
            graphUri,
            packageUUID,
            filter,
        });
    }

    function handleResetView() {
        if (renderingFormat === MERMAID_FORMAT) {
            mermaidWrapper.resetTransform();
        } else if (renderingFormat === SVELTEFLOW_FORMAT) {
            svelteFlowAPI.svelteFlow.fitView();
        }
    }
</script>

{#if editorState.selectedDiagram.getProperty("id")}
    <div class="bg-window-background flex h-full flex-col justify-between">
        <div class="relative h-full overflow-hidden">
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
                    {#if editorState.selectedDiagram.getProperty("type") === DiagramType.PACKAGE}
                        <div class="h-9 w-28">
                            <ButtonControl
                                variant="default"
                                callOnClick={() => (showFilterDialog = true)}
                            >
                                filter view
                            </ButtonControl>
                        </div>
                    {/if}
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
                        class="bg-window-background absolute inset-0 z-10 flex w-full items-center justify-center"
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
                    {#if showSvelteFlowEmptyState}
                        <div
                            class="pointer-events-none absolute inset-0 z-0 flex items-center justify-center"
                        >
                            <EmptyStateCard
                                title="No classes in this package"
                                description="Select another package to load a different diagram."
                                icon={faBoxOpen}
                            />
                        </div>
                    {/if}
                {/if}
            {:else}
                <div
                    class="absolute top-0 bottom-0 left-0 flex w-full items-center justify-center"
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
                class="absolute top-0 bottom-0 left-0 flex w-full items-center justify-center"
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
