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
    import { faFloppyDisk } from "@fortawesome/free-solid-svg-icons";
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
    import { mapReactiveClassToClassDto } from "$lib/models/reactive/mapper/map-reactive-object-to-dto.js";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import DeleteClassConfirmDialog from "../../../DeleteClassConfirmDialog.svelte";
    import SHACLClassSpecificPopUp from "../../../shacl/shaclclassspecific/SHACLClassSpecificPopUp.svelte";

    let {
        reactiveClass,
        showDiscardSaveConfirmDialog = $bindable(),
        datasetOfClassToOpenNext,
        graphOfClassToOpenNext,
        classToOpenNext,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const classEditorContext = getContext("classEditor");
    let readonly = $state(false);
    let datasetName = $state();
    let graphUri = $state();

    let showClassDeleteDialog = $state(false);
    let showSHACLClassDialog = $state(false);

    onMount(() => {
        readonly = classEditorContext.readonly;
        datasetName = classEditorContext.datasetName;
        graphUri = classEditorContext.graphUri;
    });

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
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
            editorState.selectedPackageUUID.trigger();
            forceReloadTrigger.trigger();
        } else {
            console.error(
                "Could not save unsaved changes to class:",
                responseText,
            );
        }
    }
</script>

<div class="flex gap-1">
    <div class="w-1/4">
        <FaIconButton
            callOnClick={() => (showSHACLClassDialog = true)}
            icon={faDiagramProject}
            text="SHACL"
            title="View SHACL shapes"
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
        <div class="w-1/4">
            <FaIconButton
                callOnClick={() => saveChanges(reactiveClass)}
                icon={faFloppyDisk}
                text={"Save"}
                disabled={!reactiveClass.isValid || !reactiveClass.isModified}
                title="Save class"
            />
        </div>
        <div class="w-1/4">
            <FaIconButton
                callOnClick={() => reactiveClass.reset()}
                icon={faRotateLeft}
                disabled={!reactiveClass.isModified}
                text="Reset"
                title="Reset changes"
            />
        </div>
        <div class="w-1/4">
            <FaIconButton
                callOnClick={() => (showClassDeleteDialog = true)}
                icon={faTrash}
                variant="danger"
                text="Delete"
                title="Delete Class"
            />
            <DeleteClassConfirmDialog
                {datasetName}
                {graphUri}
                classUuid={reactiveClass.uuid.value}
                classLabel={reactiveClass.label.value}
                bind:showDialog={showClassDeleteDialog}
            />
        </div>
    {/if}
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
