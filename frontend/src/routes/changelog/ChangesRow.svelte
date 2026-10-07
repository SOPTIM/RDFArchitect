<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script>
    import { faCaretDown, faCaretUp } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";

    import { labelOf } from "./changeKinds.js";
    import { visibleDeltas as deltasToShow } from "./changelogFilter.js";
    import TripleTable from "./TripleTable.svelte";

    const {
        change,
        getExpanded,
        setExpanded,
        hiddenKinds = new Set(),
        graphUri = null,
        current = false,
        readonly,
        onRestore,
    } = $props();

    const rowKey = $derived(`${change.changeId}::row`);

    /**
     * The deltas the filter leaves showing, and only those that have something
     * to show: a delta without triples would be a heading over nothing.
     */
    const visibleDeltas = $derived(
        deltasToShow(change, { hiddenKinds, graphUri }).filter(
            context =>
                context.additions?.length > 0 || context.deletions?.length > 0,
        ),
    );

    const hasTriples = $derived(visibleDeltas.length > 0);

    /**
     * How the row is drawn where it stands: the one the workspace is on is
     * marked, the ones ahead of it are faded. Held here because the detail row
     * below has to be drawn the same way.
     */
    const placement = $derived(
        `${current ? "bg-background-select" : ""} ${change.undone ? "opacity-50" : ""}`,
    );

    function toggleRowExpanded() {
        setExpanded(rowKey, !getExpanded(rowKey));
    }

    function formatTimestamp(rawTimestamp) {
        const date = new Date(rawTimestamp);

        const pad = n => n.toString().padStart(2, "0");

        return (
            `${pad(date.getDate())}-${pad(date.getMonth() + 1)}-${date.getFullYear()} ` +
            `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
        );
    }

    function describeContext(context) {
        const kind = labelOf(context.contextName);
        return context.graphUri ? `${kind} — ${context.graphUri}` : kind;
    }

    function getAdditionsKey(context) {
        return `${change.changeId}::${contextKey(context)}::additions`;
    }

    function getDeletionsKey(context) {
        return `${change.changeId}::${contextKey(context)}::deletions`;
    }

    // One change can touch the same kind of data in several graphs, so the kind alone would give
    // two contexts the same key and make them expand and collapse together.
    function contextKey(context) {
        return `${context.graphUri ?? "workspace"}::${context.contextName}`;
    }
</script>

<tr class={placement}>
    <td class="p-4">
        {change.message}
    </td>

    <td class="p-4">
        {formatTimestamp(change.timestamp)}
    </td>

    <td class="p-4 text-center">
        {#if hasTriples}
            <button
                onclick={toggleRowExpanded}
                class="cursor-pointer text-lg"
                title="Toggle details"
            >
                <Fa icon={getExpanded(rowKey) ? faCaretUp : faCaretDown} />
            </button>
        {/if}
    </td>

    <td class="w-px p-4 text-center whitespace-nowrap">
        {#if current}
            <span
                class="text-default-text text-sm font-semibold"
                title="The version the workspace stands on"
            >
                Current version
            </span>
        {:else}
            <ButtonControl
                disabled={readonly}
                title={readonly
                    ? "The workspace is read-only"
                    : change.undone
                      ? "Advance the workspace to this version"
                      : "Restore the workspace to this version"}
                callOnClick={() => onRestore(change)}
            >
                {change.undone ? "Advance" : "Restore"}
            </ButtonControl>
        {/if}
    </td>
</tr>

{#if getExpanded(rowKey)}
    <tr class={placement}>
        <td colspan="4" class="p-0">
            <div class="space-y-6 p-2">
                {#each visibleDeltas as context}
                    <div class="border-border rounded-xl border p-2">
                        <h3 class="mb-2 font-semibold">
                            Context: {describeContext(context)}
                        </h3>

                        {#if context.additions?.length}
                            <TripleTable
                                triples={context.additions}
                                color="green"
                                title="Additions"
                                expandedKey={getAdditionsKey(context)}
                                {getExpanded}
                                {setExpanded}
                            />
                        {/if}
                        <div class="h-1"></div>
                        {#if context.deletions?.length}
                            <TripleTable
                                triples={context.deletions}
                                color="red"
                                title="Deletions"
                                expandedKey={getDeletionsKey(context)}
                                {getExpanded}
                                {setExpanded}
                            />
                        {/if}
                    </div>
                {/each}
            </div>
        </td>
    </tr>
{/if}
