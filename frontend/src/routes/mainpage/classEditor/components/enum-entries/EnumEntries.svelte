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
    import { faPlus } from "@fortawesome/free-solid-svg-icons";
    import { getContext } from "svelte";

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import List from "$lib/components/List.svelte";
    import { ReactiveEnumEntry } from "$lib/models/reactive/reactive-enum-entry.svelte.js";

    import EnumEntry from "./EnumEntry.svelte";

    const { enumEntries, cls } = $props();

    const readonly = getContext("classEditor").readonly;

    let expandStereotypes = $state(true);
</script>

<List
    legend="Enum entries"
    bind:isExpanded={expandStereotypes}
    highlight={enumEntries.isModified}
    warn={!enumEntries.isValid}
>
    {#snippet actions()}
        {#if !readonly}
            <div class="size-8">
                <FaIconButton
                    callOnClick={() => {
                        enumEntries.append(
                            new ReactiveEnumEntry({
                                namespace: cls.namespace,
                            }),
                        );
                        expandStereotypes = true;
                    }}
                    icon={faPlus}
                />
            </div>
        {/if}
    {/snippet}
    {#snippet contents()}
        <tbody>
            {#each enumEntries.values as enumEntry}
                <EnumEntry {enumEntries} {enumEntry} />
            {/each}
        </tbody>
    {/snippet}
</List>
