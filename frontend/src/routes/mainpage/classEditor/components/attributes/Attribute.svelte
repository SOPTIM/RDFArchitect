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
    import {
        claimPropertyFocus,
        PROPERTY_HIGHLIGHT_MS,
        scrollRowIntoView,
    } from "$lib/utils/property-focus.js";

    let {
        attributes,
        attribute,
        openAttributeEditor,
        openPropertySHACLRulesDialog,
        inherited = false,
        targetClass = null,
    } = $props();

    const classEditorContext = getContext("classEditor");

    let row = $state(null);
    let revealed = $state(false);
    let readonly = $derived(classEditorContext.readonly);

    $effect(() => {
        editorState.selectedDiagram.subscribe();
        readonly = classEditorContext.readonly;
    });

    // A deep link or search hit on this attribute reveals the declaring class, never the inheriting
    // one, so an inherited row never claims the request (its section may even be collapsed).
    $effect(() => {
        editorState.focusedPropertyUUID.subscribe();
        if (inherited || !claimPropertyFocus(attribute?.uuid?.value)) {
            return;
        }
        revealed = true;
        scrollRowIntoView(row);
        const timeout = setTimeout(
            () => (revealed = false),
            PROPERTY_HIGHLIGHT_MS,
        );
        return () => clearTimeout(timeout);
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

<tr
    bind:this={row}
    class={revealed ? "bg-background-select ring-border-select ring-2" : ""}
>
    <td class="w-1/3">
        <TextEditControl
            placeholder="attribute label..."
            bind:value={attribute.label.value}
            highlight={attribute.label.isModified}
            warn={!attribute.label.isValid}
            readonly={readonly || inherited}
            buttons={getControlButtonsForReactiveObject(
                attribute.label,
                readonly || inherited,
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
            callOnChange={newDatatype =>
                (attribute.datatype.value = newDatatype?.prefix
                    ? newDatatype.prefix + newDatatype.label
                    : (newDatatype ?? null))}
            readonly={readonly || inherited}
            tooltip={attribute.datatype.value}
            buttons={getControlButtonsForReactiveObject(
                attribute.datatype,
                readonly || inherited,
            )}
        />
    </td>
    <td>
        <FaIconButton
            callOnClick={() =>
                openPropertySHACLRulesDialog(
                    attribute,
                    inherited ? targetClass?.uuid : null,
                )}
            title={readonly ? "View" : "Edit" + " Constraints (SHACL)"}
            icon={faDiagramProject}
        />
    </td>
    <td>
        <FaIconButton
            callOnClick={() =>
                openAttributeEditor(attribute, inherited ? targetClass : null)}
            icon={readonly ? faEye : faGear}
            title={readonly ? "View" : "Edit" + " attribute"}
        />
    </td>
    {#if !classEditorContext.readonly}
        <td>
            {#if !inherited}
                <FaIconButton
                    callOnClick={() => attributes.remove(attribute, true)}
                    icon={faMinus}
                    title="Remove attribute"
                />
            {/if}
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
