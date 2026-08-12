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
        faLock,
        faPenToSquare,
        faPlus,
        faShare,
        faTags,
        faTrash,
    } from "@fortawesome/free-solid-svg-icons";

    import {
        disableEditing,
        enableEditing,
    } from "$lib/actions/editingActions.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { workspaceState } from "$lib/workspaceState.svelte.js";

    import ImportDialog from "../../ImportDialog.svelte";
    import NamespacesDialog from "../../NamespacesDialog.svelte";
    import NewGraphDialog from "../../NewGraphDialog.svelte";
    import SnapshotDialog from "../../SnapshotDialog.svelte";
    import WorkspaceDeleteDialog from "../../WorkspaceDeleteDialog.svelte";
    import CustomDatasetDiagramDialog from "../packageNavigation/custom-diagram-dialogs/CustomDatasetDiagramDialog.svelte";

    // Rendered inside a ContextMenu.Root: the workspace tabs and the
    // navigation background share these actions.
    let { workspaceName, showDeleteDialog = $bindable(false) } = $props();

    let showNewGraphDialog = $state(false);
    let showImportDialog = $state(false);
    let showNewDiagramDialog = $state(false);
    let showNamespacesDialog = $state(false);
    let showSnapshotDialog = $state(false);

    const readonly = $derived(workspaceState.isReadOnly(workspaceName));

    async function toggleEditing(editingEnabled) {
        const succeeded = editingEnabled
            ? await enableEditing(workspaceName)
            : await disableEditing(workspaceName);
        if (!succeeded) {
            return;
        }
        workspaceState.setReadOnly(workspaceName, !editingEnabled);
        forceReloadTrigger.trigger();
    }
</script>

<ContextMenu.Content>
    <ContextMenu.Item.Button
        onSelect={() => (showNewGraphDialog = true)}
        disabled={readonly}
        faIcon={faDiagramProject}
    >
        New Schema
    </ContextMenu.Item.Button>
    <ContextMenu.Item.Button
        onSelect={() => (showImportDialog = true)}
        disabled={readonly}
        faIcon={faFileImport}
        altText="Ctrl+I"
    >
        Import Schema (RDFS)
    </ContextMenu.Item.Button>
    <ContextMenu.Item.Button
        onSelect={() => (showNewDiagramDialog = true)}
        faIcon={faPlus}
    >
        New Workspace Diagram
    </ContextMenu.Item.Button>
    <ContextMenu.Separator />
    <ContextMenu.Item.Button
        onSelect={() => (showNamespacesDialog = true)}
        faIcon={faTags}
        altText="Ctrl+Shift+A"
    >
        {#if readonly}
            View Namespaces
        {:else}
            Edit Namespaces
        {/if}
    </ContextMenu.Item.Button>
    <ContextMenu.Item.Button
        onSelect={() => (showSnapshotDialog = true)}
        faIcon={faShare}
        altText="Ctrl+Shift+S"
    >
        Share Snapshot
    </ContextMenu.Item.Button>
    {#if readonly}
        <ContextMenu.Item.Button
            onSelect={() => toggleEditing(true)}
            faIcon={faPenToSquare}
            altText="Ctrl+Alt+R"
        >
            Enable Editing
        </ContextMenu.Item.Button>
    {:else}
        <ContextMenu.Item.Button
            onSelect={() => toggleEditing(false)}
            faIcon={faLock}
            altText="Ctrl+Alt+R"
        >
            Disable Editing
        </ContextMenu.Item.Button>
    {/if}
    <ContextMenu.Separator />
    <ContextMenu.Item.Button
        onSelect={() => (showDeleteDialog = true)}
        faIcon={faTrash}
        variant="danger"
    >
        Delete Workspace
    </ContextMenu.Item.Button>
</ContextMenu.Content>

<NewGraphDialog
    bind:showDialog={showNewGraphDialog}
    lockedDatasetName={workspaceName}
/>
<ImportDialog
    bind:showDialog={showImportDialog}
    lockedDatasetName={workspaceName}
/>
<CustomDatasetDiagramDialog
    bind:showDialog={showNewDiagramDialog}
    lockedDatasetName={workspaceName}
/>
<NamespacesDialog
    bind:showDialog={showNamespacesDialog}
    lockedDatasetName={workspaceName}
/>
<SnapshotDialog
    bind:showDialog={showSnapshotDialog}
    lockedDatasetName={workspaceName}
/>
<WorkspaceDeleteDialog bind:showDialog={showDeleteDialog} {workspaceName} />
