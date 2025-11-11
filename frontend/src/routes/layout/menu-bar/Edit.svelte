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
        faCube,
        faLock,
        faPlus,
        faFolderPlus,
        faPen,
        faPenToSquare,
        faRotateLeft,
        faRotateRight,
        faTags,
        faTrash,
        faEye,
    } from "@fortawesome/free-solid-svg-icons";

    import {
        undo as doUndo,
        redo as doRedo,
    } from "$lib/actions/versionControlActions.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import { Menubar } from "$lib/components/bitsui/menubar";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import FilterViewDialog from "../../FilterViewDialog.svelte";
    import PackageEditorDialog from "../../mainpage/packageEditorDialog.svelte";
    import OntologyDialog from "../../mainpage/packageNavigation/ontology-editor-dialog/OntologyDialog.svelte";
    import PackageDeleteDialog from "../../mainpage/packageNavigation/PackageDeleteDialog.svelte";
    import NamespacesDialog from "../../NamespacesDialog.svelte";
    import NewClassDialog from "../../NewClassDialog.svelte";
    import NewPackageDialog from "../../NewPackageDialog.svelte";

    let { canUndo, canRedo, isDatasetReadOnly, reload = () => {} } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let showNewClassDialog = $state(false);
    let showNewPackageDialog = $state(false);
    let showFilterViewDialog = $state(false);
    let showPackageDeleteDialog = $state(false);
    let showPackageEditorDialog = $state(false);
    let showNamespaceDialog = $state(false);
    let showEditOntologyDialog = $state(false);

    let packageDialogTarget = $state(null);
    let packageDialogDataset = $state(null);
    let packageDialogGraph = $state(null);
    let selectedPackageDetails = $state(null);
    let packageDetailsRequestId = 0;

    let ontology = $state();

    let selectedDataset = $derived(editorState.selectedDataset.getValue());
    let selectedGraph = $derived(editorState.selectedGraph.getValue());
    let hasDatasetSelected = $derived(!!selectedDataset);
    let hasGraphSelected = $derived(
        hasDatasetSelected && !!editorState.selectedGraph.getValue(),
    );
    let canAccessNamespaces = $derived(hasDatasetSelected);
    let canEditCurrentPackage = $derived(
        selectedPackageDetails &&
            !selectedPackageDetails.external &&
            selectedPackageDetails.label !== "default" &&
            !isDatasetReadOnly,
    );
    let canDeleteCurrentPackage = $derived(
        selectedPackageDetails &&
            !selectedPackageDetails.external &&
            selectedPackageDetails.label !== "default" &&
            !isDatasetReadOnly,
    );
    let graphHasOntology = $derived(!!ontology);

    $effect(async () => {
        editorState.selectedPackageUUID.subscribe();
        editorState.selectedClassUUID.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedDataset.subscribe();
        forceReloadTrigger.subscribe();
        ontology = await getOntology();
        await refreshSelectedPackageDetails();
    });

    async function getOntology() {
        if (!hasGraphSelected) {
            return null;
        }
        const res = await bec.getOntology(selectedDataset, selectedGraph);
        let content = await res.text();
        if (!content) {
            return null;
        }
        return JSON.parse(content);
    }

    async function requestEnableEditing() {
        if (!selectedDataset || !isDatasetReadOnly) {
            return;
        }
        await enableEditing(selectedDataset);
        await reload();
    }

    async function requestDisableEditing() {
        if (!selectedDataset || isDatasetReadOnly) {
            return;
        }
        await disableEditing(selectedDataset);
        await reload();
    }

    function openNamespaceManager() {
        if (!canAccessNamespaces) return;
        showNamespaceDialog = true;
    }

    function launchPackageEditor() {
        if (!selectedPackageDetails) return;
        packageDialogTarget = { ...selectedPackageDetails };
        packageDialogDataset = selectedDataset;
        packageDialogGraph = selectedGraph;
        showPackageEditorDialog = true;
    }

    function launchPackageDeleteDialog() {
        if (!canDeleteCurrentPackage || !selectedPackageDetails) return;
        packageDialogTarget = { ...selectedPackageDetails };
        packageDialogDataset = selectedDataset;
        packageDialogGraph = selectedGraph;
        showPackageDeleteDialog = true;
    }

    async function refreshSelectedPackageDetails() {
        const datasetName = editorState.selectedDataset.getValue();
        const graphURI = editorState.selectedGraph.getValue();
        const packageId = editorState.selectedPackageUUID.getValue();

        if (!datasetName || !graphURI || !packageId) {
            selectedPackageDetails = null;
            return;
        }

        if (packageId === "default") {
            selectedPackageDetails = null;
            return;
        }

        const requestId = ++packageDetailsRequestId;

        try {
            const response = await bec.getPackages(datasetName, graphURI);
            if (!response.ok) {
                throw new Error("Failed to fetch packages");
            }
            const packagesJSON = await response.json();
            const candidates = [
                ...(packagesJSON.internalPackageList ?? []),
                ...(packagesJSON.externalPackageList ?? []),
            ];
            const match = candidates.find(pkg => pkg?.uuid === packageId);
            if (requestId === packageDetailsRequestId) {
                selectedPackageDetails = match ?? null;
                if (!match) {
                    packageDialogTarget = null;
                }
            }
        } catch (error) {
            console.error("Failed to resolve selected package", error);
            if (requestId === packageDetailsRequestId) {
                selectedPackageDetails = null;
                packageDialogTarget = null;
            }
        }
    }

    async function undo() {
        if (await doUndo()) reload();
    }
    async function redo() {
        if (await doRedo()) reload();
    }

    async function enableEditing(datasetName) {
        await bec.enableEditing(datasetName);
    }

    async function disableEditing(datasetName) {
        await bec.disableEditing(datasetName);
    }
