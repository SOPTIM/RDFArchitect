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
    import WorkspaceAndGraphSelection from "$lib/components/WorkspaceAndGraphSelection.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import {
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { classStore } from "$lib/stores/classStore.ts";
    import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";

    let {
        showDialog = $bindable(),
        workspaceName,
        graphUri,
        classUUID,
    } = $props();

    let selectedWorkspaceName = $state(null);
    let selectedGraphURI = $state(null);

    let disableSubmit = $derived(!selectedWorkspaceName || !selectedGraphURI);

    async function extendClass() {
        let body = {
            workspaceName: selectedWorkspaceName,
            graphUri: selectedGraphURI,
        };

        const { data, error } = await classStore.extendClass(
            workspaceName,
            graphUri,
            classUUID,
            body,
        );
        if (error) return;

        crossProfileStore.invalidateWorkspace(selectedWorkspaceName);
        editorState.selectedWorkspace.updateValue(selectedWorkspaceName);
        editorState.selectedGraph.updateValue(selectedGraphURI);
        editorState.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: data.belongsToCategory,
        });
        forceReloadTrigger.trigger();
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
