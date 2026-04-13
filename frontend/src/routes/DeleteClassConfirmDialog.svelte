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
    import { faExclamation, faTrash } from "@fortawesome/free-solid-svg-icons";

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    let {
        datasetName,
        graphUri,
        classUuid,
        classLabel,
        showDialog = $bindable(),
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    async function onOpen() {
        if (!datasetName || !graphUri || !classUuid) {
            console.error(
                "Missing required properties to delete class:",
                datasetName,
                graphUri,
                classUuid,
            );
            showDialog = false;
        }
        let res = await bec.getDeleteRelation(datasetName, graphUri, classUuid);
        let json = await res.json();
        console.warn(
            "Delete class response - check for warnings before confirming deletion:",
            json,
        );
    }
    async function deleteClass() {
        bec.deleteClass(datasetName, graphUri, classUuid).then(
            async response => {
                if (response.ok) {
                    const responseText = await response.text();
                    console.log("Successfully deleted class:", responseText);
                    editorState.selectedPackageUUID.trigger();
                    if (
                        editorState.selectedClassUUID.getValue() === classUuid
                    ) {
                        editorState.selectedClassDataset.updateValue(null);
                        editorState.selectedClassGraph.updateValue(null);
                        editorState.selectedClassUUID.updateValue(null);
                    }
                    forceReloadTrigger.trigger();
                } else {
                    const errorText = await response.text();
                    console.error("Could not delete class:", errorText);
                }
            },
        );
    }
</script>

<ActionDialog
    {onOpen}
    bind:showDialog
    size="w-full max-w-md"
    primaryVariant="danger"
    primaryIcon={faTrash}
    primaryLabel="Confirm"
    onPrimary={deleteClass}
    title={classLabel ? `Delete class "${classLabel}"?` : "Delete class?"}
    titleIcon={faExclamation}
    titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
>
    <div class="space-y-4 px-3 py-3">
        <p class="text-default-text w-3/4 text-sm leading-relaxed">
            The class will be removed from the model.
            <br />
            References to this class will remain.
        </p>
    </div>
</ActionDialog>
