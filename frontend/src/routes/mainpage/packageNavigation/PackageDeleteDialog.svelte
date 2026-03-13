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
    import Dialog from "$lib/dialog/Dialog.svelte";
    import DialogButtons from "$lib/dialog/DialogButtons.svelte";
    import {
        forceReloadTrigger,
        editorState,
    } from "$lib/sharedState.svelte.js";

    let { showDialog = $bindable(), datasetName, graphUri, pack } = $props();

    async function deletePackage() {
        try {
            const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphUri)}/packages/${encodeURIComponent(pack.uuid)}`;
            const res = await fetch(url, {
                method: "DELETE",
                credentials: "include",
            });
            if (!res.ok) {
                console.error("Failed to delete package");
            }
            if (editorState.selectedPackageUUID.getValue() === pack.uuid) {
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

<Dialog bind:showDialog size="w-full max-w-lg">
    <DialogButtons
        bind:showDialog
        primaryLabel="Delete Package"
        onPrimary={deletePackage}
        primaryVariant="danger"
        title={pack?.label
            ? `Delete package "${pack.label}"?`
            : "Delete package?"}
        titleIcon={faExclamation}
        titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
    >
        <div class="space-y-4 px-3 py-3">
            <p class="text-default-text w-2/3 text-sm leading-relaxed">
                This removes the package and all of its classes from the current
                graph.
            </p>
        </div>
    </DialogButtons>
</Dialog>
