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
        faBoxArchive,
        faDownload,
        faFileExport,
        faFileImport,
        faFloppyDisk,
        faFolderOpen,
        faGear,
        faShare,
        faTrash,
        faUpload,
    } from "@fortawesome/free-solid-svg-icons";
    import { onDestroy, onMount } from "svelte";

    import { exportWorkspace } from "$lib/api/generated/index.ts";
    import { Menubar } from "$lib/components/bitsui/menubar";
    import {
        PUBLIC_DESKTOP,
        PUBLIC_SHARE_SNAPSHOT_ENABLED,
    } from "$lib/config/runtime";
    import { saveWorkspace } from "$lib/desktop/saveWorkspace.js";
    import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";
    import {
        saveFile,
        workspaceArchiveMediaType,
    } from "$lib/utils/fileUtils.ts";

    import DocumentationExportDialog from "../../DocumentationExportDialog.svelte";
    import ExportDialog from "../../ExportDialog.svelte";
    import GraphDeleteDialog from "../../GraphDeleteDialog.svelte";
    import ImportDialog from "../../ImportDialog.svelte";
    import SHACLExportDialog from "../../shacl/SHACLExportDialog.svelte";
    import SHACLUploadDialog from "../../shacl/SHACLUploadDialog.svelte";
    import SnapshotDialog from "../../SnapshotDialog.svelte";
    import UserSettingDialog from "../../UserSettingDialog.svelte";
    import WorkspaceDeleteDialog from "../../WorkspaceDeleteDialog.svelte";
    import WorkspaceImportDialog from "../../WorkspaceImportDialog.svelte";
    import XmiImportDialog from "../../XmiImportDialog.svelte";

    let { isWorkspaceReadOnly } = $props();

    const shortcutsUnregister = [];

    let showExportDialog = $state(false);
    let showSnapshotDialog = $state(false);
    let showImportDialog = $state(false);
    let showWorkspaceImportDialog = $state(false);
    let showXmiImportDialog = $state(false);
    let showSHACLExportDialog = $state(false);
    let showSHACLUploadDialog = $state(false);
    let showDeleteDialog = $state(false);
    let showWorkspaceDeleteDialog = $state(false);
    let showUserSettingDialog = $state(false);
    let showDocumentationExportDialog = $state(false);

    let selectedWorkspace = $derived(editorState.selectedWorkspace.getValue());
    let selectedGraph = $derived(editorState.selectedGraph.getValue());
    let hasWorkspaceSelected = $derived(!!selectedWorkspace);
    let hasGraphSelected = $derived(hasWorkspaceSelected && !!selectedGraph);

    $effect(() => {
        editorState.selectedWorkspace.subscribe();
        editorState.selectedGraph.subscribe();
    });

    onMount(() => {
        shortcutsUnregister.push(
            shortcutStore.register(
                "import",
                ["ctrl", "i"],
                () => (showImportDialog = true),
                true,
            ),
            shortcutStore.register(
                "export",
                ["ctrl", "e"],
                () => (showExportDialog = true),
                true,
            ),
            shortcutStore.register(
                "shaclImport",
                ["ctrl", "shift", "i"],
                () => (showSHACLUploadDialog = true),
                true,
            ),
            shortcutStore.register(
                "shaclExport",
                ["ctrl", "shift", "e"],
                () => (showSHACLExportDialog = true),
                true,
            ),
            shortcutStore.register(
                "settings",
                ["ctrl", "alt", "s"],
                () => (showUserSettingDialog = true),
                true,
            ),
            shortcutStore.register(
                "htmlExport",
                ["ctrl", "alt", "h"],
                () => (showDocumentationExportDialog = true),
                true,
            ),
        );
        // The desktop app has no snapshots, and gives their keys to Save As.
        if (PUBLIC_SHARE_SNAPSHOT_ENABLED && !PUBLIC_DESKTOP) {
            shortcutsUnregister.push(
                shortcutStore.register(
                    "snapshot",
                    ["ctrl", "shift", "s"],
                    () => (showSnapshotDialog = true),
                    true,
                ),
            );
        }
        if (PUBLIC_DESKTOP) {
            shortcutsUnregister.push(
                shortcutStore.register(
                    "openWorkspace",
                    ["ctrl", "o"],
                    () => (showWorkspaceImportDialog = true),
                    true,
                    { whileTyping: true },
                ),
                shortcutStore.register(
                    "saveWorkspace",
                    ["ctrl", "s"],
                    event => saveFromShortcut(event, false),
                    false,
                    { whileTyping: true, keepsEdits: true },
                ),
                shortcutStore.register(
                    "saveWorkspaceAs",
                    ["ctrl", "shift", "s"],
                    event => saveFromShortcut(event, true),
                    false,
                    { whileTyping: true, keepsEdits: true },
                ),
            );
        }
    });

    onDestroy(() => {
        shortcutsUnregister.forEach(unregister => unregister());
    });

    function saveFromShortcut(event, saveAs) {
        // A held key repeats; one press saves once.
        if (!event.repeat) {
            saveSelectedWorkspace(saveAs);
        }
    }

    function saveSelectedWorkspace(saveAs) {
        if (hasWorkspaceSelected) {
            void saveWorkspace(selectedWorkspace, { saveAs });
        }
    }

    async function downloadWorkspaceArchive() {
        const workspaceName = selectedWorkspace;
        try {
            const { data, error, response } = await exportWorkspace({
                path: { workspaceName },
            });
            if (error) {
                toastStore.error(
                    "Export failed",
                    `Could not export workspace "${workspaceName}".`,
                );
                return;
            }
            saveFile(
                data,
                response.headers.get("content-disposition"),
                workspaceArchiveMediaType,
            );
            toastStore.success(
                "Export ready",
                `Workspace "${workspaceName}" downloaded as a ${workspaceArchiveMediaType.fileExtension} file.`,
            );
        } catch (e) {
            console.error("Failed to export workspace:", e);
            toastStore.error(
                "Export failed",
                "An unexpected error occurred while exporting.",
            );
        }
    }
