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
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import MermaidWrapper from "$lib/rendering/mermaid/mermaidWrapper.svelte";
    import SvelteFlowWrapper from "$lib/rendering/svelteflow/svelteFlowWrapper.svelte";
    import { renderOptions } from "$lib/renderOptions.svelte.js";
    import {
        editorState,
        forceReloadTrigger,
        DiagramType,
    } from "$lib/sharedState.svelte.js";
    import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import RenderFilterBar from "./RenderFilterBar.svelte";

    const MERMAID_FORMAT = "MERMAID";
    const SVELTEFLOW_FORMAT = "SVELTEFLOW";

    let isLoading = $state(false);

    let svelteFlowAPI = $state({});

    let response = $state(null);
    let isWorkspaceReadOnly = $state();
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
        editorState.selectedWorkspace.subscribe();
        const workspace = editorState.selectedWorkspace.getValue();
        isWorkspaceReadOnly = workspace
            ? await workspaceStore.isReadOnly(workspace)
            : false;
    });

    $effect(async () => {
        forceReloadTrigger.subscribe();
        editorState.selectedWorkspace.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedDiagram.subscribe();

        const workspaceName = editorState.selectedWorkspace.getValue();
        const graphUri = editorState.selectedGraph.getValue();
        const diagramId = editorState.selectedDiagram.getProperty("id");
        const diagramType = editorState.selectedDiagram.getProperty("type");
        const filter = renderOptions.graphFilter();

        const nextDiagramRequestKey = getDiagramRequestKey(
            workspaceName,
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
            } else if (diagramType === DiagramType.CUSTOM_WORKSPACE_DIAGRAM) {
                await fetchWorkspaceDiagramRenderingData(diagramId);
            } else if (diagramType === DiagramType.CROSS_PROFILE) {
                await fetchCrossProfileRenderingData();
            } else {
                await fetchPackageRenderingData(
                    workspaceName,
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
        workspaceName,
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
            includePropertiesFromOtherProfiles:
                filter.includePropertiesFromOtherProfiles,
        };

        try {
            const { data, error } = await getRenderingDataParameterized({
                path: { datasetName: workspaceName, graphURI: graphUri },
                body: graphFilter,
            });

            if (error) {
                response = null;
                renderingFormat = null;
                displayDiagram = false;
                isLoading = false;
            } else {
                //TODO TESTDATEN FÜR PROTOTYPING, ENTFERNE SPÄTER
                //response = data;
                response = {
                    edges: [
                        {
                            data: {
                                color: null,
                                fromMultiplicity: "1",
                                graphUri: null,
                                toMultiplicity: "1",
                                useFromAssociation: true,
                                useToAssociation: true,
                                bendPoints: [
                                    {
                                        id: "e104239a-681f-4663-8c34-718957fe8120",
                                        x: 200,
                                        y: 200,
                                    },
                                ],
                            },
                            id: "26aa92ce-8946-4321-a406-fa95b27ebf68",
                            source: "867c72bc-3abf-4f96-b816-4a528d1cd99e",
                            target: "88ca000e-28c2-4f68-8ecc-f7bf34d77f75",
                            type: "association",
                            zIndex: -1,
                        },
                        {
                            data: {
                                color: null,
                                fromMultiplicity: "2",
                                graphUri: null,
                                toMultiplicity: "2",
                                useFromAssociation: true,
                                useToAssociation: true,
                                bendPoints: [
                                    {
                                        id: "e104239a-681f-4663-8c34-718957fe8120",
                                        x: -250,
                                        y: 400,
                                    },
                                    {
                                        id: "4d86da93-3306-481b-a75e-a0dd4ac7aadc",
                                        x: -300,
                                        y: 300,
                                    },
                                ],
                            },
                            id: "0034d8f0-ae20-47ca-8ebe-9da6272859ec",
                            source: "867c72bc-3abf-4f96-b816-4a528d1cd99e",
                            target: "9e864145-9510-4ab3-8576-b2f154e66fe0",
                            type: "association",
                            zIndex: -1,
                        },
                    ],
                    format: "SVELTEFLOW",
                    nodes: [
                        {
                            data: {
                                attributes: [],
                                belongsToCategory: "DiagramLayout",
                                enumEntries: [],
                                graphUri:
                                    "http://graph#DiagramLayoutProfileRDFSAugmented_v2_4_15_4Sep2020",
                                label: "A",
                                stereotypes: [],
                            },
                            id: "867c72bc-3abf-4f96-b816-4a528d1cd99e",
                            position: {
                                x: 12,
                                y: 467,
                                z: 1,
                            },
                            type: "class",
                        },
                        {
                            data: {
                                attributes: [],
                                belongsToCategory: "DiagramLayout",
                                enumEntries: [],
                                graphUri:
                                    "http://graph#DiagramLayoutProfileRDFSAugmented_v2_4_15_4Sep2020",
                                label: "B",
                                stereotypes: [],
                            },
                            id: "88ca000e-28c2-4f68-8ecc-f7bf34d77f75",
                            position: {
                                x: 355.5,
                                y: 100,
                                z: 1,
                            },
                            type: "class",
                        },
                        {
                            data: {
                                attributes: [],
                                belongsToCategory: "DiagramLayout",
                                enumEntries: [],
                                graphUri:
                                    "http://graph#DiagramLayoutProfileRDFSAugmented_v2_4_15_4Sep2020",
                                label: "C",
                                stereotypes: [],
                            },
                            id: "9e864145-9510-4ab3-8576-b2f154e66fe0",
                            position: {
                                x: -355.5,
                                y: 100,
                                z: 1,
                            },
                            type: "class",
                        },
                    ],
                };
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

    async function fetchWorkspaceDiagramRenderingData(diagramId) {
        try {
            const { data, error } = await getCustomDatasetViewRenderingData({
                path: {
                    datasetName: editorState.selectedWorkspace.getValue(),
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
                    datasetName: editorState.selectedWorkspace.getValue(),
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
            editorState.selectedWorkspace.getValue(),
        );

        if (error || !data) {
            displayDiagram = false;
            response = null;
            renderingFormat = null;
        } else {
            response = data;
            renderingFormat = response.format;
            displayDiagram = true;
        }

        isLoading = false;
    }

    function getDiagramRequestKey(
        workspaceName,
        graphUri,
        packageUUID,
        filter,
    ) {
        return JSON.stringify({
            workspaceName,
            graphUri,
            packageUUID,
            filter,
        });
    }

    function handleResetView() {
        if (renderingFormat === MERMAID_FORMAT) {
            mermaidWrapper.resetTransform();
        } else if (renderingFormat === SVELTEFLOW_FORMAT) {
            svelteFlowWrapper.fitViewIncludingBendPoints();
        }
    }
</script>

{#if editorState.selectedDiagram.getProperty("id")}
    <div class="bg-window-background flex h-full flex-col">
        {#if displayDiagram}
            <RenderFilterBar
                diagramType={editorState.selectedDiagram.getProperty("type")}
                onResetView={() => handleResetView()}
                onResetLayout={async () =>
                    await svelteFlowWrapper.applyELKLayout()}
                showResetLayout={!isWorkspaceReadOnly &&
                    renderingFormat === SVELTEFLOW_FORMAT}
            />
        {/if}
        <div class="relative min-h-0 flex-1 overflow-hidden">
            {#if displayDiagram}
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
