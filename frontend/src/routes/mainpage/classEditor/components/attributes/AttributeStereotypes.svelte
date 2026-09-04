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
    import { ATTRIBUTE_STEREOTYPE } from "$lib/models/stereotype-constants.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    import Stereotype from "../stereotypes/Stereotype.svelte";

    const { attributeStereotypes } = $props();

    const classEditorContext = getContext("classEditor");
    let expandStereotypes = $state(true);
    let readonly = $derived(classEditorContext.readOnly);

    $effect(() => {
        editorState.selectedDiagram.subscribe();
        readonly = classEditorContext.readOnly;
    });

    onMount(() => (readonly = classEditorContext.readOnly));
</script>

<List legend="Stereotypes" bind:isExpanded={expandStereotypes}>
    {#snippet actions()}
        {#if !readonly}
            <div class="size-8">
                <FaIconButton
                    callOnClick={() => {
                        attributeStereotypes.append("");
                        expandStereotypes = true;
                    }}
                    icon={faPlus}
                />
            </div>
        {/if}
    {/snippet}
    {#snippet contents()}
        <tbody>
            {#each attributeStereotypes.values as stereotype}
                <Stereotype
                    classStereotypes={attributeStereotypes}
                    {stereotype}
                    readonlyStereotypes={[ATTRIBUTE_STEREOTYPE]}
                />
            {/each}
        </tbody>
    {/snippet}
</List>
