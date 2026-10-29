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
        values,
        title = "Values",
        expandedKey,
        getExpanded,
        setExpanded,
    } = $props();

    /** What a colour looks like, so that one can be shown as well as spelled out. */
    const COLOR = /^#(?:[0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i;

    function toggle() {
        setExpanded(expandedKey, !getExpanded(expandedKey));
    }

    function isColor(value) {
        return !!value && COLOR.test(value);
    }
</script>

<div class="border-border bg-background-subtle rounded-xl border">
    <div
        class="text-default-text flex cursor-pointer items-center px-4 py-2 text-sm font-semibold"
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
                class="border-border w-full table-auto border-t text-left text-xs"
            >
                <thead class="text-default-text font-semibold">
                    <tr>
                        <th class="w-1/3 py-2">Name</th>
                        <th class="w-1/3 py-2">Before</th>
                        <th class="w-1/3 py-2">After</th>
                    </tr>
                </thead>
                <tbody>
                    {#each values as value}
                        <tr class="border-border border-t">
                            <td class="text-default-text py-2 break-all">
                                {value.key}
                            </td>
                            {#each [value.before, value.after] as side}
                                <td class="text-default-text py-2">
                                    {#if side}
                                        <span
                                            class="flex items-center gap-1.5 break-all"
                                        >
                                            {#if isColor(side)}
                                                <span
                                                    class="border-border inline-block size-3 shrink-0 rounded-sm border"
                                                    style={`background-color: ${side}`}
                                                ></span>
                                            {/if}
                                            {side}
                                        </span>
                                    {:else}
                                        <span class="text-text-subtle">—</span>
                                    {/if}
                                </td>
                            {/each}
                        </tr>
                    {/each}
                </tbody>
            </table>
        </div>
    {/if}
</div>
