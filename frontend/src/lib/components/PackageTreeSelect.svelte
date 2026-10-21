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
        describeSize,
        groupState,
        hasOwnRow,
        isSchema,
        nest,
        ownRowId,
        PackageScope,
        toggledGroup,
        toggledSchema,
    } from "$lib/utils/packageTree.svelte.js";

    import PackageTreeSelect from "./PackageTreeSelect.svelte";

    let {
        packages = [],
        selectedIds = $bindable(new Set()),
        nodes = null,
        depth = 0,
    } = $props();

    let expanded = $state(new Set());

    const shown = $derived(nodes ?? nest(packages));

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
        <li>
            <div
                class="hover:bg-background-subtle flex items-center gap-1 rounded py-0.5"
                style="padding-left: {depth * 14}px"
            >
                {#if schema || node.children.length === 0}
                    <span class="w-4 shrink-0"></span>
                {:else}
                    <button
                        type="button"
                        class="text-text-subtle w-4 shrink-0 text-xs"
                        aria-label={expanded.has(node.id)
                            ? "Collapse"
                            : "Expand"}
                        onclick={() => toggleExpanded(node)}
                    >
                        {expanded.has(node.id) ? "▾" : "▸"}
                    </button>
                {/if}

                <label class="flex min-w-0 flex-1 items-center gap-2">
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

                    {#if schema}
                        <span class="text-text-subtle ml-auto shrink-0 text-xs">
                            {describeSize(node)}
                        </span>
                    {:else}
                        <span class="text-text-subtle ml-auto shrink-0 text-xs">
                            picks what is inside
                        </span>
                    {/if}
                </label>
            </div>

            {#if !schema && expanded.has(node.id)}
                {#if hasOwnRow(node)}
                    <div
                        class="hover:bg-background-subtle flex items-center gap-1 rounded py-0.5"
                        style="padding-left: {(depth + 1) * 14}px"
                    >
                        <span class="w-4 shrink-0"></span>
                        <label class="flex min-w-0 flex-1 items-center gap-2">
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
                            <span
                                class="text-text-subtle ml-auto shrink-0 text-xs"
                            >
                                {describeSize(node, PackageScope.PACKAGE_ONLY)}
                            </span>
                        </label>
                    </div>
                {/if}
                <PackageTreeSelect
                    {packages}
                    bind:selectedIds
                    nodes={node.children}
                    depth={depth + 1}
                />
            {/if}
        </li>
    {/each}
</ul>
