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
    import { faCaretDown } from "@fortawesome/free-solid-svg-icons";
    import { CollapsibleCard } from "svelte-collapsible";
    import { Fa } from "svelte-fa";

    import ChangeTable from "./ChangeTable.svelte";
    import NestedChangeGroup from "./NestedChangeObject.svelte";

    let { data = [] } = $props();
</script>

<h1 class="mb-2 text-2xl font-bold">{data.label}</h1>
<div class="border-default-text bg-window-background rounded border p-6 shadow">
    {#if data.changes}
        <CollapsibleCard>
            <h2
                slot="header"
                class="text-default-text mb-2 text-lg font-semibold"
            >
                Package Changes <Fa
                    class="collapsible-caret"
                    icon={faCaretDown}
                />
            </h2>
            <ChangeTable slot="body" changes={data.changes} />
        </CollapsibleCard>
    {:else}
        <p class="text-default-text italic">
            No changes in package definition.
        </p>
    {/if}
</div>

{#each data.classes as classChange}
    <div
        class="border-default-text bg-window-background mb-6 rounded border p-6 shadow"
    >
        <CollapsibleCard>
            <h2 slot="header" class="mb-2 text-lg font-bold">
                {classChange.label}
                <Fa class="collapsible-caret" icon={faCaretDown} />
            </h2>
            <div slot="body">
                {#if classChange.changes}
                    <div class="mb-4">
                        <CollapsibleCard>
                            <h3 slot="header" class="mb-2 text-lg font-bold">
                                Class Changes <Fa
                                    class="collapsible-caret"
                                    icon={faCaretDown}
                                />
                            </h3>
                            <ChangeTable
                                slot="body"
                                changes={classChange.changes}
                            />
                        </CollapsibleCard>
                    </div>
                {:else}
                    <p class="mb-4 italic">No changes in class definition.</p>
                {/if}

                <NestedChangeGroup
                    title="Attributes"
                    items={classChange.attributes}
                />
                <NestedChangeGroup
                    title="Association Pairs"
                    items={classChange.associationPairs}
                />
                <NestedChangeGroup
                    title="Enum Entries"
                    items={classChange.enumEntries}
                />
            </div>
        </CollapsibleCard>
    </div>
{/each}
