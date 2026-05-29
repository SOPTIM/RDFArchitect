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
    import { faChevronRight } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    let { title, children, open = true } = $props();

    let isOpen = $state(open);
</script>

<div class="flex flex-col gap-2">
    {#if title}
        <button
            type="button"
            class="flex cursor-pointer items-center gap-2 border-none bg-transparent p-0"
            onclick={() => (isOpen = !isOpen)}
        >
            <Fa
                icon={faChevronRight}
                class="text-default-text opacity-80 transition-transform duration-200 {isOpen
                    ? 'rotate-90'
                    : ''}"
            />
            <span
                class="text-default-text text-sm font-semibold whitespace-nowrap uppercase opacity-80"
            >
                {title}
            </span>
            <hr class="flex-1 border-t border-gray-600" />
        </button>
    {/if}
    {#if isOpen}
        <div class="mt-1 flex flex-col gap-2">
            {@render children?.()}
        </div>
    {/if}
</div>
