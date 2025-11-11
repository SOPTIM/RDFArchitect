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
    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import DeleteConfirmationContent from "$lib/dialog/DeleteConfirmationContent.svelte";
    import Dialog from "$lib/dialog/Dialog.svelte";
    import DialogLeaveButtons from "$lib/dialog/DialogLeaveButtons.svelte";
    import {
        forceReloadTrigger,
        editorState,
    } from "$lib/sharedState.svelte.js";

    let { showDialog = $bindable(), datasetName } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    const baseDeletionDescription =
        "All graphs and packages inside this dataset will be permanently removed.";
    let graphs = $state(null);

    async function onOpen() {
        graphs = null;
        if (datasetName) {
            const res = await bec.getGraphNames(datasetName);
            graphs = await res.json();
        } else {
            graphs = [];
        }
    }

    function onClose() {
        graphs = null;
    }

    async function deleteDataset() {
        try {
            if (datasetName) {
                await bec.deleteDataset(datasetName);
            }

            if (editorState.selectedDataset.getValue() === datasetName) {
                editorState.selectedDataset.updateValue(null);
                editorState.selectedGraph.updateValue(null);
                editorState.selectedPackageUUID.updateValue(null);
                editorState.selectedClassDataset.updateValue(null);
                editorState.selectedClassGraph.updateValue(null);
                editorState.selectedClassUUID.updateValue(null);
            }
        } finally {
            forceReloadTrigger.trigger();
        }
    }
</script>

<Dialog bind:showDialog {onOpen} {onClose} size="w-full max-w-lg">
    <div class="space-y-4 px-3 py-3">
        <DeleteConfirmationContent
            title={datasetName
                ? `Delete dataset "${datasetName}"?`
                : "Delete dataset?"}
            description={(() => {
                if (!datasetName || graphs === null) {
                    return baseDeletionDescription;
                }
                const graphCount = graphs.length ?? 0;
                const label = graphCount === 1 ? "graph" : "graphs";
                return `${baseDeletionDescription} ${graphCount} ${label} will be deleted.`;
            })()}
        />
    </div>
    <DialogLeaveButtons
        bind:showDialog
        submitLabel="Delete Dataset"
        onSubmit={deleteDataset}
    />
</Dialog>
