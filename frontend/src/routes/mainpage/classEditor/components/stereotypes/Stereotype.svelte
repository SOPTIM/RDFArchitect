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
    import { faMinus } from "@fortawesome/free-solid-svg-icons";
    import { getContext, onMount } from "svelte";

    import ComboBoxEditControl from "$lib/components/ComboBoxEditControl.svelte";
    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/utils/reactive-objects-control-button-utils.js";
    import { CONCRETE_STEREOTYPE } from "$lib/models/stereotype-constants.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    let { classStereotypes, stereotype } = $props();

    const classEditorContext = getContext("classEditor");

    // The concrete stereotype is managed via the "Abstract" checkbox, so it
    // must not be offered as a regular, manually selectable stereotype.
    let suggestedStereotypes = $derived(
        withoutConcrete(classEditorContext.stereotypes),
    );
    let readonly = $derived(classEditorContext.readOnly);

    // The persisted concrete stereotype is surfaced by the "Abstract" checkbox
    // and hidden from the list. A row the user has just edited to the concrete
    // URI stays visible so its violation message can explain the checkbox.
    let isManagedConcrete = $derived(
        stereotype.value === CONCRETE_STEREOTYPE && !stereotype.isModified,
    );

    $effect(() => {
        editorState.selectedDiagram.subscribe();
        readonly = classEditorContext.readOnly;
    });

    $effect(() => {
        editorState.selectedContext.subscribe();
        suggestedStereotypes = withoutConcrete(classEditorContext.stereotypes);
    });

    onMount(() => {
        readonly = classEditorContext.readOnly;
        suggestedStereotypes = withoutConcrete(classEditorContext.stereotypes);
    });

    function withoutConcrete(stereotypes) {
        return (stereotypes ?? []).filter(s => s !== CONCRETE_STEREOTYPE);
    }
</script>

{#if !isManagedConcrete}
    <tr>
        <td>
            <div class="flex gap-0.5">
                <ComboBoxEditControl
                    value={stereotype.value}
                    placeholder="stereotype..."
                    callOnInput={newValue => (stereotype.value = newValue)}
                    optionValues={suggestedStereotypes}
                    highlight={stereotype.isModified}
                    warn={!stereotype.isValid}
                    buttons={getControlButtonsForReactiveObject(
                        stereotype,
                        readonly,
                    )}
                    {readonly}
                />
                {#if !readonly}
                    <div class="size-8">
                        <FaIconButton
                            icon={faMinus}
                            callOnClick={() =>
                                classStereotypes.remove(stereotype, true)}
                        />
                    </div>
                {/if}
            </div>
            <div>
                <ViolationMessages violations={stereotype.violations} />
            </div>
        </td>
    </tr>
{/if}
