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
        faAngleDown,
        faAngleRight,
        faMagnifyingGlass,
        faWandMagicSparkles,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import TurtleEditor from "$lib/monaco/TurtleEditor.svelte";
    import {
        classRules,
        constraintRows,
        GENERATED,
        originsOf,
        ruleCount,
        SCOPES,
        summaryOf,
    } from "$lib/shacl/classConstraints.js";

    let { custom, generated, onopen = () => {} } = $props();

    let scope = $state("both");
    let filter = $state("");
    /** Which rows are open, by key. Only an open row mounts an editor. */
    let open = $state({});
    let showClassRules = $state(false);

    const rows = $derived(constraintRows({ custom, generated, scope, filter }));
    const allRows = $derived(constraintRows({ custom, generated, scope }));
    const nodeShapes = $derived(classRules({ custom, generated, scope }));

    /** "18 rules on 12 properties · generated + 2 documents" — the answer without any clicking. */
    const headline = $derived.by(() => {
        const rules = allRows.reduce((total, row) => total + ruleCount(row), 0);
        if (rules === 0) {
            return null;
        }
        const documents = new Set();
        let fromSchema = false;
        for (const row of allRows) {
            for (const origin of originsOf(row)) {
                if (origin.generated) {
                    fromSchema = true;
                } else {
                    documents.add(origin.label);
                }
            }
        }
        const sources = [];
        if (fromSchema) {
            sources.push("generated");
        }
        if (documents.size > 0) {
            sources.push(
                `${documents.size} document${documents.size === 1 ? "" : "s"}`,
            );
        }
        return `${rules} rule${rules === 1 ? "" : "s"} on ${allRows.length} propert${allRows.length === 1 ? "y" : "ies"}${
            sources.length > 0 ? ` · ${sources.join(" + ")}` : ""
        }`;
    });
</script>

<!--
  @component
  What is enforced on one class, one row per property.

  Read-only: rules arrive merged from every enabled document, and a merged rule cannot be written
  back — see the workbench. The provenance chip on each row is the way back to the file it really
  lives in.
-->

