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
    import { faCaretRight } from "@fortawesome/free-solid-svg-icons";
    import { getContext } from "svelte";
    import { Fa } from "svelte-fa";

    import { userSettings } from "$lib/userSettings.svelte.js";

    const { groups = [], label, colspan, rows } = $props();

    const classEditorContext = getContext("classEditor");

    let expanded = $state(false);
    let visible = $derived(
        userSettings.get("showInheritedProperties", true) && groups.length > 0,
    );
</script>

{#if visible}
    <tr>
        <td {colspan} class="pt-0.5">
            <button
                type="button"
                class="text-blue flex w-fit cursor-pointer items-center gap-1.5 pl-1 text-sm"
                onclick={() => (expanded = !expanded)}
            >
                <Fa
                    icon={faCaretRight}
                    class={`w-2 transition-transform duration-200 ${
                        expanded ? "rotate-90" : "rotate-0"
                    }`}
                />
                <span>{label}</span>
            </button>
        </td>
    </tr>
    {#if expanded}
        {#each groups as group}
            <tr>
                <td {colspan} class="pt-0.5 pl-2">
                    <button
                        type="button"
                        class="text-text-subtle hover:text-blue cursor-pointer text-xs italic hover:underline"
                        title="Open class"
                        onclick={() =>
                            classEditorContext.openClass(group.sourceClassUuid)}
                    >
                        from {group.sourceClassLabel}
                    </button>
                </td>
            </tr>
            {@render rows(group)}
        {/each}
    {/if}
    <tr>
        <td {colspan} class="pt-1"></td>
    </tr>
{/if}
