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
    import {
        createShapesDocumentFromFile,
        listShapesDocuments,
    } from "$lib/api/generated/index.ts";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import WorkspaceAndGraphSelection from "$lib/components/WorkspaceAndGraphSelection.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { uniqueDocumentName } from "$lib/shacl/documentNames.js";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        lockedWorkspaceName,
        lockedGraphUri,
    } = $props();
    const fileInputId = `actual-file-input-shacl-upload-${crypto.randomUUID()}`;
    let workspaceName = $state("");
    let graphURI = $state("");
    let file = $state(null);

    const lockedWorkspaceNameValue = $derived(lockedWorkspaceName);
    const lockedGraphUriValue = $derived(lockedGraphUri);
    let disableSubmit = $derived(!file || !workspaceName || !graphURI);

    async function onOpen() {
        if (showDialog) {
            workspaceName =
                lockedWorkspaceNameValue ??
                editorState.selectedWorkspace.getValue();
            graphURI =
                lockedGraphUriValue ?? editorState.selectedGraph.getValue();
            file = null;
        }
    }

    /**
     * Adds the file to the graph as its own constraints document.
     *
     * It used to replace the graph's default document instead, which cost two things a user
     * noticed: the file's name, since everything landed in "custom.ttl", and the file itself,
     * since that path stored a parsed graph and threw the text away. Official constraints files
     * carry comments and a deliberate ordering that have to come back unchanged.
     */
    async function importGraph() {
        const path = { datasetName: workspaceName, graphURI: graphURI };
        const fileName = file.name;
        try {
            const { data: documents } = await listShapesDocuments({ path });
            const { error } = await createShapesDocumentFromFile({
                path,
                query: {
                    name: uniqueDocumentName(
                        (documents ?? []).map(document => document.name),
                        fileName,
                    ),
                },
                body: { file },
            });
            if (error) {
                toastStore.error(
                    "Import failed",
                    error.detail ?? `Could not import "${fileName}".`,
                );
                return;
            }
            toastStore.success(
                "Constraints imported",
                `"${fileName}" was added to "${graphURI}".`,
            );
        } catch (e) {
            console.warn("failed to import the SHACL file:", e);
            toastStore.error(
                "Import failed",
                "An unexpected error occurred while uploading the SHACL file.",
            );
        } finally {
            forceReloadTrigger.trigger();
        }
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    primaryLabel="Import"
    disablePrimary={disableSubmit}
    onPrimary={importGraph}
    title="Import Constraints (SHACL)"
>
    <div class="mx-2 flex h-full flex-col">
        <WorkspaceAndGraphSelection
            bind:workspace={workspaceName}
            bind:graph={graphURI}
            {lockedWorkspaceName}
            {lockedGraphUri}
            displayAsCard={false}
        />
        <input
            class="hidden"
            type="file"
            id={fileInputId}
            onchange={event => {
                file = event.target.files[0];
            }}
        />
        <div class="mt-4 flex h-9 w-full space-x-4">
            <div class="h-9 w-24">
                <ButtonControl
                    height={9}
                    callOnClick={() => {
                        document.getElementById(fileInputId).click();
                    }}
                >
                    Select File
                </ButtonControl>
            </div>
            <div class="h-9 w-full content-center">
                <p class="break-all">
                    {#if file}
                        {file.name}
                    {:else}
                        No file selected
                    {/if}
                </p>
            </div>
        </div>
    </div>
</ActionDialog>
