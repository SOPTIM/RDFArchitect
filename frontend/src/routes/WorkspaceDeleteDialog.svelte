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
    import {
        forceReloadTrigger,
        editorState,
    } from "$lib/sharedState.svelte.js";
    import { classStore } from "$lib/stores/classStore.ts";
    import { datasetStore } from "$lib/stores/datasetStore.ts";
    import { datatypesStore } from "$lib/stores/datatypesStore.ts";
    import { customDiagramStore } from "$lib/stores/diagramStore.ts";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { ontologyStore } from "$lib/stores/ontologyStore.ts";
    import { packageStore } from "$lib/stores/packageStore.ts";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { workspaceState } from "$lib/workspaceState.svelte.js";

    let { showDialog = $bindable(), workspaceName } = $props();

    const baseDeletionDescription =
        "All schemas and packages inside this workspace will be permanently removed.";
    let graphs = $state(null);

    async function onOpen() {
        graphs = await graphStore.getGraphs(workspaceName);
    }

    function onClose() {
        graphs = null;
    }

    async function deleteWorkspace() {
        const deletedWorkspace = workspaceName;
        if (!deletedWorkspace) {
            return;
        }
        try {
            if (!workspaceName) return;

            const res = await datasetStore.remove(workspaceName);
            if (res.error) return;

            graphStore.invalidateDataset(workspaceName);
            classStore.invalidateDataset(workspaceName);
            packageStore.invalidateDataset(workspaceName);
            datatypesStore.invalidateDataset(workspaceName);
            ontologyStore.invalidateDataset(workspaceName);
            customDiagramStore.invalidateDataset(workspaceName);

            if (editorState.selectedWorkspace.getValue() === workspaceName) {
                editorState.reset();
            }
        } finally {
            forceReloadTrigger.trigger();
        }
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    size="w-full max-w-lg"
    primaryLabel="Delete Workspace"
    onPrimary={deleteWorkspace}
    title={workspaceName
        ? `Delete Workspace "${workspaceName}"?`
        : "Delete Workspace?"}
    primaryVariant="danger"
    titleIcon={faExclamation}
    titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
>
    <div class="space-y-4 px-3 py-3">
        <p class="text-default-text w-3/4 text-sm leading-relaxed">
            {(() => {
                if (!workspaceName || graphs === null) {
                    return baseDeletionDescription;
                }
                const graphCount = graphs.length ?? 0;
                const label = graphCount === 1 ? "schema" : "schemas";
                return `${baseDeletionDescription} ${graphCount} ${label} will be deleted.`;
            })()}
            <br />
            This action is not reversible.
        </p>
    </div>
</ActionDialog>
