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
    import SearchableSelect from "$lib/components/SearchableSelect.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/utils/reactive-objects-control-button-utils.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    let {
        attributes,
        attribute,
        openAttributeEditor,
        openPropertySHACLRulesDialog,
    } = $props();

    const classEditorContext = getContext("classEditor");
    let readonly = $derived(classEditorContext.readonly);

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        readonly = classEditorContext.readonly;
    });

    onMount(() => (readonly = classEditorContext.readonly));

    function getDatatypeLabelByUri(uri) {
        const datatype = classEditorContext.getDatatypeByUri(uri);
        if (!datatype) {
            return uri;
        }
        return datatype.label;
    }
</script>

<tr>
    <td class="w-1/3">
        <TextEditControl
            placeholder="attribute label..."
            bind:value={attribute.label.value}
            highlight={attribute.label.isModified}
            warn={!attribute.label.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                attribute.label,
                readonly,
            )}
        />
    </td>
    <td class="w-fit">
        <SearchableSelect
            placeholder="type label..."
            value={getDatatypeLabelByUri(attribute.datatype.value)}
            highlight={attribute.datatype.isModified}
            warn={!attribute.datatype.isValid}
            optionObjectList={classEditorContext.datatypes}
            accessDisplayData={datatype => datatype.label}
            accessIdentifier={datatype =>
                classEditorContext.getSubstitutedNamespace(datatype.prefix) +
                ":" +
                datatype.label}
            callOnValidChange={newDatatype =>
                (attribute.datatype.value = newDatatype
                    ? newDatatype.prefix + newDatatype.label
                    : null)}
            {readonly}
            tooltip={attribute.datatype.value}
            buttons={getControlButtonsForReactiveObject(
                attribute.datatype,
                readonly,
            )}
        />
    </td>
    <td>
        <FaIconButton
            callOnClick={() => openPropertySHACLRulesDialog(attribute)}
            title={readonly ? "View" : "Edit" + " Constrains (SHACL)"}
            icon={faDiagramProject}
        />
    </td>
    <td>
        <FaIconButton
            callOnClick={() => openAttributeEditor(attribute)}
            icon={readonly ? faEye : faGear}
            title={readonly ? "View" : "Edit" + " attribute"}
        />
    </td>
    {#if !classEditorContext.readonly}
        <td>
            <FaIconButton
                callOnClick={() => attributes.remove(attribute, true)}
                icon={faMinus}
                title="Remove attribute"
            />
        </td>
    {/if}
</tr>
{#if !attribute.label.isValid || !attribute.datatype.isValid}
    <tr>
        <td class="align-top">
            <ViolationMessages violations={attribute.label.violations} />
        </td>
        <td class="align-top">
            <ViolationMessages violations={attribute.datatype.violations} />
        </td>
    </tr>
{/if}
