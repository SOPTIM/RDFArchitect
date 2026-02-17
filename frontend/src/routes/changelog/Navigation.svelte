<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script>
    import {
        faDatabase,
        faDiagramProject,
    } from "@fortawesome/free-solid-svg-icons";

    import { BackendConnection } from "$lib/api/backend.js";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import {
        forceReloadTrigger,
        editorState,
    } from "$lib/sharedState.svelte.js";

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    let datasetList = $state([]);
    let selectedDatasetName = $derived(editorState.selectedDataset.getValue());
    let selectedGraphUri = $derived(editorState.selectedGraph.getValue());

    $effect(async () => {
        forceReloadTrigger.subscribe();
        await fetchNavigationObject();
    });

    async function fetchNavigationObject() {
        const datasetNames = await getDatasetNames();
        const newDatasetList = [];
        for (const datasetName of datasetNames) {
            let showDatasetContents = datasetName === selectedDatasetName;
            showDatasetContents |= datasetList.find(
                datasetObject => datasetObject.label === datasetName,
            )?.showContents;
            newDatasetList.push({
                label: datasetName,
                graphs: [],
                showContents: showDatasetContents,
            });
            const graphUris = await getGraphUris(datasetName);
            graphUris.forEach(graphUri =>
                newDatasetList.at(-1).graphs.push(graphUri),
            );
        }
        datasetList = newDatasetList;
    }

    async function getDatasetNames() {
        const res = await bec.getDatasetNames();
        return await res.json();
    }

    async function getGraphUris(datasetName) {
        const res = await bec.getGraphNames(datasetName);
        return await res.json();
    }

    function getUri(uri) {
        return uri.prefix ? uri.prefix + uri.suffix : uri.suffix;
    }
</script>

<div class="nav-sidebar h-full w-full">
    <div class="nav-sidebar__scroll no-scrollbar">
        {#if datasetList && datasetList.length > 0}
            <div class="flex flex-col gap-1 pr-2">
                {#each datasetList as dataset}
                    <div>
                        <NavigationEntry
                            level={1}
                            label={dataset.label}
                            icon={faDatabase}
                            hasChildren={dataset.graphs.length > 0}
                            expanded={dataset.showContents}
                            isSelected={dataset.label === selectedDatasetName}
                            title={dataset.label}
                            onclick={() => {
                                editorState.selectedDataset.updateValue(
                                    dataset.label,
                                );
                            }}
                            onToggle={() => {
                                if (!dataset.graphs.length) return;
                                dataset.showContents = !dataset.showContents;
                            }}
                        />
                        {#if dataset.showContents}
                            {#each dataset.graphs as graph}
                                <NavigationEntry
                                    level={2}
                                    label={graph.suffix}
                                    secondaryLabel={graph.prefix ?? ""}
                                    icon={faDiagramProject}
                                    isSelected={selectedDatasetName ===
                                        dataset.label &&
                                        getUri(graph) === selectedGraphUri}
                                    title={getUri(graph)}
                                    onclick={() => {
                                        editorState.selectedDataset.updateValue(
                                            dataset.label,
                                        );
                                        editorState.selectedGraph.updateValue(
                                            getUri(graph),
                                        );
                                    }}
                                />
                            {/each}
                        {/if}
                    </div>
                {/each}
            </div>
        {:else}
            <div class="p-4 text-left">No data available</div>
        {/if}
    </div>
</div>
