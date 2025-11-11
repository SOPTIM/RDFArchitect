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
        faDownload,
        faFileExport,
        faFileImport,
        faShare,
        faTrash,
        faUpload,
    } from "@fortawesome/free-solid-svg-icons";

    import { Menubar } from "$lib/components/bitsui/menubar";
    import { editorState } from "$lib/sharedState.svelte.js";

    import DeleteDatasetDialog from "../../DeleteDatasetDialog.svelte";
    import ExportDialog from "../../ExportDialog.svelte";
    import ImportDialog from "../../ImportDialog.svelte";
    import DatasetDeleteDialog from "../../mainpage/packageNavigation/DatasetDeleteDialog.svelte";
    import SHACLExportDialog from "../../shacl/SHACLExportDialog.svelte";
    import SHACLUploadDialog from "../../shacl/SHACLUploadDialog.svelte";
    import SnapshotDialog from "../../SnapshotDialog.svelte";

    let { isDatasetReadOnly } = $props();

    let showExportDialog = $state(false);
    let showSnapshotDialog = $state(false);
    let showImportDialog = $state(false);
    let showSHACLExportDialog = $state(false);
    let showSHACLUploadDialog = $state(false);
    let showDeleteDialog = $state(false);
    let showDatasetDeleteDialog = $state(false);

    let selectedDataset = $derived(editorState.selectedDataset.getValue());
    let selectedGraph = $derived(editorState.selectedGraph.getValue());
    let hasDatasetSelected = $derived(!!selectedDataset);
    let hasGraphSelected = $derived(hasDatasetSelected && !!selectedGraph);

    $effect(() => {
        editorState.selectedDataset.subscribe();
        editorState.selectedGraph.subscribe();
    });
</script>

<Menubar.Menu value="file">
    <Menubar.Trigger>File</Menubar.Trigger>
    <Menubar.Content side="bottom" sideOffset={8}>
        <Menubar.SubMenu.Root>
            <Menubar.SubMenu.Trigger faIcon={faFileImport}>
                Import
            </Menubar.SubMenu.Trigger>
            <Menubar.SubMenu.Content>
                <Menubar.Item.Button
                    onSelect={() => (showImportDialog = true)}
                    faIcon={faFileImport}
                >
                    Graph
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showSHACLUploadDialog = true)}
                    faIcon={faUpload}
                >
                    SHACL
                </Menubar.Item.Button>
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
                >
                    Graph
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showSHACLExportDialog = true)}
                    faIcon={faDownload}
                >
                    SHACL
                </Menubar.Item.Button>
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
        <Menubar.Separator />
        <Menubar.Item.Button
            onSelect={() => (showSnapshotDialog = true)}
            faIcon={faShare}
        >
            Share Snapshot
        </Menubar.Item.Button>
        <Menubar.Separator />
        <Menubar.SubMenu.Root>
            <Menubar.SubMenu.Trigger faIcon={faTrash} variant="danger">
                Delete
            </Menubar.SubMenu.Trigger>
            <Menubar.SubMenu.Content>
                <Menubar.Item.Button
                    onSelect={() => (showDeleteDialog = true)}
                    disabled={!hasGraphSelected || isDatasetReadOnly}
                    faIcon={faTrash}
                    variant="danger"
                >
                    Graph
                </Menubar.Item.Button>
                <Menubar.Item.Button
                    onSelect={() => (showDatasetDeleteDialog = true)}
                    disabled={!hasDatasetSelected}
                    faIcon={faTrash}
                    variant="danger"
                >
                    Dataset
                </Menubar.Item.Button>
            </Menubar.SubMenu.Content>
        </Menubar.SubMenu.Root>
    </Menubar.Content>
</Menubar.Menu>

<ImportDialog bind:showDialog={showImportDialog} />
<ExportDialog bind:showDialog={showExportDialog} />
<SnapshotDialog bind:showDialog={showSnapshotDialog} />
<SHACLUploadDialog bind:showDialog={showSHACLUploadDialog} />
<SHACLExportDialog bind:showDialog={showSHACLExportDialog} />

<DeleteDatasetDialog bind:showDialog={showDeleteDialog} />
<DatasetDeleteDialog
    bind:showDialog={showDatasetDeleteDialog}
    datasetName={selectedDataset}
/>
