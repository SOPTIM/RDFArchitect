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

    import { BackendConnection } from "$lib/api/backend.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import TripleTable from "./TripleTable.svelte";

    const {
        change,
        getExpanded,
        setExpanded,
        newest = false,
        readonly,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const rowKey = $derived(`${change.changeId}::row`);
    const additionsKey = $derived(`${change.changeId}::additions`);
    const deletionsKey = $derived(`${change.changeId}::deletions`);

    async function restoreVersion(changeId) {
        console.log("restoreVersion", changeId);
        const res = await bec.restoreVersion(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
            changeId,
        );
        if (res.ok) {
            console.log("Version restored successfully");
            forceReloadTrigger.trigger();
        } else {
            console.error("Failed to restore version:", res.statusText);
        }
    }

    function hasTriples(change) {
        return (
            (change.additions && change.additions.length > 0) ||
            (change.deletions && change.deletions.length > 0)
        );
    }

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
</script>

<tr>
    <td class="p-4">
        {change.message}
    </td>

    <td class="p-4">
        {formatTimestamp(change.timestamp)}
    </td>
    <td class="p-4 text-center">
        {#if hasTriples(change)}
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
        {#if newest}
            <span class="text-default-text text-sm">Current Version</span>
        {:else if hasTriples(change)}
            <ButtonControl
                disabled={readonly}
                title={readonly ? "cannot restore in readonly dataset" : ""}
                callOnClick={() => restoreVersion(change.changeId)}
            >
                Restore Version
            </ButtonControl>
        {:else}
            <span class="text-default-text text-sm">
                Can no longer be restored
            </span>
        {/if}
    </td>
</tr>

{#if getExpanded(rowKey)}
    <tr>
        <td colspan="4" class="p-0">
            <div class="space-y-4 p-4">
                {#if change.additions?.length}
                    <TripleTable
                        triples={change.additions}
                        color="green"
                        title="Additions"
                        expandedKey={additionsKey}
                        {getExpanded}
                        {setExpanded}
                    />
                {/if}

                {#if change.deletions?.length}
                    <TripleTable
                        triples={change.deletions}
                        color="red"
                        title="Deletions"
                        expandedKey={deletionsKey}
                        {getExpanded}
                        {setExpanded}
                    />
                {/if}
            </div>
        </td>
    </tr>
{/if}
