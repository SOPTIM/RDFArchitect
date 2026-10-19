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
    import { compareGraphs } from "$lib/utils/graph-order.js";
    import { uriSuffix } from "$lib/utils/iri.js";

    import { getUri } from "../mainpage/packageNavigation/packageNavigationUtils.svelte.js";

    const { graphUri = null, onSelectGraph } = $props();

    let workspaceList = $state([]);

    let selectedWorkspaceName = $derived(
        editorState.selectedWorkspace.getValue(),
    );

    $effect(async () => {
        forceReloadTrigger.subscribe();
        await fetchNavigationObject();
    });

    async function fetchNavigationObject() {
        const workspaces = (await workspaceStore.getWorkspaces()) ?? [];
        workspaceList = await Promise.all(
            workspaces.map(async workspace => {
                const workspaceName = workspace.label;
                const wasExpanded = workspaceList.find(
                    entry => entry.label === workspaceName,
                )?.showContents;
                return {
                    label: workspaceName,
                    graphs: await listGraphs(workspaceName),
                    showContents:
                        wasExpanded ?? workspaceName === selectedWorkspaceName,
                };
            }),
        );
    }

    /**
     * The schemas of a workspace, in the order the editor's own navigation
     * lists them — two sidebars showing the same schemas must not disagree
     * about where each of them sits.
     */
    async function listGraphs(workspaceName) {
        const graphs = (await graphStore.getGraphs(workspaceName)) ?? [];
        return [...graphs].sort((a, b) =>
            compareGraphs(
                { label: labelOf(a), uri: getUri(a) },
                { label: labelOf(b), uri: getUri(b) },
            ),
        );
    }

    /** What a schema is called in the navigation. */
    function labelOf(graph) {
        return graph.keyword ?? uriSuffix(getUri(graph));
    }
</script>

<div class="flex h-full min-h-0 w-full flex-col">
    <div class="no-scrollbar min-h-0 flex-1 overflow-y-auto py-[0.4rem]">
        {#if workspaceList && workspaceList.length > 0}
            <div class="flex flex-col gap-1 px-2">
                {#each workspaceList as workspace (workspace.label)}
                    <div>
                        <NavigationEntry
                            level={1}
                            label={workspace.label}
                            icon={faDatabase}
                            hasChildren={workspace.graphs.length > 0}
                            expanded={workspace.showContents}
                            isSelected={workspace.label ===
                                selectedWorkspaceName && !graphUri}
                            title="{workspace.label} — every change in the workspace"
                            onclick={() => {
                                editorState.selectedWorkspace.updateValue(
                                    workspace.label,
                                );
                                onSelectGraph(null);
                            }}
                            onToggle={() => {
                                if (!workspace.graphs.length) return;
                                workspace.showContents =
                                    !workspace.showContents;
                            }}
                        />
                        {#if workspace.showContents}
                            {#each workspace.graphs as graph (getUri(graph))}
                                <NavigationEntry
                                    level={2}
                                    label={labelOf(graph)}
                                    secondaryLabel={graph.uri.prefix ?? ""}
                                    icon={faDiagramProject}
                                    isSelected={selectedWorkspaceName ===
                                        workspace.label &&
                                        getUri(graph) === graphUri}
                                    title={getUri(graph)}
                                    onclick={() => {
                                        editorState.selectedWorkspace.updateValue(
                                            workspace.label,
                                        );
                                        onSelectGraph(getUri(graph));
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