</script>

<Menubar.Menu value="file">
    <Menubar.Trigger>File</Menubar.Trigger>
    <Menubar.Content side="bottom" sideOffset={8}>
        {#if PUBLIC_DESKTOP}
            <Menubar.Item.Button
                onSelect={() => (showWorkspaceImportDialog = true)}
                faIcon={faFolderOpen}
                altText="Ctrl+O"
            >
                Open Workspace…
            </Menubar.Item.Button>
            <Menubar.Item.Button
                onSelect={() => saveSelectedWorkspace(false)}
                disabled={!hasWorkspaceSelected}
                faIcon={faFloppyDisk}
                altText="Ctrl+S"
            >
                Save Workspace
            </Menubar.Item.Button>
            <Menubar.Item.Button
                onSelect={() => saveSelectedWorkspace(true)}
                disabled={!hasWorkspaceSelected}
                faIcon={faFloppyDisk}
                altText="Ctrl+Shift+S"
            >
                Save Workspace As…
            </Menubar.Item.Button>
            <Menubar.Separator />
        {/if}
        <Menubar.SubMenu.Root>
            <Menubar.SubMenu.Trigger faIcon={faFileImport}>
                Import
            </Menubar.SubMenu.Trigger>
            <Menubar.SubMenu.Content>
                <Menubar.Item.Button
                    onSelect={() => (showImportDialog = true)}
                    faIcon={faFileImport}
                    altText="Ctrl+I"
                >
                    Schema (RDFS)
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showXmiImportDialog = true)}
                    faIcon={faFileImport}
                >
                    Model (Enterprise Architect XMI)
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showSHACLUploadDialog = true)}
                    faIcon={faUpload}
                    altText="Ctrl+Shift+I"
                >
                    Constraints (SHACL)
                </Menubar.Item.Button>
                {#if !PUBLIC_DESKTOP}
                    <Menubar.Item.Button
                        onSelect={() => (showWorkspaceImportDialog = true)}
                        faIcon={faBoxArchive}
                    >
                        Workspace ({workspaceArchiveMediaType.fileExtension})
                    </Menubar.Item.Button>
                {/if}
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
        <Menubar.SubMenu.Root>
            <Menubar.SubMenu.Trigger faIcon={faFileExport}>
                Export
            </Menubar.SubMenu.Trigger>
            <Menubar.SubMenu.Content>
                <Menubar.Item.Button
                    onSelect={() => (showExportDialog = true)}
                    faIcon={faFileExport}
                    altText="Ctrl+E"
                >
                    Schema (RDFS)
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showSHACLExportDialog = true)}
                    faIcon={faDownload}
                    altText="Ctrl+Shift+E"
                >
                    Constraints (SHACL)
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showDocumentationExportDialog = true)}
                    faIcon={faDownload}
                    altText="Ctrl+Alt+H"
                >
                    Documentation (HTML, AsciiDoc)
                </Menubar.Item.Button>
                {#if !PUBLIC_DESKTOP}
                    <Menubar.Item.Button
                        onSelect={downloadWorkspaceArchive}
                        disabled={!hasWorkspaceSelected}
                        faIcon={faBoxArchive}
                    >
                        Workspace ({workspaceArchiveMediaType.fileExtension})
                    </Menubar.Item.Button>
                {/if}
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
        <Menubar.Separator />
        <Menubar.Item.Button
            onSelect={() => (showUserSettingDialog = true)}
            faIcon={faGear}
            altText="Ctrl+Alt+S"
        >
            Settings
        </Menubar.Item.Button>
        <Menubar.Separator />
        {#if PUBLIC_SHARE_SNAPSHOT_ENABLED}
            <Menubar.Item.Button
                onSelect={() => (showSnapshotDialog = true)}
                faIcon={faShare}
                altText={PUBLIC_DESKTOP ? undefined : "Ctrl+Shift+S"}
            >
                Share Snapshot
            </Menubar.Item.Button>
            <Menubar.Separator />
        {/if}
        <Menubar.SubMenu.Root>
            <Menubar.SubMenu.Trigger faIcon={faTrash} variant="danger">
                Delete
            </Menubar.SubMenu.Trigger>
            <Menubar.SubMenu.Content>
                <Menubar.Item.Button
                    onSelect={() => (showDeleteDialog = true)}
                    disabled={!hasGraphSelected || isWorkspaceReadOnly}
                    faIcon={faTrash}
                    variant="danger"
                >
                    Schema
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showWorkspaceDeleteDialog = true)}
                    disabled={!hasWorkspaceSelected}
                    faIcon={faTrash}
                    variant="danger"
                >
                    Workspace
                </Menubar.Item.Button>
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
    </Menubar.Content>
</Menubar.Menu>

<ImportDialog bind:showDialog={showImportDialog} />
<WorkspaceImportDialog bind:showDialog={showWorkspaceImportDialog} />
<XmiImportDialog bind:showDialog={showXmiImportDialog} />
<ExportDialog bind:showDialog={showExportDialog} />
{#if PUBLIC_SHARE_SNAPSHOT_ENABLED}
    <SnapshotDialog bind:showDialog={showSnapshotDialog} />
{/if}
<SHACLUploadDialog bind:showDialog={showSHACLUploadDialog} />
<SHACLExportDialog bind:showDialog={showSHACLExportDialog} />
<DocumentationExportDialog bind:showDialog={showDocumentationExportDialog} />
<UserSettingDialog bind:showDialog={showUserSettingDialog} />

<GraphDeleteDialog bind:showDialog={showDeleteDialog} />
<WorkspaceDeleteDialog
    bind:showDialog={showWorkspaceDeleteDialog}
    workspaceName={selectedWorkspace}
/>
