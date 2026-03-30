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
        faRightLeft,
    } from "@fortawesome/free-solid-svg-icons";
    import {
        faFileExport,
        faTrash,
        faPlus,
        faClockRotateLeft,
        faCodeBranch,
        faFileImport,
        faUpload,
        faDownload,
        faEye,
        faRotateLeft,
        faRotateRight,
        faGear,
    } from "@fortawesome/free-solid-svg-icons";
    import { onMount } from "svelte";

    import {
        undo,
        fetchCanUndo,
        redo,
        fetchCanRedo,
    } from "$lib/actions/versionControlActions.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { editorState } from "$lib/sharedState.svelte.js";
    import { shortenIri } from "$lib/utils/iri.js";

    import { populateGraph } from "./build-nav-object.js";
    import PackageButton from "./PackageButton.svelte";
    import { isSelectedGraph } from "./packageNavigationUtils.svelte.js";
    import CompareDialog from "../../compare/CompareDialog.svelte";
    import ExportDialog from "../../ExportDialog.svelte";
    import GraphDeleteDialog from "../../GraphDeleteDialog.svelte";
    import NewPackageDialog from "../../NewPackageDialog.svelte";
    import OntologyDialog from "./ontology-editor-dialog/OntologyDialog.svelte";
    import SHACLExportDialog from "../../shacl/SHACLExportDialog.svelte";
    import SHACLFullViewDialog from "../../shacl/SHACLFullViewDialog.svelte";
    import SHACLUploadDialog from "../../shacl/SHACLUploadDialog.svelte";

    import { goto } from "$app/navigation";

    let {
        datasetNavEntry,
        graphNavEntry,
        namespaces = [],
        readonly = false,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let ontology = $state();
    let showExportDialog = $state(false);
    let showDeleteDialog = $state(false);
    let showNewPackageDialog = $state(false);
    let showCompareDialog = $state(false);
    let showSHACLUploadDialog = $state(false);
    let showSHACLExportDialog = $state(false);
    let showSHACLFullViewDialog = $state(false);
    let canUndo = $state(false);
    let canRedo = $state(false);
    let showEditOntologyDialog = $state(false);

    let wasGraphSelected = false;

    let graphHighlightLabel = $derived(
        shortenIri(namespaces, graphNavEntry.id),
    );

    const isGraphSelected = $derived(
        isSelectedGraph(datasetNavEntry.id, graphNavEntry.id),
    );
    $effect(() => {
        if (isGraphSelected && !wasGraphSelected) {
            graphNavEntry.parent?.open();
        }
        wasGraphSelected = isGraphSelected;
    });

    onMount(async () => {
        await initialize();
    });

    async function initialize() {
        ontology = await getOntology();
        canUndo = await fetchCanUndo(datasetNavEntry.id, graphNavEntry.id);
        canRedo = await fetchCanRedo(datasetNavEntry.id, graphNavEntry.id);
    }
    async function getOntology() {
        const res = await bec.getOntology(
            datasetNavEntry.label,
            graphNavEntry.id,
        );
        let content = await res.text();
        if (!content) {
            return null;
        }
        return JSON.parse(content);
    }

    function focusGraphContext() {
        const nextDataset = datasetNavEntry.label;
        const nextGraph = graphNavEntry.id;
        const previousDataset = editorState.selectedDataset.getValue();
        const previousGraph = editorState.selectedGraph.getValue();
        const graphChanged =
            previousDataset !== nextDataset || previousGraph !== nextGraph;

        editorState.selectedDataset.updateValue(nextDataset);
        editorState.selectedGraph.updateValue(nextGraph);
        if (graphChanged) {
            editorState.selectedPackageUUID.updateValue(null);
        }
    }

    async function hotReload() {
        await populateGraph(datasetNavEntry, graphNavEntry);
        await initialize();
    }
</script>

<div class={`flex w-full flex-col items-stretch gap-[0.1rem]`}>
    <ContextMenu.Root>
        <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
            <NavigationEntry
                level={2}
                label={graphNavEntry.label}
                icon={faDiagramProject}
                hasChildren={graphNavEntry.children.length > 0}
                expanded={graphNavEntry.isOpen}
                isSelected={isGraphSelected}
                title={graphNavEntry.tooltip}
                highlightLabel={graphHighlightLabel}
                onclick={focusGraphContext}
                onToggle={() => graphNavEntry.toggle()}
            />
        </ContextMenu.TriggerArea>
        <ContextMenu.Content>
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    showNewPackageDialog = true;
                }}
                disabled={readonly}
                faIcon={faPlus}
            >
                New Package
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    undo(datasetNavEntry.id, graphNavEntry.id).then(success => {
                        if (success) hotReload();
                    });
                }}
                disabled={readonly || !canUndo}
                faIcon={faRotateLeft}
            >
                Undo
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    redo(datasetNavEntry.id, graphNavEntry.id).then(success => {
                        if (success) hotReload();
                    });
                }}
                disabled={readonly || !canRedo}
                faIcon={faRotateRight}
            >
                Redo
            </ContextMenu.Item.Button>
            {#if !readonly}
                <ContextMenu.Separator />
                {#if ontology}
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            showEditOntologyDialog = true;
                        }}
                        faIcon={faGear}
                    >
                        Edit Ontology
                    </ContextMenu.Item.Button>
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            bec.deleteOntology(
                                datasetNavEntry.id,
                                graphNavEntry.id,
                            );
                            initialize();
                        }}
                        variant="danger"
                        faIcon={faTrash}
                    >
                        Delete Ontology
                    </ContextMenu.Item.Button>
                {:else}
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            showEditOntologyDialog = true;
                        }}
                        faIcon={faPlus}
                    >
                        Create Ontology
                    </ContextMenu.Item.Button>
                {/if}
                <ContextMenu.Separator />
            {:else if ontology}
                <ContextMenu.Item.Button
                    onSelect={() => {
                        showEditOntologyDialog = true;
                    }}
                    faIcon={faEye}
                >
                    View Ontology
                </ContextMenu.Item.Button>
                <ContextMenu.Separator />
            {/if}
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    goto("/changelog");
                }}
                faIcon={faClockRotateLeft}
            >
                Changelog
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    showCompareDialog = true;
                }}
                faIcon={faCodeBranch}
            >
                Compare...
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    goto("/migrate");
                }}
                faIcon={faRightLeft}
            >
                Migrate...
            </ContextMenu.Item.Button>
            <ContextMenu.SubMenu.Root>
                <ContextMenu.SubMenu.Trigger faIcon={faFileImport}>
                    SHACL
                </ContextMenu.SubMenu.Trigger>
                <ContextMenu.SubMenu.Content>
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            focusGraphContext();
                            showSHACLUploadDialog = true;
                        }}
                        disabled={readonly}
                        faIcon={faUpload}
                    >
                        Import
                    </ContextMenu.Item.Button>
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            focusGraphContext();
                            showSHACLExportDialog = true;
                        }}
                        faIcon={faDownload}
                    >
                        Export
                    </ContextMenu.Item.Button>
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            focusGraphContext();
                            showSHACLFullViewDialog = true;
                        }}
                        faIcon={faEye}
                    >
                        Full SHACL
                    </ContextMenu.Item.Button>
                </ContextMenu.SubMenu.Content>
            </ContextMenu.SubMenu.Root>
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    showExportDialog = true;
                }}
                faIcon={faFileExport}
            >
                Export Graph
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    showDeleteDialog = true;
                }}
                disabled={readonly}
                faIcon={faTrash}
                variant="danger"
            >
                Delete Graph
            </ContextMenu.Item.Button>
        </ContextMenu.Content>
    </ContextMenu.Root>
    {#if graphNavEntry.isOpen}
        <div
            class="flex w-full flex-col items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each graphNavEntry.children as packageNavEntry (packageNavEntry.id)}
                <PackageButton
                    {datasetNavEntry}
                    {graphNavEntry}
                    {packageNavEntry}
                    {namespaces}
                    {readonly}
                />
            {/each}
        </div>
    {/if}
</div>

<ExportDialog
    bind:showDialog={showExportDialog}
    lockedDatasetName={datasetNavEntry.id}
    lockedGraphUri={graphNavEntry.id}
/>
<GraphDeleteDialog bind:showDialog={showDeleteDialog} />
<NewPackageDialog
    bind:showDialog={showNewPackageDialog}
    lockedDatasetName={datasetNavEntry.id}
    lockedGraphUri={graphNavEntry.id}
/>
<CompareDialog
    bind:showDialog={showCompareDialog}
    lockedDatasetName={datasetNavEntry.id}
    lockedGraphUri={graphNavEntry.id}
/>
<SHACLUploadDialog
    bind:showDialog={showSHACLUploadDialog}
    lockedDatasetName={datasetNavEntry.id}
    lockedGraphUri={graphNavEntry.id}
/>
<SHACLExportDialog
    bind:showDialog={showSHACLExportDialog}
    lockedDatasetName={datasetNavEntry.id}
    lockedGraphUri={graphNavEntry.id}
/>
<SHACLFullViewDialog bind:showDialog={showSHACLFullViewDialog} />
<OntologyDialog
    bind:showDialog={showEditOntologyDialog}
    graphUri={graphNavEntry.id}
    dataset={datasetNavEntry.id}
    {namespaces}
    bind:ontology
    {readonly}
    onSubmit={initialize}
/>
