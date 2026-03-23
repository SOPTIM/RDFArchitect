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

    import IsAbstract from "./IsAbstract.svelte";
    import Stereotype from "./Stereotype.svelte";

    const { classStereotypes } = $props();

    const classEditorContext = getContext("classEditor");
    let readonly = $state(false);

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        readonly = classEditorContext.readonly;
    });

    onMount(() => (readonly = classEditorContext.readonly));

    let expandStereotypes = $state(true);
</script>

<IsAbstract {classStereotypes} />
<tr>
    <td colspan={2} class="text-left font-normal">
        <List legend="Stereotypes" bind:isExpanded={expandStereotypes}>
            {#snippet actions()}
                {#if !readonly}
                    <div class="size-8">
                        <FaIconButton
                            callOnClick={() => {
                                classStereotypes.append("");
                                expandStereotypes = true;
                            }}
                            icon={faPlus}
                        />
                    </div>
                {/if}
            {/snippet}
            {#snippet contents()}
                <tbody>
                    {#each classStereotypes.values as stereotype}
                        <Stereotype {classStereotypes} {stereotype} />
                    {/each}
                </tbody>
            {/snippet}
        </List>
    </td>
</tr>
