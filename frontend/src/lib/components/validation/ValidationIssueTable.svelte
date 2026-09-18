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
        faChevronRight,
        faLayerGroup,
        faPenToSquare,
    } from "@fortawesome/free-solid-svg-icons";
    import { SvelteSet } from "svelte/reactivity";
    import { Fa } from "svelte-fa";

    import {
        canOpenOccurrence,
        openOccurrence,
    } from "$lib/actions/validationActions.js";

    import {
        countBySeverity,
        GroupBy,
        ruleLabel,
        severityMeta,
    } from "./validationIssues.js";

    let {
        groups,
        groupBy,
        workspace = null,
        collapsedGroups,
        onToggleGroup,
        onSelectGrouping,
        allCollapsed = false,
        onToggleAllGroups,
    } = $props();

    const SEVERITY_KEYS = ["ERROR", "WARNING", "INFO"];

    const expandedRows = new SvelteSet();

    const allIssues = $derived(groups.flatMap(group => group.issues));

    const showType = $derived(allIssues.some(issue => issue.ruleId));

    const canGroupByClass = $derived(
        allIssues.some(issue => issue.resourceUri),
    );

    const showSchemas = $derived(
        allIssues.some(issue => issue.occurrences?.length),
    );

    const columnCount = $derived(4 + (showType ? 1 : 0));

    function selectGrouping(value) {
        onSelectGrouping(groupBy === value ? GroupBy.NONE : value);
    }

    function rowKey(groupKey, index) {
        return `${groupKey}|${index}`;
    }

    function toggleRow(key) {
        if (expandedRows.has(key)) {
            expandedRows.delete(key);
        } else {
            expandedRows.add(key);
        }
    }

    function localName(uri) {
        return uri.substring(
            Math.max(uri.lastIndexOf("#"), uri.lastIndexOf("/")) + 1,
        );
    }

    function schemaLabel(occurrence) {
        return occurrence.keyword ?? localName(occurrence.graphUri ?? "");
    }

    function hasDetails(issue) {
        return (issue.occurrences?.length ?? 0) > 0;
    }
</script>

{#snippet groupingHeader(value, label)}
    {@const active = groupBy === value}
    <button
        type="button"
        class="hover:text-default-text focus-visible:outline-button-default-background flex cursor-pointer items-center gap-1 rounded focus-visible:outline-2 {active
            ? 'text-default-text font-semibold'
            : ''}"
        aria-pressed={active}
        title={active
            ? `Ungroup ${label.toLowerCase()}`
            : `Group by ${label.toLowerCase()}`}
        onclick={() => selectGrouping(value)}
    >
        {label}
        <Fa icon={faLayerGroup} class={active ? "" : "opacity-40"} />
    </button>
{/snippet}

