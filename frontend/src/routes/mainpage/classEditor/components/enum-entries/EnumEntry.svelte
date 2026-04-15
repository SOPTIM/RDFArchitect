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
    import { faEye, faGear, faMinus } from "@fortawesome/free-solid-svg-icons";
    import { getContext, onMount } from "svelte";

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/utils/reactive-objects-control-button-utils.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    const { enumEntries, enumEntry, openEnumEntryEditor } = $props();

    const classEditorContext = getContext("classEditor");
    let readonly = $derived(classEditorContext.readonly);

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        readonly = classEditorContext.readonly;
    });

    onMount(() => (readonly = classEditorContext.readonly));
</script>

<tr>
    <td>
        <TextEditControl
            bind:value={enumEntry.label.value}
            highlight={enumEntry.label.isModified}
            warn={!enumEntry.label.isValid}
            buttons={getControlButtonsForReactiveObject(
                enumEntry.label,
                readonly,
            )}
            {readonly}
        />
    </td>
    <td class="size-8">
        <FaIconButton
            callOnClick={() => openEnumEntryEditor(enumEntry)}
            icon={readonly ? faEye : faGear}
            title={readonly ? "View" : "Edit" + " enum entry"}
        />
    </td>
    {#if !readonly}
        <td class="size-8">
            <FaIconButton
                icon={faMinus}
                callOnClick={() => enumEntries.remove(enumEntry, true)}
                title="Remove enum entry"
            />
        </td>
    {/if}
</tr>

{#if !enumEntry.label.isValid}
    <tr>
        <td class="align-top">
            <ViolationMessages violations={enumEntry.label.violations} />
        </td>
    </tr>
{/if}
