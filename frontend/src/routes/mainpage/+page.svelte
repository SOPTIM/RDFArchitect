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
    import { onMount, untrack } from "svelte";
    import { Pane, Splitpanes } from "svelte-splitpanes";
    import { validate } from "uuid";

    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import {
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { workspaceState } from "$lib/workspaceState.svelte.js";

    import NoSchemaPlaceholder from "./emptyStates/NoSchemaPlaceholder.svelte";
    import NoWorkspacePlaceholder from "./emptyStates/NoWorkspacePlaceholder.svelte";
    import PackageNavigation from "./packageNavigation/packageNavigation.svelte";
    import PackageWindow from "./packageWindow.svelte";
    import WorkspaceTabs from "./workspaceTabs/WorkspaceTabs.svelte";

    const activeWorkspace = $derived(editorState.selectedWorkspace.getValue());
    const hasNoWorkspaces = $derived(
        workspaceState.isLoaded() && workspaceState.getNames().length === 0,
    );
    const hasNoSchemas = $derived(
        !hasNoWorkspaces &&
            workspaceState.getSchemaCount(activeWorkspace) === 0,
    );

    // The placeholder replaces the navigation, so the schema count is tracked
    // here — inside the navigation it would never refresh again.
    $effect(async () => {
        forceReloadTrigger.subscribe();
        const workspaceName = activeWorkspace;
        await untrack(
            async () => await workspaceState.refreshSchemaCount(workspaceName),
        );
    });

    onMount(() => {
        parseModelSelectionUrlParameters();
    });

    async function parseModelSelectionUrlParameters() {
        const url = new URL(window.location.href);
        const queryParams = new URLSearchParams(url.search);
        const workspace = queryParams.get("dataset") || null;
        const graph = queryParams.get("graph") || null;
        let pack = queryParams.get("package") || null;
        if (!workspace) return;
        editorState.selectedWorkspace.updateValue(workspace);
        editorState.selectedGraph.updateValue(graph);
        if (!graph || !pack) return;
        if (pack !== "default" && !validate(pack)) {
            pack = await resolveIRI(workspace, graph, pack);
        }
        editorState.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: pack,
        });
    }

    async function resolveIRI(workspace, graph, iri) {
        return await fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(workspace) +
                "/graphs/" +
                encodeURIComponent(graph) +
                "/resolve/iri/" +
                encodeURIComponent(iri),
            {
                method: "GET",
                credentials: "include",
            },
        ).then(res => res.text());
    }
</script>

<div class="flex h-full w-full flex-col">
    <WorkspaceTabs />
    {#if hasNoWorkspaces}
        <NoWorkspacePlaceholder />
    {:else if hasNoSchemas}
        <NoSchemaPlaceholder workspaceName={activeWorkspace} />
    {:else}
        <Splitpanes theme="opencgmes-theme" class="flex min-h-0 flex-1">
            <Pane
                size={18}
                maxSize={30}
                class="bg-window-background rounded-xs border-none "
            >
                <div class="h-full">
                    <PackageNavigation />
                </div>
            </Pane>
            <Pane size={82} class="bg-window-background rounded-xs border-none">
                <PackageWindow />
            </Pane>
        </Splitpanes>
    {/if}
</div>
