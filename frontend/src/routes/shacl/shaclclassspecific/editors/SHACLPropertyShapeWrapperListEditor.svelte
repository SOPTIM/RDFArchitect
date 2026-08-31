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
    import TurtleEditor from "$lib/monaco/TurtleEditor.svelte";

    let { propertyShapesWrapperList } = $props();

    /** Which properties are open, by label — no coupling to the list's length or order. */
    let open = $state({});
</script>

{#if propertyShapesWrapperList}
    {#each propertyShapesWrapperList as shapesWrapper (shapesWrapper.label)}
        <div class="ml-4">
            <button
                class="w-fit hover:cursor-pointer hover:underline"
                onclick={() =>
                    (open[shapesWrapper.label] = !open[shapesWrapper.label])}
            >
                {shapesWrapper.label}
            </button>
        </div>
        <div class="space-y-4">
            {#if open[shapesWrapper.label]}
                {#each shapesWrapper.propertyShapes as propertyShape}
                    <TurtleEditor
                        autoGrow
                        value={propertyShape.triples}
                        readOnly={true}
                    />
                {/each}
            {/if}
        </div>
    {/each}
{/if}
