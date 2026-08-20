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
        faCopy,
        faCube,
        faDiagramProject,
        faEye,
        faFolderPlus,
        faLock,
        faPaste,
        faPen,
        faPenToSquare,
        faPlus,
        faRotateLeft,
        faRotateRight,
        faTags,
        faTrash,
    } from "@fortawesome/free-solid-svg-icons";
    import { onDestroy, onMount } from "svelte";

    import { Menubar } from "$lib/components/bitsui/menubar";
    import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
    import { PASTE_VARIANTS } from "$lib/pasteOptions.js";
    import {
        copyState,
        editorState,
        forceReloadTrigger,
        multiSelectState,
        SelectionLevel,
    } from "$lib/sharedState.svelte.js";
    import { datasetStore } from "$lib/stores/datasetStore.ts";
    import { ontologyStore } from "$lib/stores/ontologyStore.ts";
    import { packageStore } from "$lib/stores/packageStore.ts";
    import { versionControlStore } from "$lib/stores/versionControlStore.ts";

    import DeleteDependenciesDialog from "../../delete-relations-dialog/DeleteDependenciesDialog.svelte";
    import GraphDeleteDialog from "../../GraphDeleteDialog.svelte";
    import PackageEditorDialog from "../../mainpage/packageEditorDialog.svelte";
    import OntologyDialog from "../../mainpage/packageNavigation/ontology-editor-dialog/OntologyDialog.svelte";
    import { inferSelectionLevel } from "../../mainpage/packageNavigation/packageNavigationUtils.svelte.js";
    import { startPaste } from "../../mainpage/packageNavigation/paste-flow.svelte.js";
    import PasteMenuItems from "../../mainpage/packageNavigation/PasteMenuItems.svelte";
    import NamespacesDialog from "../../NamespacesDialog.svelte";
    import NewClassDialog from "../../NewClassDialog.svelte";
    import NewGraphDialog from "../../NewGraphDialog.svelte";
    import NewPackageDialog from "../../NewPackageDialog.svelte";
    import RenameGraphDialog from "../../RenameGraphDialog.svelte";
    import RenameWorkspaceDialog from "../../RenameWorkspaceDialog.svelte";
    import WorkspaceDeleteDialog from "../../WorkspaceDeleteDialog.svelte";

    let { canUndo, canRedo, isWorkspaceReadOnly, reload = () => {} } = $props();

    const shortcutsUnregister = [];

    let showNewClassDialog = $state(false);
    let showNewGraphDialog = $state(false);
    let showNewPackageDialog = $state(false);
    let ShowPackageDeleteDependenciesDialog = $state(false);
    let showOntologyDeleteDependenciesDialog = $state(false);
    let showClassDeleteDependenciesDialog = $state(false);
    let showGraphDeleteDialog = $state(false);
    let showWorkspaceDeleteDialog = $state(false);
    let showGraphRenameDialog = $state(false);
    let showWorkspaceRenameDialog = $state(false);
    let showPackageEditorDialog = $state(false);
    let showNamespaceDialog = $state(false);
    let showEditOntologyDialog = $state(false);

    let packageDialogTarget = $state(null);
    let packageDialogWorkspace = $state(null);
    let packageDialogGraph = $state(null);
    let selectedPackageDetails = $state(null);
    let packageDetailsRequestId = 0;
    let packages = $state([]);

    let ontology = $state();

    let selectedWorkspace = $derived(editorState.selectedWorkspace.getValue());
    let selectedGraph = $derived(editorState.selectedGraph.getValue());
    let hasWorkspaceSelected = $derived(!!selectedWorkspace);
    let hasGraphSelected = $derived(
        hasWorkspaceSelected && !!editorState.selectedGraph.getValue(),
    );
    let canAccessNamespaces = $derived(hasWorkspaceSelected);
    let canEditCurrentPackage = $derived(
        selectedPackageDetails &&
            !selectedPackageDetails.external &&
            selectedPackageDetails.label !== "default" &&
            !isWorkspaceReadOnly,
    );
    let canDeleteCurrentPackage = $derived(
        selectedPackageDetails &&
            !selectedPackageDetails.external &&
            selectedPackageDetails.label !== "default" &&
            !isWorkspaceReadOnly,
    );
    let graphHasOntology = $derived(!!ontology);

    let disableCopyClassButton = $derived(
        !editorState.selectedClass.getProperty("id") &&
            multiSelectState.getSelected().length === 0,
    );

    /*
      The class(es) targeted by the delete shortcut: the multi-selection if
        present (and within a single graph), otherwise the single selected class.
    */
    let deleteClassSelection = $derived.by(() => {
        const selected = multiSelectState.getSelected();
        if (selected.length > 0) {
            return {
                workspaceName: selected[0].workspaceName,
                graphUri: selected[0].graphUri,
                uuids: selected.map(e => e.classUuid),
                singleGraph: multiSelectState.isSingleGraph,
            };
        }
        const uuid = editorState.selectedClass.getProperty("id");
        if (uuid) {
            return {
                workspaceName: editorState.selectedClassWorkspace.getValue(),
                graphUri: editorState.selectedClassGraph.getValue(),
                uuids: [uuid],
                singleGraph: true,
            };
        }
        return null;
    });
    let disableDeleteClassButton = $derived(
        isWorkspaceReadOnly ||
            !deleteClassSelection ||
            !deleteClassSelection.singleGraph,
    );

    // A custom diagram isn't deletable here, so it resolves to its graph/workspace.
    let deleteShortcutTarget = $derived.by(() => {
        if (deleteClassSelection) {
            return SelectionLevel.CLASS;
        }
        switch (inferSelectionLevel()) {
            case SelectionLevel.PACKAGE:
                return SelectionLevel.PACKAGE;
            case SelectionLevel.DIAGRAM:
            case SelectionLevel.GRAPH:
                return hasGraphSelected
                    ? SelectionLevel.GRAPH
                    : SelectionLevel.WORKSPACE;
            case SelectionLevel.WORKSPACE:
                return SelectionLevel.WORKSPACE;
            default:
                return null;
        }
    });

    // Shift+F6 renames the schema in context, or the workspace when the
    // selection sits at workspace level.
    let renameShortcutTarget = $derived.by(() => {
        if (inferSelectionLevel() !== SelectionLevel.WORKSPACE) {
            if (hasGraphSelected) {
                return SelectionLevel.GRAPH;
            }
        }
        return hasWorkspaceSelected ? SelectionLevel.WORKSPACE : null;
    });

    let disablePasteButton = $derived(
        isWorkspaceReadOnly ||
            !hasGraphSelected ||
            !editorState.selectedDiagram.getProperty("id") ||
            copyState.isEmpty,
    );

    $effect(async () => {
        editorState.selectedDiagram.subscribe();
        editorState.selectedClass.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedWorkspace.subscribe();
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
            shortcutStore.register(
                "renameSelection",
                ["shift", "f6"],
                () => renameSelectionWithShortcut(),
                true,
            ),
            shortcutStore.register("copyClass", ["ctrl", "c"], () =>
                copyClassWithShortcut(),
            ),
            shortcutStore.register("deleteSelection", ["delete"], () =>
                deleteSelectionWithShortcut(),
            ),
            ...PASTE_VARIANTS.map(variant =>
                shortcutStore.register(
                    variant.id,
                    variant.keys,
                    () => pasteClassWithShortcut(variant.options),
                    true,
                ),
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

        return await ontologyStore.getOntologyForGraph(
            selectedWorkspace,
            selectedGraph,
        );
    }

    async function requestEnableEditing() {
        if (!selectedWorkspace || !isWorkspaceReadOnly) {
            return;
        }

        const { error } = await datasetStore.updateReadonly(
            selectedWorkspace,
            false,
        );
        if (error) return;

        await reload();
        forceReloadTrigger.trigger();
    }

    async function requestDisableEditing() {
        if (!selectedWorkspace || isWorkspaceReadOnly) {
            return;
        }

        const { error } = await datasetStore.updateReadonly(
            selectedWorkspace,
            true,
        );
        if (error) return;

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
        packageDialogWorkspace = selectedWorkspace;
        packageDialogGraph = selectedGraph;
        showPackageEditorDialog = true;
    }

    function launchPackageDeleteDialog() {
        if (!canDeleteCurrentPackage || !selectedPackageDetails) return;
        packageDialogTarget = { ...selectedPackageDetails };
        packageDialogWorkspace = selectedWorkspace;
        packageDialogGraph = selectedGraph;
        ShowPackageDeleteDependenciesDialog = true;
    }

    async function getPackages() {
        if (!hasGraphSelected) {
            return [];
        }

        const packageData = await packageStore.getPackages(
            selectedWorkspace,
            selectedGraph,
        );

        if (!packageData) {
            return [];
        }

        return [
            ...(packageData?.internal ?? []).map(p => ({
                ...p,
                external: false,
            })),
            ...(packageData?.external ?? []).map(p => ({
                ...p,
                external: true,
            })),
        ];
    }

    async function refreshSelectedPackageDetails(packages) {
        const workspaceName = editorState.selectedWorkspace.getValue();
        const graphURI = editorState.selectedGraph.getValue();
        const packageId = editorState.selectedDiagram.getProperty("id");

        if (!workspaceName || !graphURI || !packageId) {
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
        const { error } = await versionControlStore.undo(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
        );
        if (!error) {
            reload();
        }
    }

    async function redo() {
        const { error } = await versionControlStore.redo(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
        );
        if (!error) {
            reload();
        }
    }

    function copyClass() {
        copyState.set(
            multiSelectState.copyEntriesOr({
                classUUID: editorState.selectedClass.getProperty("id"),
                graphURI: editorState.selectedClassGraph.getValue(),
                workspaceName: editorState.selectedClassWorkspace.getValue(),
            }),
        );
    }

    async function pasteClass(options) {
        await startPaste(
            editorState.selectedWorkspace.getValue(),
            editorState.selectedGraph.getValue(),
            selectedPackageDetails?.uuid ?? null,
            options,
        );
    }

    function copyClassWithShortcut() {
        if (!disableCopyClassButton) {
            copyClass();
        }
    }

    function deleteSelectionWithShortcut() {
        switch (deleteShortcutTarget) {
            case SelectionLevel.CLASS:
                if (!disableDeleteClassButton) {
                    showClassDeleteDependenciesDialog = true;
                }
                break;
            case SelectionLevel.PACKAGE:
                launchPackageDeleteDialog();
                break;
            case SelectionLevel.GRAPH:
                if (!isWorkspaceReadOnly) {
                    showGraphDeleteDialog = true;
                }
                break;
            case SelectionLevel.WORKSPACE:
                showWorkspaceDeleteDialog = true;
                break;
        }
    }

    function renameSelectionWithShortcut() {
        if (isWorkspaceReadOnly) {
            return;
        }
        switch (renameShortcutTarget) {
            case SelectionLevel.GRAPH:
                showGraphRenameDialog = true;
                break;
            case SelectionLevel.WORKSPACE:
                showWorkspaceRenameDialog = true;
                break;
        }
    }

    function pasteClassWithShortcut(options) {
        if (!disablePasteButton) {
            pasteClass(options);
        }
    }

    function toggleReadonly() {
        if (isWorkspaceReadOnly) {
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
                    Schema
                </Menubar.Item.Button>
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
        <Menubar.SubMenu.Root>
            <Menubar.SubMenu.Trigger
                faIcon={isWorkspaceReadOnly ? faEye : faPen}
            >
                {isWorkspaceReadOnly ? "View" : "Edit"}
            </Menubar.SubMenu.Trigger>
            <Menubar.SubMenu.Content>
                <Menubar.Item.Button
                    onSelect={() => (showEditOntologyDialog = true)}
                    disabled={!hasGraphSelected ||
                        (isWorkspaceReadOnly && !graphHasOntology)}
                    faIcon={graphHasOntology
                        ? isWorkspaceReadOnly
                            ? faEye
                            : faPen
                        : faPlus}
                    altText="Ctrl+Alt+P"
                >
                    Profile Header
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
                <PasteMenuItems
                    Item={Menubar.Item.Button}
                    disabled={disablePasteButton}
                    onPaste={pasteClass}
                />
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
        <Menubar.Separator />
        <Menubar.Item.Button
            onSelect={() => undo()}
            disabled={isWorkspaceReadOnly || !canUndo}
            faIcon={faRotateLeft}
            altText="Ctrl+Z"
        >
            Undo
        </Menubar.Item.Button>
        <Menubar.Item.Button
            onSelect={() => redo()}
            disabled={isWorkspaceReadOnly || !canRedo}
            faIcon={faRotateRight}
            altText="Ctrl+Y"
        >
            Redo
        </Menubar.Item.Button>
        <Menubar.Separator />
        {#if !hasWorkspaceSelected || isWorkspaceReadOnly}
            <Menubar.Item.Button
                onSelect={() => requestEnableEditing()}
                disabled={!hasWorkspaceSelected || !isWorkspaceReadOnly}
                faIcon={faPenToSquare}
                altText="Ctrl+Alt+R"
            >
                Enable Editing
            </Menubar.Item.Button>
        {:else}
            <Menubar.Item.Button
                onSelect={() => requestDisableEditing()}
                disabled={!hasWorkspaceSelected || isWorkspaceReadOnly}
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
            {#if isWorkspaceReadOnly}
                View Namespaces
            {:else}
                Edit Namespaces
            {/if}
        </Menubar.Item.Button>
        <Menubar.Separator />
        <Menubar.SubMenu.Root>
            <Menubar.SubMenu.Trigger faIcon={faPen}>
                Rename
            </Menubar.SubMenu.Trigger>
            <Menubar.SubMenu.Content>
                <Menubar.Item.Button
                    onSelect={() => (showWorkspaceRenameDialog = true)}
                    disabled={!hasWorkspaceSelected || isWorkspaceReadOnly}
                    faIcon={faPen}
                >
                    Workspace
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showGraphRenameDialog = true)}
                    disabled={!hasGraphSelected || isWorkspaceReadOnly}
                    faIcon={faDiagramProject}
                >
                    Schema
                </Menubar.Item.Button>
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
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
                    Profile Header
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
        workspaceName={packageDialogWorkspace}
        graphUri={packageDialogGraph}
        {packages}
        pack={packageDialogTarget}
        readonly={isWorkspaceReadOnly}
    />
{/if}
{#if packageDialogTarget && ShowPackageDeleteDependenciesDialog}
    <DeleteDependenciesDialog
        bind:showDialog={ShowPackageDeleteDependenciesDialog}
        workspaceName={packageDialogWorkspace}
        graphUri={packageDialogGraph}
        resourceUuid={packageDialogTarget.uuid}
    />
{/if}
{#if showClassDeleteDependenciesDialog && deleteClassSelection}
    <DeleteDependenciesDialog
        bind:showDialog={showClassDeleteDependenciesDialog}
        workspaceName={deleteClassSelection.workspaceName}
        graphUri={deleteClassSelection.graphUri}
        resourceUuids={deleteClassSelection.uuids}
    />
{/if}
<GraphDeleteDialog bind:showDialog={showGraphDeleteDialog} />
<WorkspaceDeleteDialog
    bind:showDialog={showWorkspaceDeleteDialog}
    workspaceName={selectedWorkspace}
/>
<RenameWorkspaceDialog
    bind:showDialog={showWorkspaceRenameDialog}
    workspaceName={selectedWorkspace}
/>
<RenameGraphDialog
    bind:showDialog={showGraphRenameDialog}
    workspaceName={selectedWorkspace}
    graphUri={selectedGraph}
/>
<NamespacesDialog bind:showDialog={showNamespaceDialog} />
{#if ontology}
    <DeleteDependenciesDialog
        bind:showDialog={showOntologyDeleteDependenciesDialog}
        onClose={reload}
        workspaceName={editorState.selectedWorkspace.getValue()}
        graphUri={editorState.selectedGraph.getValue()}
        resourceUuid={ontology.uuid}
    />
{/if}

{#if showEditOntologyDialog}
    <OntologyDialog
        bind:showDialog={showEditOntologyDialog}
        graphUri={selectedGraph}
        workspace={selectedWorkspace}
        bind:ontology
        readonly={isWorkspaceReadOnly}
    />
{/if}
