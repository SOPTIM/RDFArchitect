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
    let {
        /** What leads the list, saying what the files have in common. */
        heading,
        /**
         * The names, one per line; nothing is shown for an empty list.
         *
         * @type {string[]}
         */
        names = [],
        /** Classes placing the box in its surroundings. */
        placement = "mt-3",
        /** Anything to say below the list, such as where the files do belong. */
        children,
    } = $props();
</script>

{#if names.length > 0}
    <div
        class="bg-red-background text-red-text border-red-border {placement} rounded border px-3 py-2 text-xs"
    >
        <p class="font-semibold">{heading}</p>
        <ul class="list-disc pl-5">
            {#each names as name (name)}
                <li>{name}</li>
            {/each}
        </ul>
        {@render children?.()}
    </div>
{/if}
