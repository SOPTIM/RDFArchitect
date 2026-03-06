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
    import { Handle, Position } from "@xyflow/svelte";

    import { editorState } from "$lib/sharedState.svelte.js";

    let { id, data, dragging } = $props();

    const highlighted = $derived(
        editorState.selectedClassUUID.getValue() === id,
    );

    const label = $derived(data.label);
    const stereotypes = $derived(data.stereotypes);
    const attributes = $derived(data.attributes);
    const enumEntries = $derived(data.enumEntries);

    const cursorClass = $derived(dragging ? "cursor-move" : "cursor-pointer");
</script>

<div
    class={`class-node-shell bg-class-node-upper-background min-w-45 overflow-hidden rounded-md bg-clip-padding font-sans text-sm ${cursorClass} ${
        highlighted ? "ring-class-node-highlighted ring-3" : ""
    }`}
    role="button"
    tabindex="0"
>
    <Handle
        class="absolute top-0 left-0 h-full w-full transform-none rounded-none border-none opacity-0"
        position={Position.Right}
        style="z-index: 1;"
        isConnectableStart={false}
    />

    <div class="border-default-text border-b p-2 text-center">
        {#if stereotypes.length > 0}
            <div class="flex flex-col gap-0.5">
                {#each stereotypes as stereotype}
                    <div class="text-default-text text-xs">
                        &laquo;{stereotype}&raquo;
                    </div>
                {/each}
            </div>
        {/if}

        {#if data.belongsToCategory}
            <div class="text-default-text mb-0.5 text-sm italic">
                {data.belongsToCategory} ::
            </div>
        {/if}

        <span class="text-default-text mt-1 font-bold">{label}</span>
    </div>
    <div class="bg-class-node-lower-background min-h-6 rounded-b-md p-2">
        {#if attributes && attributes.length > 0}
            {#each attributes as attr}
                <div class="text-default-text leading-6">
                    {attr.label}: {attr.type} &nbsp;[{attr.multiplicity}]
                </div>
            {/each}
        {:else if enumEntries && enumEntries.length > 0}
            {#each enumEntries as enumEntry}
                <div class="text-default-text leading-6">
                    {enumEntry}
                </div>
            {/each}
        {/if}
    </div>
</div>

<style>
    .class-node-shell {
        position: relative;
        isolation: isolate;
    }

    .class-node-shell::after {
        content: "";
        position: absolute;
        inset: 0;
        border-radius: inherit;
        box-shadow: inset 0 0 0 1px var(--color-default-text);
        pointer-events: none;
        z-index: 2;
    }
</style>
