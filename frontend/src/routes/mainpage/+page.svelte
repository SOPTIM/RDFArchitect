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
    import { onMount } from "svelte";
    import { Pane, Splitpanes } from "svelte-splitpanes";
    import { validate } from "uuid";

    import { resolveIri as fetchResolveIRI } from "$lib/api/generated/index";
    import { asyncValue } from "$lib/asyncValue.svelte.js";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { extendSourceRequest } from "$lib/extendSourceRequest.svelte.js";
    import { DiagramType, editorState } from "$lib/sharedState.svelte.js";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";
    import { resolveTermTarget } from "$lib/utils/deep-link.js";
    import { navigateToClass } from "$lib/utils/model-navigation.js";

    import NoSchemaPlaceholder from "./emptyStates/NoSchemaPlaceholder.svelte";
    import NoWorkspacePlaceholder from "./emptyStates/NoWorkspacePlaceholder.svelte";
    import ExtendSourceDialog from "./packageNavigation/ExtendSourceDialog.svelte";
    import PackageNavigation from "./packageNavigation/packageNavigation.svelte";
    import PackageWindow from "./packageWindow.svelte";
    import WorkspaceTabs from "./workspaceTabs/WorkspaceTabs.svelte";

    // The placeholder replaces the navigation, so the schema count is loaded
    // here — inside the navigation it would never refresh again.
    const schemaCount = asyncValue(() => activeWorkspace, loadSchemaCount);

    const activeWorkspace = $derived(editorState.selectedWorkspace.getValue());
    const workspaceNames = $derived(
        ($workspaceStore.data ?? []).map(ws => ws.label),
    );
    const hasNoWorkspaces = $derived(
        $workspaceStore.data !== null && workspaceNames.length === 0,
    );
    const schemaCountKnown = $derived(schemaCount.current !== null);
    const hasNoSchemas = $derived(
        !hasNoWorkspaces && schemaCount.current === 0,
    );

    onMount(() => {
        // Nothing awaits this, so an unhandled rejection would leave the page silently unchanged.
        parseModelSelectionUrlParameters().catch(error => {
            console.error("Could not apply the URL parameters:", error);
            toastStore.error(
                "Could not open the link",
                "The model selection in this link could not be applied.",
            );
        });
    });

    async function loadSchemaCount(workspaceName) {
        const graphs = (await graphStore.getGraphs(workspaceName)) ?? [];
        return graphs.length;
    }

    async function parseModelSelectionUrlParameters() {
        const url = new URL(window.location.href);
        const queryParams = new URLSearchParams(url.search);
        const workspace = queryParams.get("dataset") || null;
        const graph = queryParams.get("graph") || null;
        let pack = queryParams.get("package") || null;
        const classRef = queryParams.get("class") || null;

        // A term deep link (IRI or uuid) selects the class and its package diagram; an attribute,
        // association or enum entry selects the class declaring it. workspace and graph merely
        // narrow the lookup. Falls through to the plain selection when not found.
        if (classRef && (await openTermFromUrl(workspace, graph, classRef))) {
            return;
        }

        if (!workspace) return;
        editorState.selectedWorkspace.updateValue(workspace);
        editorState.selectedGraph.updateValue(graph);
        if (!graph || !pack) return;
        if (pack !== "default" && !validate(pack)) {
            pack = await resolveIRI(workspace, graph, pack);
            if (!pack) return;
        }
        editorState.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: pack,
        });
    }

    async function openTermFromUrl(workspace, graph, ref) {
        const target = await resolveTermTarget({
            dataset: workspace,
            graph,
            ref,
        });
        if (!target) {
            toastStore.error(
                "Not found",
                `No schema in this session contains "${ref}".`,
            );
            return false;
        }
        navigateToClass(target);
        return true;
    }

    async function resolveIRI(workspace, graph, iri) {
        return await fetchResolveIRI({
            path: {
                datasetName: workspace,
                graphURI: graph,
                iriIdentifier: iri,
            },
        }).then(res => res.data);
    }
</script>

<div class="flex h-full w-full flex-col">
    <WorkspaceTabs />
    {#if hasNoWorkspaces}
        <NoWorkspacePlaceholder />
    {:else if !schemaCountKnown}
        <div
            class="bg-window-background flex min-h-0 flex-1 items-center justify-center"
        >
            <LoadingSpinner />
        </div>
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

{#if extendSourceRequest.open}
    <ExtendSourceDialog
        bind:showDialog={extendSourceRequest.open}
        workspaceName={extendSourceRequest.workspaceName}
        candidates={extendSourceRequest.candidates}
        targetLabel={extendSourceRequest.targetLabel}
        onPick={picked => extendSourceRequest.onPick?.(picked)}
    />
{/if}
