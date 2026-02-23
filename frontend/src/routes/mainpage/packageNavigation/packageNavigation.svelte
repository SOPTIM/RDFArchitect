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
    import { faFileImport } from "@fortawesome/free-solid-svg-icons";

    import { BackendConnection } from "$lib/api/backend.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";

    import DatasetSection from "./DatasetSection.svelte";
    import { isSelectedDataset } from "./packageNavigationUtils.svelte.js";
    import ImportDialog from "../../ImportDialog.svelte";

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    let datasetList = $state([]);
    let initialDatasetsLoaded = $state(false);
    let showImportDialog = $state(false);

    $effect(async () => {
        forceReloadTrigger.subscribe();
        await fetchDatasets();
    });

    async function getDatasetNames() {
        const res = await bec.getDatasetNames();
        return await res.json();
    }

    async function fetchDatasets() {
        let datasetNames = await getDatasetNames();
        let newDatasetList = [];

        const previous = datasetList ?? [];
        for (const datasetName of datasetNames) {
            const prev = previous.find(d => d.label === datasetName);
            const keepExpanded = prev?.showContents ?? false;
            newDatasetList.push({
                label: datasetName,
                showContents: keepExpanded || isSelectedDataset(datasetName),
            });
        }
        newDatasetList = newDatasetList.sort((a, b) =>
            a.label.localeCompare(b.label),
        );
        datasetList = newDatasetList;
        initialDatasetsLoaded = true;
    }
</script>

<div class="flex h-full min-h-0 w-full flex-1 flex-col">
    <ContextMenu.Root>
        <ContextMenu.TriggerArea
            class="m-0 flex h-full w-full flex-1 flex-col items-stretch gap-0 p-0"
        >
            <div class="flex h-full w-full">
                <div
                    class="flex h-full min-h-0 w-full flex-1 flex-col border-r border-[var(--color-nav-border)] bg-[var(--color-nav-surface)]"
                >
                    <div
                        class="no-scrollbar min-h-0 flex-1 overflow-y-auto py-[0.4rem]"
                    >
                        {#if datasetList && datasetList.length > 0}
                            <div
                                class="flex w-full flex-col items-stretch justify-start gap-[0.1rem] px-2"
                            >
                                {#each datasetList as dataset}
                                    <DatasetSection {dataset} />
                                {/each}
                            </div>
                        {:else if initialDatasetsLoaded}
                            <div class="text-default-text px-4 py-2 text-sm">
                                No data available
                            </div>
                        {/if}
                    </div>
                </div>
            </div>
        </ContextMenu.TriggerArea>
        <ContextMenu.Content>
            <ContextMenu.Item.Button
                onSelect={() => (showImportDialog = true)}
                faIcon={faFileImport}
            >
                Import Graph
            </ContextMenu.Item.Button>
        </ContextMenu.Content>
        <ImportDialog bind:showDialog={showImportDialog} />
    </ContextMenu.Root>
</div>
