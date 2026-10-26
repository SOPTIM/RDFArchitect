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
    import { SvelteSet } from "svelte/reactivity";

    import {
        GroupBy,
        groupIssues,
    } from "$lib/components/validation/validationIssues.js";
    import ValidationIssueTable from "$lib/components/validation/ValidationIssueTable.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { asValidationResult } from "$lib/utils/importWarnings.js";

    let {
        showDialog = $bindable(false),
        /**
         * What the import could not carry over as stated.
         *
         * @type {{fileName: string, graphUri: string,
         *     items: {resource: string, reason: string}[]}[]}
         */
        warnings = [],
    } = $props();

    const collapsedGroups = new SvelteSet();

    // Grouped by what they have in common rather than by the file they were found in: one kind
    // of warning usually runs through the whole import, and that is what is worth seeing.
    let groupBy = $state(GroupBy.MESSAGE);

    const issues = $derived(asValidationResult(warnings).issues);
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
</script>

<ActionDialog
    bind:showDialog
    title="Not everything could be imported"
    primaryLabel="Close"
    size="w-3/4"
>
    <!-- Sized by what it holds, up to the point where the list scrolls instead of growing. A
         height of its own would leave a single warning in an empty dialog. -->
    <div class="mx-2 flex flex-col">
        <div
            class="border-border max-h-[60vh] overflow-y-auto rounded border px-2 py-1"
        >
            <ValidationIssueTable
                {groups}
                {groupBy}
                {collapsedGroups}
                {allCollapsed}
                onToggleGroup={toggleGroup}
                onSelectGrouping={selectGrouping}
                onToggleAllGroups={toggleAllGroups}
                typeLabel="Found in"
                wrapText
            />
        </div>
    </div>
</ActionDialog>