</script>

<Menubar.Menu value="edit">
    <Menubar.Trigger>Edit</Menubar.Trigger>
    <Menubar.Content side="bottom" sideOffset={8}>
        <Menubar.SubMenu.Root>
            <Menubar.SubMenu.Trigger faIcon={faPlus}>
                New
            </Menubar.SubMenu.Trigger>
            <Menubar.SubMenu.Content>
                <Menubar.Item.Button
                    onSelect={() => (showNewClassDialog = true)}
                    disabled={isDatasetReadOnly}
                    faIcon={faCube}
                >
                    Class
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showNewPackageDialog = true)}
                    disabled={isDatasetReadOnly}
                    faIcon={faFolderPlus}
                >
                    Package
                </Menubar.Item.Button>
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
        <Menubar.SubMenu.Root>
            <Menubar.SubMenu.Trigger faIcon={isDatasetReadOnly ? faEye : faPen}>
                {isDatasetReadOnly ? "View" : "Edit"}
            </Menubar.SubMenu.Trigger>
            <Menubar.SubMenu.Content>
                <Menubar.Item.Button
                    onSelect={() => (showEditOntologyDialog = true)}
                    disabled={!hasGraphSelected ||
                        (isDatasetReadOnly && !graphHasOntology)}
                    faIcon={graphHasOntology
                        ? isDatasetReadOnly
                            ? faEye
                            : faPen
                        : faPlus}
                >
                    Ontology
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={launchPackageEditor}
                    disabled={!selectedPackageDetails}
                    faIcon={canEditCurrentPackage ? faPen : faEye}
                >
                    Package
                </Menubar.Item.Button>
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
        <Menubar.Separator />
        <Menubar.Item.Button
            onSelect={() => undo()}
            disabled={isDatasetReadOnly || !canUndo}
            faIcon={faRotateLeft}
            altText="Ctrl+Z"
        >
            Undo
        </Menubar.Item.Button>
        <Menubar.Item.Button
            onSelect={() => redo()}
            disabled={isDatasetReadOnly || !canRedo}
            faIcon={faRotateRight}
            altText="Ctrl+Y"
        >
            Redo
        </Menubar.Item.Button>
        <Menubar.Separator />
        {#if !hasDatasetSelected || isDatasetReadOnly}
            <Menubar.Item.Button
                onSelect={() => requestEnableEditing()}
                disabled={!hasDatasetSelected || !isDatasetReadOnly}
                faIcon={faPenToSquare}
            >
                Enable Editing
            </Menubar.Item.Button>
        {:else}
            <Menubar.Item.Button
                onSelect={() => requestDisableEditing()}
                disabled={!hasDatasetSelected || isDatasetReadOnly}
                faIcon={faLock}
            >
                Disable Editing
            </Menubar.Item.Button>
        {/if}
        <Menubar.Item.Button
            onSelect={() => openNamespaceManager()}
            disabled={!canAccessNamespaces}
            faIcon={faTags}
        >
            {#if isDatasetReadOnly}
                View Namespaces
            {:else}
                Manage Namespaces
            {/if}
        </Menubar.Item.Button>
        <Menubar.Separator />
        <Menubar.SubMenu.Root>
            <Menubar.SubMenu.Trigger faIcon={faTrash} variant="danger">
                Delete
            </Menubar.SubMenu.Trigger>
            <Menubar.SubMenu.Content>
                <Menubar.Item.Button
                    onSelect={async () => {
                        await bec.deleteOntology(
                            selectedDataset,
                            selectedGraph,
                        );
                        reload();
                    }}
                    disabled={!hasGraphSelected || !graphHasOntology}
                    faIcon={faTrash}
                    variant="danger"
                >
                    Ontology
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={launchPackageDeleteDialog}
                    disabled={!canDeleteCurrentPackage}
                    faIcon={faTrash}
                    variant="danger"
                >
                    Package
                </Menubar.Item.Button>
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
    </Menubar.Content>
</Menubar.Menu>

<NewClassDialog bind:showDialog={showNewClassDialog} />
<NewPackageDialog bind:showDialog={showNewPackageDialog} />
{#if packageDialogTarget && showPackageEditorDialog}
    <PackageEditorDialog
        bind:showDialog={showPackageEditorDialog}
        datasetName={packageDialogDataset}
        graphUri={packageDialogGraph}
        pack={packageDialogTarget}
        readonly={isDatasetReadOnly}
    />
{/if}
{#if packageDialogTarget && showPackageDeleteDialog}
    <PackageDeleteDialog
        bind:showDialog={showPackageDeleteDialog}
        datasetName={packageDialogDataset}
        graphUri={packageDialogGraph}
        pack={packageDialogTarget}
    />
{/if}
<NamespacesDialog bind:showDialog={showNamespaceDialog} />
<FilterViewDialog bind:showDialog={showFilterViewDialog} />
{#if showEditOntologyDialog}
    <OntologyDialog
        bind:showDialog={showEditOntologyDialog}
        graphUri={selectedGraph}
        dataset={selectedDataset}
        bind:ontology
        readonly={isDatasetReadOnly}
    />
{/if}
