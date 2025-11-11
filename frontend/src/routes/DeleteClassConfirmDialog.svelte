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
    import { faTrash, faXmark } from "@fortawesome/free-solid-svg-icons";

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import DeleteConfirmationContent from "$lib/dialog/DeleteConfirmationContent.svelte";
    import Dialog from "$lib/dialog/Dialog.svelte";
    import DialogLeaveButtons from "$lib/dialog/DialogLeaveButtons.svelte";
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

<Dialog bind:showDialog size="w-full max-w-md">
    <div class="space-y-4 px-3 py-3">
        <DeleteConfirmationContent
            title={classLabel
                ? `Delete class "${classLabel}"?`
                : "Delete class?"}
            description="The class will be removed from the model and cannot be restored."
        />
    </div>
    <DialogLeaveButtons
        bind:showDialog
        submitVariant="danger"
        submitIcon={faTrash}
        cancelIcon={faXmark}
        submitLabel="Confirm"
        onSubmit={deleteClass}
    />
</Dialog>
