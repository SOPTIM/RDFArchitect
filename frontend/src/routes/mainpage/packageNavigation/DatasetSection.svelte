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
        faFileImport,
        faShare,
        faTrash,
        faTags,
        faDatabase,
        faPenToSquare,
        faLock,
        faDiagramProject,
    } from "@fortawesome/free-solid-svg-icons";
    import { getContext } from "svelte";

    import { getNamespaces, isReadOnly } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    import GraphSection from "./GraphSection.svelte";
    import { isSelectedDataset } from "./packageNavigationUtils.svelte.js";
    import DatasetDeleteDialog from "../../DatasetDeleteDialog.svelte";
    import ImportDialog from "../../ImportDialog.svelte";
    import NamespacesDialog from "../../NamespacesDialog.svelte";
    import NewGraphDialog from "../../NewGraphDialog.svelte";
    import SnapshotDialog from "../../SnapshotDialog.svelte";

    let { datasetNavEntry } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let showImportDialog = $state(false);
    let showNewGraphDialog = $state(false);
    let showSnapshotDialog = $state(false);
    let showDatasetDeleteDialog = $state(false);
    let showNamespacesDialog = $state(false);
    let readonly = $state(false);
    let namespaces = $state([]);

    let wasDatasetSelected = false;

    const isDatasetSelected = $derived(
        isSelectedDataset(datasetNavEntry.label),
    );

    $effect(async () => {
        getContext("packageNavigation").reloadTrigger?.subscribe();
        readonly = await isReadOnly(datasetNavEntry.label);
        await fetchNamespaces();
    });
    $effect(() => {
        if (isDatasetSelected && !wasDatasetSelected) {
            datasetNavEntry.parent?.open();
        }
        wasDatasetSelected = isDatasetSelected;
    });

    async function fetchNamespaces() {
        if (!datasetNavEntry?.label) {
            namespaces = [];
            return;
        }
        try {
            namespaces = await getNamespaces(datasetNavEntry.label);
        } catch (err) {
            console.error("Failed to load namespaces:", err);
            namespaces = [];
        }
    }

    function selectDataset() {
        if (editorState.selectedDataset.getValue() === datasetNavEntry.label) {
            return;
        }
        editorState.selectedGraph.updateValue(null);
        editorState.selectedPackageUUID.updateValue(null);
        editorState.selectedDataset.updateValue(datasetNavEntry.label);
    }

    async function enableEditing() {
        if (!datasetNavEntry?.id || !readonly) {
            return;
        }

        await bec.enableEditing(datasetNavEntry.id).then(() => {
            readonly = false;
            forceReloadTrigger.trigger();
        });
    }

    async function disableEditing() {
        if (!datasetNavEntry?.id || readonly) {
            return;
        }
        await bec.disableEditing(datasetNavEntry.id).then(() => {
            readonly = true;
            forceReloadTrigger.trigger();
        });
    }
</script>

<div class="flex w-full flex-col items-stretch gap-[0.1rem]">
    <ContextMenu.Root>
        <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
            <NavigationEntry
                level={1}
                label={datasetNavEntry.label}
                icon={faDatabase}
                hasChildren={datasetNavEntry.children?.length > 0}
                expanded={datasetNavEntry.isOpen}
                isSelected={isDatasetSelected}
                title={datasetNavEntry.tooltip}
                badgeText={readonly ? "Read-only" : ""}
                badgeVariant="readonly"
                onclick={selectDataset}
                onToggle={() => datasetNavEntry.toggle()}
            />
        </ContextMenu.TriggerArea>
        <ContextMenu.Content>
            <ContextMenu.Item.Button
                onSelect={() => {
                    selectDataset();
                    showNewGraphDialog = true;
                }}
                disabled={readonly}
                faIcon={faDiagramProject}
            >
                Add Graph
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    selectDataset();
                    showImportDialog = true;
                }}
                disabled={readonly}
                faIcon={faFileImport}
            >
                Import Graph
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    selectDataset();
                    showNamespacesDialog = true;
                }}
                faIcon={faTags}
            >
                {#if readonly}
                    View Namespaces
                {:else}
                    Manage Namespaces
                {/if}
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    selectDataset();
                    showSnapshotDialog = true;
                }}
                faIcon={faShare}
            >
                Share Snapshot
            </ContextMenu.Item.Button>
            {#if readonly}
                <ContextMenu.Item.Button
                    onSelect={() => enableEditing()}
                    faIcon={faPenToSquare}
                >
                    Enable Editing
                </ContextMenu.Item.Button>
            {:else}
                <ContextMenu.Item.Button
                    onSelect={() => disableEditing()}
                    faIcon={faLock}
                >
                    Disable Editing
                </ContextMenu.Item.Button>
            {/if}

            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    showDatasetDeleteDialog = true;
                }}
                faIcon={faTrash}
                variant="danger"
            >
                Delete Dataset
            </ContextMenu.Item.Button>
        </ContextMenu.Content>
    </ContextMenu.Root>
    {#if datasetNavEntry.isOpen}
        <div
            class="flex w-full flex-col items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each datasetNavEntry.children as graphNavEntry}
                <GraphSection
                    {datasetNavEntry}
                    {graphNavEntry}
                    {namespaces}
                    {readonly}
                />
            {/each}
        </div>
    {/if}
</div>

<ImportDialog
    bind:showDialog={showImportDialog}
    lockedDatasetName={datasetNavEntry.label}
/>
<NewGraphDialog
    bind:showDialog={showNewGraphDialog}
    lockedDatasetName={datasetNavEntry.label}
/>
<SnapshotDialog
    bind:showDialog={showSnapshotDialog}
    lockedDatasetName={datasetNavEntry.label}
/>
<NamespacesDialog bind:showDialog={showNamespacesDialog} />
<DatasetDeleteDialog
    bind:showDialog={showDatasetDeleteDialog}
    datasetName={datasetNavEntry.label}
/>
