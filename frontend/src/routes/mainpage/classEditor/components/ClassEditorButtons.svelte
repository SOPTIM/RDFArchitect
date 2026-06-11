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

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import DiscardCancelConfirmDialog from "$lib/dialog/DiscardCancelConfirmDialog.svelte";
    import { mapReactiveClassToClassDto } from "$lib/models/reactive/mapper/map-reactive-object-to-dto.js";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { classStore } from "$lib/stores/ClassStore.ts";

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

    const classEditorContext = getContext("classEditor");

    let showDeleteDependenciesDialog = $state(false);
    let showSHACLClassDialog = $state(false);
    let readonly = $derived(classEditorContext.readonly);
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
</script>

<div class="flex gap-1">
    <div class="w-1/5">
        <FaIconButton
            callOnClick={() => (showSHACLClassDialog = true)}
            icon={faDiagramProject}
            text="Constraints"
            title="View Constraints (SHACL)"
        />

        <SHACLClassSpecificPopUp
            {datasetName}
            {graphUri}
            {reactiveClass}
            bind:showDialog={showSHACLClassDialog}
            class={reactiveClass}
        />
    </div>
    {#if !readonly}
        <div class="w-1/5">
            <FaIconButton
                callOnClick={() => saveChanges(reactiveClass)}
                icon={faFloppyDisk}
                text={"Save"}
                disabled={!reactiveClass.isValid || !reactiveClass.isModified}
                title="Save class"
            />
        </div>
        <div class="w-1/5">
            <FaIconButton
                callOnClick={() => reactiveClass.reset()}
                icon={faRotateLeft}
                disabled={!reactiveClass.isModified}
                text="Reset"
                title="Reset changes"
            />
        </div>
        <div class="w-1/5">
            <FaIconButton
                callOnClick={() => (showDeleteDependenciesDialog = true)}
                icon={faTrash}
                variant="danger"
                text="Delete"
                title="Delete class"
            />
            <DeleteDependenciesDialog
                {datasetName}
                {graphUri}
                resourceUuid={reactiveClass.uuid.value}
                bind:showDialog={showDeleteDependenciesDialog}
            />
        </div>
    {/if}
    <div class="ml-auto w-1/5">
        <FaIconButton
            callOnClick={closeClassEditor}
            icon={faXmark}
            variant="danger"
            text="Close"
            title="Close class editor"
        />
    </div>
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
