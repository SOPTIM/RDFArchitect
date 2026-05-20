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
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { DiagramType, editorState, forceReloadTrigger } from "$lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        datasetName,
        graphUri,
        classUUID,
    } = $props();

    let bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let selectedDatasetName = $state(null);
    let selectedGraphURI = $state(null);

    let disableSubmit = $derived(!selectedDatasetName || !selectedGraphURI);

    async function extendClass() {
        let body = {
            datasetName: selectedDatasetName,
            graphUri: selectedGraphURI,
        };
        try {
            const response = await bec.extendClass(
                datasetName,
                graphUri,
                classUUID,
                body,
            );
            const newClass = await response.json();
            editorState.selectedDataset.updateValue(selectedDatasetName);
            editorState.selectedGraph.updateValue(selectedGraphURI);
            editorState.selectedDiagram.updateValue({
                type: DiagramType.PACKAGE,
                id: newClass.belongsToCategory,
            });
            forceReloadTrigger.trigger();
        } catch (e) {
            console.log(e);
        }
    }
</script>

<ActionDialog
    bind:showDialog
    primaryLabel="Extend Class"
    onPrimary={extendClass}
    disablePrimary={disableSubmit}
    title="Extend Class"
>
    <div class="space-y-4 px-3 py-3">
        <p class="text-default-text w-2/3 text-sm leading-relaxed">
            Please select the graph that you want to extend this class in
        </p>
        <DatasetAndGraphSelection
            bind:dataset={selectedDatasetName}
            bind:graph={selectedGraphURI}
            allowSelectionOfReadonlyDatasets={false}
            displayAsCard={false}
        />
    </div>
</ActionDialog>
