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

    let {
        diagram,
        diagramGroups = new Map(),
        orderedRowIds = [],
        deselectedDiagramIds = $bindable(new Set()),
        depth = 0,
    } = $props();

    const home = $derived(homeOf(diagram, diagramGroups, orderedRowIds));
    const available = $derived(home !== null);
</script>

<div
    class="hover:bg-background-subtle flex items-center gap-1 rounded py-0.5 select-none"
    class:opacity-50={!available}
    style="padding-left: {depth * 14}px"
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
            checked={available && !deselectedDiagramIds.has(diagram.id)}
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
            {home === WORKSPACE ? "workspace-diagram" : "schema-diagram"}
        </span>
        <span class="text-text-subtle shrink-0 text-xs">
            {diagram.classes} classes
        </span>
    </label>
</div>