<div class="flex h-full min-h-0 flex-col">
    <div class="flex shrink-0 flex-wrap items-center gap-2 pb-2">
        <div class="border-border flex overflow-hidden rounded border">
            {#each SCOPES as option (option.id)}
                <button
                    class="cursor-pointer px-3 py-1 text-sm {scope === option.id
                        ? 'bg-background-select text-nav-active-text font-semibold'
                        : 'text-text-subtle hover:text-default-text'}"
                    onclick={() => (scope = option.id)}
                >
                    {option.label}
                </button>
            {/each}
        </div>
        <div class="relative min-w-40 flex-1">
            <span
                class="text-text-subtle pointer-events-none absolute top-1/2 left-2 -translate-y-1/2 text-xs"
            >
                <Fa icon={faMagnifyingGlass} />
            </span>
            <input
                class="border-border focus:border-blue text-default-text w-full rounded border bg-transparent py-1 pr-2 pl-7 text-sm outline-none"
                type="text"
                aria-label="Filter constraints"
                placeholder="Filter by property or rule…"
                bind:value={filter}
            />
        </div>
    </div>

    {#if headline}
        <p class="text-text-subtle shrink-0 pb-2 text-xs">{headline}</p>
    {/if}

    <div class="min-h-0 flex-1 overflow-y-auto">
        {#if allRows.length === 0 && nodeShapes.length === 0}
            <p class="text-text-subtle py-4 text-sm italic">
                No constraints target this class.
            </p>
        {:else}
            {#if nodeShapes.length > 0}
                <div class="border-border mb-2 border-b pb-2">
                    <button
                        class="text-default-text w-fit cursor-pointer text-sm font-semibold hover:underline"
                        onclick={() => (showClassRules = !showClassRules)}
                    >
                        <Fa
                            icon={showClassRules ? faAngleDown : faAngleRight}
                        />
                        On the class ({nodeShapes.length})
                    </button>
                    {#if showClassRules}
                        {#each nodeShapes as shape (shape.side + shape.id)}
                            <div class="mt-1 ml-4">
                                <p
                                    class="text-text-subtle mb-1 font-mono text-xs break-all"
                                >
                                    {shape.side === GENERATED
                                        ? "generated"
                                        : (shape.origins?.[0]?.documentName ??
                                          "")}
                                </p>
                                <TurtleEditor
                                    autoGrow
                                    value={shape.triples}
                                    readOnly={true}
                                />
                            </div>
                        {/each}
                    {/if}
                </div>
            {/if}

            {#if rows.length === 0}
                <p class="text-text-subtle py-4 text-sm italic">
                    No property matches "{filter}".
                </p>
            {/if}

            <ul>
                {#each rows as row (row.key)}
                    {@const summaries = summaryOf(row)}
                    {@const origins = originsOf(row)}
                    <li class="border-border border-b last:border-b-0">
                        <div class="flex items-center gap-2 py-1.5">
                            <button
                                class="text-text-subtle hover:text-default-text w-4 shrink-0 cursor-pointer text-xs"
                                aria-label={open[row.key]
                                    ? `Collapse ${row.label}`
                                    : `Expand ${row.label}`}
                                onclick={() => (open[row.key] = !open[row.key])}
                            >
                                <Fa
                                    icon={open[row.key]
                                        ? faAngleDown
                                        : faAngleRight}
                                />
                            </button>
                            <span
                                class="text-default-text min-w-0 flex-1 truncate font-mono text-sm"
                                title={row.label}
                            >
                                {row.label}
                            </span>
                            <span class="text-text-subtle shrink-0 text-xs">
                                {#each summaries as summary, index (index)}
                                    {#if summary.side}
                                        <span class="opacity-70">
                                            {summary.side}:
                                        </span>
                                    {/if}
                                    {summary.text}{index < summaries.length - 1
                                        ? " · "
                                        : ""}
                                {/each}
                            </span>
                            <span class="flex shrink-0 gap-1">
                                {#each origins as origin (origin.label)}
                                    {#if origin.generated}
                                        <span
                                            class="text-text-subtle border-border flex items-center gap-1 rounded border px-1.5 py-0.5 text-xs"
                                            title="Derived from the schema itself"
                                        >
                                            <Fa icon={faWandMagicSparkles} />
                                            generated
                                        </span>
                                    {:else}
                                        <button
                                            class="text-blue border-border hover:bg-nav-hover-background cursor-pointer rounded border px-1.5 py-0.5 text-xs"
                                            title="Open {origin.label} in the workbench"
                                            onclick={() =>
                                                onopen(
                                                    origin.documentId,
                                                    origin.line,
                                                )}
                                        >
                                            {origin.label}
                                        </button>
                                    {/if}
                                {/each}
                            </span>
                        </div>

                        {#if open[row.key]}
                            <div class="mb-2 ml-6 flex flex-col gap-2">
                                {#each row.sources as source, sourceIndex (source.side + sourceIndex)}
                                    {#each source.shapes as shape (shape.id)}
                                        <div>
                                            <p
                                                class="text-text-subtle mb-1 font-mono text-xs break-all"
                                            >
                                                {source.side === GENERATED
                                                    ? "generated"
                                                    : (shape.origins?.[0]
                                                          ?.documentName ?? "")}
                                                {#if shape.origins?.[0]?.line}
                                                    · line {shape.origins[0]
                                                        .line}
                                                {/if}
                                            </p>
                                            <TurtleEditor
                                                autoGrow
                                                value={shape.triples.trim()}
                                                readOnly={true}
                                            />
                                        </div>
                                    {/each}
                                {/each}
                            </div>
                        {/if}
                    </li>
                {/each}
            </ul>
        {/if}
    </div>
</div>
