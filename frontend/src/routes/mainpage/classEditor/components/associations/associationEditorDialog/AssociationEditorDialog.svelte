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

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";
    import ModifyDataDialog from "$lib/dialog/ModifyDataDialog.svelte";
    import { mapReactiveAssociationToAssociationDto } from "$lib/models/reactive/mapper/map-reactive-object-to-dto.js";
    import { ReactiveAssociation } from "$lib/models/reactive/models/reactive-association.svelte.js";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";

    import Direct from "./Direct.svelte";
    import { saveApiAssociationToBackend } from "../save-association-to-backend.js";
    import Inverse from "./Inverse.svelte";

    let {
        showDialog = $bindable(),
        associations,
        association: associationProp,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let classEditorContext = $state();
    let isNewAssociation = $state(true);
    let association = $derived(associationProp);
    let readonly = $derived(classEditorContext?.readonly);

    $effect(async () => {
        const targetValue = association?.target?.value;
        const ctx = classEditorContext;

        if (!ctx || !targetValue) return;

        const existingClassInfo = ctx.getTargetClassInfoByUuid(targetValue);
        if (!existingClassInfo) {
            const res = await bec.getClassInfo(
                ctx.datasetName,
                ctx.graphUri,
                targetValue,
            );
            const classInfo = await res.json();
            ctx.addTargetClassInfo(classInfo);
        }

        // Trigger violation checks
        if (association?.inverse?.label) {
            association.inverse.label.value = association.inverse.label.value;
        }
    });

    function onOpen() {
        classEditorContext = getContext("classEditor");
        association = associationProp;
        if (!associations.contains(association)) {
            isNewAssociation = true;
            association = new ReactiveAssociation({
                namespace: classEditorContext.reactiveClass.namespace.value,
                domain: classEditorContext.reactiveClass.uuid.value,
                inverse: {
                    namespace: classEditorContext.reactiveClass.namespace.value,
                },
            });
            associations.appendClass(association);
        } else {
            isNewAssociation = false;
        }
    }

    async function saveAssociation() {
        const apiAssociation = mapReactiveAssociationToAssociationDto(
            association,
            classEditorContext.reactiveClass,
            classEditorContext.getClassByUuid,
        );
        const result = await saveApiAssociationToBackend(
            classEditorContext.datasetName,
            classEditorContext.graphUri,
            classEditorContext.reactiveClass.uuid.value,
            apiAssociation,
            isNewAssociation,
        );
        if (!result.ok) {
            return;
        }

        association.uuid.value = result.associationUUIDs.fromUUID;
        association.inverse.uuid.value = result.associationUUIDs.toUUID;
        association.save();
        if (isNewAssociation) {
            isNewAssociation = false;
        }
        association.save();
        forceReloadTrigger.trigger();
    }

    function onClose() {
        if (isNewAssociation) {
            associations.remove(association);
        }
        association = null;
    }
</script>

<ModifyDataDialog
    bind:showDialog
    {onOpen}
    {onClose}
    saveChanges={saveAssociation}
    discardChanges={() => association.reset()}
    hasChanges={association?.isModified}
    isValid={association?.isValid}
    size="w-2/3"
    {readonly}
    title={`${isNewAssociation ? "Create" : "Edit"} Association${association ? `: '${association.label.backup}' to '${association.inverse.label.backup}'` : ""}`}
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
