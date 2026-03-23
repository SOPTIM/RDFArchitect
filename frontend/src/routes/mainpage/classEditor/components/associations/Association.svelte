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
        faEye,
        faGear,
        faMinus,
    } from "@fortawesome/free-solid-svg-icons";
    import { getContext, onMount } from "svelte";

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import NumberInputControl from "$lib/components/NumberInputControl.svelte";
    import SearchableSelect from "$lib/components/SearchableSelect.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/reactive-utils.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    const {
        associations,
        association,
        openAssociationEditor,
        openPropertySHACLRulesDialog,
        w,
    } = $props();

    const classEditorContext = getContext("classEditor");
    let readonly = $state(false);
    let classes = $state();

    let lowerButtons = $derived(getButtons(association.multiplicityLowerBound));

    let upperButtons = $derived(getButtons(association.multiplicityUpperBound));

    onMount(() => {
        readonly = classEditorContext.readonly;
        classes = classEditorContext.classes;
    });

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        readonly = classEditorContext.readonly;
        classes = classEditorContext.classes;
    });

    function getButtons(multiplicityObject) {
        const buttons = getControlButtonsForReactiveObject(
            multiplicityObject,
            readonly,
        );
        if (!buttons?.length) return [];
        if (w < 13) return [];
        if (w >= 20) return buttons;
        return buttons[1] ? [buttons[1]] : [];
    }
</script>

<tr>
    <td>
        <NumberInputControl
            bind:value={association.multiplicityLowerBound.value}
            highlight={association.multiplicityLowerBound.isModified}
            warn={!association.multiplicityLowerBound.isValid}
            {readonly}
            buttons={lowerButtons}
        />
    </td>

    <td>
        <NumberInputControl
            placeholder="*"
            bind:value={association.multiplicityUpperBound.value}
            highlight={association.multiplicityUpperBound.isModified}
            warn={!association.multiplicityUpperBound.isValid}
            {readonly}
            buttons={upperButtons}
        />
    </td>

    <td>
        <SearchableSelect
            placeholder="Target"
            value={classEditorContext.getClassByUuid(association.target.value)
                ?.label}
            highlight={association.target.isModified}
            warn={!association.target.isValid}
            optionObjectList={classes}
            accessDisplayData={cls => cls.label}
            accessIdentifier={cls =>
                classEditorContext.getSubstitutedNamespace(cls.prefix) +
                cls.label}
            callOnValidChange={newTarget =>
                (association.target.value = newTarget ? newTarget.uuid : null)}
            {readonly}
            tooltip={association.target.value}
            buttons={getControlButtonsForReactiveObject(
                association.target,
                readonly,
            )}
        />
    </td>

    <td>
        <FaIconButton
            callOnClick={() => openPropertySHACLRulesDialog(association)}
            title={readonly ? "View" : "Edit" + " SHACL shapes"}
            icon={faDiagramProject}
        />
    </td>

    <td>
        <FaIconButton
            icon={readonly ? faEye : faGear}
            callOnClick={() => openAssociationEditor(association)}
            title={readonly ? "View" : "Edit" + " association"}
        />
    </td>

    {#if !readonly}
        <td>
            <FaIconButton
                icon={faMinus}
                callOnClick={() => associations.remove(association, true)}
                title="Remove association"
            />
        </td>
    {/if}
</tr>

{#if !association.multiplicityUpperBound.isValid || !association.multiplicityLowerBound.isValid || !association.target.isValid}
    <tr>
        <td class="align-top">
            <ViolationMessages
                violations={association.multiplicityLowerBound.violations}
            />
        </td>
        <td class="align-top">
            <ViolationMessages
                violations={association.multiplicityUpperBound.violations}
            />
        </td>
        <td class="align-top">
            <ViolationMessages violations={association.target.violations} />
        </td>
    </tr>
{/if}
