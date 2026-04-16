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
    import { faExclamation } from "@fortawesome/free-solid-svg-icons";

    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";

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

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    size="w-full max-w-lg"
    primaryLabel="Delete Schema"
    onPrimary={deleteGraph}
    disablePrimary={disableSubmit}
    title={graphURI ? `Delete schema "${graphURI}"?` : "Delete schema?"}
    titleIcon={faExclamation}
    titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
>
    <div class="space-y-4 px-3 py-3">
        <p class="text-default-text w-3/4 text-sm leading-relaxed">
            {datasetName
                ? `The schema will be removed from dataset "${datasetName}".`
                : "Select a dataset and schema to delete."}
            <br />
            This action is not reversible.
        </p>
    </div>
</ActionDialog>
