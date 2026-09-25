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

    import { getClassesReferencingThisClass } from "$lib/api/generated/index.ts";

    let { workspaceName, graphUri, classUUID, onClickOnClass } = $props();

    let classesReferencingThisClass = $state({});
    let failed = $state(false);

    /** Only the relations that actually reference something; the rest are noise. */
    const groups = $derived(
        Object.entries(classesReferencingThisClass).filter(
            ([, classList]) => (classList?.length ?? 0) > 0,
        ),
    );

    onMount(() => fetchClassesReferencingThisClass(classUUID));

    /** A failure is said as one: "nothing references this class" would be a wrong answer. */
    function fetchClassesReferencingThisClass(classUUID) {
        getClassesReferencingThisClass({
            path: {
                datasetName: workspaceName,
                graphURI: graphUri,
                classUUID: classUUID,
            },
        })
            .then(({ data, error }) => {
                failed = !!error;
                classesReferencingThisClass = error
                    ? {}
                    : (data?.classesReferencingThisClass ?? {});
            })
            .catch(() => {
                failed = true;
                classesReferencingThisClass = {};
            });
    }
</script>

<!--
  @component
  The classes that reference this one, as links rather than as a wall of buttons.

  Relations nobody uses are left out entirely: listing "not referenced via inheritance" for every
  empty relation made the panel longer the less it had to say.
-->

<div class="flex h-full min-h-0 flex-col">
    <h3 class="text-default-text shrink-0 pb-1 text-sm font-semibold">
        Referenced by
    </h3>
    <div class="min-h-0 flex-1 overflow-y-auto">
        {#if failed}
            <p class="text-red-text text-xs">
                The classes referencing this one could not be read.
            </p>
        {:else if groups.length === 0}
            <p class="text-text-subtle text-xs italic">
                Nothing references this class.
            </p>
        {:else}
            {#each groups as [relationType, classList] (relationType)}
                <p class="text-text-subtle mt-2 text-xs first:mt-0">
                    {relationType}
                </p>
                <ul>
                    {#each classList as classObject (classObject.uuid)}
                        <li>
                            <button
                                class="text-blue cursor-pointer truncate text-left text-sm hover:underline"
                                title={classObject.label}
                                onclick={() => onClickOnClass(classObject.uuid)}
                            >
                                {classObject.label}
                            </button>
                        </li>
                    {/each}
                </ul>
            {/each}
        {/if}
    </div>
</div>
