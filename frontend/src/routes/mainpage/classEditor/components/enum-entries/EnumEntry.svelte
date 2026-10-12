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
    import {
        claimPropertyFocus,
        PROPERTY_HIGHLIGHT_MS,
        scrollRowIntoView,
    } from "$lib/utils/property-focus.js";

    const {
        enumEntries,
        enumEntry,
        openEnumEntryEditor,
        inherited = false,
        targetClass = null,
    } = $props();

    const classEditorContext = getContext("classEditor");

    let row = $state(null);
    let revealed = $state(false);
    let readonly = $derived(classEditorContext.readOnly);

    $effect(() => {
        editorState.selectedDiagram.subscribe();
        readonly = classEditorContext.readOnly;
    });

    // See Attribute.svelte: only the declaring class's own row answers a reveal request.
    $effect(() => {
        editorState.focusedPropertyUUID.subscribe();
        if (inherited || !claimPropertyFocus(enumEntry?.uuid?.value)) {
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

    onMount(() => (readonly = classEditorContext.readOnly));
</script>

<tr
    bind:this={row}
    class={revealed ? "bg-background-select ring-border-select ring-2" : ""}
>
    <td>
        <TextEditControl
            bind:value={enumEntry.label.value}
            highlight={enumEntry.label.isModified}
            warn={!enumEntry.label.isValid}
            buttons={getControlButtonsForReactiveObject(
                enumEntry.label,
                readonly || inherited,
            )}
            readonly={readonly || inherited}
        />
    </td>
    <td class="size-8">
        <FaIconButton
            callOnClick={() =>
                openEnumEntryEditor(enumEntry, inherited ? targetClass : null)}
            icon={readonly ? faEye : faGear}
            title={readonly ? "View" : "Edit" + " enum entry"}
        />
    </td>
    {#if !readonly}
        <td class="size-8">
            {#if !inherited}
                <FaIconButton
                    icon={faMinus}
                    callOnClick={() => enumEntries.remove(enumEntry, true)}
                    title="Remove enum entry"
                />
            {/if}
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
