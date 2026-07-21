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
    import { Handle, Position } from "@xyflow/svelte";

    import { URI } from "$lib/models/dto/index.ts";
    import {
        DiagramType,
        editorState,
        multiSelectState,
        SelectionLevel,
    } from "$lib/sharedState.svelte.js";
    import { userSettings } from "$lib/userSettings.svelte.js";
    import { getPackageDisplayLabel } from "$lib/utils/package-label.js";

    let { id, data, dragging } = $props();

    const isCrossProfileDiagram = $derived(
        editorState.selectedDiagram.getProperty("type") ===
            DiagramType.CROSS_PROFILE,
    );
    const selectionGraphUri = $derived(
        isCrossProfileDiagram ? null : data.graphUri,
    );

    const isInSelection = $derived(
        multiSelectState.isSelected(
            editorState.selectedDataset.getValue(),
            selectionGraphUri,
            id,
        ),
    );
    const isOpenClass = $derived(
        editorState.selectedClass.getProperty("id") === id &&
            editorState.selectedClassGraph.getValue() === selectionGraphUri,
    );
    const isActiveLevel = $derived(
        editorState.activeSelectionKind.getValue() === SelectionLevel.CLASS,
    );
    const highlightState = $derived(
        isInSelection || (isOpenClass && isActiveLevel)
            ? "active"
            : isOpenClass
              ? "secondary"
              : null,
    );

    const label = $derived(data.label);
    const stereotypes = $derived(data.stereotypes);
    const attributes = $derived(data.attributes);
    const enumEntries = $derived(data.enumEntries);
    const inheritedGroups = $derived([...(data.superClasses ?? [])].reverse());

    const crossProfileSections = $derived(
        isCrossProfileDiagram ? buildCrossProfileSections() : [],
    );

    const cursorClass = $derived(dragging ? "cursor-move" : "cursor-pointer");

    function graphUriOf(prop) {
        return prop.graphUri ?? "";
    }

    function propsForGraph(props, graphUri) {
        return (props ?? []).filter(prop => graphUriOf(prop) === graphUri);
    }

    function collectGraphUris() {
        const inheritedProps = inheritedGroups.flatMap(superClass => [
            ...(superClass.attributes ?? []),
            ...(superClass.enumEntries ?? []),
        ]);
        const allProps = [
            ...(attributes ?? []),
            ...(enumEntries ?? []),
            ...inheritedProps,
        ];
        return [...new Set(allProps.map(graphUriOf))].sort();
    }

    function superGroupsForGraph(graphUri) {
        return inheritedGroups
            .map(superClass => ({
                label: superClass.label,
                attributes: propsForGraph(superClass.attributes, graphUri),
                enumEntries: propsForGraph(superClass.enumEntries, graphUri),
            }))
            .filter(
                group =>
                    group.attributes.length > 0 || group.enumEntries.length > 0,
            );
    }

    function buildCrossProfileSections() {
        const showInherited = userSettings.get("showInheritedProperties", true);

        return collectGraphUris()
            .map(graphUri => ({
                graphUri,
                graphName: getGraphLabel(graphUri),
                superGroups: superGroupsForGraph(graphUri),
                ownAttributes: propsForGraph(attributes, graphUri),
                ownEnumEntries: propsForGraph(enumEntries, graphUri),
            }))
            .filter(
                section =>
                    section.ownAttributes.length > 0 ||
                    section.ownEnumEntries.length > 0 ||
                    (showInherited && section.superGroups.length > 0),
            );
    }

    function getGraphLabel(graphURI) {
        try {
            return new URI(graphURI).suffix;
        } catch {
            return graphURI;
        }
    }
</script>

<div
    class={`class-node-shell bg-class-node-upper-background relative isolate min-w-45 overflow-hidden rounded-md bg-clip-padding font-sans text-sm ${cursorClass} ${
        highlightState === "active"
            ? "class-node-highlighted"
            : highlightState === "secondary"
              ? "class-node-highlighted-secondary"
              : ""
    }`}
    role="button"
    tabindex="0"
