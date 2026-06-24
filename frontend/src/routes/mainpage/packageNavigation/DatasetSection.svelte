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
        faPlus,
    } from "@fortawesome/free-solid-svg-icons";
    import { getContext, onMount } from "svelte";

    import {
        enableEditing,
        disableEditing,
    } from "$lib/actions/editingActions.js";
    import { getNamespaces, isReadOnly } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import CrossProfileDiagramsSection from "./CrossProfileDiagramsSection.svelte";
    import CustomDiagramsSection from "./CustomDiagramsSection.svelte";
    import GraphSection from "./GraphSection.svelte";
    import {
        datasetHighlight,
        isSelectedDataset,
    } from "./packageNavigationUtils.svelte.js";
    import DatasetDeleteDialog from "../../DatasetDeleteDialog.svelte";
    import ImportDialog from "../../ImportDialog.svelte";
    import NamespacesDialog from "../../NamespacesDialog.svelte";
    import NewGraphDialog from "../../NewGraphDialog.svelte";
    import SnapshotDialog from "../../SnapshotDialog.svelte";
    import CustomDatasetDiagramDialog from "./custom-diagram-dialogs/CustomDatasetDiagramDialog.svelte";

    let { datasetNavEntry } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let showImportDialog = $state(false);
    let showNewGraphDialog = $state(false);
    let showNewDiagramDialog = $state(false);
    let showSnapshotDialog = $state(false);
    let showDatasetDeleteDialog = $state(false);
    let showNamespacesDialog = $state(false);
    let readonly = $state(false);
    let namespaces = $state([]);
    let crossProfileID = $state();

    let wasDatasetSelected = false;

    const isDatasetSelected = $derived(
        isSelectedDataset(datasetNavEntry.label),
    );
    const datasetSelectionState = $derived(
        datasetHighlight(datasetNavEntry.label),
    );

    const packagesWithClassesCount = $derived(
        (datasetNavEntry.children ?? [])
            .flatMap(graphNavEntry => graphNavEntry.children ?? [])
            .filter(packageNavEntry => packageNavEntry.children?.length > 0)
            .length,
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

    onMount(async () => {
        let res = await bec.getCrossProfileID(datasetNavEntry.label);
        crossProfileID = await res.text();
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

    function handleToggleDataset() {
        const wasOpen = datasetNavEntry.isOpen;
        datasetNavEntry.toggle();
        if (!wasOpen) {
            return;
        }
        // When collapsing, dissolve an active graph/package/diagram selected
        // inside this dataset; the dataset itself becomes the selection. A class
        // as the active selection is left untouched (tracked separately).
        const kind = editorState.activeSelectionKind.getValue();
        if (
            (kind === "graph" || kind === "package" || kind === "diagram") &&
            isSelectedDataset(datasetNavEntry.label)
        ) {
            editorState.selectedGraph.updateValue(null);
            editorState.selectedDiagram.updateValue({ type: null, id: null });
            editorState.activeSelectionKind.updateValue("dataset");
        }
    }

    function selectDataset() {
        editorState.activeSelectionKind.updateValue("dataset");
        if (editorState.selectedDataset.getValue() === datasetNavEntry.label) {
            return;
        }
        editorState.selectedGraph.updateValue(null);
        editorState.selectedDiagram.updateValue({ type: null, id: null });
        editorState.selectedDataset.updateValue(datasetNavEntry.label);
    }

    async function requestEnableEditing() {
        if (!datasetNavEntry?.id || !readonly) {
            return;
        }
        if (!(await enableEditing(datasetNavEntry.id))) {
            return;
        }
        readonly = false;
        forceReloadTrigger.trigger();
    }

    async function requestDisableEditing() {
        if (!datasetNavEntry?.id || readonly) {
            return;
        }
        if (!(await disableEditing(datasetNavEntry.id))) {
            return;
        }
        readonly = true;
        forceReloadTrigger.trigger();
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
                title={datasetNavEntry.tooltip}
                badgeText={readonly ? "Read-only" : ""}
                badgeVariant="readonly"
                isSelected={datasetSelectionState === "active"}
                ancestorSelected={datasetSelectionState === "ancestor"}
                onclick={selectDataset}
                onToggle={handleToggleDataset}
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
                Add Schema
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    selectDataset();
                    showImportDialog = true;
                }}
                disabled={readonly}
                faIcon={faFileImport}
                altText="Ctrl+I"
            >
                Import Schema
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    showNewDiagramDialog = true;
                }}
                faIcon={faPlus}
            >
                New Dataset Diagram
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    selectDataset();
                    showNamespacesDialog = true;
                }}
                faIcon={faTags}
                altText="Ctrl+Shift+A"
            >
                {#if readonly}
                    View Namespaces
                {:else}
                    Manage Namespaces
                {/if}
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    selectDataset();
                    showSnapshotDialog = true;
                }}
                faIcon={faShare}
                altText="Ctrl+Shift+S"
            >
                Share Snapshot
            </ContextMenu.Item.Button>
            {#if readonly}
                <ContextMenu.Item.Button
                    onSelect={() => requestEnableEditing()}
                    faIcon={faPenToSquare}
                    altText="Ctrl+Alt+R"
                >
                    Enable Editing
                </ContextMenu.Item.Button>
            {:else}
                <ContextMenu.Item.Button
                    onSelect={() => requestDisableEditing()}
                    faIcon={faLock}
                    altText="Ctrl+Alt+R"
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

            {#if packagesWithClassesCount > 1}
                <CrossProfileDiagramsSection
                    {datasetNavEntry}
                    {crossProfileID}
                />
            {/if}

            <CustomDiagramsSection
                {datasetNavEntry}
                allGraphNavEntries={datasetNavEntry.children}
                {readonly}
            />
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
<CustomDatasetDiagramDialog
    bind:showDialog={showNewDiagramDialog}
    lockedDatasetName={datasetNavEntry.id}
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
