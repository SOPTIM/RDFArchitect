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
        faDiagramProject,
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
        faPaste,
        faCopy,
    } from "@fortawesome/free-solid-svg-icons";
    import { onDestroy, onMount } from "svelte";

    import {
        enableEditing,
        disableEditing,
    } from "$lib/actions/editingActions.js";
    import {
        undo as doUndo,
        redo as doRedo,
    } from "$lib/actions/versionControlActions.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import { Menubar } from "$lib/components/bitsui/menubar";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
    import {
        copyState,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import DeleteDependenciesDialog from "../../delete-relations-dialog/DeleteDependenciesDialog.svelte";
    import FilterViewDialog from "../../FilterViewDialog.svelte";
    import PackageEditorDialog from "../../mainpage/packageEditorDialog.svelte";
    import OntologyDialog from "../../mainpage/packageNavigation/ontology-editor-dialog/OntologyDialog.svelte";
    import { saveCopyClass } from "../../mainpage/packageNavigation/save-copy-class-to-backend.js";
    import NamespacesDialog from "../../NamespacesDialog.svelte";
    import NewClassDialog from "../../NewClassDialog.svelte";
    import NewGraphDialog from "../../NewGraphDialog.svelte";
    import NewPackageDialog from "../../NewPackageDialog.svelte";

    let { canUndo, canRedo, isDatasetReadOnly, reload = () => {} } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const shortcutsUnregister = [];

    let showNewClassDialog = $state(false);
    let showNewGraphDialog = $state(false);
    let showNewPackageDialog = $state(false);
    let showFilterViewDialog = $state(false);
    let ShowPackageDeleteDependenciesDialog = $state(false);
    let showOntologyDeleteDependenciesDialog = $state(false);
    let showPackageEditorDialog = $state(false);
    let showNamespaceDialog = $state(false);
    let showEditOntologyDialog = $state(false);

    let packageDialogTarget = $state(null);
    let packageDialogDataset = $state(null);
    let packageDialogGraph = $state(null);
    let selectedPackageDetails = $state(null);
    let packageDetailsRequestId = 0;
    let packages = $state([]);

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

    let disableCopyClassButton = $derived(
        !editorState.selectedClassUUID.getValue(),
    );
    let disablePasteButton = $derived(
        isDatasetReadOnly ||
            !hasGraphSelected ||
            !editorState.selectedDiagram.getValue() ||
            !copyState.datasetName.getValue() ||
            !copyState.graphURI.getValue() ||
            !copyState.classUUID.getValue(),
    );

    $effect(async () => {
        editorState.selectedDiagram.subscribe();
        editorState.selectedClassUUID.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedDataset.subscribe();
        forceReloadTrigger.subscribe();
        ontology = await getOntology();
        packages = await getPackages();
        await refreshSelectedPackageDetails(packages);
    });

    onMount(() => {
        shortcutsUnregister.push(
            shortcutStore.register(
                "newClass",
                ["shift", "n"],
                () => (showNewClassDialog = true),
                true,
            ),
            shortcutStore.register(
                "newPackage",
                ["alt", "n"],
                () => (showNewPackageDialog = true),
                true,
            ),
            shortcutStore.register(
                "namespaces",
                ["ctrl", "shift", "a"],
                () => (showNamespaceDialog = true),
                true,
            ),
            shortcutStore.register(
                "profileHeader",
                ["ctrl", "alt", "p"],
                () => (showEditOntologyDialog = true),
                true,
            ),
            shortcutStore.register(
                "editPackage",
                ["ctrl", "shift", "k"],
                () => launchPackageEditor(),
                true,
            ),
            shortcutStore.register(
                "toggleEdit",
                ["ctrl", "alt", "r"],
                () => toggleReadonly(),
                true,
            ),
            shortcutStore.register("copyClass", ["ctrl", "c"], () =>
                copyClassWithShortcut(),
            ),
            shortcutStore.register("paste", ["ctrl", "v"], () =>
                pasteClassWithShortcut(false, true, true),
            ),
            shortcutStore.register(
                "pasteWithoutAttributes",
                ["ctrl", "shift", "v"],
                () => pasteClassWithShortcut(false, false, true),
            ),
            shortcutStore.register(
                "pasteWithoutAssociations",
                ["ctrl", "alt", "v"],
                () => pasteClassWithShortcut(false, true, false),
            ),
            shortcutStore.register(
                "pasteBare",
                ["ctrl", "shift", "alt", "v"],
                () => pasteClassWithShortcut(true, false, false),
            ),
        );
    });

    onDestroy(() => {
        shortcutsUnregister.forEach(unregister => unregister());
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
        if (!(await enableEditing(selectedDataset))) {
            return;
        }
        await reload();
        forceReloadTrigger.trigger();
    }

    async function requestDisableEditing() {
        if (!selectedDataset || isDatasetReadOnly) {
            return;
        }
        if (!(await disableEditing(selectedDataset))) {
            return;
        }
        await reload();
        editorState.selectedDiagram.trigger();
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
        ShowPackageDeleteDependenciesDialog = true;
    }

    async function getPackages() {
        if (!hasGraphSelected) {
            return [];
        }
        try {
            const response = await bec.getPackages(
                selectedDataset,
                selectedGraph,
            );
            if (!response.ok) {
                throw new Error("Failed to fetch packages");
            }
            const packagesJSON = await response.json();
            return [
                ...(packagesJSON.internalPackageList ?? []).map(p => ({
                    ...p,
                    external: false,
                })),
                ...(packagesJSON.externalPackageList ?? []).map(p => ({
                    ...p,
                    external: true,
                })),
            ];
        } catch (error) {
            console.error("Failed to fetch packages", error);
            return [];
        }
    }

    async function refreshSelectedPackageDetails(packages) {
        const datasetName = editorState.selectedDataset.getValue();
        const graphURI = editorState.selectedGraph.getValue();
        const packageId = editorState.selectedDiagram.getProperty("id");

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
            const match = packages.find(pkg => pkg?.uuid === packageId);
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

    function copyClass() {
        copyState.classUUID.updateValue(
            editorState.selectedClassUUID.getValue(),
        );
        copyState.graphURI.updateValue(
            editorState.selectedClassGraph.getValue(),
        );
        copyState.datasetName.updateValue(
            editorState.selectedClassDataset.getValue(),
        );
    }

    function pasteClass(copyAsAbstract, copyAttributes, copyAssociations) {
        saveCopyClass(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
            selectedPackageDetails,
            copyAsAbstract,
            copyAttributes,
            copyAssociations,
        );
    }

    function copyClassWithShortcut() {
        if (!disableCopyClassButton) {
            copyClass();
        }
    }

    function pasteClassWithShortcut(
        copyAsAbstract,
        copyAttributes,
        copyAssociations,
    ) {
        if (!disablePasteButton) {
            pasteClass(copyAsAbstract, copyAttributes, copyAssociations);
        }
    }

    function toggleReadonly() {
        if (isDatasetReadOnly) {
            requestEnableEditing();
        } else {
            requestDisableEditing();
        }
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
                    faIcon={faCube}
                    altText="Shift+N"
                >
                    Class
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showNewPackageDialog = true)}
                    faIcon={faFolderPlus}
                    altText="Alt+N"
                >
                    Package
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showNewGraphDialog = true)}
                    faIcon={faDiagramProject}
                >
                    Schema (RDFS)
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
                    altText="Ctrl+Alt+P"
                >
                    Profile header
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={launchPackageEditor}
                    disabled={!selectedPackageDetails}
                    faIcon={canEditCurrentPackage ? faPen : faEye}
                    altText="Ctrl+Shift+K"
                >
                    Package
                </Menubar.Item.Button>
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
        <Menubar.Item.Button
            onSelect={copyClass}
            disabled={disableCopyClassButton}
            faIcon={faCopy}
            altText="Ctrl+C"
        >
            Copy Class
        </Menubar.Item.Button>
        <Menubar.SubMenu.Root>
            <Menubar.SubMenu.Trigger faIcon={faPaste}>
                Paste
            </Menubar.SubMenu.Trigger>
            <Menubar.SubMenu.Content>
                <Menubar.Item.Button
                    onSelect={() => pasteClass(false, true, true)}
                    disabled={disablePasteButton}
                    faIcon={faPaste}
                    altText="Ctrl+V"
                >
                    Paste
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => pasteClass(false, false, true)}
                    disabled={disablePasteButton}
                    faIcon={faPaste}
                    altText="Ctrl+Shift+V"
                >
                    Paste without attributes/enum entries
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => pasteClass(false, true, false)}
                    disabled={disablePasteButton}
                    faIcon={faPaste}
                    altText="Ctrl+Alt+V"
                >
                    Paste without associations
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => pasteClass(true, false, false)}
                    disabled={disablePasteButton}
                    faIcon={faPaste}
                    altText="Ctrl+Shift+Alt+V"
                >
                    Paste bare
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
                altText="Ctrl+Alt+R"
            >
                Enable Editing
            </Menubar.Item.Button>
        {:else}
            <Menubar.Item.Button
                onSelect={() => requestDisableEditing()}
                disabled={!hasDatasetSelected || isDatasetReadOnly}
                faIcon={faLock}
                altText="Ctrl+Alt+R"
            >
                Disable Editing
            </Menubar.Item.Button>
        {/if}
        <Menubar.Item.Button
            onSelect={() => openNamespaceManager()}
            disabled={!canAccessNamespaces}
            faIcon={faTags}
            altText="Ctrl+Shift+A"
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
                    onSelect={() => {
                        showOntologyDeleteDependenciesDialog = true;
                    }}
                    disabled={!hasGraphSelected || !graphHasOntology}
                    faIcon={faTrash}
                    variant="danger"
                >
                    Profile header
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
<NewGraphDialog bind:showDialog={showNewGraphDialog} />
<NewPackageDialog bind:showDialog={showNewPackageDialog} />
{#if packageDialogTarget && showPackageEditorDialog}
    <PackageEditorDialog
        bind:showDialog={showPackageEditorDialog}
        datasetName={packageDialogDataset}
        graphUri={packageDialogGraph}
        {packages}
        pack={packageDialogTarget}
        readonly={isDatasetReadOnly}
    />
{/if}
{#if packageDialogTarget && ShowPackageDeleteDependenciesDialog}
    <DeleteDependenciesDialog
        bind:showDialog={ShowPackageDeleteDependenciesDialog}
        datasetName={packageDialogDataset}
        graphUri={packageDialogGraph}
        resourceUuid={packageDialogTarget.uuid}
    />
{/if}
<NamespacesDialog bind:showDialog={showNamespaceDialog} />
<FilterViewDialog bind:showDialog={showFilterViewDialog} />
{#if ontology}
    <DeleteDependenciesDialog
        bind:showDialog={showOntologyDeleteDependenciesDialog}
        onClose={reload}
        datasetName={editorState.selectedDataset.getValue()}
        graphUri={editorState.selectedGraph.getValue()}
        resourceUuid={ontology.uuid}
    />
{/if}

{#if showEditOntologyDialog}
    <OntologyDialog
        bind:showDialog={showEditOntologyDialog}
        graphUri={selectedGraph}
        dataset={selectedDataset}
        bind:ontology
        readonly={isDatasetReadOnly}
    />
{/if}
