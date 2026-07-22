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
    import { faCaretRight, faPlus } from "@fortawesome/free-solid-svg-icons";
    import { getContext, onMount } from "svelte";
    import { Fa } from "svelte-fa";

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import List from "$lib/components/List.svelte";
    import { editorState } from "$lib/sharedState.svelte.js";
    import { userSettings } from "$lib/userSettings.svelte.js";

    import Attribute from "./Attribute.svelte";
    import AttributeEditorDialog from "./AttributeEditorDialog.svelte";

    const {
        attributes,
        inheritedAttributes = [],
        openPropertySHACLRulesDialog,
    } = $props();

    const classEditorContext = getContext("classEditor");

    const attributeEditorDialog = $state({
        showDialog: false,
        attribute: null,
        targetClass: null,
    });

    let expandStereotypes = $state(true);
    let expandInherited = $state(false);

    let readonly = $derived(classEditorContext.readonly);
    let showInherited = $derived(
        userSettings.get("showInheritedProperties", true) &&
            inheritedAttributes.length > 0,
    );

    $effect(() => {
        editorState.selectedDiagram.subscribe();
        readonly = getContext("classEditor").readonly;
    });

    onMount(() => (readonly = classEditorContext.readonly));

    function openAttributeEditor(attribute, targetClass = null) {
        attributeEditorDialog.attribute = attribute;
        attributeEditorDialog.targetClass = targetClass;
        attributeEditorDialog.showDialog = true;
    }
</script>

<List
    legend="Attributes"
    bind:isExpanded={expandStereotypes}
    highlight={attributes.isModified}
    warn={!attributes.isValid}
>
    {#snippet actions()}
        {#if !readonly}
            <div class="size-8">
                <FaIconButton
                    callOnClick={() => {
                        openAttributeEditor(null);
                        expandStereotypes = true;
                    }}
                    icon={faPlus}
                />
            </div>
        {/if}
    {/snippet}
    {#snippet contents()}
        <thead>
            <tr>
                <th class="text-blue w-1/2 pl-1 text-left font-normal">
                    Label
                </th>
                <th class="text-blue w-1/2 pl-1 text-left font-normal">Type</th>
                <th class="size-8"></th>
                <th class="size-8"></th>
                {#if !readonly}
                    <th class="size-8"></th>
                {/if}
            </tr>
        </thead>
        <tbody>
            {#if showInherited}
                <tr>
                    <td colspan={readonly ? 4 : 5} class="pt-0.5">
                        <button
                            type="button"
                            class="text-blue flex w-fit cursor-pointer items-center gap-1.5 pl-1 text-sm"
                            onclick={() => (expandInherited = !expandInherited)}
                        >
                            <Fa
                                icon={faCaretRight}
                                class={`w-2 transition-transform duration-200 ${
                                    expandInherited ? "rotate-90" : "rotate-0"
                                }`}
                            />
                            <span>Inherited attributes</span>
                        </button>
                    </td>
                </tr>
                {#if expandInherited}
                    {#each inheritedAttributes as group}
                        <tr>
                            <td colspan={readonly ? 4 : 5} class="pt-0.5 pl-2">
                                <button
                                    type="button"
                                    class="text-text-subtle hover:text-blue cursor-pointer text-xs italic hover:underline"
                                    title="Open class"
                                    onclick={() =>
                                        classEditorContext.openClass(
                                            group.sourceClassUuid,
                                        )}
                                >
                                    from {group.sourceClassLabel}
                                </button>
                            </td>
                        </tr>
                        {#each group.attributes as attribute}
                            <Attribute
                                {attributes}
                                {attribute}
                                {openAttributeEditor}
                                {openPropertySHACLRulesDialog}
                                inherited={true}
                                targetClass={{
                                    uuid: group.sourceClassUuid,
                                    prefix: group.sourceClassPrefix,
                                    label: group.sourceClassLabel,
                                }}
                            />
                        {/each}
                    {/each}
                {/if}
                <tr>
                    <td colspan={readonly ? 4 : 5} class="pt-1"></td>
                </tr>
            {/if}
            {#each attributes.values as attribute}
                <Attribute
                    {attributes}
                    {attribute}
                    {openAttributeEditor}
                    {openPropertySHACLRulesDialog}
                />
            {/each}
        </tbody>
    {/snippet}
</List>
<AttributeEditorDialog
    bind:showDialog={attributeEditorDialog.showDialog}
    bind:attribute={attributeEditorDialog.attribute}
    targetClass={attributeEditorDialog.targetClass}
    {attributes}
/>
