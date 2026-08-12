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

    import { isReadOnly } from "$lib/api/apiWorkspaceUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";

    let {
        workspace = $bindable(),
        graph = $bindable(),
        lockedWorkspaceName,
        lockedGraphUri,
        allowSelectionOfReadonlyWorkspaces = true,
        displayAsCard = true,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const workspaceSelectId = `workspaceSelect-${uuidv4()}`;
    const graphSelectId = `graphSelect-${uuidv4()}`;

    let workspaces = $state([]);
    let graphs = $state([]);

    const workspaceLocked = $derived(lockedWorkspaceName !== undefined);
    const graphLocked = $derived(lockedGraphUri !== undefined);

    const graphSelectDisabled = $derived(graphLocked || !workspace);

    $effect(() => {
        if (workspaceLocked) return;
        if (!workspace) {
            graph = graphLocked ? lockedGraphUri : null;
            graphs = [];
            return;
        }
        loadGraphsFor(workspace);
    });

    onMount(async () => {
        if (workspaceLocked) workspace = lockedWorkspaceName;
        if (graphLocked) graph = lockedGraphUri;

        await loadWorkspaces();
        if (workspace) {
            await loadGraphsFor(workspace);
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

    async function loadWorkspaces() {
        const res = await bec.getWorkspaceNames();
        const workspaceNames = await res.json();
        const newWorkspaces = workspaceNames.map(name => ({
            label: name,
            readonly: false,
        }));
        if (!allowSelectionOfReadonlyWorkspaces) {
            for (const workspace of newWorkspaces) {
                workspace.readonly = await isReadOnly(workspace.label);
            }
        }
        workspaces = newWorkspaces;

        if (
            !workspaceLocked &&
            workspace &&
            !allowSelectionOfReadonlyWorkspaces
        ) {
            const selectedWorkspace = newWorkspaces.find(
                option => option.label === workspace,
            );
            if (!selectedWorkspace || selectedWorkspace.readonly) {
                workspace = null;
            }
        }
    }

    async function loadGraphsFor(workspace) {
        if (!workspace) {
            graphs = [];
            return;
        }

        const res = await bec.getGraphs(workspace);
        graphs = await res.json();

        const valid = graphs.some(graphName => getUri(graphName) === graph);
        if (!valid && !graphLocked) {
            graph = null;
        }
    }
</script>

<div
    class={displayAsCard
        ? "border-border bg-background-subtle rounded border p-3"
        : ""}
>
    <label for={workspaceSelectId} class="mb-1 block text-sm">Workspace</label>
    <SelectEditControl
        id={workspaceSelectId}
        bind:value={workspace}
        options={workspaces}
        getOptionIsDisabled={workspace =>
            !allowSelectionOfReadonlyWorkspaces && workspace.readonly}
        getOptionValue={workspace => workspace.label}
        getOptionLabel={workspace =>
            workspace.label + (workspace.readonly ? " (readonly)" : "")}
        disabled={workspaceLocked || workspaces.length === 0}
        placeholder="Select workspace"
        onchange={() => (graph = null)}
    />

    <label for={graphSelectId} class="mt-3 mb-1 block text-sm">
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
