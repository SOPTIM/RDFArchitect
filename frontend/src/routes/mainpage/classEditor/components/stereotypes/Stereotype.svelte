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
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/reactive-utils.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    let { classStereotypes, stereotype } = $props();

    const classEditorContext = getContext("classEditor");
    const concreteStereotype = "http://iec.ch/TC57/NonStandard/UML#concrete";
    let suggestedStereotypes = $state();
    let readonly = $state(false);

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        readonly = classEditorContext.readonly;
    });

    $effect(() => {
        editorState.selectedContext.subscribe();
        suggestedStereotypes = classEditorContext.stereotypes;
    });

    onMount(() => {
        readonly = classEditorContext.readonly;
        suggestedStereotypes = classEditorContext.stereotypes;
    });
</script>

{#if stereotype.value !== concreteStereotype}
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
