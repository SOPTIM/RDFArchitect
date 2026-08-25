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
    import { faExclamation } from "@fortawesome/free-solid-svg-icons";

    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { classStore } from "$lib/stores/classStore.ts";
    import { datatypesStore } from "$lib/stores/datatypesStore.ts";
    import { customDiagramStore } from "$lib/stores/diagramStore.ts";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { ontologyStore } from "$lib/stores/ontologyStore.ts";
    import { packageStore } from "$lib/stores/packageStore.ts";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable() } = $props();

    let workspaceName = $state();
    let graphURI = $state();

    let disableSubmit = $derived(!workspaceName || !graphURI);

    async function onOpen() {
        workspaceName = editorState.selectedWorkspace.getValue();
        graphURI = editorState.selectedGraph.getValue();
    }

    function onClose() {
        workspaceName = null;
        graphURI = null;
    }

    async function deleteGraph() {
        const { error } = await graphStore.remove(workspaceName, graphURI);
        if (error) return;

        editorState.selectedWorkspace.updateValue(null);
        editorState.selectedGraph.updateValue(null);
        editorState.selectedDiagram.updateValue({
            type: null,
            id: null,
        });
        editorState.selectedClassWorkspace.updateValue(null);
        editorState.selectedClassGraph.updateValue(null);
        editorState.selectedClass.updateValue({ type: null, id: null });

        classStore.invalidateGraph(workspaceName, graphURI);
        packageStore.invalidateGraph(workspaceName, graphURI);
        datatypesStore.invalidateGraph(workspaceName, graphURI);
        ontologyStore.invalidateGraph(workspaceName, graphURI);
        customDiagramStore.invalidateGraph(workspaceName, graphURI);

        forceReloadTrigger.trigger();
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    size="w-full max-w-lg"
    primaryLabel="Delete Schema"
    onPrimary={deleteGraph}
    disablePrimary={disableSubmit}
    title={graphURI ? `Delete Schema "${graphURI}"?` : "Delete Schema?"}
    titleIcon={faExclamation}
    titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
>
    <div class="space-y-4 px-3 py-3">
        <p class="text-default-text w-3/4 text-sm leading-relaxed">
            {workspaceName
                ? `The schema will be removed from workspace "${workspaceName}".`
                : "Select a workspace and schema to delete."}
            <br />
            This action is not reversible.
        </p>
    </div>
</ActionDialog>