>
    <Handle
        class="absolute top-0 left-0 h-full w-full transform-none rounded-none border-none opacity-0"
        position={Position.Right}
        style="z-index: 1;"
        isConnectableStart={false}
    />

    <div
        class="p-2 text-center"
        style="box-shadow: inset 0 -1px 0 var(--color-default-text);"
    >
        {#if stereotypes.length > 0}
            <div class="flex flex-col gap-0.5">
                {#each stereotypes as stereotype}
                    <div class="text-default-text text-xs">
                        &laquo;{stereotype}&raquo;
                    </div>
                {/each}
            </div>
        {/if}

        {#if data.belongsToCategory}
            <div class="text-default-text mb-0.5 text-sm italic">
                {getPackageDisplayLabel(data.belongsToCategory)} ::
            </div>
        {/if}

        <span class="text-default-text mt-1 font-bold">{label}</span>
    </div>
    <div
        class="class-node-divider bg-class-node-lower-background p-2 text-center"
    >
        {#if isCrossProfileDiagram}
            {#each crossProfileSections as section (section.graphUri)}
                <div class="text-default-text text-xs italic opacity-70">
                    {section.graphName}
                </div>
                {#if userSettings.get("showInheritedProperties", true)}
                    {#each section.superGroups as superClass}
                        <div
                            class="text-default-text text-xs italic opacity-70 flex flex-nowrap gap-3 items-center justify-center mt-1 py-0.5"
                        >
                            <span
                                class="w-3 border-t border-current rounded"
                            ></span>
                            <span class="leading-none relative -top-px">
                                {superClass.label}
                            </span>
                            <span
                                class="w-3 border-t border-current rounded"
                            ></span>
                        </div>
                        {#each superClass.attributes as attr}
                            <div
                                class="text-default-text leading-6 opacity-70"
                                style={userSettings.get(
                                    "useColoredPropertiesInMergedView",
                                ) && attr.color
                                    ? `color: ${attr.color};`
                                    : ""}
                            >
                                {attr.label}: {attr.type} &nbsp;[{attr.multiplicity}]
                            </div>
                        {/each}
                        {#each superClass.enumEntries as enumEntry}
                            <div
                                class="text-default-text leading-6 opacity-70"
                                style={userSettings.get(
                                    "useColoredPropertiesInMergedView",
                                ) && enumEntry.color
                                    ? `color: ${enumEntry.color};`
                                    : ""}
                            >
                                {enumEntry.label ?? enumEntry}
                            </div>
                        {/each}
                    {/each}
                {/if}
                {#each section.ownAttributes as attr}
                    <div
                        class="text-default-text leading-6"
                        style={userSettings.get(
                            "useColoredPropertiesInMergedView",
                        ) && attr.color
                            ? `color: ${attr.color};`
                            : ""}
                    >
                        {attr.label}: {attr.type} &nbsp;[{attr.multiplicity}]
                    </div>
                {/each}
                {#each section.ownEnumEntries as enumEntry}
                    <div
                        class="text-default-text leading-6"
                        style={userSettings.get(
                            "useColoredPropertiesInMergedView",
                        ) && enumEntry.color
                            ? `color: ${enumEntry.color};`
                            : ""}
                    >
                        {enumEntry.label ?? enumEntry}
                    </div>
                {/each}
            {/each}
        {:else}
            {#if userSettings.get("showInheritedProperties", true) && inheritedGroups.length > 0}
                {#each inheritedGroups as superClass}
                    <div
                        class="text-default-text text-xs italic opacity-70 flex flex-nowrap gap-3 items-center justify-center py-0.5"
                    >
                        <span
                            class="w-3 border-t border-current rounded"
                        ></span>
                        <span class="leading-none relative -top-px">
                            {superClass.label}
                        </span>
                        <span
                            class="w-3 border-t border-current rounded"
                        ></span>
                    </div>
                    {#each superClass.attributes ?? [] as attr}
                        <div class="text-default-text leading-6 opacity-70">
                            {attr.label}: {attr.type} &nbsp;[{attr.multiplicity}]
                        </div>
                    {/each}
                    {#each superClass.enumEntries ?? [] as enumEntry}
                        <div class="text-default-text leading-6 opacity-70">
                            {enumEntry.label ?? enumEntry}
                        </div>
                    {/each}
                    {#if (superClass.attributes?.length ?? 0) === 0 && (superClass.enumEntries?.length ?? 0) === 0}
                        <div
                            class="text-default-text leading-6 opacity-70 flex justify-center items-center"
                        >
                            <span
                                class="w-3 border-t border-current rounded mt-1.5 mb-2"
                            ></span>
                        </div>
                    {/if}
                {/each}
            {/if}
            {#if attributes && attributes.length > 0}
                {#each attributes as attr}
                    <div class="text-default-text leading-6">
                        {attr.label}: {attr.type} &nbsp;[{attr.multiplicity}]
                    </div>
                {/each}
            {:else if enumEntries && enumEntries.length > 0}
                {#each enumEntries as enumEntry}
                    <div class="text-default-text leading-6">
                        {enumEntry.label ?? enumEntry}
                    </div>
                {/each}
            {/if}
        {/if}
    </div>
</div>

<style>
    .class-node-shell::after {
        content: "";
        position: absolute;
        inset: 0;
        border-radius: inherit;
        box-shadow: inset 0 0 0 1px var(--color-default-text);
        pointer-events: none;
        z-index: 2;
    }

    .class-node-highlighted::after {
        box-shadow: inset 0 0 0 3px var(--color-class-node-highlighted);
    }

    .class-node-highlighted-secondary::after {
        box-shadow: inset 0 0 0 3px
            var(--color-class-node-highlighted-secondary);
    }

    .class-node-divider {
        box-shadow: inset 0 -1px 0 var(--color-default-text);
    }
</style>
