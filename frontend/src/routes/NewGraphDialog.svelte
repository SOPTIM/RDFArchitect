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
    import { v4 as uuidv4 } from "uuid";

    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { URI } from "$lib/models/dto/index.ts";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import {
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable(), lockedWorkspaceName } = $props();

    const uniqueId = uuidv4();
    const defaultGraphUriPrefix = "http://graph#";
    const uriSchemePattern = /^[a-zA-Z][a-zA-Z\d+.-]*:/;
    const workspaceInputId = `workspaceNameNewGraph-${uniqueId}`;
    const workspaceListId = `workspaceNamesNewGraph-${uniqueId}`;
    const graphInputId = `graphUriNewGraph-${uniqueId}`;

    let workspaceNameUserInput = $state("");
    let graphUriUserInput = $state("");
    let readOnlyWorkspaces = $state([]);
    let modifiableWorkspaces = $state([]);
    let graphNames = $state([]);

    const workspaceSelectionLocked = $derived(!!lockedWorkspaceName);
    const workspaceIsReadOnly = $derived(
        workspaceNameUserInput &&
            readOnlyWorkspaces.includes(workspaceNameUserInput),
    );

    const resolvedGraphUri = $derived(resolveGraphUri(graphUriUserInput));
    const graphExists = $derived(
        !!resolvedGraphUri &&
            graphNames.some(g => new URI(g).toString() === resolvedGraphUri),
    );
    const disableSubmit = $derived(
        !workspaceNameUserInput ||
            !resolvedGraphUri ||
            workspaceIsReadOnly ||
            graphExists,
    );

    function resolveGraphUri(graphInput) {
        const trimmedInput = graphInput.trim();
        if (!trimmedInput) {
            return "";
        }
        if (uriSchemePattern.test(trimmedInput)) {
            return trimmedInput;
        }
        return defaultGraphUriPrefix + trimmedInput;
    }

    async function onOpen() {
        workspaceNameUserInput =
            lockedWorkspaceName ??
            editorState.selectedWorkspace.getValue() ??
            "";
        graphUriUserInput = "";

        const workspaces = (await workspaceStore.getWorkspaces()) ?? [];
        readOnlyWorkspaces = [];
        modifiableWorkspaces = [];
        for (const workspace of workspaces) {
            if (workspace.readOnly) {
                readOnlyWorkspaces.push(workspace.label);
            } else {
                modifiableWorkspaces.push(workspace.label);
            }
        }
        await refreshGraphNames();
    }

    function onClose() {
        workspaceNameUserInput = "";
        graphUriUserInput = "";
        graphNames = [];
    }

    async function refreshGraphNames() {
        if (!workspaceNameUserInput) {
            graphNames = [];
            return;
        }

        if (!modifiableWorkspaces.includes(workspaceNameUserInput)) {
            graphNames = [];
            return;
        }

        graphNames = (await graphStore.getGraphs(workspaceNameUserInput)) ?? [];
    }

    async function addGraph() {
        const workspaceNameLocal = workspaceNameUserInput;
        const graphURILocal = resolvedGraphUri;

        const { error } = await graphStore.addEmptyGraph(
            workspaceNameLocal,
            graphURILocal,
        );
        if (error) return;

        editorState.selectedWorkspace.updateValue(workspaceNameLocal);
        editorState.selectedGraph.updateValue(graphURILocal);
        editorState.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: "default",
        });
        editorState.selectedClass.updateValue({ type: null, id: null });

        graphStore.invalidateWorkspace(workspaceNameLocal);
        workspaceStore.invalidate();
        forceReloadTrigger.trigger();
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Create Schema"
    onPrimary={addGraph}
    title="New Schema"
    disablePrimary={disableSubmit}
>
    <div class="mx-2 flex h-full flex-col">
        {#if !workspaceSelectionLocked}
            <label for={workspaceInputId} class="mb-1">Workspace</label>
            <input
                class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
                type="text"
                id={workspaceInputId}
                list={workspaceListId}
                placeholder="Workspace name"
                bind:value={workspaceNameUserInput}
                onchange={() => refreshGraphNames()}
            />
            <datalist id={workspaceListId}>
                {#each modifiableWorkspaces as workspaceName}
                    <option value={workspaceName}>{workspaceName}</option>
                {/each}
            </datalist>

            {#if workspaceIsReadOnly}
                <div class="mt-1 mb-1 h-6 text-sm">
                    Cannot create schemas in read-only workspace
                </div>
            {/if}
        {/if}

        <label for={graphInputId} class="mt-2 mb-1">Schema (RDFS)</label>
        <input
            class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
            type="text"
            id={graphInputId}
            placeholder={defaultGraphUriPrefix}
            autocomplete="off"
            bind:value={graphUriUserInput}
        />

        {#if graphExists}
            <div class="mt-1 mb-1 h-6 text-sm">Schema already exists</div>
        {/if}
    </div>
</ActionDialog>
