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
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import DeleteConfirmationContent from "$lib/dialog/DeleteConfirmationContent.svelte";
    import Dialog from "$lib/dialog/Dialog.svelte";
    import DialogLeaveButtons from "$lib/dialog/DialogLeaveButtons.svelte";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable() } = $props();

    let datasetName = $state();
    let graphURI = $state();

    let disableSubmit = $derived(!datasetName || !graphURI);

    async function onOpen() {
        datasetName = editorState.selectedDataset.getValue();
        graphURI = editorState.selectedGraph.getValue();
    }

    function onClose() {
        datasetName = null;
        graphURI = null;
    }

    async function deleteGraph() {
        let promise = fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(datasetName) +
                "/graphs/" +
                encodeURIComponent(graphURI) +
                "/content",
            {
                method: "DELETE",
                credentials: "include",
            },
        ).then(res => {
            if (res.ok) {
                console.log("successfully deleted data");
                editorState.selectedDataset.updateValue(null);
                editorState.selectedGraph.updateValue(null);
                editorState.selectedPackageUUID.updateValue(null);
                editorState.selectedClassDataset.updateValue(null);
                editorState.selectedClassGraph.updateValue(null);
                editorState.selectedClassUUID.updateValue(null);
            } else {
                console.log("failed to insert data");
            }
        });
        promise
            .catch(e => {
                console.log("failed to delete graph:");
                console.log(e);
            })
            .finally(() => {
                forceReloadTrigger.trigger();
            });
    }
</script>

<Dialog bind:showDialog {onOpen} {onClose} size="w-full max-w-lg">
    <div class="space-y-4 px-3 py-3">
        <DeleteConfirmationContent
            title={graphURI ? `Delete graph "${graphURI}"?` : "Delete graph?"}
            description={datasetName
                ? `The graph will be removed from dataset "${datasetName}".`
                : "Select a dataset and graph to delete."}
        >
            {#if disableSubmit}
                <p class="text-red-text font-semibold">
                    Select a dataset and graph before deleting.
                </p>
            {/if}
        </DeleteConfirmationContent>
    </div>
    <DialogLeaveButtons
        bind:showDialog
        submitLabel="Delete Graph"
        onSubmit={deleteGraph}
        {disableSubmit}
    />
</Dialog>
