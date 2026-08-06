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
    import ComboBoxEditControl from "$lib/components/ComboBoxEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { hasNoSpaces } from "$lib/models/reactive/validity-rules/validityFunctions.js";
    import {
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
        classes,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let diagramNameInput = $state("");
    let existingDiagrams = $state([]);
    let diagramsLoaded = $state(false);

    let diagramNames = $derived([
        ...new Set(existingDiagrams.map(diagram => diagram.name)),
    ]);
    let trimmedName = $derived(diagramNameInput.trim());
    let matchingDiagrams = $derived(
        existingDiagrams.filter(
            diagram => diagram.name.toLowerCase() === trimmedName.toLowerCase(),
        ),
    );
    let matchingDiagram = $derived(
        matchingDiagrams.find(takesAnyClass) ?? matchingDiagrams[0] ?? null,
    );
    let createsNewDiagram = $derived(!!trimmedName && !matchingDiagram);
    let alreadyContained = $derived(
        !!matchingDiagram && !takesAnyClass(matchingDiagram),
    );
    let violations = $derived(
        alreadyContained
            ? [
                  classes.length === 1
                      ? "Class is already in this diagram"
                      : "Classes are already in this diagram",
              ]
            : createsNewDiagram
              ? hasNoSpaces(trimmedName)
              : [],
    );
    let disableSubmit = $derived(
        !trimmedName || violations.length > 0 || !diagramsLoaded,
    );

    async function getCustomDiagrams() {
        const res = await bec.getCustomDiagramsForGraph(
            lockedDatasetName,
            lockedGraphUri,
        );
        existingDiagrams = await res.json();
        diagramsLoaded = true;
    }

    function takesAnyClass(diagram) {
        const diagramClassIds = new Set(diagram.classes.map(cls => cls.uuid));
        return classes.some(cls => !diagramClassIds.has(cls.id));
    }

    function isDiagramNameFull(name) {
        return !existingDiagrams
            .filter(
                diagram => diagram.name.toLowerCase() === name.toLowerCase(),
            )
            .some(takesAnyClass);
    }

    function onOpen() {
        getCustomDiagrams();
    }

    function onClose() {
        diagramNameInput = "";
        diagramsLoaded = false;
    }

    function classesToAdd() {
        return classes.map(cls => ({
            uuid: cls.id,
            graphUri: lockedGraphUri,
        }));
    }

    async function submitDialog() {
        if (matchingDiagram) {
            await addToDiagram(matchingDiagram);
        } else {
            await createDiagram();
        }
    }

    async function createDiagram() {
        const diagramId = crypto.randomUUID();
        const count = classes.length;
        const diagramData = {
            diagramId: diagramId,
            name: trimmedName,
            classes: classesToAdd(),
        };

        try {
            const res = await bec.putCustomDiagram(
                lockedDatasetName,
                lockedGraphUri,
                diagramId,
                diagramData,
            );

            if (!res.ok) {
                toastStore.error(
                    "Create failed",
                    `Could not create diagram "${trimmedName}".`,
                );
                return;
            }

            editorState.selectedDataset.updateValue(lockedDatasetName);
            editorState.selectedGraph.updateValue(lockedGraphUri);
            editorState.selectedDiagram.updateValue({
                type: DiagramType.CUSTOM_GRAPH_DIAGRAM,
                id: diagramId,
            });
            toastStore.success(
                "Diagram created",
                `"${trimmedName}" was created with ${count} ${count === 1 ? "class" : "classes"}.`,
            );
        } finally {
            forceReloadTrigger.trigger();
        }
    }

    async function addToDiagram(diagram) {
        const diagramName = diagram.name;
        const count = classes.length;
        try {
            const res = await bec.addToCustomGraphDiagram(
                lockedDatasetName,
                lockedGraphUri,
                diagram.diagramId,
                classesToAdd(),
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
    primaryLabel={createsNewDiagram ? "Create Diagram" : "Add to Diagram"}
    onPrimary={submitDialog}
    disablePrimary={disableSubmit}
    title="Add to Schema Diagram"
>
    <div class="mx-2 flex h-full flex-col">
        <label for="diagram-select" class="mt-3 mb-1 block text-sm">
            Diagram
        </label>
        <ViolationMessages {violations} />
        <ComboBoxEditControl
            id="diagram-select"
            bind:value={diagramNameInput}
            optionValues={diagramNames}
            getOptionIsDisabled={isDiagramNameFull}
            placeholder="Select a diagram or enter a new name"
            warn={violations.length > 0}
        />
    </div>
</ActionDialog>
