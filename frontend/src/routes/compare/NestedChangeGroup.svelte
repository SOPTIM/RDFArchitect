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

    let { items, title } = $props();
</script>

{#if items && items.length > 0}
    <div class="border-default-text mb-4 ml-6 border-l-4 pl-6">
        <CollapsibleCard>
            <h3 slot="header" class="text-default-text text-lg font-semibold">
                {title}
                <Fa class="collapsible-caret" icon={faCaretDown} />
            </h3>
            <div slot="body">
                {#each items as item}
                    <div class="mb-3">
                        <CollapsibleCard>
                            <p
                                slot="header"
                                class="text-md mt-4 mb-2 font-semibold"
                            >
                                {item.uri}
                                <Fa
                                    class="collapsible-caret"
                                    icon={faCaretDown}
                                />
                            </p>
                            <ChangeTable slot="body" changes={item.changes} />
                        </CollapsibleCard>
                    </div>
                {/each}
            </div>
        </CollapsibleCard>
    </div>
{/if}

<style>
    :global(.card .card-header .collapsible-caret) {
        display: inline-block;
        transition: transform 0.2s ease;
    }
    :global(.card:not(.open) .card-header .collapsible-caret) {
        transform: rotate(-90deg);
    }
</style>
