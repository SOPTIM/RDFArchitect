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
    import TreeChevron from "$lib/components/TreeChevron.svelte";
    import { homeOf, toggled, WORKSPACE } from "$lib/utils/diagramSelection.js";
    import {
        describeSize,
        groupState,
        hasOwnRow,
        isSchema,
        nest,
        ownRowId,
        PackageScope,
        toggledGroup,
        toggledSchema,
    } from "$lib/utils/packageTree.js";

    import PackageTreeSelect from "./PackageTreeSelect.svelte";

    let {
        packages = [],
        selectedIds = $bindable(new Set()),
        diagramsByPackage = new Map(),
        diagramGroups = new Map(),
        orderedRowIds = [],
        deselectedDiagramIds = $bindable(new Set()),
        /**
         * Which packages are opened. Held by the caller rather than here, so that it survives the
         * tree being taken off screen and put back — a step of the import away and back.
         */
        expanded = $bindable(new Set()),
        nodes = null,
        depth = 0,
    } = $props();

    const shown = $derived(nodes ?? nest(packages));

    function diagramsOf(node) {
        return diagramsByPackage.get(node.id) ?? [];
    }

    /**
     * Whether the row opens. A schema is not drilled into — a part of it cannot be picked — but it
     * still opens where it carries diagrams, which are the one thing within it there is to choose.
     */
    function isExpandable(node) {
        return (
            diagramsOf(node).length > 0 ||
            (!isSchema(node) && node.children.length > 0)
        );
    }

    /** Opens a package, or closes it. */
    function toggleExpanded(node) {
        const next = new Set(expanded);
        if (next.has(node.id)) {
            next.delete(node.id);
        } else {
            next.add(node.id);
        }
        expanded = next;
    }
</script>

<ul class="flex flex-col">
    {#each shown as node (node.id)}
        {@const schema = isSchema(node)}
        {@const state = schema ? null : groupState(node, selectedIds)}
        {@const diagrams = diagramsOf(node)}
        {@const open = expanded.has(node.id)}
        <li>
            <!-- Not selectable: a double click on a row means to open it, not to take its text. -->
            <div
                class="hover:bg-background-subtle flex items-center gap-1 rounded py-0.5 select-none"
                style="padding-left: {depth * 14}px"
            >
                {#if isExpandable(node)}
                    <button
                        type="button"
                        class="-m-1 shrink-0 cursor-pointer p-1"
                        aria-label={open ? "Collapse" : "Expand"}
                        aria-expanded={open}
                        onclick={() => toggleExpanded(node)}
                    >
                        <TreeChevron expanded={open} />
                    </button>
                {:else}
                    <TreeChevron placeholder />
                {/if}

                <label
                    class="flex min-w-0 flex-1 cursor-pointer items-center gap-2"
                >
                    <input
                        type="checkbox"
                        class="shrink-0"
                        checked={schema
                            ? selectedIds.has(node.id)
                            : state === "all"}
                        indeterminate={!schema && state === "some"}
                        onchange={() =>
                            (selectedIds = schema
                                ? toggledSchema(node.id, selectedIds)
                                : toggledGroup(node, selectedIds))}
                    />
                    <span class="truncate text-sm">{node.name}</span>

                    {#if node.profile}
                        <span
                            class="border-border text-text-subtle shrink-0 rounded border px-1 text-[10px]"
                            title={node.namespaceCandidates?.join("\n")}
                        >
                            profile
                        </span>
                    {/if}

                    <!-- A tag says what the row is; what it holds is counted rather than
                         tagged, so that the numbers read as one line of their own. -->
                    {#if diagrams.length > 0 || schema}
                        <span class="text-text-subtle shrink-0 text-xs">
                            {#if diagrams.length > 0}
                                {diagrams.length} diagram{diagrams.length === 1
                                    ? ""
                                    : "s"}{schema ? " · " : ""}
                            {/if}
                            {#if schema}
                                {describeSize(node)}
                            {/if}
                        </span>
                    {/if}
                </label>
            </div>

            {#if open}
                {#if !schema && hasOwnRow(node)}
                    <div
                        class="hover:bg-background-subtle flex items-center gap-1 rounded py-0.5 select-none"
                        style="padding-left: {(depth + 1) * 14}px"
                    >
                        <TreeChevron placeholder />
                        <label
                            class="flex min-w-0 flex-1 cursor-pointer items-center gap-2"
                        >
                            <input
                                type="checkbox"
                                class="shrink-0"
                                checked={selectedIds.has(ownRowId(node.id))}
                                onchange={() =>
                                    (selectedIds = toggledSchema(
                                        ownRowId(node.id),
                                        selectedIds,
                                    ))}
                            />
                            <span
                                class="text-text-subtle truncate text-sm italic"
                            >
                                only {node.name} itself
                            </span>
                            <span class="text-text-subtle shrink-0 text-xs">
                                {describeSize(node, PackageScope.PACKAGE_ONLY)}
                            </span>
                        </label>
                    </div>
                {/if}

                {#each diagrams as diagram (diagram.id)}
                    {@const home = homeOf(
                        diagram,
                        diagramGroups,
                        orderedRowIds,
                    )}
                    {@const available = home !== null}
                    <div
                        class="hover:bg-background-subtle flex items-center gap-1 rounded py-0.5 select-none"
                        class:opacity-50={!available}
                        style="padding-left: {(depth + 1) * 14}px"
                        title={available
                            ? undefined
                            : "Draws classes outside the selected packages"}
                    >
                        <TreeChevron placeholder />
                        <label
                            class="flex min-w-0 flex-1 items-center gap-2"
                            class:cursor-pointer={available}
                        >
                            <input
                                type="checkbox"
                                class="shrink-0"
                                disabled={!available}
                                checked={available &&
                                    !deselectedDiagramIds.has(diagram.id)}
                                onchange={() =>
                                    (deselectedDiagramIds = toggled(
                                        diagram.id,
                                        deselectedDiagramIds,
                                    ))}
                            />
                            <span class="truncate text-sm">{diagram.name}</span>
                            <span
                                class="border-border text-text-subtle shrink-0 rounded border px-1 text-[10px]"
                                title={home === WORKSPACE
                                    ? "Drawn across several schemas, so it is stored in the workspace rather than in one of them"
                                    : "Stored in the schema that holds every class it draws"}
                            >
                                {home === WORKSPACE
                                    ? "workspace-diagram"
                                    : "schema-diagram"}
                            </span>
                            <span class="text-text-subtle shrink-0 text-xs">
                                {diagram.classes} classes
                            </span>
                        </label>
                    </div>
                {/each}

                {#if !schema}
                    <PackageTreeSelect
                        {packages}
                        bind:selectedIds
                        {diagramsByPackage}
                        {diagramGroups}
                        {orderedRowIds}
                        bind:deselectedDiagramIds
                        bind:expanded
                        nodes={node.children}
                        depth={depth + 1}
                    />
                {/if}
            {/if}
        </li>
    {/each}
</ul>
