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
    import { faFloppyDisk, faXmark } from "@fortawesome/free-solid-svg-icons";
    import {
        faDiagramProject,
        faRotateLeft,
        faTrash,
    } from "@fortawesome/free-solid-svg-icons";
    import { getContext, onMount } from "svelte";

    import { BackendConnection } from "$lib/api/backend.js";
    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import DiscardCancelConfirmDialog from "$lib/dialog/DiscardCancelConfirmDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { mapReactiveClassToClassDto } from "$lib/models/reactive/mapper/map-reactive-object-to-dto.js";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import DeleteDependenciesDialog from "../../../delete-relations-dialog/DeleteDependenciesDialog.svelte";
    import SHACLClassSpecificPopUp from "../../../shacl/shaclclassspecific/SHACLClassSpecificPopUp.svelte";

    let {
        reactiveClass,
        showDiscardSaveConfirmDialog = $bindable(),
        datasetOfClassToOpenNext,
        graphOfClassToOpenNext,
        classToOpenNext,
        closeClassEditor,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const classEditorContext = getContext("classEditor");

    let showDeleteDependenciesDialog = $state(false);
    let showSHACLClassDialog = $state(false);
    let readonly = $derived(classEditorContext.readonly);

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
        readonly = classEditorContext.readonly;
        datasetName = classEditorContext.datasetName;
        graphUri = classEditorContext.graphUri;
    });

    onMount(() => {
        readonly = classEditorContext.readonly;
        datasetName = classEditorContext.datasetName;
        graphUri = classEditorContext.graphUri;
    });

    function saveChanges() {
        console.log("Saving changes for class");
        const classDto = mapReactiveClassToClassDto(
            reactiveClass,
            classEditorContext.getClassByUuid,
            classEditorContext.getDatatypeByUri,
            classEditorContext.getPackageByUuid,
        );
        saveChangesToBackend(classDto);
    }

    async function saveChangesToBackend(classDto) {
        const classLabel = classDto.label ?? classDto.uuid;
        const res = await bec.replaceClass(
            datasetName,
            graphUri,
            classDto.uuid,
            classDto,
        );
        const responseText = await res.text();
        if (res.ok) {
            console.log(
                "Successfully saved unsaved changes to class:",
                responseText,
            );
            reactiveClass.save();
            editorState.selectedClassUUID.trigger();
            editorState.selectedDiagram.trigger();
            forceReloadTrigger.trigger();
            toastStore.success("Class saved", `"${classLabel}" was saved.`);
        } else {
            console.error(
                "Could not save unsaved changes to class:",
                responseText,
            );
            toastStore.error(
                "Save failed",
                `Could not save class "${classLabel}".`,
            );
        }
        forceReloadTrigger.trigger();
    }
</script>

<!--
  - @container lets the labels collapse based on this row's own width (the
  - class editor lives in a resizable split pane), not the viewport. Below
  - ~28rem the labels are hidden via {labelHideClass} so the buttons stay
  - readable as icon-only instead of clipping their text.
-->
<div class="@container flex items-center gap-1">
    <div class="flex min-w-0 grow gap-1">
        <FaIconButton
            callOnClick={() => (showSHACLClassDialog = true)}
            icon={faDiagramProject}
            text="Constraints"
            labelClass={labelHideClass}
            containerClass="w-auto"
            title="View Constraints (SHACL)"
        />

        <SHACLClassSpecificPopUp
            {datasetName}
            {graphUri}
            {reactiveClass}
            bind:showDialog={showSHACLClassDialog}
            class={reactiveClass}
        />

        {#if !readonly}
            <FaIconButton
                callOnClick={() => saveChanges(reactiveClass)}
                icon={faFloppyDisk}
                text="Save"
                labelClass={labelHideClass}
                containerClass="w-auto"
                disabled={!reactiveClass.isValid || !reactiveClass.isModified}
                title="Save class"
            />
            <FaIconButton
                callOnClick={() => reactiveClass.reset()}
                icon={faRotateLeft}
                disabled={!reactiveClass.isModified}
                text="Reset"
                labelClass={labelHideClass}
                containerClass="w-auto"
                title="Reset changes"
            />
            <FaIconButton
                callOnClick={() => (showDeleteDependenciesDialog = true)}
                icon={faTrash}
                variant="danger"
                text="Delete"
                labelClass={labelHideClass}
                containerClass="w-auto"
                title="Delete class"
            />
            <DeleteDependenciesDialog
                {datasetName}
                {graphUri}
                resourceUuid={reactiveClass.uuid.value}
                bind:showDialog={showDeleteDependenciesDialog}
            />
        {/if}
    </div>
    <FaIconButton
        callOnClick={closeClassEditor}
        icon={faXmark}
        variant="contrast"
        containerClass="w-auto"
        title="Close class editor"
    />
</div>

<DiscardCancelConfirmDialog
    bind:showDialog={showDiscardSaveConfirmDialog}
    onDiscard={() => {
        reactiveClass.reset();
        editorState.selectedClassDataset.updateValue(datasetOfClassToOpenNext);
        editorState.selectedClassGraph.updateValue(graphOfClassToOpenNext);
        editorState.selectedClassUUID.updateValue(classToOpenNext);
    }}
    onSave={() => {
        saveChanges();
        editorState.selectedClassDataset.updateValue(datasetOfClassToOpenNext);
        editorState.selectedClassGraph.updateValue(graphOfClassToOpenNext);
        editorState.selectedClassUUID.updateValue(classToOpenNext);
    }}
    disableSave={!reactiveClass?.isModified || !reactiveClass?.isValid}
/>
