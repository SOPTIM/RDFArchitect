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
    import { faCaretUp } from "@fortawesome/free-solid-svg-icons";
    import { slide } from "svelte/transition";
    import { Fa } from "svelte-fa";

    let {
        actions,
        contents,
        legend,
        highlight = false,
        warn = false,
        isExpanded = $bindable(true),
        isCollapsible = true,
    } = $props();

    let scrollContainer = $state();

    // If the consumer disables collapsing, ensure we render expanded (unless explicitly "always expanded" via null).
    $effect(() => {
        if (!isCollapsible && isExpanded !== null && isExpanded === false) {
            isExpanded = true;
        }
    });

    export function scrollToTop() {
        if (!scrollContainer) return;
        scrollContainer.scrollTop = 0;
    }

    export function scrollToBottom() {
        if (!scrollContainer) return;
        scrollContainer.scrollTop = scrollContainer.scrollHeight;
    }

    function toggleExpanded() {
        if (!isCollapsible) return;
        if (isExpanded === null) return;
        isExpanded = !isExpanded;
    }
</script>

<fieldset
    class="
        flex h-full
        w-full flex-col
        rounded border
        {warn ? 'border-red' : 'border-blue'}
    "
>
    <div
        class="
            flex h-full
            w-full flex-col
            rounded-[3px] border p-1
            {warn
            ? 'border-red'
            : highlight
              ? 'border-blue'
              : 'border-transparent'}
        "
    >
        <div class="flex flex-none justify-start text-left">
            {#if isExpanded === null || !isCollapsible}
                <span class="text-blue w-fit pl-1 text-lg">{legend}</span>
            {:else}
                <button
                    type="button"
                    class="text-blue flex w-fit cursor-pointer pl-1 text-lg"
                    onclick={toggleExpanded}
                >
                    <span class="flex items-center space-x-1">
                        <span>{legend}</span>
                        <Fa
                            icon={faCaretUp}
                            class={`transition-transform duration-300 ${
                                isExpanded ? "rotate-180" : "rotate-0"
                            }`}
                        />
                    </span>
                </button>
            {/if}

            <span class="ml-auto pr-0.5">
                {@render actions?.()}
            </span>
        </div>

        {#if isExpanded === null || !isCollapsible || isExpanded}
            <div
                class="overflow-auto"
                bind:this={scrollContainer}
                transition:slide={{ duration: 300 }}
            >
                <table
                    class="
                        w-full
                        table-fixed
                        border-separate
                        border-spacing-x-0.5
                        border-spacing-y-0.5
                    "
                >
                    {@render contents?.()}
                </table>
            </div>
        {/if}
    </div>
</fieldset>
