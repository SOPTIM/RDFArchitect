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

    import { editorState } from "$lib/sharedState.svelte.js";

    import ClassEditor from "./classEditor/classEditor.svelte";
    import RenderingWrapper from "./renderingWrapper.svelte";

    let classEditorPaneWidth = $state(30);
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
        editorState.selectedPackageUUID.subscribe(),
        editorState.selectedClassUUID.subscribe(),
    ]);
    const isClassSelected = $derived(
        selectionTrigger && !!editorState.selectedClassUUID.getValue(),
    );
    const classEditorKey = $derived(
        `${classDatasetName ?? ""}::${classGraphUri ?? ""}::${editorState.selectedClassUUID.getValue() ?? ""}::${editorState.selectedClassUUID.subscribe()}`,
    );

    $effect(() => {
        editorState.selectedDataset.subscribe();
        editorState.selectedGraph.subscribe();
        editorState.selectedPackageUUID.subscribe();
        const packageKey = getPackageKey();
        if (packageKey && paneSizeByPackage[packageKey]) {
            classEditorPaneWidth = paneSizeByPackage[packageKey];
        } else {
            classEditorPaneWidth = 30;
        }
    });

    function getPackageKey() {
        const dataset = editorState.selectedDataset.getValue();
        const graph = editorState.selectedGraph.getValue();
        const pack = editorState.selectedPackageUUID.getValue();
        if (!dataset || !graph || !pack) {
            return null;
        }
        return `${dataset}::${graph}::${pack}`;
    }

    function handleSplitPaneResize(event) {
        if (event.detail && event.detail.length > 1) {
            if (!editorState.selectedClassUUID.getValue()) {
                return;
            }
            // event.detail[1] holds the size of the class editor pane.
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

<div class="relative h-full w-full overflow-hidden">
    <Splitpanes
        theme="opencgmes-theme"
        class={`h-full w-full ${isClassSelected ? "" : "editor-closed"}`}
        onresize={handleSplitPaneResize}
    >
        <Pane
            size={isClassSelected ? 100 - classEditorPaneWidth : 100}
            minSize={50}
        >
            <RenderingWrapper />
        </Pane>
        <Pane
            size={isClassSelected ? classEditorPaneWidth : 0}
            minSize={isClassSelected ? 25 : 0}
            class={`h-full ${isClassSelected ? "overflow-auto" : "pointer-events-none hidden overflow-hidden"}`}
        >
            {#if isClassSelected}
                {#key classEditorKey}
                    <ClassEditor
                        datasetName={classDatasetName}
                        graphUri={classGraphUri}
                        classUuid={editorState.selectedClassUUID.getValue()}
                    />
                {/key}
            {/if}
        </Pane>
    </Splitpanes>
</div>

<style>
    :global(.editor-closed .splitpanes__splitter) {
        display: none;
    }
</style>
