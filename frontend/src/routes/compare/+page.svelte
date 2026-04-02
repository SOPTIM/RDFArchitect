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
    import { Pane, Splitpanes } from "svelte-splitpanes";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { compareState } from "$lib/sharedState.svelte.js";

    import PackageView from "./PackageView.svelte";

    import { goto } from "$app/navigation";

    let changeList = $state(null);
    let selectedPackage = $state(null);

    $effect(() => {
        compareState.changeList.subscribe();
        changeList = compareState.changeList.getValue();
        selectedPackage = null;
    });

    function getPackageLabel(pack) {
        if (pack.label === "default") return "default";
        return pack.label.replace("Package_", "");
    }

    function getPackageChangeType(pack) {
        const RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
        const typeChange = pack.changes?.[RDF_TYPE];

        if (typeChange) {
            if (typeChange.from === null && typeChange.to !== null) {
                return "addition";
            } else if (typeChange.from !== null && typeChange.to === null) {
                return "deletion";
            }
        } else {
            return "modification";
        }
    }

    function getPackageClasses(pack) {
        const baseClasses =
            "hover:bg-opacity-80 text-nowrap px-2 text-left hover:cursor-pointer transition-colors border-l-2 ";
        if (pack.external) {
            return `${baseClasses}`;
        }
        const changeType = getPackageChangeType(pack);

        if (pack === selectedPackage) {
            switch (changeType) {
                case "addition":
                    return `${baseClasses} bg-green-hover-background text-green-text font-bold border-l-green-border`;
                case "deletion":
                    return `${baseClasses} bg-red-hover-background text-red-text font-bold border-l-red-border`;
                case "modification":
                    return `${baseClasses} bg-window-background text-default-text font-bold border-l-default-text`;
                default:
                    return `${baseClasses} bg-default-background text-default-text font-bold  border-l-default-text`;
            }
        } else {
            switch (changeType) {
                case "addition":
                    return `${baseClasses} bg-green-background hover:bg-green-hover-background border-l-green-border`;
                case "deletion":
                    return `${baseClasses} bg-red-background hover:bg-red-hover-background border-l-red-border`;
                case "modification":
                    return `${baseClasses} bg-window-background hover:bg-default-background border-l-default-text`;
                default:
                    return `${baseClasses} hover:bg-window-background border-l-default-text`;
            }
        }
    }
</script>

<Splitpanes theme="dark-theme" class="flex h-full">
    <Pane size={18} maxSize={30} class="bg-window-background">
        <div
            class="no-scrollbar flex h-full flex-1 flex-col space-y-[1px] overflow-y-scroll text-lg"
        >
            {#if changeList}
                {#each changeList as pack}
                    <button
                        class={getPackageClasses(pack)}
                        onclick={() => {
                            selectedPackage = pack;
                        }}
                    >
                        {#if pack.external}
                            <strong>[external]</strong>
                        {/if}
                        {getPackageLabel(pack)}
                    </button>
                {/each}
            {:else}
                <div class="text-default-text/70 p-4 text-sm">
                    Use the menu to start a new comparison.
                </div>
            {/if}
        </div>
    </Pane>
    <Pane size={82} class="bg-window-background pb-18">
        <div
            class="flex h-full flex-1 flex-col space-y-8 overflow-y-scroll p-6"
        >
            {#if selectedPackage}
                <PackageView data={selectedPackage} />
            {/if}
            {#if changeList?.length === 0}
                <div class="bg-window-background rounded-xl border p-6 shadow">
                    <p class="text-default-text italic">No changes in graph.</p>
                </div>
            {/if}
        </div>
    </Pane>
</Splitpanes>

{#if changeList && changeList.length > 0}
    <div class="fixed right-5 bottom-4 w-40 shadow-lg">
        <ButtonControl
            callOnClick={() => goto("/migrate")}
            variant="default"
            title="Start Migration"
        >
            Start Migration
        </ButtonControl>
    </div>
{/if}
