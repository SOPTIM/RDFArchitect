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
        faCaretDown,
        faCaretUp,
        faDatabase,
        faSitemap,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";
    import { Pane, Splitpanes } from "svelte-splitpanes";

    import { BackendConnection } from "$lib/api/backend.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { URI } from "$lib/models/dto";
    import {
        forceReloadTrigger,
        editorState,
    } from "$lib/sharedState.svelte.js";

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    let datasetList = $state();
    let selectedDataset = $state();
    let selectedGraph = $state();
    let selectedGraphUri = $state();
    let changelog = $state();

    let expandedChanges = $state(new Set());
    let expandedAdditions = $state(new Set());
    let expandedDeletions = $state(new Set());

    $effect(async () => {
        forceReloadTrigger.subscribe();
        await fetchNavigationObject();
        // Preselect from editor state
        const preDataset = editorState.selectedDataset.getValue();
        const preGraphUri = editorState.selectedGraph.getValue();
        if (preDataset && preGraphUri) {
            selectedDataset = preDataset;
            selectedGraphUri = preGraphUri;
            syncSelectedGraphReference();
            await getChangelog();
        }
    });

    async function fetchNavigationObject() {
        let datasetNames = await getDatasetNames();
        let newDatasetList = [];
        const previous = datasetList ?? [];
        for (const datasetName of datasetNames) {
            let graphUris = await getGraphNames(datasetName);
            const graphs = [];
            for (let graphUri of graphUris) {
                graphUri = new URI(graphUri);
                graphs.push(graphUri);
            }
            newDatasetList.push({
                label: datasetName,
                graphs: graphs,
                showContents:
                    previous.find(d => d.label === datasetName)?.showContents ??
                    datasetName === selectedDataset,
            });
        }
        datasetList = newDatasetList;
        syncSelectedGraphReference();
    }

    async function getChangelog() {
        const graphUri = currentGraphUri();
        if (!selectedDataset || !graphUri) {
            changelog = [];
            return;
        }
        const res = await bec.getChangelog(selectedDataset, graphUri);
        if (res.ok) {
            changelog = await res.json();
            for (const change of changelog) {
                expandedAdditions.add(change.changeId);
                expandedDeletions.add(change.changeId);
            }
        } else {
            console.error("Failed to fetch changelog:", res.statusText);
        }
    }

    async function getDatasetNames() {
        const res = await bec.getDatasetNames();
        return await res.json();
    }

    async function getGraphNames(datasetName) {
        const res = await bec.getGraphNames(datasetName);
        return await res.json();
    }

    function getUri(uri) {
        return uri.prefix ? uri.prefix + uri.suffix : uri.suffix;
    }

    function currentGraphUri() {
        if (selectedGraph) {
            return getUri(selectedGraph);
        }
        return selectedGraphUri;
    }

    async function restoreVersion(changeId) {
        console.log("restoreVersion", changeId);
        const graphUri = currentGraphUri();
        if (!graphUri) return;
        const res = await bec.restoreVersion(
            selectedDataset,
            graphUri,
            changeId,
        );
        if (res.ok) {
            console.log("Version restored successfully");
            getChangelog();
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

    function formatTimestamp(rawTimestamp) {
        const date = new Date(rawTimestamp);
        const pad = n => n.toString().padStart(2, "0");
        return (
            `${pad(date.getDate())}-${pad(date.getMonth() + 1)}-${date.getFullYear()} ` +
            `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
        );
    }

    function toggleExpanded(changeId) {
        if (expandedChanges.has(changeId)) {
            expandedChanges.delete(changeId);
        } else {
            expandedChanges.add(changeId);
        }
        expandedChanges = new Set(expandedChanges);
    }

    function toggleAdditions(changeId) {
        if (expandedAdditions.has(changeId)) {
            expandedAdditions.delete(changeId);
        } else {
            expandedAdditions.add(changeId);
        }
        expandedAdditions = new Set(expandedAdditions);
    }

    function toggleDeletions(changeId) {
        if (expandedDeletions.has(changeId)) {
            expandedDeletions.delete(changeId);
        } else {
            expandedDeletions.add(changeId);
        }
        expandedDeletions = new Set(expandedDeletions);
    }

    function toggleDatasetVisibility(dataset) {
        dataset.showContents = !dataset.showContents;
        datasetList = [...datasetList];
    }

    function syncSelectedGraphReference() {
        if (
            !datasetList ||
            !selectedDataset ||
            !selectedGraphUri ||
            datasetList.length === 0
        ) {
            return;
        }
        const targetDataset = datasetList.find(
            d => d.label === selectedDataset,
        );
        if (!targetDataset) {
            return;
        }
        targetDataset.showContents = true;
        const matchingGraph = targetDataset.graphs?.find(
            graph => getUri(graph) === selectedGraphUri,
        );
        if (matchingGraph) {
            selectedGraph = matchingGraph;
            selectedGraphUri = getUri(matchingGraph);
        }
        datasetList = [...datasetList];
    }
</script>

<Splitpanes theme="opencgmes-theme" class="flex h-full">
    <Pane size={18} maxSize={30} class="bg-window-background">
        <div
            class="flex h-full min-h-0 w-full flex-1 flex-col border-r border-[var(--color-nav-border)] bg-[var(--color-nav-surface)]"
        >
            <div
                class="no-scrollbar min-h-0 flex-1 overflow-y-auto py-[0.4rem]"
            >
                {#if datasetList && datasetList.length > 0}
                    <div
                        class="flex w-full flex-col items-stretch justify-start gap-[0.1rem] px-2"
                    >
                        {#each datasetList as dataset}
                            <div class="flex flex-col gap-[0.1rem]">
                                <NavigationEntry
                                    level={1}
                                    label={dataset.label}
                                    icon={faDatabase}
                                    hasChildren={dataset.graphs.length > 0}
                                    expanded={dataset.showContents}
                                    isSelected={dataset.label ===
                                        selectedDataset}
                                    title={dataset.label}
                                    onclick={() => {
                                        selectedDataset = dataset.label;
                                    }}
                                    onToggle={() => {
                                        if (!dataset.graphs.length) return;
                                        toggleDatasetVisibility(dataset);
                                    }}
                                />
                                {#if dataset.showContents}
                                    <div class="flex flex-col gap-[0.1rem]">
                                        {#each dataset.graphs as graph}
                                            <NavigationEntry
                                                level={2}
                                                label={graph.suffix}
                                                secondaryLabel={graph.prefix ??
                                                    ""}
                                                icon={faSitemap}
                                                isSelected={graph ===
                                                    selectedGraph}
                                                title={graph.suffix}
                                                onclick={() => {
                                                    selectedDataset =
                                                        dataset.label;
                                                    selectedGraph = graph;
                                                    selectedGraphUri =
                                                        getUri(graph);
                                                    getChangelog();
                                                }}
                                            />
                                        {/each}
                                    </div>
                                {/if}
                            </div>
                        {/each}
                    </div>
                {:else}
                    <div class="p-4 text-left">No data available</div>
                {/if}
            </div>
        </div>
    </Pane>
    <Pane size={82} class="bg-window-background h-full">
        {#if changelog && changelog.length > 0}
            <div class="no-scrollbar h-full overflow-auto pb-10">
                <div
                    class="border-border bg-window-background m-4 rounded border p-6 shadow"
                >
                    <table class="w-full table-auto text-left">
                        <thead class="border-border border-b-4">
                            <tr>
                                <th class="p-4">Change</th>
                                <th class="p-4">Timestamp</th>
                                <th class="w-0 p-4">Details</th>
                                <th class="w-0 p-4"></th>
                            </tr>
                        </thead>
                        <tbody class="divide-border divide-y text-sm">
                            {#each changelog as change}
                                <tr>
                                    <td class="p-4">{change.message}</td>
                                    <td class="p-4">
                                        {formatTimestamp(change.timestamp)}
                                    </td>
                                    <td class="p-4 text-center">
                                        {#if hasTriples(change)}
                                            <button
                                                onclick={() =>
                                                    toggleExpanded(
                                                        change.changeId,
                                                    )}
                                                class="cursor-pointer text-lg"
                                                title="Toggle details"
                                            >
                                                <Fa
                                                    icon={expandedChanges.has(
                                                        change.changeId,
                                                    )
                                                        ? faCaretUp
                                                        : faCaretDown}
                                                />
                                            </button>
                                        {/if}
                                    </td>
                                    <td
                                        class="w-px p-4 text-center whitespace-nowrap"
                                    >
                                        {#if hasTriples(change)}
                                            <ButtonControl
                                                callOnClick={() =>
                                                    restoreVersion(
                                                        change.changeId,
                                                    )}
                                            >
                                                Restore Version
                                            </ButtonControl>
                                        {/if}
                                    </td>
                                </tr>

                                {#if expandedChanges.has(change.changeId)}
                                    <tr>
                                        <td colspan="4" class="p-0">
                                            <div class="space-y-4 p-4">
                                                {#if change.additions?.length}
                                                    <div
                                                        class="border-green-border bg-green-background rounded-xl border"
                                                    >
                                                        <div
                                                            class="text-green-text flex cursor-pointer items-center px-4 py-2 text-sm font-semibold"
                                                            role="button"
                                                            tabindex="0"
                                                            onkeydown={e =>
                                                                (e.key ===
                                                                    "Enter" ||
                                                                    e.key ===
                                                                        " ") &&
                                                                toggleAdditions(
                                                                    change.changeId,
                                                                )}
                                                            onclick={() =>
                                                                toggleAdditions(
                                                                    change.changeId,
                                                                )}
                                                        >
                                                            Additions
                                                            <Fa
                                                                class="pl-1"
                                                                icon={expandedAdditions.has(
                                                                    change.changeId,
                                                                )
                                                                    ? faCaretUp
                                                                    : faCaretDown}
                                                            />
                                                        </div>
                                                        {#if expandedAdditions.has(change.changeId)}
                                                            <div
                                                                class="overflow-auto px-4 pb-4"
                                                            >
                                                                <table
                                                                    class="border-green-border w-full table-auto border-t text-left text-xs"
                                                                >
                                                                    <thead
                                                                        class="text-green-text font-semibold"
                                                                    >
                                                                        <tr>
                                                                            <th
                                                                                class="w-1/3 py-2"
                                                                            >
                                                                                Subject
                                                                            </th>
                                                                            <th
                                                                                class="w-1/3 py-2"
                                                                            >
                                                                                Predicate
                                                                            </th>
                                                                            <th
                                                                                class="w-1/3 py-2"
                                                                            >
                                                                                Object
                                                                            </th>
                                                                        </tr>
                                                                    </thead>
                                                                    <tbody>
                                                                        {#each change.additions as triple}
                                                                            <tr
                                                                                class="border-green-border border-t"
                                                                            >
                                                                                <td
                                                                                    class="text-green-text py-2"
                                                                                >
                                                                                    {triple.subject}
                                                                                </td>
                                                                                <td
                                                                                    class="text-green-text py-2"
                                                                                >
                                                                                    {triple.predicate}
                                                                                </td>
                                                                                <td
                                                                                    class="text-green-text py-2"
                                                                                >
                                                                                    {triple.object}
                                                                                </td>
                                                                            </tr>
                                                                        {/each}
                                                                    </tbody>
                                                                </table>
                                                            </div>
                                                        {/if}
                                                    </div>
                                                {/if}

                                                {#if change.deletions?.length}
                                                    <div
                                                        class="border-red-border bg-red-background rounded-xl border"
                                                    >
                                                        <div
                                                            class="text-red-text flex cursor-pointer items-center px-4 py-2 text-sm font-semibold"
                                                            role="button"
                                                            tabindex="0"
                                                            onkeydown={e =>
                                                                (e.key ===
                                                                    "Enter" ||
                                                                    e.key ===
                                                                        " ") &&
                                                                toggleDeletions(
                                                                    change.changeId,
                                                                )}
                                                            onclick={() =>
                                                                toggleDeletions(
                                                                    change.changeId,
                                                                )}
                                                        >
                                                            Deletions
                                                            <Fa
                                                                class="pl-1"
                                                                icon={expandedDeletions.has(
                                                                    change.changeId,
                                                                )
                                                                    ? faCaretUp
                                                                    : faCaretDown}
                                                            />
                                                        </div>
                                                        {#if expandedDeletions.has(change.changeId)}
                                                            <div
                                                                class="overflow-auto px-4 pb-4"
                                                            >
                                                                <table
                                                                    class="border-red-border w-full table-auto border-t text-left text-xs"
                                                                >
                                                                    <thead
                                                                        class="text-red-text font-semibold"
                                                                    >
                                                                        <tr>
                                                                            <th
                                                                                class="w-1/3 py-2"
                                                                            >
                                                                                Subject
                                                                            </th>
                                                                            <th
                                                                                class="w-1/3 py-2"
                                                                            >
                                                                                Predicate
                                                                            </th>
                                                                            <th
                                                                                class="w-1/3 py-2"
                                                                            >
                                                                                Object
                                                                            </th>
                                                                        </tr>
                                                                    </thead>
                                                                    <tbody>
                                                                        {#each change.deletions as triple}
                                                                            <tr
                                                                                class="border-red-border border-t"
                                                                            >
                                                                                <td
                                                                                    class="text-red-text py-2"
                                                                                >
                                                                                    {triple.subject}
                                                                                </td>
                                                                                <td
                                                                                    class="text-red-text py-2"
                                                                                >
                                                                                    {triple.predicate}
                                                                                </td>
                                                                                <td
                                                                                    class="text-red-text py-2"
                                                                                >
                                                                                    {triple.object}
                                                                                </td>
                                                                            </tr>
                                                                        {/each}
                                                                    </tbody>
                                                                </table>
                                                            </div>
                                                        {/if}
                                                    </div>
                                                {/if}
                                            </div>
                                        </td>
                                    </tr>
                                {/if}
                            {/each}
                        </tbody>
                    </table>
                </div>
            </div>
        {:else if currentGraphUri()}
            <div class="flex h-full items-center justify-center">
                <p class="text-default-text text-lg">
                    No changes in current session
                </p>
            </div>
        {/if}
    </Pane>
</Splitpanes>
