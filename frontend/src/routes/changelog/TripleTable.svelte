<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->
<script>
    import { faCaretDown, faCaretUp } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    const {
        triples,
        color = "green",
        title = "Triples",
        expandedKey,
        getExpanded,
        setExpanded,
    } = $props();

    const colorMap = {
        red: {
            border: "border-red-border",
            background: "bg-red-background",
            text: "text-red-text",
        },
        green: {
            border: "border-green-border",
            background: "bg-green-background",
            text: "text-green-text",
        },
    };

    const colors = $derived(colorMap[color] ?? colorMap.green);

    function toggle() {
        setExpanded(expandedKey, !getExpanded(expandedKey));
    }
</script>

<div class={`rounded-xl border ${colors.border} ${colors.background}`}>
    <div
        class={`flex cursor-pointer items-center px-4 py-2 text-sm font-semibold ${colors.text}`}
        role="button"
        tabindex="0"
        onkeydown={e => {
            if (e.key === "Enter" || e.key === " ") {
                toggle();
            }
        }}
        onclick={toggle}
    >
        {title}
        <Fa
            class="pl-1"
            icon={getExpanded(expandedKey) ? faCaretUp : faCaretDown}
        />
    </div>

    {#if getExpanded(expandedKey)}
        <div class="overflow-auto px-4 pb-4">
            <table
                class={`w-full table-auto border-t text-left text-xs ${colors.border}`}
            >
                <thead class={`${colors.text} font-semibold`}>
                    <tr>
                        <th class="w-1/3 py-2">Subject</th>
                        <th class="w-1/3 py-2">Predicate</th>
                        <th class="w-1/3 py-2">Object</th>
                    </tr>
                </thead>
                <tbody>
                    {#each triples as triple}
                        <tr class={`border-t ${colors.border}`}>
                            <td class={`py-2 ${colors.text}`}>
                                {triple.subject}
                            </td>
                            <td class={`py-2 ${colors.text}`}>
                                {triple.predicate}
                            </td>
                            <td class={`py-2 ${colors.text}`}>
                                {triple.object}
                            </td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        </div>
    {/if}
</div>
