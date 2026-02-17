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
    import { onMount } from "svelte";

    import { PUBLIC_BACKEND_URL } from "$env/static/public";

    import { getNamespaces, isReadOnly } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { URI } from "$lib/models/dto";
    import {
        forceReloadTrigger,
        editorState,
    } from "$lib/sharedState.svelte.js";

    import DatasetDeleteDialog from "./DatasetDeleteDialog.svelte";
    import GraphSection from "./GraphSection.svelte";
    import {
        isSelectedDataset,
        isSelectedGraph,
        getUri,
    } from "./packageNavigationUtils.svelte.js";
    import ImportDialog from "../../ImportDialog.svelte";
    import NamespacesDialog from "../../NamespacesDialog.svelte";
    import NewGraphDialog from "../../NewGraphDialog.svelte";
    import SnapshotDialog from "../../SnapshotDialog.svelte";

    let { dataset } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let graphs = $state([]);
    let showImportDialog = $state(false);
    let showNewGraphDialog = $state(false);
    let showSnapshotDialog = $state(false);
    let showDatasetDeleteDialog = $state(false);
    let showNamespacesDialog = $state(false);
    let readonly = $state(false);
    let prefixes = $state([]);

    $effect(async () => {
        forceReloadTrigger.subscribe();
        readonly = dataset ? await isReadOnly(dataset.label) : false;
        await fetchGraphs();
        await updateReadonly();
        await fetchNamespaces();
    });

    onMount(() => {
        updateReadonly();
        fetchNamespaces();
        fetchGraphs();
    });

    async function getGraphNames(datasetName) {
        const res = await bec.getGraphNames(datasetName);
        return await res.json();
    }

    async function fetchGraphs() {
        let graphUris = await getGraphNames(dataset.label);
        let newGraphs = [];
        const previous = graphs ?? [];

        for (let graphUri of graphUris) {
            const uriString = getUri(graphUri);
            const prev = previous.find(g => getUri(g.uri) === uriString);
            const keepExpanded = prev?.showContents ?? false;

            newGraphs.push({
                uri: new URI(graphUri),
                showContents:
                    keepExpanded || isSelectedGraph(dataset.label, graphUri),
            });
        }
        newGraphs = newGraphs.sort((a, b) =>
            a.uri.suffix.localeCompare(b.uri.suffix),
        );
        graphs = newGraphs;
    }

    async function fetchNamespaces() {
        if (!dataset?.label) {
            prefixes = [];
            return;
        }
        try {
            prefixes = await getNamespaces(dataset.label);
        } catch (err) {
            console.error("Failed to load prefixes:", err);
            prefixes = [];
        }
    }

    function selectDataset() {
        const nextDataset = dataset.label;
        const previousDataset = editorState.selectedDataset.getValue();
        const datasetChanged = previousDataset !== nextDataset;

        editorState.selectedDataset.updateValue(nextDataset);
        if (datasetChanged) {
            editorState.selectedGraph.updateValue(null);
            editorState.selectedPackageUUID.updateValue(null);
        }
    }

    function toggleDatasetContentsVisibility(dataset) {
        dataset.showContents = !dataset.showContents;
    }

    function ensureDatasetExpanded() {
        if (!dataset?.showContents) {
            dataset.showContents = true;
        }
    }

    async function updateReadonly() {
        readonly = await isReadOnly(dataset.label);
    }

    async function enableEditing() {
        if (!dataset?.label || !readonly) {
            return;
        }
        await bec.enableEditing(dataset.label);
        await updateReadonly();
        forceReloadTrigger.trigger();
        editorState.selectedClassUUID.trigger();
    }

    async function disableEditing() {
        if (!dataset?.label || readonly) {
            return;
        }
        await bec.disableEditing(dataset.label);
        await updateReadonly();
        forceReloadTrigger.trigger();
        editorState.selectedClassUUID.trigger();
    }
</script>

<div class="flex w-full flex-col items-stretch gap-[0.1rem]">
    <ContextMenu.Root>
        <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
            <NavigationEntry
                level={1}
                label={dataset.label}
                icon={faDatabase}
                hasChildren={graphs.length > 0}
                expanded={dataset.showContents}
                isSelected={isSelectedDataset(dataset)}
                title={dataset.label}
                badgeText={readonly ? "Read-only" : ""}
                badgeVariant="readonly"
                onclick={() => {
                    selectDataset();
                }}
                onToggle={() => toggleDatasetContentsVisibility(dataset)}
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
    {#if dataset.showContents}
        <div
            class="flex w-full flex-col items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each graphs as graph}
                <GraphSection
                    {dataset}
                    {graph}
                    onExpandDataset={ensureDatasetExpanded}
                    {prefixes}
                />
            {/each}
        </div>
    {/if}
</div>

<ImportDialog
    bind:showDialog={showImportDialog}
    lockedDatasetName={dataset.label}
/>
<NewGraphDialog
    bind:showDialog={showNewGraphDialog}
    lockedDatasetName={dataset.label}
/>
<SnapshotDialog
    bind:showDialog={showSnapshotDialog}
    lockedDatasetName={dataset.label}
/>
<NamespacesDialog bind:showDialog={showNamespacesDialog} />
<DatasetDeleteDialog
    bind:showDialog={showDatasetDeleteDialog}
    datasetName={dataset.label}
/>
