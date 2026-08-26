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
    import { onMount } from "svelte";
    import { v4 as uuidv4 } from "uuid";

    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    let {
        workspace = $bindable(),
        graph = $bindable(),
        lockedWorkspaceName,
        lockedGraphUri,
        allowSelectionOfReadonlyWorkspaces = true,
        displayAsCard = true,
    } = $props();

    const workspaceSelectId = `workspaceSelect-${uuidv4()}`;
    const graphSelectId = `graphSelect-${uuidv4()}`;

    let workspaces = $state([]);
    let graphs = $state([]);

    const workspaceLocked = $derived(lockedWorkspaceName !== undefined);
    const graphLocked = $derived(lockedGraphUri !== undefined);

    const graphSelectDisabled = $derived(graphLocked || !workspace);

    $effect(async () => {
        if (workspaceLocked) return;
        if (!workspace) {
            graph = graphLocked ? lockedGraphUri : null;
            graphs = [];
            return;
        }

        graphs = (await graphStore.getGraphs(workspace)) ?? [];
        const valid = graphs.some(graphName => getUri(graphName) === graph);
        if (!valid && !graphLocked) {
            graph = null;
        }
    });

    onMount(async () => {
        workspaces = (await workspaceStore.getWorkspaces()) ?? [];
        if (workspaceLocked) workspace = lockedWorkspaceName;
        if (graphLocked) graph = lockedGraphUri;

        if (
            !workspaceLocked &&
            workspace &&
            !allowSelectionOfReadonlyWorkspaces
        ) {
            const selectedWorkspace = workspaces.find(
                option => option.label === workspace,
            );
            if (!selectedWorkspace || selectedWorkspace.readOnly) {
                workspace = null;
            }
        }

        if (workspace) {
            graphs = await graphStore.getGraphs(workspace);
        } else {
            graphs = [];
        }
    });

    /**
     * Full URI of a graph as it comes from the backend: a GraphDTO holding the
     * URI next to its dcat:keyword. A bare URI is still accepted so that a
     * locked graph can be passed in as is.
     */
    function getUri(graph) {
        const uri = graph.uri ?? graph;
        return (uri.prefix ?? "") + (uri.suffix ?? "");
    }
</script>

<div
    class={displayAsCard
        ? "border-border bg-background-subtle rounded border p-3"
        : ""}
>
    {#if !workspaceLocked}
        <label for={workspaceSelectId} class="mb-1 block text-sm">
            Workspace
        </label>
        <SelectEditControl
            id={workspaceSelectId}
            bind:value={workspace}
            options={workspaces}
            getOptionIsDisabled={workspace =>
                !allowSelectionOfReadonlyWorkspaces && workspace.readonly}
            getOptionValue={workspace => workspace.label}
            getOptionLabel={workspace =>
                workspace.label + (workspace.readOnly ? " (readonly)" : "")}
            disabled={(workspaces?.length ?? 0) === 0}
            placeholder="Select workspace"
            onchange={() => (graph = null)}
        />
    {/if}

    <label
        for={graphSelectId}
        class={`mb-1 block text-sm ${workspaceLocked ? "" : "mt-3"}`}
    >
        Schema (RDFS)
    </label>
    <SelectEditControl
        id={graphSelectId}
        bind:value={graph}
        options={graphs}
        disabled={graphSelectDisabled}
        placeholder={workspace ? "Select schema" : "Select a workspace first"}
        getOptionValue={getUri}
        getOptionLabel={g => g.keyword ?? g.uri.suffix}
    />
</div>
