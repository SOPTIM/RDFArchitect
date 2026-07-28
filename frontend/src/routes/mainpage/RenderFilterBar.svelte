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
        faAnglesUp,
        faArrowUpRightFromSquare,
        faCaretDown,
        faDiagramProject,
        faExpand,
        faFilter,
        faLayerGroup,
        faLink,
        faListUl,
        faPalette,
        faRotateLeft,
        faSitemap,
        faTag,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import { DropdownMenu } from "$lib/components/bitsui/dropdown/index";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { renderOptions } from "$lib/renderOptions.svelte.js";
    import { DiagramType } from "$lib/sharedState.svelte.js";

    let {
        diagramType,
        onResetView = () => {},
        onResetLayout = () => {},
        showResetLayout = false,
    } = $props();

    const ALL_TOGGLES = [
        {
            key: "includePropertiesFromOtherProfiles",
            label: "Other profiles",
            icon: faLayerGroup,
            appliesTo: [DiagramType.PACKAGE],
        },
        {
            key: "useColoredPropertiesInMergedView",
            label: "Colored by profile",
            icon: faPalette,
            appliesTo: [DiagramType.PACKAGE, DiagramType.CROSS_PROFILE],
        },
        {
            key: "showInheritedProperties",
            label: "Inherited properties",
            icon: faAnglesUp,
            appliesTo: [DiagramType.PACKAGE, DiagramType.CROSS_PROFILE],
        },
        {
            key: "includeAttributes",
            label: "Attributes",
            icon: faTag,
            appliesTo: [DiagramType.PACKAGE],
        },
        {
            key: "includeAssociations",
            label: "Associations",
            icon: faLink,
            appliesTo: [DiagramType.PACKAGE],
        },
        {
            key: "includeEnumEntries",
            label: "Enum entries",
            icon: faListUl,
            appliesTo: [DiagramType.PACKAGE],
        },
        {
            key: "includeInheritance",
            label: "Inheritance",
            icon: faSitemap,
            appliesTo: [DiagramType.PACKAGE],
        },
        {
            key: "includeRelationsToExternalPackages",
            label: "External relations",
            icon: faArrowUpRightFromSquare,
            appliesTo: [DiagramType.PACKAGE],
        },
    ];

    const toggles = $derived(
        ALL_TOGGLES.filter(toggle => toggle.appliesTo.includes(diagramType)),
    );
</script>

<div
    class="bg-window-background/90 border-border flex items-center gap-1 border-b px-2 py-1 backdrop-blur-sm"
>
    {#if toggles.length > 0}
        <DropdownMenu.Root>
            <DropdownMenu.Trigger>
                <button
                    type="button"
                    class="border-border text-blue group-hover:text-white group-data-[state=open]:text-white flex h-8 items-center gap-2 rounded border px-3 text-sm hover:cursor-pointer"
                >
                    <Fa icon={faFilter} />
                    Filters
                    <Fa icon={faCaretDown} />
                </button>
            </DropdownMenu.Trigger>
            <DropdownMenu.Content>
                {#each toggles as toggle (toggle.key)}
                    <DropdownMenu.Item.CheckBox
                        bind:value={
                            () => renderOptions.get(toggle.key),
                            v => renderOptions.set(toggle.key, v)
                        }
                    >
                        <span class="flex items-center gap-2">
                            <Fa icon={toggle.icon} />
                            {toggle.label}
                        </span>
                    </DropdownMenu.Item.CheckBox>
                {/each}
            </DropdownMenu.Content>
        </DropdownMenu.Root>

        {#if renderOptions.hasOverrides}
            <div class="h-8 w-8">
                <ButtonControl
                    variant="default"
                    title="Reset filters to defaults"
                    callOnClick={() => renderOptions.reset()}
                >
                    <Fa icon={faRotateLeft} />
                </ButtonControl>
            </div>
        {/if}

        <div class="bg-border mx-1 h-5 w-px"></div>
    {/if}

    <div class="h-8 w-8">
        <ButtonControl
            variant="default"
            title="Reset view"
            callOnClick={onResetView}
        >
            <Fa icon={faExpand} />
        </ButtonControl>
    </div>

    {#if showResetLayout}
        <div class="h-8 w-8">
            <ButtonControl
                variant="default"
                title="Reset layout"
                callOnClick={onResetLayout}
            >
                <Fa icon={faDiagramProject} />
            </ButtonControl>
        </div>
    {/if}
</div>
