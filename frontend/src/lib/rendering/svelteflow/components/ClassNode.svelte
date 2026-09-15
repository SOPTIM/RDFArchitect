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
    import { faLink } from "@fortawesome/free-solid-svg-icons";
    import { Handle, Position } from "@xyflow/svelte";
    import { Fa } from "svelte-fa";

    import { URI } from "$lib/models/dto/index.ts";
    import { renderOptions } from "$lib/renderOptions.svelte.js";
    import {
        editorState,
        isMergedDiagramType,
        multiSelectState,
        SelectionLevel,
    } from "$lib/sharedState.svelte.js";
    import { getPackageDisplayLabel } from "$lib/utils/package-label.js";

    let { id, data, dragging } = $props();

    const isMergedDiagram = $derived(
        isMergedDiagramType(editorState.selectedDiagram.getProperty("type")),
    );
    const selectionGraphUri = $derived(isMergedDiagram ? null : data.graphUri);

    const isInSelection = $derived(
        multiSelectState.isSelected(
            editorState.selectedWorkspace.getValue(),
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
    const showAssociations = $derived(
        renderOptions.get("showAssociationsInClass"),
    );
    const associations = $derived(
        showAssociations ? (data.associations ?? []) : [],
    );
    const enumEntries = $derived(data.enumEntries);
    const inheritedGroups = $derived(
        [...(data.superClasses ?? [])]
            .reverse()
            .map(superClass =>
                showAssociations
                    ? superClass
                    : { ...superClass, associations: [] },
            ),
    );

    const hasProfileInfo = $derived(
        collectGraphUris().some(graph => graph.graphUri),
    );

    const useProfileSections = $derived(
        isMergedDiagram ||
            (renderOptions.get("includePropertiesFromOtherProfiles") &&
                hasProfileInfo),
    );

    const profileSections = $derived(
        useProfileSections
            ? buildProfileSections(
                  isMergedDiagram
                      ? collectGraphUris()
                      : ownGraphUriFirst(collectGraphUris()),
              )
            : [],
    );

    /**
     * The column that carries the association icon is only reserved where a node
     * actually shows associations, so every other class keeps its plain look -
     * and where it is reserved, attribute and association text share one margin.
     */
    const showsAssociationLines = $derived(
        associations.length > 0 ||
            (renderOptions.get("showInheritedProperties") &&
                inheritedGroups.some(
                    superClass => (superClass.associations?.length ?? 0) > 0,
                )),
    );

    const plainItems = $derived([
        ...(renderOptions.get("showInheritedProperties")
            ? inheritedGroups.flatMap(superClass =>
                  superClassItems(superClass, false),
              )
            : []),
        ...propertyBlocks({ attributes, associations, enumEntries }, false),
    ]);

    const cursorClass = $derived(dragging ? "cursor-move" : "cursor-pointer");
    const isExternal = $derived(data.external === true);
    const isOutsidePackage = $derived(data.outsidePackage === true);

    function graphUriOf(prop) {
        return prop.graphUri ?? "";
    }

    function propertyText(property) {
        if (typeof property === "string") {
            return property;
        }
        if (!property.type) {
            return property.label;
        }
        const typed = `${property.type} \u00a0[${property.multiplicity}]`;
        return property.label ? `${property.label}: ${typed}` : typed;
    }

    function propertyColor(property) {
        return renderOptions.get("useColoredPropertiesInMergedView") &&
            property.color
            ? `color: ${property.color};`
            : "";
    }

    function propsForGraph(props, graphUri) {
        return (props ?? []).filter(prop => graphUriOf(prop) === graphUri);
    }

    function collectGraphUris() {
        const inheritedProps = inheritedGroups.flatMap(superClass => [
            ...(superClass.attributes ?? []),
            ...(superClass.associations ?? []),
            ...(superClass.enumEntries ?? []),
        ]);
        const allProps = [
            ...(attributes ?? []),
            ...associations,
            ...(enumEntries ?? []),
            ...inheritedProps,
        ];

        const graphUriMap = new Map();
        for (const prop of allProps) {
            const graphUri = graphUriOf(prop);
            if (!graphUriMap.has(graphUri)) {
                graphUriMap.set(graphUri, prop.graphKeyword);
            }
        }

        return [...graphUriMap.entries()]
            .map(([graphUri, keyword]) => ({ graphUri, keyword }))
            .sort((a, b) => a.graphUri.localeCompare(b.graphUri));
    }

    function superGroupsForGraph(graphUri) {
        return inheritedGroups
            .map(superClass => ({
                label: superClass.label,
                attributes: propsForGraph(superClass.attributes, graphUri),
                associations: propsForGraph(superClass.associations, graphUri),
                enumEntries: propsForGraph(superClass.enumEntries, graphUri),
            }))
            .filter(
                group =>
                    group.attributes.length > 0 ||
                    group.associations.length > 0 ||
                    group.enumEntries.length > 0,
            );
    }

    function ownGraphUriFirst(graphs) {
        const own = data.graphUri;
        if (!graphs.some(graph => graph.graphUri === own)) {
            return graphs;
        }
        return [
            ...graphs.filter(graph => graph.graphUri === own),
            ...graphs.filter(graph => graph.graphUri !== own),
        ];
    }

    function buildProfileSections(orderedGraphs) {
        const showInherited = renderOptions.get("showInheritedProperties");

        return orderedGraphs
            .map(({ graphUri, keyword }) => ({
                graphUri,
                graphName: getGraphLabel(graphUri, keyword),
                superGroups: superGroupsForGraph(graphUri),
                ownAttributes: propsForGraph(attributes, graphUri),
                ownAssociations: propsForGraph(associations, graphUri),
                ownEnumEntries: propsForGraph(enumEntries, graphUri),
            }))
            .filter(
                section =>
                    section.ownAttributes.length > 0 ||
                    section.ownAssociations.length > 0 ||
                    section.ownEnumEntries.length > 0 ||
                    (showInherited && section.superGroups.length > 0),
            );
    }

    function propertyBlocks(source, faded) {
        return [
            { icon: null, properties: source.attributes },
            { icon: faLink, properties: source.associations },
            { icon: null, properties: source.enumEntries },
        ]
            .filter(block => (block.properties?.length ?? 0) > 0)
            .map(block => ({ kind: "lines", faded, ...block }));
    }

    function superClassItems(superClass, spaced) {
        const blocks = propertyBlocks(superClass, true);
        return [
            { kind: "superClass", label: superClass.label, spaced },
            ...(blocks.length > 0 ? blocks : [{ kind: "emptySuperClass" }]),
        ];
    }

    function sectionItems(section) {
        return [
            ...(renderOptions.get("showInheritedProperties")
                ? section.superGroups.flatMap(superClass =>
                      superClassItems(superClass, true),
                  )
                : []),
            ...propertyBlocks(
                {
                    attributes: section.ownAttributes,
                    associations: section.ownAssociations,
                    enumEntries: section.ownEnumEntries,
                },
                false,
            ),
        ];
    }

    function getGraphLabel(graphURI, keyword) {
        if (keyword) {
            return keyword;
        }
        try {
            return new URI(graphURI).suffix;
        } catch {
            return graphURI;
        }
    }
</script>

{#snippet nodeItems(items)}
    {#each items as item}
        {#if item.kind === "superClass"}
            <div
                class="text-default-text flex flex-nowrap items-center justify-center gap-3 py-0.5 text-xs italic opacity-70"
                class:mt-1={item.spaced}
            >
                <span class="w-3 rounded border-t border-current"></span>
                <span class="relative -top-px leading-none">{item.label}</span>
                <span class="w-3 rounded border-t border-current"></span>
            </div>
        {:else if item.kind === "emptySuperClass"}
            <div
                class="text-default-text flex items-center justify-center leading-6 opacity-70"
            >
                <span
                    class="mt-1.5 mb-2 w-3 rounded border-t border-current"
                ></span>
            </div>
        {:else}
            {#each item.properties as property}
                <div
                    class="text-default-text flex items-baseline gap-1.5 leading-6"
                    class:opacity-70={item.faded}
                    style={propertyColor(property)}
                >
                    {#if showsAssociationLines}
                        <span class="w-3 shrink-0 text-[0.7em] opacity-70">
                            {#if item.icon}
                                <Fa icon={item.icon} />
                            {/if}
                        </span>
                    {/if}
                    <span>{propertyText(property)}</span>
                </div>
            {/each}
        {/if}
    {/each}
{/snippet}

<div
    class={`class-node-shell bg-class-node-upper-background relative isolate min-w-45 overflow-hidden rounded-md bg-clip-padding font-sans text-sm ${cursorClass} ${
        isExternal
            ? "class-node-external"
            : isOutsidePackage
              ? "class-node-outside-package"
              : ""
    } ${
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
        class="class-node-divider bg-class-node-lower-background p-2 text-left"
    >
        {#if isExternal}
            <div class="text-default-text text-xs italic opacity-70">
                not defined in this schema
            </div>
        {:else if useProfileSections}
            {#each profileSections as section (section.graphUri)}
                <div
                    class="text-default-text text-center text-xs italic opacity-70"
                >
                    {section.graphName}
                </div>
                {@render nodeItems(sectionItems(section))}
            {/each}
        {:else}
            {@render nodeItems(plainItems)}
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

    .class-node-external {
        --color-class-node-upper-background: rgba(224, 224, 224, 0.45);
        --color-class-node-lower-background: rgba(242, 242, 242, 0.4);
        --color-default-text: #5a5a5a;
    }

    .class-node-outside-package {
        --color-class-node-upper-background: #eeeeee;
        --color-class-node-lower-background: #fafafa;
        --color-default-text: #6a6a6a;
    }

    .class-node-external::after {
        box-shadow: none;
        border: 1px dashed var(--color-default-text);
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
