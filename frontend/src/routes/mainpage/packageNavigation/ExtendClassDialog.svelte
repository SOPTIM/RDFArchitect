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
    import { BackendConnection } from "$lib/api/backend.js";
    import WorkspaceAndGraphSelection from "$lib/components/WorkspaceAndGraphSelection.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import {
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        workspaceName,
        graphUri,
        classUUID,
    } = $props();

    let bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let selectedWorkspaceName = $state(null);
    let selectedGraphURI = $state(null);

    let disableSubmit = $derived(!selectedWorkspaceName || !selectedGraphURI);

    async function extendClass() {
        let body = {
            workspaceName: selectedWorkspaceName,
            graphUri: selectedGraphURI,
        };
        try {
            const response = await bec.extendClass(
                workspaceName,
                graphUri,
                classUUID,
                body,
            );
            if (!response.ok) {
                toastStore.error(
                    "Could not extend class",
                    "The class could not be extended. Please try again.",
                );
                return;
            }
            const newClass = await response.json();
            editorState.selectedWorkspace.updateValue(selectedWorkspaceName);
            editorState.selectedGraph.updateValue(selectedGraphURI);
            editorState.selectedDiagram.updateValue({
                type: DiagramType.PACKAGE,
                id: newClass.belongsToCategory,
            });
            forceReloadTrigger.trigger();
            toastStore.success(
                "Class extended",
                `The class has been extended in "${selectedWorkspaceName}".`,
            );
        } catch (e) {
            console.log(e);
            toastStore.error(
                "Could not extend class",
                "An unexpected error occurred. Please try again.",
            );
        }
    }
</script>

<ActionDialog
    bind:showDialog
    primaryLabel="Extend Class"
    onPrimary={extendClass}
    disablePrimary={disableSubmit}
    title="Extend Class"
>
    <div class="space-y-4 px-3 py-3">
        <p class="text-default-text w-2/3 text-sm leading-relaxed">
            Please select the schema that you want to extend this class in
        </p>
        <WorkspaceAndGraphSelection
            bind:workspace={selectedWorkspaceName}
            bind:graph={selectedGraphURI}
            allowSelectionOfReadonlyWorkspaces={false}
            displayAsCard={false}
        />
    </div>
</ActionDialog>
