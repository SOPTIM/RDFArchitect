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
    import TtlCodeEditor from "$lib/ttl/TtlCodeEditor.svelte";

    let {
        nodeShapesList = $bindable(),
        readOnly = false,
        expanded = false,
    } = $props();

    let showNodeShapes = $state(Array(nodeShapesList.length).fill(expanded));
</script>

{#if nodeShapesList}
    {#each nodeShapesList as nodeShape, i}
        <div class="ml-4">
            <button
                class="w-fit hover:cursor-pointer hover:underline"
                onmousedown={() => {
                    showNodeShapes[i] = !showNodeShapes[i];
                }}
            >
                {nodeShape.id.split("#", 2)[1] + ":"}
            </button>
        </div>
        {#if showNodeShapes[i]}
            <TtlCodeEditor bind:value={nodeShape.triples} {readOnly} />
        {/if}
    {/each}
{/if}
