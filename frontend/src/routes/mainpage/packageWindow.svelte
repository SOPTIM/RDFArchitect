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
    import { Pane, Splitpanes } from "svelte-splitpanes";

    import { ClassType, editorState } from "$lib/sharedState.svelte.js";

    import ClassEditorHost from "./classEditor/classEditorHost.svelte";
    import RenderingWrapper from "./renderingWrapper.svelte";

    let classEditorPaneWidth = $state(27);
    let paneSizeByPackage = $state({});

    let classDatasetName = $derived(
        editorState.selectedClassDataset.getValue() ??
            editorState.selectedDataset.getValue(),
    );
    let classGraphUri = $derived(
        editorState.selectedClassGraph.getValue() ??
            editorState.selectedGraph.getValue(),
    );

    const selectionTrigger = $derived([
        editorState.selectedDiagram.subscribe(),
        editorState.selectedClass.subscribe(),
    ]);
    const isClassSelected = $derived(
        selectionTrigger && !!editorState.selectedClass.getProperty("id"),
    );
    const renderingKey = $derived(
        `${editorState.selectedDataset.getValue() ?? ""}::${editorState.selectedGraph.getValue() ?? ""}::${editorState.selectedDiagram.getProperty("id") ?? ""}`,
    );

    const isMergedClass = $derived(
        editorState.selectedClass.getProperty("type") ===
            ClassType.MERGED_CLASS,
    );

    $effect(() => {
        editorState.selectedDataset.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedDiagram.subscribe();
        const packageKey = getPackageKey();
        if (packageKey && paneSizeByPackage[packageKey]) {
            classEditorPaneWidth = paneSizeByPackage[packageKey];
        } else {
            classEditorPaneWidth = 27;
        }
    });

    function getPackageKey() {
        const dataset = editorState.selectedDataset.getValue();
        const graph = editorState.selectedGraph.getValue();
        const pack = editorState.selectedDiagram.getProperty("id");
        if (!dataset || !graph || !pack) {
            return null;
        }
        return `${dataset}::${graph}::${pack}`;
    }

    function handleSplitPaneResize(event) {
        if (event.detail && event.detail.length > 1) {
            if (!editorState.selectedClass.getProperty("id")) {
                return;
            }
            classEditorPaneWidth = event.detail[1].size;
            const packageKey = getPackageKey();
            if (packageKey) {
                paneSizeByPackage = {
                    ...paneSizeByPackage,
                    [packageKey]: classEditorPaneWidth,
                };
            }
        }
    }
</script>

<div class="h-full w-full overflow-hidden">
    <Splitpanes
        theme="opencgmes-theme"
        class="flex h-full"
        onresize={handleSplitPaneResize}
    >
        <Pane
            size={isClassSelected ? 100 - classEditorPaneWidth : 100}
            class="bg-window-background h-full overflow-hidden"
        >
            {#key renderingKey}
                <div class="h-full">
                    <RenderingWrapper />
                </div>
            {/key}
        </Pane>

        <Pane
            size={isClassSelected ? classEditorPaneWidth : 0}
            minSize={isClassSelected ? 25 : 0}
            class="h-full overflow-auto"
        >
            {#if isClassSelected}
                <ClassEditorHost
                    datasetName={classDatasetName}
                    graphUri={classGraphUri}
                    classUuid={editorState.selectedClass.getProperty("id")}
                    isMerged={isMergedClass}
                />
            {/if}
        </Pane>
    </Splitpanes>
</div>
