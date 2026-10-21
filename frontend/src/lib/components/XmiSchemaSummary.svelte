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
    import { toggled, WORKSPACE } from "$lib/utils/diagramSelection.js";
    import {
        classNamesOf,
        declaredOf,
        dependenciesOf,
        dependencyNamesOf,
        PackageScope,
    } from "$lib/utils/packageTree.js";
    import { plural } from "$lib/utils/plural.js";

    let {
        /** The selection worked out, as {@link importChoiceOf} reports it. */
        choice,
        /** The pool of class names the read reported. */
        classNames = [],
        /**
         * Which rows are opened. Held by the caller rather than here, so that it survives the
         * list being taken off screen and put back.
         */
        openedRows = $bindable(new Set()),
    } = $props();

    // Every line looks the same and a line's contents are a list within it, so the indentation
    // comes from the nesting rather than from each line being told how deep it sits. The margin
    // on a nested list is half a chevron, which puts its guide line under the chevron above it.
    const LINE = "flex w-full items-center gap-1 py-0.5 text-left";
    // Not selectable, a double click on one meaning to open it rather than to take its text. The
    // lines below are: a class name is worth copying.
    const OPENS = `hover:bg-background-subtle cursor-pointer justify-start rounded select-none ${LINE}`;
    const NESTED = "border-border/40 ml-1.5 border-l pl-2";

    /** How a schema reads here, an own-classes row saying so. */
    function schemaLabel(row) {
        return row.scope === PackageScope.PACKAGE_ONLY
            ? `${row.node.name} (itself only)`
            : row.node.name;
    }
</script>

<!-- A line that opens. The app centres a button that states no justification of its own, which
     a line of a list is not, so this one states one. -->
{#snippet opener(key, label)}
    <button
        type="button"
        class={OPENS}
        aria-expanded={openedRows.has(key)}
        onclick={() => (openedRows = toggled(key, openedRows))}
    >
        <TreeChevron expanded={openedRows.has(key)} />
        <span class="truncate">{label}</span>
    </button>
{/snippet}

<!-- A line that opens nothing. It keeps the room a chevron takes, which is what lines it up with
     the lines around it. -->
{#snippet line(label)}
    <li class="text-text-subtle {LINE}">
        <TreeChevron placeholder />
        <span class="truncate">{label}</span>
    </li>
{/snippet}

{#snippet listing(key, label, names, apart = "")}
    <li class={apart}>
        {@render opener(key, label)}
        {#if openedRows.has(key)}
            <ul class={NESTED}>
                {#each names as name, at (at)}
                    {@render line(name)}
                {/each}
            </ul>
        {/if}
    </li>
{/snippet}

<div class="flex h-full min-h-0 flex-col">
    <p class="border-border border-b px-3 py-2 text-xs font-semibold">
        Imported profiles
    </p>

    <div class="min-h-0 flex-1 overflow-y-auto px-3 py-2 text-xs">
        {#if choice.schemas.length === 0}
            <p class="text-font-secondary">Nothing picked yet.</p>
        {:else}
            <ul>
                {#each choice.schemas as row (row.id)}
                    {@const diagrams = choice.homes.byRow.get(row.id) ?? []}
                    <li>
                        {@render opener(row.id, schemaLabel(row))}
                        {#if openedRows.has(row.id)}
                            <ul class={NESTED}>
                                {@render listing(
                                    `${row.id}::diagrams`,
                                    plural(diagrams.length, "diagram"),
                                    diagrams.map(diagram => diagram.name),
                                )}
                                {@render listing(
                                    `${row.id}::classes`,
                                    plural(
                                        declaredOf(row.node, row.scope),
                                        "class",
                                        "classes",
                                    ),
                                    classNamesOf(
                                        row.node,
                                        row.scope,
                                        classNames,
                                    ),
                                )}
                                {@render listing(
                                    `${row.id}::dependencies`,
                                    plural(
                                        dependenciesOf(row.node, row.scope),
                                        "dependency",
                                        "dependencies",
                                    ),
                                    dependencyNamesOf(
                                        row.node,
                                        row.scope,
                                        classNames,
                                    ),
                                )}
                            </ul>
                        {/if}
                    </li>
                {/each}

                <!-- The workspace is no schema and holds nothing but these, so they are named
                     where a schema would name its own. -->
                {#if choice.homes.workspace.length > 0}
                    {@render listing(
                        WORKSPACE,
                        plural(
                            choice.homes.workspace.length,
                            "workspace diagram",
                        ),
                        choice.homes.workspace.map(diagram => diagram.name),
                        "border-border mt-1 border-t pt-1",
                    )}
                {/if}
            </ul>
        {/if}
    </div>

    <p class="border-border text-text-subtle border-t px-3 py-2 text-xs">
        {plural(choice.schemas.length, "schema")},
        {plural(choice.classes, "class definition")},
        {plural(choice.diagramIds.length, "diagram")}
    </p>
</div>
