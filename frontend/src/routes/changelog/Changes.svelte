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
    import { getChangeLog as getChangeLogAPI } from "$lib/api/generated/index.ts";
    import FilterChipControl from "$lib/components/FilterChipControl.svelte";
    import {
        forceReloadTrigger,
        editorState,
    } from "$lib/sharedState.svelte.js";
    import { versionControlStore } from "$lib/stores/versionControlStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import {
        countByKind,
        descriptionOf,
        filterableKinds,
        labelOf,
    } from "./changeKinds.js";
    import { showsChange } from "./changelogFilter.js";
    import ChangesRow from "./ChangesRow.svelte";
    import RestoreConfirmDialog from "./RestoreConfirmDialog.svelte";

    const { getExpanded, setExpanded, cleanExpandedStateMap, graphUri } =
        $props();

    let changelog = $state();

    let readonlyWorkspace = $state(true);

    /**
     * The kinds of data the user has hidden. A filter on what is shown only; what a restore puts
     * back is asked separately, so that a view setting someone forgot about cannot decide how much
     * of the workspace is written over.
     */
    let hiddenKinds = $state(new Set());

    /** The change a restore has been asked for, with what restoring it would take back. */
    let restoreRequest = $state(null);

    let selectedWorkspaceName = $derived(
        editorState.selectedWorkspace.getValue(),
    );

    /** What the schema in view has, before the kinds the user hid are taken off it. */
    let changesInView = $derived(
        (changelog ?? []).filter(change => showsChange(change, { graphUri })),
    );

    let filterKinds = $derived(filterableKinds(changelog));

    /** How many of the changes in view each kind has, which is what greys an option out. */
    let countPerKind = $derived(countByKind(changesInView));

    // The schema is already answered for by changesInView; only the kinds the
    // user hid are left to take off.
    let visibleChanges = $derived(
        changesInView.filter(change => showsChange(change, { hiddenKinds })),
    );

    /**
     * The change the workspace stands on.
     *
     * The newest change an undo has not stepped over, since undone ones sit above it, ahead of the
     * workspace. Read from the whole changelog rather than from what the filter leaves, so that
     * hiding something cannot make another change look like the current one.
     */
    let currentChange = $derived(
        (changelog ?? []).find(change => !change.undone),
    );

    $effect(async () => {
        forceReloadTrigger.subscribe();
        if (selectedWorkspaceName) {
            readonlyWorkspace = await workspaceStore.isReadOnly(
                selectedWorkspaceName,
            );
        }
    });

    $effect(async () => {
        forceReloadTrigger.subscribe();
        if (selectedWorkspaceName) {
            await getChangelog();
        }
    });

    async function getChangelog() {
        if (!selectedWorkspaceName) {
            return;
        }
        const { data, error } = await getChangeLogAPI({
            path: { datasetName: selectedWorkspaceName },
        });
        if (!error) {
            changelog = data;
            cleanExpandedStateMap(changelog);
        } else {
            console.error("Failed to fetch changelog:", error);
        }
    }

    function toggleKind(kind, hidden) {
        const next = new Set(hiddenKinds);
        if (hidden) {
            next.add(kind);
        } else {
            next.delete(kind);
        }
        hiddenKinds = next;
    }

    /**
     * Asks before restoring, naming the changes between the workspace and the target. The
     * changelog already holds them, so the question can be asked without another request.
     *
     * A target below the workspace is reached by taking changes back, one above it by reapplying
     * them, and the two read differently enough that the question says which it is. How many
     * changes lie ahead goes with it, because a restore recorded as a new version leaves none of
     * them to be redone.
     */
    function askToRestore(change) {
        const all = changelog ?? [];
        const current = all.findIndex(entry => !entry.undone);
        const index = all.findIndex(
            entry => entry.changeId === change.changeId,
        );
        restoreRequest = change.undone
            ? { change, reapplied: all.slice(index, current) }
            : {
                  change,
                  takenBack: all.slice(current, index),
                  undoneAhead: current,
              };
    }

    async function restore(graphUris) {
        const change = restoreRequest.change;
        restoreRequest = null;
        const { error, skipped } = await versionControlStore.restore(
            change.changeId,
            graphUris,
        );
        // A restore that wrote nothing leaves nothing to reload.
        if (!error && !skipped) {
            forceReloadTrigger.trigger();
        }
    }
</script>

{#if changelog && changelog.length > 0}
    <div class="no-scrollbar h-full overflow-auto pb-10">
        <div
            class="border-border bg-window-background m-4 rounded border p-6 shadow"
        >
            <div
                class="border-border mb-4 flex flex-wrap items-center gap-2 border-b pb-4"
            >
                <span class="text-default-text text-sm font-semibold">
                    Show
                </span>
                {#each filterKinds as kind (kind)}
                    {@const count = countPerKind.get(kind) ?? 0}
                    <FilterChipControl
                        label={labelOf(kind)}
                        description={descriptionOf(kind)}
                        {count}
                        noun="change"
                        shown={count > 0 && !hiddenKinds.has(kind)}
                        onToggle={shown => toggleKind(kind, shown)}
                    />
                {/each}
            </div>

            {#if visibleChanges.length > 0}
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
                        {#each visibleChanges as change (change.changeId)}
                            <ChangesRow
                                {change}
                                {getExpanded}
                                {setExpanded}
                                {hiddenKinds}
                                {graphUri}
                                current={change.changeId ===
                                    currentChange?.changeId}
                                readonly={readonlyWorkspace}
                                onRestore={askToRestore}
                            />
                        {/each}
                    </tbody>
                </table>
            {:else}
                <p class="text-default-text p-4 text-center">
                    {changesInView.length > 0
                        ? "Nothing of the selected kinds"
                        : "No changes to this schema yet"}
                </p>
            {/if}
        </div>
    </div>
{:else if selectedWorkspaceName}
    <div class="flex h-full items-center justify-center">
        <p class="text-default-text text-lg">No changes in current session</p>
    </div>
{/if}

<RestoreConfirmDialog
    request={restoreRequest}
    {graphUri}
    onConfirm={restore}
    onCancel={() => (restoreRequest = null)}
/>
