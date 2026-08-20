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
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable() } = $props();

    let workspaceName = $state();
    let graphURI = $state();

    let disableSubmit = $derived(!workspaceName || !graphURI);

    async function onOpen() {
        workspaceName = editorState.selectedWorkspace.getValue();
        graphURI = editorState.selectedGraph.getValue();
    }

    function onClose() {
        workspaceName = null;
        graphURI = null;
    }

    async function deleteGraph() {
        let promise = fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(workspaceName) +
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
                const deletedGraph = graphURI;
                editorState.selectedGraph.updateValue(null);
                editorState.selectedDiagram.updateValue({
                    type: null,
                    id: null,
                });
                editorState.selectedClassWorkspace.updateValue(null);
                editorState.selectedClassGraph.updateValue(null);
                editorState.selectedClass.updateValue({ type: null, id: null });
                toastStore.success(
                    "Schema deleted",
                    `"${deletedGraph}" was removed.`,
                );
            } else {
                console.log("failed to insert data");
                toastStore.error(
                    "Delete failed",
                    `Could not delete schema "${graphURI}".`,
                );
            }
        });
        promise
            .catch(e => {
                console.log("failed to delete graph:");
                console.log(e);
                toastStore.error(
                    "Delete failed",
                    "An unexpected error occurred while deleting the schema.",
                );
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
    title={graphURI ? `Delete Schema "${graphURI}"?` : "Delete Schema?"}
    titleIcon={faExclamation}
    titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
>
    <div class="space-y-4 px-3 py-3">
        <p class="text-default-text w-3/4 text-sm leading-relaxed">
            {workspaceName
                ? `The schema will be removed from workspace "${workspaceName}".`
                : "Select a workspace and schema to delete."}
            <br />
            This action is not reversible.
        </p>
    </div>
</ActionDialog>
