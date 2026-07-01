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
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { customDiagramStore } from "$lib/stores/DiagramStore.ts";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
        classes,
    } = $props();

    let selectedDiagram = $state(null);
    let diagramList = $state([]);
    let disableSubmit = $derived(!selectedDiagram);

    async function getCustomDiagrams() {
        await customDiagramStore.loadDatasetDiagrams(lockedDatasetName);
        const diagrams = await customDiagramStore.getDatasetDiagrams(lockedDatasetName);
        diagramList = diagrams.filter(diagram => {
            const classesToAddIds = new Set(classes.map(cls => cls.id));
            const diagramClassIds = new Set(
                diagram.classes.map(cls => cls.uuid),
            );

            // only keep entries where at least on of the classes to add is not already in the diagram
            return Array.from(classesToAddIds).some(
                id => !diagramClassIds.has(id),
            );
        });
    }

    function onOpen() {
        getCustomDiagrams();
    }

    function onClose() {
        selectedDiagram = null;
    }

    async function addToDiagram() {
        const { error } = await customDiagramStore.addClassesToDatasetDiagram(
            lockedDatasetName,
            selectedDiagram.diagramId,
            classes.map(cls => ({
                graphUri: lockedGraphUri,
                uuid: cls.id,
            })),
        );
        if (!error) {
            forceReloadTrigger.trigger();
        }
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Add to Diagram"
    onPrimary={addToDiagram}
    disablePrimary={disableSubmit}
    title="Add to Dataset Diagram"
>
    <div class="mx-2 flex h-full flex-col">
        <label for="diagram-select" class="mt-3 mb-1 block text-sm">
            Diagram
        </label>
        <SelectEditControl
            id="diagram-select"
            bind:value={selectedDiagram}
            options={diagramList}
            placeholder={diagramList.length > 0
                ? "Select diagram"
                : "No diagrams available"}
            getOptionLabel={diagram => diagram.label}
        />
    </div>
</ActionDialog>
