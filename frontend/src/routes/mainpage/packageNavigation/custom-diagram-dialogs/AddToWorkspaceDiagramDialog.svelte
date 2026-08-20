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
    import ComboBoxEditControl from "$lib/components/ComboBoxEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { hasNoSpaces } from "$lib/models/reactive/validity-rules/validityFunctions.js";
    import {
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { customDiagramStore } from "$lib/stores/diagramStore.ts";

    let {
        showDialog = $bindable(),
        lockedWorkspaceName,
        lockedGraphUri,
        classes,
    } = $props();

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
        existingDiagrams =
            (await customDiagramStore.getDatasetDiagrams(lockedWorkspaceName)) ??
            [];
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
        existingDiagrams = [];
    }

    function classesToAdd() {
        return classes.map(cls => ({
            graphUri: cls.graphUri ?? lockedGraphUri,
            uuid: cls.id,
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
        const diagramData = {
            diagramId: diagramId,
            name: trimmedName,
            classes: classesToAdd(),
        };

        const { error } = await customDiagramStore.saveDatasetDiagram(
            lockedWorkspaceName,
            diagramId,
            diagramData,
        );

        if (error) return;

        editorState.selectedWorkspace.updateValue(lockedWorkspaceName);
        editorState.selectedGraph.updateValue(null);
        editorState.selectedDiagram.updateValue({
            type: DiagramType.CUSTOM_WORKSPACE_DIAGRAM,
            id: diagramId,
        });
        forceReloadTrigger.trigger();
    }

    async function addToDiagram(diagram) {
        const { error } = await customDiagramStore.addClassesToDatasetDiagram(
            lockedWorkspaceName,
            diagram.diagramId,
            classesToAdd(),
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
    primaryLabel={createsNewDiagram ? "Create Diagram" : "Add to Diagram"}
    onPrimary={submitDialog}
    disablePrimary={disableSubmit}
    title="Add to Workspace Diagram"
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
