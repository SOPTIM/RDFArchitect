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
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
        classes,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let selectedDiagram = $state(null);
    let diagramList = $state([]);
    let disableSubmit = $derived(!selectedDiagram);

    async function getCustomDiagrams() {
        const res = await bec.getCustomDiagramsForGraph(
            lockedDatasetName,
            lockedGraphUri,
        );
        const allDiagrams = await res.json();
        diagramList = allDiagrams.filter(diagram => {
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
        const diagramName = selectedDiagram?.name;
        const count = classes.length;
        try {
            const res = await bec.addToCustomGraphDiagram(
                lockedDatasetName,
                lockedGraphUri,
                selectedDiagram.diagramId,
                classes.map(cls => ({
                    uuid: cls.id,
                    graphUri: lockedGraphUri,
                })),
            );
            if (res && res.ok === false) {
                toastStore.error(
                    "Add failed",
                    `Could not add ${count === 1 ? "class" : "classes"} to "${diagramName}".`,
                );
                return;
            }
            toastStore.success(
                "Added to diagram",
                `${count} ${count === 1 ? "class" : "classes"} added to "${diagramName}".`,
            );
        } finally {
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
    title="Add to Profile Diagram"
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
            getOptionLabel={diagram => diagram.name}
        />
    </div>
</ActionDialog>
