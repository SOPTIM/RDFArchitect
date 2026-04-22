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
    import { faCube } from "@fortawesome/free-solid-svg-icons";

    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";

    let { pack, classes, level = 1 } = $props();

    let packageIcon = $derived(pack.showContents ? faFolderOpen : faFolder);
    const hasClasses = $derived(classes?.length > 0);

    $effect(() => {
        pack.selected = classes?.every(cls => cls.selected === true);
    });

    function togglePackageContentsVisibility() {
        pack.expanded = !pack.expanded;
    }

    function toggleClassesInPackage() {
        const newPackState = !pack.selected;
        pack.selected = newPackState;
        classes.forEach(cls => {
            cls.selected = newPackState;
        });
    }

    function toggleSelectClass(cls) {
        cls.selected = !cls.selected;
    }
</script>

<div class="flex w-full flex-col items-stretch gap-[0.1rem]">
    <NavigationEntry
        {level}
        label={pack.label}
        icon={packageIcon}
        title={pack.label}
        selected={pack.selected}
        expanded={pack.expanded}
        hasChildren={hasClasses}
        onToggle={togglePackageContentsVisibility}
        showCheckbox={hasClasses}
        onSelect={toggleClassesInPackage}
    />
    {#if pack.expanded && hasClasses}
        <div
            class="flex w-full flex-col items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each classes as cls}
                <NavigationEntry
                    level={2}
                    label={cls.label}
                    icon={faCube}
                    title={cls.label}
                    selected={cls.selected}
                    onSelect={() => toggleSelectClass(cls)}
                    showCheckbox={true}
                />
            {/each}
        </div>
    {/if}
</div>
