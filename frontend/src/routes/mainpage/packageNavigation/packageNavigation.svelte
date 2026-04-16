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
    import {
        faDiagramProject,
        faFileImport,
    } from "@fortawesome/free-solid-svg-icons";
    import { setContext, untrack } from "svelte";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { SimpleTrigger } from "$lib/statePrimitives.svelte.js";

    import { getNavEntryList } from "./build-nav-object.js";
    import DatasetSection from "./DatasetSection.svelte";
    import ImportDialog from "../../ImportDialog.svelte";
    import NewGraphDialog from "../../NewGraphDialog.svelte";

    const localReloadTrigger = new SimpleTrigger();
    let initialDatasetsLoaded = $state(false);
    let showImportDialog = $state(false);
    let showNewGraphDialog = $state(false);
    let datasetNavEntryList = $state(null);

    $effect(async () => {
        forceReloadTrigger.subscribe();
        await untrack(
            async () =>
                (datasetNavEntryList =
                    await getNavEntryList(datasetNavEntryList)),
        );
        initialDatasetsLoaded = true;
        localReloadTrigger.trigger();
    });

    setContext("packageNavigation", {
        reloadTrigger: localReloadTrigger,
    });
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
                        {#if datasetNavEntryList && datasetNavEntryList.length > 0}
                            <div
                                class="flex w-full flex-col items-stretch justify-start gap-[0.1rem] px-2"
                            >
                                {#key datasetNavEntryList}
                                    {#each datasetNavEntryList as datasetNavEntry (datasetNavEntry.id)}
                                        <DatasetSection {datasetNavEntry} />
                                    {/each}
                                {/key}
                            </div>
                        {:else if initialDatasetsLoaded}
                            <div class="text-default-text px-4 py-2 text-sm">
                                Not yet loaded
                            </div>
                        {/if}
                    </div>
                </div>
            </div>
        </ContextMenu.TriggerArea>
        <ContextMenu.Content>
            <ContextMenu.Item.Button
                onSelect={() => (showNewGraphDialog = true)}
                faIcon={faDiagramProject}
            >
                Add schema
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => (showImportDialog = true)}
                faIcon={faFileImport}
            >
                Import Schema
            </ContextMenu.Item.Button>
        </ContextMenu.Content>
        <ImportDialog bind:showDialog={showImportDialog} />
        <NewGraphDialog bind:showDialog={showNewGraphDialog} />
    </ContextMenu.Root>
</div>
