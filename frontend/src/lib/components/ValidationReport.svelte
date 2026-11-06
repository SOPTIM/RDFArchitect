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
        faCircleCheck,
        faCircleExclamation,
    } from "@fortawesome/free-solid-svg-icons";
    import { SvelteSet } from "svelte/reactivity";
    import { Fa } from "svelte-fa";

    import {
        countBySeverity,
        GroupBy,
        groupIssues,
        SEVERITY,
    } from "$lib/components/validation/validationIssues.js";
    import ValidationIssueTable from "$lib/components/validation/ValidationIssueTable.svelte";

    let { result, subject = "Schema", workspace = null } = $props();

    const SEVERITY_KEYS = ["ERROR", "WARNING", "INFO"];

    const collapsedGroups = new SvelteSet();

    let groupBy = $state(GroupBy.SEVERITY);

    const issues = $derived(result?.issues ?? []);

    const counts = $derived(countBySeverity(issues));

    const groups = $derived(groupIssues(issues, groupBy));

    const allCollapsed = $derived(
        groups.length > 0 &&
            groups.every(group => collapsedGroups.has(group.key)),
    );

    function selectGrouping(value) {
        groupBy = value;
        collapsedGroups.clear();
    }

    function toggleGroup(key) {
        if (collapsedGroups.has(key)) {
            collapsedGroups.delete(key);
        } else {
            collapsedGroups.add(key);
        }
    }

    function toggleAllGroups() {
        if (allCollapsed) {
            collapsedGroups.clear();
            return;
        }
        for (const group of groups) {
            collapsedGroups.add(group.key);
        }
    }

    function severityLabel(key, count) {
        return count === 1 ? SEVERITY[key].label : SEVERITY[key].pluralLabel;
    }
</script>

<!-- Status summary -->
<div
    class={`mb-6 flex flex-wrap items-center gap-x-6 gap-y-2 rounded border p-4 ${
        result.valid
            ? "bg-green-background border-green-border"
            : "bg-red-background border-red-border"
    }`}
>
    <div class="flex items-center gap-2">
        <Fa
            icon={result.valid ? faCircleCheck : faCircleExclamation}
            class={result.valid ? "text-green-text" : "text-red-text"}
        />
        <span
            class={`text-base font-semibold ${
                result.valid ? "text-green-text" : "text-red-text"
            }`}
        >
            {result.valid ? `${subject} is valid` : `${subject} is invalid`}
        </span>
    </div>

    <div class="text-default-text flex flex-wrap items-center gap-4 text-sm">
        {#each SEVERITY_KEYS as key}
            <span class="flex items-center gap-1.5">
                <Fa icon={SEVERITY[key].icon} class={SEVERITY[key].iconClass} />
                {counts[key]}
                {severityLabel(key, counts[key])}
            </span>
        {/each}
    </div>
</div>

{#if issues.length > 0}
    <!-- Issue list -->
    <ValidationIssueTable
        {groups}
        {groupBy}
        {workspace}
        {collapsedGroups}
        {allCollapsed}
        onToggleGroup={toggleGroup}
        onSelectGrouping={selectGrouping}
        onToggleAllGroups={toggleAllGroups}
    />
{:else}
    <div class="bg-green-background border-green-border rounded border p-6">
        <p class="text-green-text italic">No issues found.</p>
    </div>
{/if}
