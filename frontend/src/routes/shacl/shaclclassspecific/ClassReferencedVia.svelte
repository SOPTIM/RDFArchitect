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
    import { onMount } from "svelte";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { editorState } from "$lib/sharedState.svelte.js";

    let { classUUID, onClickOnClass } = $props();
    let classesReferencingThisClass = $state({});
    let classDatasetName = $derived(
        editorState.selectedClassDataset.getValue() ??
            editorState.selectedDataset.getValue(),
    );
    let classGraphUri = $derived(
        editorState.selectedClassGraph.getValue() ??
            editorState.selectedGraph.getValue(),
    );
    onMount(() => fetchClassesReferencingThisClass(classUUID));
    function fetchClassesReferencingThisClass(classUUID) {
        fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(classDatasetName) +
                "/graphs/" +
                encodeURIComponent(classGraphUri) +
                "/classes/" +
                encodeURIComponent(classUUID) +
                "/referencedByClasses",
            {
                method: "GET",
                credentials: "include",
            },
        )
            .then(res => res.text())
            .then(res => {
                const parsed = JSON.parse(res);
                classesReferencingThisClass =
                    parsed?.classesReferencingThisClass ?? {};
            })
            .catch(() => {
                classesReferencingThisClass = {};
            });
    }
</script>

<!-- right column for navigating to referencing classes -->
<p class="mx-1">Classes referencing this class via:</p>
{#each Object.entries(classesReferencingThisClass) as [relationType, classList]}
    <p class="under mx-1">
        {relationType}:
    </p>
    {#if classList.length === 0}
        <p class="ml-4">
            not referenced via "{relationType}".
        </p>
    {/if}
    {#each classList as classObject}
        <div class="ml-4 w-fit">
            <ButtonControl
                callOnClick={() => {
                    onClickOnClass(classObject.uuid);
                }}
            >
                {classObject.label}
            </ButtonControl>
        </div>
    {/each}
{/each}
