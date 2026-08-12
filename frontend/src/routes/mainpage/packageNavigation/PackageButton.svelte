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
        faFolder,
        faFolderOpen,
    } from "@fortawesome/free-regular-svg-icons";
    import {
        faPencil,
        faPlus,
        faLink,
        faTrash,
        faEye,
        faPaste,
        faObjectGroup,
    } from "@fortawesome/free-solid-svg-icons";
    import { getContext } from "svelte";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import {
        copyState,
        editorState,
        multiSelectState,
    } from "$lib/sharedState.svelte.js";
    import { shortenIri } from "$lib/utils/iri.js";

    import ClassEntry from "./ClassEntry.svelte";
    import AddToGraphDiagramDialog from "./custom-diagram-dialogs/AddToGraphDiagramDialog.svelte";
    import {
        isSelectedPackage,
        packageHighlight,
    } from "./packageNavigationUtils.svelte.js";
    import DeleteDependenciesDialog from "../../delete-relations-dialog/DeleteDependenciesDialog.svelte";
    import NewClassDialog from "../../NewClassDialog.svelte";
    import PackageEditorDialog from "../packageEditorDialog.svelte";
    import AddToGraphDiagramDialog from "./custom-diagram-dialogs/AddToGraphDiagramDialog.svelte";
    import AddToWorkspaceDiagramDialog from "./custom-diagram-dialogs/AddToWorkspaceDiagramDialog.svelte";
    import { startPaste } from "./paste-flow.svelte.js";
    import PasteMenuItems from "./PasteMenuItems.svelte";

    let {
        workspaceNavEntry,
        graphNavEntry,
        packageNavEntry,
        namespaces = [],
        readonly,
    } = $props();
    let showNewClassDialog = $state(false);
    let showAddToGraphDiagramDialog = $state(false);
    let showAddToWorkspaceDiagramDialog = $state(false);
    let showPackageEditorDialog = $state(false);
    let showDeleteDependenciesDialog = $state(false);

    let wasPackageSelected = false;

    let disablePasteButton = $derived(readonly || copyState.isEmpty);

    let isProtectedPackage = $derived(
        packageNavEntry?.data.uuid == null || packageNavEntry?.data.external,
    );

    const selectionTrigger = $derived([
        editorState.selectedWorkspace.subscribe(),
        editorState.selectedGraph.subscribe(),
        editorState.selectedDiagram.subscribe(),
        editorState.activeSelectionKind.subscribe(),
        editorState.selectedClass.subscribe(),
        multiSelectState.subscribe(),
        getContext("packageNavigation").reloadTrigger?.subscribe(),
    ]);

    let isPackageSelected = $derived(
        selectionTrigger &&
            isSelectedPackage(
                workspaceNavEntry.id,
                graphNavEntry.id,
                packageNavEntry.id,
            ),
    );
    let packageSelectionState = $derived(
        selectionTrigger &&
            packageHighlight(
                workspaceNavEntry.id,
                graphNavEntry.id,
                packageNavEntry.id,
                packageNavEntry.children,
            ),
    );

    let packageHighlightLabel = $derived(
        shortenIri(namespaces, packageNavEntry.tooltip),
    );
    const packageActionLabel = $derived(
        readonly ? "View Package" : "Edit Package",
    );
    const packageActionIcon = $derived(readonly ? faEye : faPencil);
    const disablePackageAction = $derived(
        readonly ? false : isProtectedPackage,
    );
    const hasClasses = $derived(packageNavEntry?.children?.length > 0);
    $effect(() => {
        if (selectionTrigger && isPackageSelected && !wasPackageSelected) {
            packageNavEntry.parent?.open();
        }
        wasPackageSelected = isPackageSelected;
    });

    function copyWorkspaceUrl() {
        const params = new URLSearchParams({
            dataset: workspaceNavEntry.id,
            graph: graphNavEntry.id,
            package: packageNavEntry.id,
        });
        const url = `${window.location.origin}/mainpage?${params}`;
        navigator.clipboard
            .writeText(url)
            .catch(err =>
                console.error("Writing to the clipboard is not allowed: ", err),
            );
    }

    function selectPackage() {
        editorState.selectPackage(
            workspaceNavEntry.id,
            graphNavEntry.id,
            packageNavEntry.id,
        );
    }

    async function pasteClass(options) {
        await startPaste(
            workspaceNavEntry.id,
            graphNavEntry.id,
            packageNavEntry.data.uuid,
            options,
        );
    }
</script>

