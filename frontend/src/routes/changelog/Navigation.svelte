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
    import {
        faDatabase,
        faDiagramProject,
    } from "@fortawesome/free-solid-svg-icons";

    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import {
        forceReloadTrigger,
        editorState,
    } from "$lib/sharedState.svelte.js";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import { getUri } from "../mainpage/packageNavigation/packageNavigationUtils.svelte.js";

    let workspaceList = $state([]);
    let selectedWorkspaceName = $derived(
        editorState.selectedWorkspace.getValue(),
    );
    let selectedGraphUri = $derived(editorState.selectedGraph.getValue());

    $effect(async () => {
        forceReloadTrigger.subscribe();
        await fetchNavigationObject();
    });

    async function fetchNavigationObject() {
        const newWorkspaceList = [];
        const workspaces = (await workspaceStore.getWorkspaces()) ?? [];
        for (const workspace of workspaces) {
            const workspaceName = workspace.label;
            let showWorkspaceContents = workspaceName === selectedWorkspaceName;
            showWorkspaceContents |= workspaceList.find(
                workspaceObject => workspaceObject.label === workspaceName,
            )?.showContents;
            newWorkspaceList.push({
                label: workspaceName,
                graphs: [],
                showContents: showWorkspaceContents,
            });
            const graphs = (await graphStore.getGraphs(workspaceName)) ?? [];
            graphs.forEach(graph => newWorkspaceList.at(-1).graphs.push(graph));
        }
        workspaceList = newWorkspaceList;
    }
</script>

<div class="nav-sidebar h-full w-full">
    <div class="nav-sidebar__scroll no-scrollbar">
        {#if workspaceList && workspaceList.length > 0}
            <div class="flex flex-col gap-1 pr-2">
                {#each workspaceList as workspace}
                    <div>
                        <NavigationEntry
                            level={1}
                            label={workspace.label}
                            icon={faDatabase}
                            hasChildren={workspace.graphs.length > 0}
                            expanded={workspace.showContents}
                            isSelected={workspace.label ===
                                selectedWorkspaceName}
                            title={workspace.label}
                            onclick={() => {
                                editorState.selectedWorkspace.updateValue(
                                    workspace.label,
                                );
                            }}
                            onToggle={() => {
                                if (!workspace.graphs.length) return;
                                workspace.showContents =
                                    !workspace.showContents;
                            }}
                        />
                        {#if workspace.showContents}
                            {#each workspace.graphs as graph}
                                <NavigationEntry
                                    level={2}
                                    label={graph.keyword ?? graph.uri.suffix}
                                    secondaryLabel={graph.uri.prefix ?? ""}
                                    icon={faDiagramProject}
                                    isSelected={selectedWorkspaceName ===
                                        workspace.label &&
                                        getUri(graph) === selectedGraphUri}
                                    title={getUri(graph)}
                                    onclick={() => {
                                        editorState.selectedWorkspace.updateValue(
                                            workspace.label,
                                        );
                                        editorState.selectedGraph.updateValue(
                                            getUri(graph),
                                        );
                                    }}
                                />
                            {/each}
                        {/if}
                    </div>
                {/each}
            </div>
        {:else}
            <div class="p-4 text-left">No data available</div>
        {/if}
    </div>
</div>
