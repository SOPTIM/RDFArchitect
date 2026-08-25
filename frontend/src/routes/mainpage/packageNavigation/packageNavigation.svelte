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
    import { onMount, setContext, untrack } from "svelte";

    import { asyncValue } from "$lib/asyncValue.svelte.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import { graphColors } from "$lib/graphColors.svelte.js";
    import {
        editorState,
        forceReloadTrigger
    } from "$lib/sharedState.svelte.js";
    import { SimpleTrigger } from "$lib/statePrimitives.svelte.js";
    import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import { getWorkspaceNavEntry } from "./build-nav-object.js";
    import CrossProfileDiagramsSection from "./CrossProfileDiagramsSection.svelte";
    import CustomDiagramsSection from "./CustomDiagramsSection.svelte";
    import GraphSection from "./GraphSection.svelte";
    import WorkspaceActionsMenu from "../workspaceActions/WorkspaceActionsMenu.svelte";


    const localReloadTrigger = new SimpleTrigger();
    const readonlyValue = asyncValue(
        () => activeWorkspace,
        (workspace) => workspaceStore.isReadOnly(workspace)
    );

    let workspaceNavEntry = $state(null);
    let namespaces = $state([]);
    let crossProfileID = $state();
    let latestLoadRequest = 0;

    const activeWorkspace = $derived(editorState.selectedWorkspace.getValue());
    const readonly = $derived(readonlyValue.current ?? false);
    // Entries of a workspace that is no longer active must not be rendered:
    // their sections would fire requests mixing the new workspace with the old
    // schemas while the tree is being rebuilt.
    const navEntryReady = $derived(workspaceNavEntry?.id === activeWorkspace);
    const graphNavEntries = $derived(
        navEntryReady ? (workspaceNavEntry?.children ?? []) : []
    );
    const packagesWithClassesCount = $derived(
        graphNavEntries
            .flatMap(graphNavEntry => graphNavEntry.children ?? [])
            .filter(packageNavEntry => packageNavEntry.children?.length > 0)
            .length
    );

    $effect(async () => {
        forceReloadTrigger.subscribe();
        const workspaceName = activeWorkspace;
        await untrack(async () => await loadWorkspace(workspaceName));
        localReloadTrigger.trigger();
    });

    $effect(() => {
        editorState.selectedClass.subscribe();
        if (editorState.selectedClass.getProperty("id")) {
            untrack(() => editorState.markClassActive());
        }
    });

    async function loadWorkspace(workspaceName) {
        const request = ++latestLoadRequest;
        const previousNavEntry =
            workspaceNavEntry?.id === workspaceName ? workspaceNavEntry : null;
        if (!workspaceName) {
            workspaceNavEntry = null;
            namespaces = [];
            crossProfileID = undefined;
            return;
        }
        const navEntry = await getWorkspaceNavEntry(
            workspaceName,
            previousNavEntry
        );
        const loadedNamespaces = await workspaceStore.getNamespaces(workspaceName) ?? [];
        const loadedCrossProfileID = await crossProfileStore.getId(workspaceName);
        await graphColors.reload(workspaceName);
        if (request !== latestLoadRequest) {
            return;
        }
        workspaceNavEntry = navEntry;
        namespaces = loadedNamespaces;
        crossProfileID = loadedCrossProfileID;
    }

    setContext("packageNavigation", {
        reloadTrigger: localReloadTrigger
    });
</script>

<div class="flex h-full min-h-0 w-full flex-1 flex-col">
    <ContextMenu.Root>
        <ContextMenu.TriggerArea
            class="m-0 flex h-full w-full flex-1 flex-col items-stretch gap-0 p-0"
        >
            <div class="flex h-full w-full">
                <div
                    class="border-nav-border bg-color-nav-surface flex h-full min-h-0 w-full flex-1 flex-col border-r"
                >
                    <div
                        class="no-scrollbar min-h-0 flex-1 overflow-y-auto py-[0.4rem]"
                    >
                        {#if navEntryReady}
                            {#key activeWorkspace}
                                <div
                                    class="flex w-full flex-col items-stretch justify-start gap-[0.1rem] px-2"
                                >
                                    {#each graphNavEntries as graphNavEntry (graphNavEntry.id)}
                                        <GraphSection
                                            {workspaceNavEntry}
                                            {graphNavEntry}
                                            {namespaces}
                                            {readonly}
                                        />
                                    {/each}

                                    {#if packagesWithClassesCount > 1}
                                        <CrossProfileDiagramsSection
                                            {workspaceNavEntry}
                                            {crossProfileID}
                                        />
                                    {/if}

                                    <CustomDiagramsSection
                                        {workspaceNavEntry}
                                        allGraphNavEntries={graphNavEntries}
                                        {readonly}
                                    />
                                </div>
                            {/key}
                        {/if}
                    </div>
                </div>
            </div>
        </ContextMenu.TriggerArea>
        <WorkspaceActionsMenu workspaceName={activeWorkspace} {readonly} />
    </ContextMenu.Root>
</div>