<div class="flex w-full flex-col items-stretch gap-[0.1rem]">
    <ContextMenu.Root>
        <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
            <NavigationEntry
                level={2}
                label={packageNavEntry.label}
                icon={packageNavEntry?.isOpen ? faFolderOpen : faFolder}
                isSelected={packageSelectionState === "active"}
                ancestorSelected={packageSelectionState === "ancestor"}
                hasChildren={hasClasses}
                expanded={packageNavEntry.isOpen}
                title={packageNavEntry.tooltip}
                highlightLabel={packageHighlightLabel}
                badgeText={packageNavEntry.data.external ? "External" : ""}
                badgeVariant={packageNavEntry.data.external
                    ? "external"
                    : "default"}
                onclick={selectPackage}
                onToggle={() => packageNavEntry.toggle()}
            />
        </ContextMenu.TriggerArea>
        <ContextMenu.Content>
            <ContextMenu.Item.Button
                onSelect={() => {
                    showNewClassDialog = true;
                }}
                disabled={readonly}
                faIcon={faPlus}
                altText="Shift+N"
            >
                New Class
            </ContextMenu.Item.Button>
            <ContextMenu.SubMenu.Root>
                <ContextMenu.SubMenu.Trigger faIcon={faPaste} disabled={false}>
                    Paste
                </ContextMenu.SubMenu.Trigger>
                <ContextMenu.SubMenu.Content>
                    <PasteMenuItems
                        Item={ContextMenu.Item.Button}
                        disabled={disablePasteButton}
                        onPaste={pasteClass}
                    />
                </ContextMenu.SubMenu.Content>
            </ContextMenu.SubMenu.Root>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    showAddToGraphDiagramDialog = true;
                }}
                faIcon={faObjectGroup}
            >
                Add to Schema Diagram
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    showAddToWorkspaceDiagramDialog = true;
                }}
                faIcon={faObjectGroup}
            >
                Add to Workspace Diagram
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    showPackageEditorDialog = true;
                }}
                disabled={disablePackageAction}
                faIcon={packageActionIcon}
                altText="Ctrl+Shift+K"
            >
                {packageActionLabel}
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={copyWorkspaceUrl}
                faIcon={faLink}
            >
                Copy Link to Package
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    showDeleteDependenciesDialog = true;
                }}
                disabled={readonly || isProtectedPackage}
                faIcon={faTrash}
                variant="danger"
            >
                Delete Package
            </ContextMenu.Item.Button>
        </ContextMenu.Content>
    </ContextMenu.Root>
    {#if packageNavEntry.isOpen && hasClasses}
        <div
            class="flex w-full flex-col items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each packageNavEntry.children as classNavEntry (classNavEntry.id)}
                <ClassEntry
                    {workspaceNavEntry}
                    {graphNavEntry}
                    {classNavEntry}
                    {namespaces}
                    {readonly}
                />
            {/each}
        </div>
    {/if}
</div>

{#if showNewClassDialog}
    <NewClassDialog
        bind:showDialog={showNewClassDialog}
        lockedWorkspaceName={workspaceNavEntry.id}
        lockedGraphUri={graphNavEntry.id}
        lockedPackage={packageNavEntry.data}
    />
{/if}

{#if showAddToGraphDiagramDialog}
    <AddToGraphDiagramDialog
        bind:showDialog={showAddToGraphDiagramDialog}
        lockedWorkspaceName={workspaceNavEntry.id}
        lockedGraphUri={graphNavEntry.id}
        classes={packageNavEntry.children}
    />
{/if}

{#if showAddToWorkspaceDiagramDialog}
    <AddToWorkspaceDiagramDialog
        bind:showDialog={showAddToWorkspaceDiagramDialog}
        lockedWorkspaceName={workspaceNavEntry.id}
        lockedGraphUri={graphNavEntry.id}
        classes={packageNavEntry.children}
    />
{/if}

{#if showPackageEditorDialog}
    <PackageEditorDialog
        bind:showDialog={showPackageEditorDialog}
        workspaceName={workspaceNavEntry.id}
        graphUri={graphNavEntry.id}
        pack={packageNavEntry.data}
        {readonly}
    />
{/if}

{#if showDeleteDependenciesDialog}
    <DeleteDependenciesDialog
        bind:showDialog={showDeleteDependenciesDialog}
        workspaceName={workspaceNavEntry.id}
        graphUri={graphNavEntry.id}
        resourceUuid={packageNavEntry.data.uuid}
    />
{/if}
