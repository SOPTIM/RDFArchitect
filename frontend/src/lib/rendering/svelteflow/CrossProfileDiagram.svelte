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
    import { SvelteFlowProvider } from "@xyflow/svelte";
    import { onMount } from "svelte";
    import { Pane, Splitpanes } from "svelte-splitpanes";

    import { BackendConnection } from "$lib/api/backend.js";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import SvelteFlowWrapper from "$lib/rendering/svelteflow/svelteFlowWrapper.svelte";
    import {
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import MergedClassEditor from "./../../../routes/mainpage/classEditor/mergedClassEditor.svelte";
    import { mapCrossProfileDiagramToFlow } from "./components/crossProfileDiagramUtils.js";

    let { datasetName } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let nodes = $state.raw([]);
    let edges = $state.raw([]);
    let isLoading = $state(true);
    let isFlowLoading = $state(true);
    let error = $state(null);
    let mergedClassByUuid = $state(new Map());

    let classEditorPaneWidth = $state(35);

    const selectionTrigger = $derived(
        editorState.selectedClassUUID.subscribe(),
    );
    const isClassSelected = $derived(
        selectionTrigger !== undefined &&
            !!editorState.selectedClassUUID.getValue(),
    );
    const selectedMergedClass = $derived(
        isClassSelected
            ? (mergedClassByUuid.get(
                  editorState.selectedClassUUID.getValue(),
              ) ?? null)
            : null,
    );
    const classEditorKey = $derived(
        editorState.selectedClassUUID.getValue() ?? "",
    );

    $effect(() => {
        forceReloadTrigger.subscribe();
        loadDiagram();
    });

    onMount(() => loadDiagram());

    async function loadDiagram() {
        isLoading = true;
        isFlowLoading = true;
        error = null;
        try {
            const res = await bec.getCrossProfileDiagramForDataset(datasetName);
            if (!res.ok) {
                error = `Fehler beim Laden des Diagramms: ${res.status}`;
                return;
            }
            const dto = await res.json();
            const flow = mapCrossProfileDiagramToFlow(dto);
            nodes = flow.nodes;
            edges = flow.edges;
            mergedClassByUuid = new Map(dto.classes.map(c => [c.uuid, c]));
            editorState.selectedDiagram.updateValue({
                type: DiagramType.CROSS_PROFILE,
                id: dto.diagramId,
            });
        } catch (e) {
            error = e.message;
        } finally {
            isLoading = false;
        }
    }

    function handleSplitPaneResize(event) {
        if (event.detail && event.detail.length > 1 && isClassSelected) {
            classEditorPaneWidth = event.detail[1].size;
        }
    }
</script>

<div class="h-full w-full overflow-hidden">
    <SvelteFlowProvider>
        <Splitpanes
            theme="opencgmes-theme"
            class="flex h-full"
            onresize={handleSplitPaneResize}
        >
            <Pane
                size={isClassSelected ? 100 - classEditorPaneWidth : 100}
                class="bg-window-background relative h-full overflow-hidden"
            >
                <div
                    class="h-full w-full"
                    style:visibility={isLoading || isFlowLoading
                        ? "hidden"
                        : "visible"}
                >
                    {#if !error}
                        <SvelteFlowWrapper
                            {nodes}
                            {edges}
                            bind:isLoading={isFlowLoading}
                        />
                    {/if}
                </div>

                {#if isLoading || isFlowLoading}
                    <div
                        class="bg-window-background absolute inset-0 z-50 flex items-center justify-center"
                    >
                        <LoadingSpinner />
                    </div>
                {:else if error}
                    <div class="flex h-full items-center justify-center p-4">
                        <p class="text-red-500">{error}</p>
                    </div>
                {/if}
            </Pane>

            <Pane
                size={isClassSelected ? classEditorPaneWidth : 0}
                minSize={isClassSelected ? 25 : 0}
                class="h-full overflow-auto"
            >
                {#if selectedMergedClass}
                    {#key classEditorKey}
                        <MergedClassEditor mergedClass={selectedMergedClass} />
                    {/key}
                {/if}
            </Pane>
        </Splitpanes>
    </SvelteFlowProvider>
</div>
