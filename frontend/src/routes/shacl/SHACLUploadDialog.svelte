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
    import { untrack } from "svelte";

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
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";
    import { graphLabeller, graphUri } from "$lib/utils/graph-label.js";
    import { compareGraphs } from "$lib/utils/graph-order.js";

    import SchemaChecklist from "./workbench/SchemaChecklist.svelte";

    let {
        showDialog = $bindable(),
        lockedWorkspaceName,
        lockedGraphUri,
    } = $props();
    const fileInputId = `actual-file-input-shacl-upload-${crypto.randomUUID()}`;
    let workspaceName = $state("");
    /** The workspace's schemas, as `{ uri, label }`, and the ones the file will apply to. */
    let schemas = $state([]);
    let chosenSchemas = $state([]);
    let file = $state(null);

    const lockedWorkspaceNameValue = $derived(lockedWorkspaceName);
    const lockedGraphUriValue = $derived(lockedGraphUri);
    let disableSubmit = $derived(!file || !workspaceName);

    /** Ticks the schema in view to begin with, when the workspace has it. */
    $effect(() => {
        const workspace = workspaceName;
        // Closing is not asking again: the import that closes the dialog still reads the choice.
        if (!showDialog) {
            return;
        }
        schemas = [];
        chosenSchemas = [];
        if (!workspace) {
            return;
        }
        let current = true;
        graphStore.getGraphs(workspace).then(graphs => {
            if (!current) {
                return;
            }
            const labelOf = graphLabeller(graphs ?? []);
            schemas = (graphs ?? [])
                .map(graph => ({ uri: graphUri(graph), label: labelOf(graph) }))
                .sort(compareGraphs);
            const inView =
                lockedGraphUriValue ??
                untrack(() => editorState.selectedGraph.getValue());
            chosenSchemas = schemas.some(schema => schema.uri === inView)
                ? [inView]
                : [];
        });
        return () => (current = false);
    });

    async function onOpen() {
        if (showDialog) {
            workspaceName =
                lockedWorkspaceNameValue ??
                editorState.selectedWorkspace.getValue();
            file = null;
        }
    }

    /**
     * Adds the file to the workspace as its own constraints document.
     *
     * Under its own name and with its text as written: official constraints files carry comments
     * and a deliberate ordering that have to come back unchanged.
     *
     * The server does not enforce read-only, so this has to: a workspace locked by the caller
     * never went through the picker, and an unknown answer is treated as read-only.
     */
    async function importGraph() {
        const path = { datasetName: workspaceName };
        const fileName = file.name;
        if ((await workspaceStore.isReadOnly(workspaceName)) !== false) {
            toastStore.error(
                "Import refused",
                `"${workspaceName}" is read-only. Enable editing to add constraints to it.`,
            );
            return;
        }
        try {
            const { data: documents } = await listShapesDocuments({ path });
            const { error } = await createShapesDocumentFromFile({
                path,
                query: {
                    name: uniqueDocumentName(
                        (documents ?? []).map(document => document.name),
                        fileName,
                    ),
                    schema: chosenSchemas,
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
                `"${fileName}" was added to "${workspaceName}".`,
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
            {lockedWorkspaceName}
            allowSelectionOfReadonlyWorkspaces={false}
            displayAsCard={false}
            showGraphSelection={false}
        />
        {#if workspaceName}
            <p class="mt-3 mb-1 text-sm">Applies to</p>
            <div
                class="border-border max-h-48 overflow-y-auto rounded border p-2"
            >
                <SchemaChecklist
                    {schemas}
                    selected={chosenSchemas}
                    onchange={next => (chosenSchemas = next)}
                />
            </div>
        {/if}
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
