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
    import {
        faDiagramProject,
        faFloppyDisk,
        faRotateLeft,
        faTrash,
        faXmark,
    } from "@fortawesome/free-solid-svg-icons";
    import { getContext, onDestroy, onMount } from "svelte";

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import DiscardCancelConfirmDialog from "$lib/dialog/DiscardCancelConfirmDialog.svelte";
    import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
    import { mapReactiveClassToClassDto } from "$lib/models/reactive/mapper/map-reactive-object-to-dto.js";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { classStore } from "$lib/stores/ClassStore.ts";
    import { datatypesStore } from "$lib/stores/DatatypesStore.ts";

    import DeleteDependenciesDialog from "../../../delete-relations-dialog/DeleteDependenciesDialog.svelte";
    import SHACLClassSpecificPopUp from "../../../shacl/shaclclassspecific/SHACLClassSpecificPopUp.svelte";

    let {
        reactiveClass,
        showDiscardSaveConfirmDialog = $bindable(),
        pendingAction = $bindable(),
        datasetOfClassToOpenNext,
        graphOfClassToOpenNext,
        classToOpenNext,
        closeClassEditor,
    } = $props();

    const classEditorContext = getContext("classEditor");

    const shortcutsUnregister = [];

    let showDeleteDependenciesDialog = $state(false);
    let showSHACLClassDialog = $state(false);
    let readonly = $derived(classEditorContext.readOnly);

    // Hide the button labels (icon-only) once this row gets too narrow to fit
    // them. Readonly mode only shows Constraints + Close, so it can keep the
    // label far longer; edit mode has four labels and collapses earlier.
    // Both branches must be complete literals so Tailwind's scanner emits them.
    let labelHideClass = $derived(
        readonly ? "@max-[12rem]:hidden" : "@max-[30rem]:hidden",
    );
    let datasetName = $derived(classEditorContext.datasetName);
    let graphUri = $derived(classEditorContext.graphUri);

    $effect(() => {
        editorState.selectedDiagram.subscribe();
        readonly = classEditorContext.readOnly;
        datasetName = classEditorContext.datasetName;
        graphUri = classEditorContext.graphUri;
    });

    onMount(() => {
        readonly = classEditorContext.readOnly;
        datasetName = classEditorContext.datasetName;
        graphUri = classEditorContext.graphUri;
    });

    onMount(() => {
        shortcutsUnregister.push(
            shortcutStore.register("saveClass", ["ctrl", "s"], () => {
                if (reactiveClass?.isValid && reactiveClass?.isModified)
                    saveFromShortcut();
            }),
        );
    });

    onDestroy(() => {
        shortcutsUnregister.forEach(unregister => unregister());
    });

    function saveChanges() {
        const classDto = mapReactiveClassToClassDto(
            reactiveClass,
            classEditorContext.getClassByUuid,
            classEditorContext.getDatatypeByUri,
            classEditorContext.getPackageByUuid,
        );
        saveChangesToBackend(classDto);
        datatypesStore.invalidateGraph(datasetName, graphUri);
    }

    async function saveChangesToBackend(classDto) {
        const { error } = await classStore.replaceClass(
            datasetName,
            graphUri,
            classDto.uuid,
            classDto,
        );
        if (!error) {
            reactiveClass.save();
            editorState.selectedClassUUID.trigger();
            editorState.selectedDiagram.trigger();
            forceReloadTrigger.trigger();
        }
    }

    export function saveFromShortcut() {
        if (!readonly && reactiveClass?.isValid && reactiveClass?.isModified) {
            saveChanges();
        }
    }

    function handleCancel() {
        pendingAction = null;
    }

    function handleDiscard() {
        reactiveClass.reset();
        if (pendingAction) {
            pendingAction();
            pendingAction = null;
        } else {
            editorState.selectedClassDataset.updateValue(
                datasetOfClassToOpenNext,
            );
            editorState.selectedClassGraph.updateValue(graphOfClassToOpenNext);
            editorState.selectedClassUUID.updateValue(classToOpenNext);
        }
    }

    function handleSave() {
        saveChanges();
        editorState.selectedClassDataset.updateValue(datasetOfClassToOpenNext);
        editorState.selectedClassGraph.updateValue(graphOfClassToOpenNext);
        editorState.selectedClassUUID.updateValue(classToOpenNext);
    }
</script>

<!--
  - @container lets the labels collapse based on this row's own width (the
  - class editor lives in a resizable split pane), not the viewport. Once the
  - row is too narrow, {labelHideClass} hides the labels so the buttons stay
  - readable as icon-only instead of clipping their text.
-->
<div class="@container flex items-center gap-1">
    <!--
      - grid-flow-col + auto-cols-fr makes every action button equal width
      - (matching the widest), while the grid stays content-sized so the row
      - is left-aligned instead of stretching to full width.
    -->
    <div class="grid auto-cols-fr grid-flow-col gap-1">
        <FaIconButton
            callOnClick={() => (showSHACLClassDialog = true)}
            icon={faDiagramProject}
            text="Constraints"
            labelClass={labelHideClass}
            title="View Constraints (SHACL)"
        />
        {#if !readonly}
            <FaIconButton
                callOnClick={() => saveChanges(reactiveClass)}
                icon={faFloppyDisk}
                text="Save"
                labelClass={labelHideClass}
                disabled={!reactiveClass.isValid || !reactiveClass.isModified}
                title="Save class"
            />
            <FaIconButton
                callOnClick={() => reactiveClass.reset()}
                icon={faRotateLeft}
                disabled={!reactiveClass.isModified}
                text="Reset"
                labelClass={labelHideClass}
                title="Reset changes"
            />
            <FaIconButton
                callOnClick={() => (showDeleteDependenciesDialog = true)}
                icon={faTrash}
                variant="danger"
                text="Delete"
                labelClass={labelHideClass}
                title="Delete class"
            />
        {/if}
    </div>
    <FaIconButton
        callOnClick={closeClassEditor}
        icon={faXmark}
        variant="contrast"
        containerClass="ml-auto w-auto"
        title="Close class editor"
    />
</div>

<SHACLClassSpecificPopUp
    {datasetName}
    {graphUri}
    {reactiveClass}
    bind:showDialog={showSHACLClassDialog}
    class={reactiveClass}
/>
<DeleteDependenciesDialog
    {datasetName}
    {graphUri}
    resourceUuid={reactiveClass.uuid.value}
    bind:showDialog={showDeleteDependenciesDialog}
/>

<DiscardCancelConfirmDialog
    bind:showDialog={showDiscardSaveConfirmDialog}
    onDiscard={handleDiscard}
    onSave={handleSave}
    onCancel={handleCancel}
    disableSave={!reactiveClass?.isValid}
    hideSave={!!pendingAction}
/>
