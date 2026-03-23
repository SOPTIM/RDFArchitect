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

    let classEditorPaneWidth = 30;
    let classDatasetName = $derived(
        editorState.selectedClassDataset.getValue() ??
            editorState.selectedDataset.getValue(),
    );
    let classGraphUri = $derived(
        editorState.selectedClassGraph.getValue() ??
            editorState.selectedGraph.getValue(),
    );

    function handleSplitPaneResize(event) {
        /* event.detail is an array holding size information about each pane.
         Since this Splitpane has two panes, event.detail has two entries
         the second entry (index 1) holds the size information about the class editor pane */
        if (event.detail && event.detail.length > 1) {
            classEditorPaneWidth = event.detail[1].size;
        }
    }
</script>

<div class="relative h-full w-full overflow-hidden">
    {#key editorState.selectedPackageUUID.subscribe()}
        <div class="h-full">
            <RenderingWrapper
                rightInsetPercent={editorState.selectedClassUUID.getValue()
                    ? classEditorPaneWidth
                    : 0}
            />
        </div>
    {/key}

    {#if editorState.selectedClassUUID.getValue()}
        <Splitpanes
            theme="opencgmes-theme"
            class="pointer-events-none absolute top-0 right-0 h-screen w-screen"
            onresize={handleSplitPaneResize}
        >
            <Pane
                size={100 - classEditorPaneWidth}
                class="pointer-events-none bg-transparent"
            ></Pane>
            <Pane
                size={classEditorPaneWidth}
                minSize={25}
                class="pointer-events-auto h-full overflow-auto"
            >
                <ClassEditor
                    datasetName={classDatasetName}
                    graphUri={classGraphUri}
                    classUuid={editorState.selectedClassUUID.getValue()}
                />
            </Pane>
        </Splitpanes>
    {/if}
</div>
