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

    import EnumEntry from "./EnumEntry.svelte";
    import EnumEntryEditorDialog from "./EnumEntryEditorDialog.svelte";
    import InheritedSection from "../InheritedSection.svelte";

    const { enumEntries, inheritedEnumEntries = [] } = $props();

    const classEditorContext = getContext("classEditor");
    const enumEntryEditorDialog = $state({
        showDialog: false,
        enumEntry: null,
        targetClass: null,
    });

    let expandStereotypes = $state(true);

    let readonly = $derived(classEditorContext.readonly);

    $effect(() => {
        editorState.selectedDiagram.subscribe();
        readonly = classEditorContext.readonly;
    });

    onMount(() => (readonly = classEditorContext.readonly));

    function openEnumEntryEditor(enumEntry, targetClass = null) {
        enumEntryEditorDialog.enumEntry = enumEntry;
        enumEntryEditorDialog.targetClass = targetClass;
        enumEntryEditorDialog.showDialog = true;
    }
</script>

<List
    legend="Enum entries"
    bind:isExpanded={expandStereotypes}
    highlight={enumEntries.isModified}
    warn={!enumEntries.isValid}
>
    {#snippet actions()}
        {#if !readonly}
            <div class="size-8">
                <FaIconButton
                    callOnClick={() => {
                        openEnumEntryEditor(null);
                        expandStereotypes = true;
                    }}
                    icon={faPlus}
                />
            </div>
        {/if}
    {/snippet}
    {#snippet contents()}
        <colgroup>
            <col />
            <col style="width: 2rem" />
            {#if !readonly}
                <col style="width: 2rem" />
            {/if}
        </colgroup>
        <tbody>
            <InheritedSection
                groups={inheritedEnumEntries}
                label="Inherited enum entries"
                colspan={readonly ? 2 : 3}
            >
                {#snippet rows(group)}
                    {#each group.enumEntries as enumEntry}
                        <EnumEntry
                            {enumEntries}
                            {enumEntry}
                            {openEnumEntryEditor}
                            inherited={true}
                            targetClass={{
                                uuid: group.sourceClassUuid,
                                prefix: group.sourceClassPrefix,
                                label: group.sourceClassLabel,
                            }}
                        />
                    {/each}
                {/snippet}
            </InheritedSection>
            {#each enumEntries.values as enumEntry}
                <EnumEntry {enumEntries} {enumEntry} {openEnumEntryEditor} />
            {/each}
        </tbody>
    {/snippet}
</List>
<EnumEntryEditorDialog
    bind:showDialog={enumEntryEditorDialog.showDialog}
    bind:enumEntry={enumEntryEditorDialog.enumEntry}
    targetClass={enumEntryEditorDialog.targetClass}
    {enumEntries}
/>
