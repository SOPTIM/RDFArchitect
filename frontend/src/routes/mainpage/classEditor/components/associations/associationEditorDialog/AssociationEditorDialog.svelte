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
    import { getContext } from "svelte";

    import ModifyDataDialog from "$lib/dialog/ModifyDataDialog.svelte";
    import { mapReactiveAssociationToAssociationDto } from "$lib/models/reactive/mapper/map-reactive-object-to-dto.js";
    import { ReactiveAssociation } from "$lib/models/reactive/reactive-association.svelte.js";

    import Direct from "./Direct.svelte";
    import { saveApiAssociationToBackend } from "../save-association-to-backend.js";
    import Inverse from "./Inverse.svelte";

    let { showDialog = $bindable(), associations, association } = $props();

    let classEditorContext = $state();
    let readonly = $derived(classEditorContext?.readonly);
    let isNewAssociation = $derived(true);

    function onOpen() {
        classEditorContext = getContext("classEditor");
        if (!associations.contains(association)) {
            isNewAssociation = true;
            association = new ReactiveAssociation({
                namespace: classEditorContext.reactiveClass.namespace.value,
                domain: classEditorContext.reactiveClass.uuid.value,
                inverse: {
                    namespace: classEditorContext.reactiveClass.namespace.value,
                },
            });
        } else {
            isNewAssociation = false;
        }
    }

    async function saveAssociation() {
        if (isNewAssociation) {
            associations.append(association);
        }
        const apiAssociation = mapReactiveAssociationToAssociationDto(
            association,
            classEditorContext.reactiveClass,
            classEditorContext.getClassByUuid,
        );
        saveApiAssociationToBackend(
            classEditorContext.datasetName,
            classEditorContext.graphUri,
            classEditorContext.reactiveClass.uuid.value,
            apiAssociation,
            isNewAssociation,
        ).then(res => {
            if (res.ok) {
                association.save();
            }
        });
    }
</script>

<ModifyDataDialog
    bind:showDialog
    {onOpen}
    saveChanges={saveAssociation}
    discardChanges={() => association.reset()}
    hasChanges={isNewAssociation || association?.isModified}
    isValid={association?.isValid}
    size="w-2/3"
    {readonly}
>
    {#if association}
        <div
            class="mx-2 grid w-full grid-cols-2 items-start gap-x-4 gap-y-1 px-2"
        >
            <Direct {association} />
            <Inverse {association} />
        </div>
    {/if}
</ModifyDataDialog>
