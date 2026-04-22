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

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import {
        forceReloadTrigger,
        editorState,
    } from "$lib/sharedState.svelte.js";

    let { showDialog = $bindable(), datasetName } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    const baseDeletionDescription =
        "All schemas and packages inside this dataset will be permanently removed.";
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
                editorState.reset();
            }
        } finally {
            forceReloadTrigger.trigger();
        }
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    size="w-full max-w-lg"
    primaryLabel="Delete Dataset"
    onPrimary={deleteDataset}
    title={datasetName ? `Delete dataset "${datasetName}"?` : "Delete dataset?"}
    primaryVariant="danger"
    titleIcon={faExclamation}
    titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
>
    <div class="space-y-4 px-3 py-3">
        <p class="text-default-text w-3/4 text-sm leading-relaxed">
            {(() => {
                if (!datasetName || graphs === null) {
                    return baseDeletionDescription;
                }
                const graphCount = graphs.length ?? 0;
                const label = graphCount === 1 ? "schema" : "schemas";
                return `${baseDeletionDescription} ${graphCount} ${label} will be deleted.`;
            })()}
            <br />
            This action is not reversible.
        </p>
    </div>
</ActionDialog>
