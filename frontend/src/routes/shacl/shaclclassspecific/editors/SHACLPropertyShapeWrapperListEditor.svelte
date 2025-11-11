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
        propertyShapesWrapperList = $bindable(),
        readOnly = false,
        expanded = false,
    } = $props();

    let showPropertyShapes = $state(
        Array(propertyShapesWrapperList.length).fill(expanded),
    );
</script>

{#if propertyShapesWrapperList}
    {#each propertyShapesWrapperList as shapesWrapper, i}
        <div class="ml-4">
            <button
                class="w-fit hover:cursor-pointer hover:underline"
                onmousedown={() => {
                    showPropertyShapes[i] = !showPropertyShapes[i];
                }}
            >
                {shapesWrapper.label + ":"}
            </button>
        </div>
        <div class="space-y-4">
            {#if showPropertyShapes[i]}
                {#each shapesWrapper.propertyShapes as propertyShape}
                    <TtlCodeEditor
                        bind:value={propertyShape.triples}
                        {readOnly}
                    />
                {/each}
            {/if}
        </div>
    {/each}
{/if}
