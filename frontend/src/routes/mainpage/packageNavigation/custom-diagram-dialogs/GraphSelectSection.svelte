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
    import {
        faFolder,
        faFolderOpen,
    } from "@fortawesome/free-regular-svg-icons";

    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";

    import { getUri } from "../packageNavigationUtils.svelte.js";
    import PackageSelectSection from "./PackageSelectSection.svelte";

    let { graph, packages, classesByPackage } = $props();

    let graphIcon = $derived(graph.showContents ? faFolderOpen : faFolder);
    const hasPackages = $derived(packages?.length > 0);

    $effect(() => {
        graph.selected = packages?.every(pack => pack.selected === true);
    });

    function toggleGraphContentsVisibility() {
        graph.expanded = !graph.expanded;
    }

    function togglePackagesInGraph() {
        const newGraphState = !graph.selected;
        graph.selected = newGraphState;
        packages.forEach(pack => {
            pack.selected = newGraphState;
            classesByPackage[pack.uuid]?.forEach(cls => {
                cls.selected = newGraphState;
            });
        });
    }
</script>

<div class="flex w-full flex-col items-stretch gap-[0.1rem]">
    <NavigationEntry
        level={1}
        label={graph.suffix}
        icon={graphIcon}
        title={getUri(graph)}
        selected={graph.selected}
        expanded={graph.expanded}
        hasChildren={hasPackages}
        onToggle={toggleGraphContentsVisibility}
        showCheckbox={hasPackages}
        onSelect={togglePackagesInGraph}
    />
    {#if graph.expanded && hasPackages}
        <div
            class="flex w-full flex-col items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each packages as pack}
                <PackageSelectSection
                    {pack}
                    classes={classesByPackage[pack.uuid]}
                    level={2}
                />
            {/each}
        </div>
    {/if}
</div>
