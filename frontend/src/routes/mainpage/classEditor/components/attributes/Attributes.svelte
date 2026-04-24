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
    import { faPlus } from "@fortawesome/free-solid-svg-icons";
    import { getContext, onMount } from "svelte";

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import List from "$lib/components/List.svelte";
    import { editorState } from "$lib/sharedState.svelte.js";

    import Attribute from "./Attribute.svelte";
    import AttributeEditorDialog from "./AttributeEditorDialog.svelte";

    const { attributes, openPropertySHACLRulesDialog } = $props();

    const classEditorContext = getContext("classEditor");

    const attributeEditorDialog = $state({
        showDialog: false,
        attribute: null,
    });

    let expandStereotypes = $state(true);
    let readonly = $derived(classEditorContext.readonly);

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        readonly = getContext("classEditor").readonly;
    });

    onMount(() => (readonly = classEditorContext.readonly));

    function openAttributeEditor(attribute) {
        attributeEditorDialog.attribute = attribute;
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
    {attributes}
/>