{#snippet collapseAll()}
    {#if groupBy !== GroupBy.NONE}
        <button
            type="button"
            class="text-blue hover:bg-nav-hover-background focus-visible:outline-button-default-background cursor-pointer rounded px-1 font-medium focus-visible:outline-2"
            onclick={onToggleAllGroups}
        >
            {allCollapsed ? "Expand All" : "Collapse All"}
        </button>
    {/if}
{/snippet}

<table class="w-full table-fixed border-collapse text-sm">
    <thead>
        <tr class="text-text-subtle border-border border-b text-left text-xs">
            <th scope="col" class="w-28 py-1 font-medium">
                {@render groupingHeader(GroupBy.SEVERITY, "Severity")}
            </th>
            {#if showType}
                <th scope="col" class="w-44 py-1 font-medium">
                    {@render groupingHeader(GroupBy.RULE, "Check")}
                </th>
            {/if}
            <th scope="col" class="w-56 py-1 font-medium">
                {#if canGroupByClass}
                    {@render groupingHeader(GroupBy.CLASS, "Resource")}
                {:else}
                    Resource
                {/if}
            </th>
            <th scope="col" class="py-1 font-medium">
                {@render groupingHeader(GroupBy.MESSAGE, "Finding")}
            </th>
            {#if showSchemas}
                <th scope="col" class="w-40 py-1 font-medium">
                    <span class="flex items-center justify-between gap-2">
                        {@render groupingHeader(GroupBy.SCHEMA, "Schemas")}
                        {@render collapseAll()}
                    </span>
                </th>
            {:else}
                <th scope="col" class="w-28 py-1 text-right font-medium">
                    {@render collapseAll()}
                </th>
            {/if}
        </tr>
    </thead>
    {#each groups as group (group.key)}
        {@const groupExpanded = !collapsedGroups.has(group.key)}
        {@const groupCounts = countBySeverity(group.issues)}
        <tbody>
            {#if group.label !== null}
                <tr class="bg-background-subtle border-border border-b">
                    <td colspan={columnCount} class="px-1 py-1">
                        <div
                            class="flex flex-wrap items-center gap-x-3 gap-y-1"
                        >
                            <button
                                type="button"
                                class="text-default-text focus-visible:outline-button-default-background flex cursor-pointer items-center gap-2 rounded focus-visible:outline-2"
                                aria-expanded={groupExpanded}
                                onclick={() => onToggleGroup(group.key)}
                            >
                                <Fa
                                    icon={faChevronRight}
                                    class="opacity-80 transition-transform duration-200 {groupExpanded
                                        ? 'rotate-90'
                                        : ''}"
                                />
                                <span
                                    class="font-semibold"
                                    title={groupBy === GroupBy.CLASS
                                        ? group.key
                                        : undefined}
                                >
                                    {group.label}
                                </span>
                            </button>
                            <span
                                class="bg-border-strong text-default-text rounded-full px-2 py-[0.1rem] text-xs"
                            >
                                {group.issues.length}
                            </span>
                            {#if groupBy !== GroupBy.SEVERITY}
                                <span
                                    class="text-text-subtle flex items-center gap-3 text-xs"
                                >
                                    {#each SEVERITY_KEYS as key}
                                        {#if groupCounts[key] > 0}
                                            <span
                                                class="flex items-center gap-1"
                                            >
                                                <Fa
                                                    icon={severityMeta(key)
                                                        .icon}
                                                    class={severityMeta(key)
                                                        .iconClass}
                                                />
                                                {groupCounts[key]}
                                            </span>
                                        {/if}
                                    {/each}
                                </span>
                            {/if}
                        </div>
                    </td>
                </tr>
            {/if}
            {#if groupExpanded}
                {#each group.issues as issue, index (rowKey(group.key, index))}
                    {@const meta = severityMeta(issue.severity)}
                    {@const key = rowKey(group.key, index)}
                    {@const expanded = expandedRows.has(key)}
                    {@const detailed = hasDetails(issue)}
                    <tr
                        class="border-border hover:bg-nav-hover-background border-b align-top {detailed
                            ? 'cursor-pointer'
                            : ''}"
                        onclick={detailed ? () => toggleRow(key) : undefined}
                    >
                        <td class="py-1.5 pl-1">
                            <span class="flex items-center gap-1">
                                {#if detailed}
                                    <button
                                        type="button"
                                        class="text-text-subtle focus-visible:outline-button-default-background cursor-pointer rounded focus-visible:outline-2"
                                        aria-expanded={expanded}
                                        aria-label={`${expanded ? "Hide" : "Show"} details`}
                                        onclick={event => {
                                            event.stopPropagation();
                                            toggleRow(key);
                                        }}
                                    >
                                        <Fa
                                            icon={faChevronRight}
                                            class="text-[0.7rem] transition-transform duration-200 {expanded
                                                ? 'rotate-90'
                                                : ''}"
                                        />
                                    </button>
                                {/if}
                                <Fa
                                    icon={meta.icon}
                                    class={meta.iconClass}
                                    title={meta.label}
                                />
                                <span class={`text-xs ${meta.text}`}>
                                    {meta.label}
                                </span>
                            </span>
                        </td>
                        {#if showType}
                            <td class="text-text-subtle truncate py-1.5 pr-3">
                                {issue.ruleId ? ruleLabel(issue.ruleId) : ""}
                            </td>
                        {/if}
                        <td
                            class="text-default-text truncate py-1.5 pr-3 font-mono text-xs"
                            title={issue.resourceUri}
                        >
                            {issue.resourceUri
                                ? localName(issue.resourceUri)
                                : ""}
                        </td>
                        <td
                            class="text-default-text py-1.5 pr-3 {expanded
                                ? ''
                                : 'truncate'}"
                            title={issue.message}
                        >
                            {issue.message}
                        </td>
                        {#if showSchemas}
                            <td class="py-1.5">
                                <span class="flex flex-wrap gap-1">
                                    {#each issue.occurrences ?? [] as occurrence}
                                        <span
                                            class="bg-border-strong text-default-text rounded-full px-2 py-[0.05rem] text-xs tracking-[0.04em] uppercase"
                                            title={occurrence.graphUri}
                                        >
                                            {schemaLabel(occurrence)}
                                        </span>
                                    {/each}
                                </span>
                            </td>
                        {/if}
                    </tr>
                    {#if expanded}
                        <tr class="border-border bg-background-subtle border-b">
                            <td></td>
                            <td colspan={columnCount - 1} class="py-2 pr-3">
                                {#if issue.resourceUri}
                                    <p
                                        class="text-text-subtle mb-2 font-mono text-xs break-all"
                                    >
                                        {issue.resourceUri}
                                    </p>
                                {/if}
                                <ul class="flex flex-col gap-1">
                                    {#each issue.occurrences ?? [] as occurrence}
                                        <li
                                            class="flex min-w-0 items-center gap-2 text-xs"
                                        >
                                            <span
                                                class="bg-border-strong text-default-text w-16 shrink-0 rounded-full px-2 py-[0.05rem] text-center tracking-[0.04em] uppercase"
                                                title={occurrence.graphUri}
                                            >
                                                {schemaLabel(occurrence)}
                                            </span>
                                            {#if occurrence.value != null}
                                                <span
                                                    class="text-default-text min-w-0 font-mono break-all"
                                                >
                                                    {occurrence.value}
                                                </span>
                                            {:else}
                                                <span
                                                    class="text-text-subtle italic"
                                                >
                                                    none
                                                </span>
                                            {/if}
                                            {#if canOpenOccurrence(workspace, occurrence)}
                                                <button
                                                    type="button"
                                                    class="text-blue hover:bg-nav-hover-background focus-visible:outline-button-default-background ml-auto flex shrink-0 cursor-pointer items-center gap-1 rounded px-1.5 py-0.5 focus-visible:outline-2"
                                                    title="Open the class in the class editor"
                                                    onclick={event => {
                                                        event.stopPropagation();
                                                        openOccurrence(
                                                            workspace,
                                                            occurrence,
                                                        );
                                                    }}
                                                >
                                                    <Fa icon={faPenToSquare} />
                                                    Open Class
                                                </button>
                                            {/if}
                                        </li>
                                    {/each}
                                </ul>
                            </td>
                        </tr>
                    {/if}
                {/each}
            {/if}
        </tbody>
    {/each}
</table>
