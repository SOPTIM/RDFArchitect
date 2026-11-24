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

    import GraphFilePicker from "$lib/components/GraphFilePicker.svelte";
    import ImportProgressPanel from "$lib/components/ImportProgressPanel.svelte";
    import NamespacePrefixComparison from "$lib/components/NamespacePrefixComparison.svelte";
    import SkippedFilesNotice from "$lib/components/SkippedFilesNotice.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";
    import {
        ImportJobRunner,
        newWorkspaceNameError as nameErrorOfNewWorkspace,
        openImportedWorkspace,
    } from "$lib/utils/importJob.svelte.js";

    import { editorState } from "../lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        lockedWorkspaceName,
        droppedItems = null,
        forNewWorkspace = false,
    } = $props();

    const DEFAULT_WORKSPACE_NAME = "default";
    const uniqueId = uuidv4();
    const workspaceInputId = `workspaceNameImport-${uniqueId}`;
    const workspaceListId = `workspaceNamesImport-${uniqueId}`;
    const job = new ImportJobRunner(progress => {
        if (!progress.awaitingPrefixResolution) {
            submittingResolutions = false;
        }
    });
    let workspaceNameUserInput = $state("");
    let files = $state([]);
    let filesValid = $state(true);

    let readOnlyWorkspaces = $state([]);
    let modifiableWorkspaces = $state([]);

    /** What the comparison decided, and whether those decisions can be sent as they are. */
    let prefixResolutions = $state([]);
    let prefixResolutionsValid = $state(true);
    let submittingResolutions = $state(false);

    let progress = $derived(job.progress);
    let importing = $derived(progress !== null && !progress.finished);
    let resolvingPrefixes = $derived(
        progress?.awaitingPrefixResolution ?? false,
    );
    let unreadableFiles = $derived(progress?.failed ?? []);
    let primaryLabel = $derived(
        resolvingPrefixes
            ? "Continue import"
            : progress?.finished
              ? "Close"
              : "Import",
    );
    let primaryAction = $derived(
        resolvingPrefixes
            ? submitPrefixResolutions
            : progress?.finished
              ? closeAfterImport
              : importGraphs,
    );
    let primaryDisabled = $derived(
        resolvingPrefixes
            ? !prefixResolutionsValid || submittingResolutions
            : !progress?.finished && !enableSubmit,
    );
    /** Why the name cannot be that of a new workspace, empty while it can. */
    let newWorkspaceNameError = $derived(
        forNewWorkspace
            ? nameErrorOfNewWorkspace(workspaceNameUserInput, [
                  ...modifiableWorkspaces,
                  ...readOnlyWorkspaces,
              ])
            : "",
    );
    let enableSubmit = $derived(
        files.length > 0 &&
            !isWorkspaceReadOnly(workspaceNameUserInput) &&
            !newWorkspaceNameError &&
            filesValid &&
            progress === null,
    );

    const workspaceSelectionLocked = $derived(!!lockedWorkspaceName);

    async function onOpen() {
        clearInputs();
        workspaceNameUserInput = forNewWorkspace
            ? ""
            : (lockedWorkspaceName ?? editorState.selectedWorkspace.getValue());

        const workspaces = (await workspaceStore.getWorkspaces()) ?? [];
        for (const workspace of workspaces) {
            if (workspace.readOnly) {
                readOnlyWorkspaces.push(workspace.label);
            } else {
                modifiableWorkspaces.push(workspace.label);
            }
        }
    }

    function onClose() {
        void dismissImport();
        clearInputs();
    }

    function clearInputs() {
        workspaceNameUserInput = "";
        files = [];
        modifiableWorkspaces = [];
        readOnlyWorkspaces = [];
        prefixResolutions = [];
        prefixResolutionsValid = true;
        submittingResolutions = false;
    }

    function isWorkspaceReadOnly(workspaceName) {
        const targetWorkspace = workspaceName || DEFAULT_WORKSPACE_NAME;
        return readOnlyWorkspaces.includes(targetWorkspace);
    }

    function getUserInputWorkspaceName() {
        return workspaceNameUserInput || DEFAULT_WORKSPACE_NAME;
    }

    function importGraphs() {
        const workspaceName = getUserInputWorkspaceName();
        const filesToImport = files.map(entry => entry.file);
        const schemaNames = files.map(entry =>
            entry.isZip ? "" : entry.schemaName.trim(),
        );
        void job.start(workspaceName, options =>
            graphStore.startImport(
                workspaceName,
                filesToImport,
                schemaNames,
                options,
            ),
        );
    }

    /** Hands the decisions to the import; a rejected answer leaves it waiting for a new one. */
    async function submitPrefixResolutions() {
        if (!job.jobId || submittingResolutions || !prefixResolutionsValid) {
            return;
        }
        submittingResolutions = true;
        const { error } = await graphStore.resolvePrefixConflicts(
            job.workspaceName,
            job.jobId,
            prefixResolutions,
        );
        if (error) {
            submittingResolutions = false;
        }
    }

    /**
     * Ends the dialog's part in the current import and opens what it imported either way: a
     * cancelled import keeps the graphs it already wrote.
     */
    async function dismissImport() {
        const result = await job.dismiss();
        if (result && result.importedSchemaNames.length > 0) {
            openImportedWorkspace(
                result.workspaceName,
                result.importedSchemaNames,
            );
        }
    }

    function closeAfterImport() {
        void dismissImport();
        clearInputs();
        showDialog = false;
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    {primaryLabel}
    onPrimary={primaryAction}
    disablePrimary={primaryDisabled}
    secondaryLabel={importing ? "Cancel" : undefined}
    onSecondary={() => job.cancel()}
    disableSecondary={progress?.cancelling}
    closeOnPrimary={false}
    title="Import Schema (RDFS)"
    size={resolvingPrefixes ? "w-1/2" : "w-1/3"}
>
    {#if resolvingPrefixes}
        <SkippedFilesNotice
            heading="These files could not be read and will not be imported:"
            names={unreadableFiles.map(file => file.fileName)}
            placement="mx-2 mt-2"
        />
        <NamespacePrefixComparison
            comparison={progress.prefixComparison}
            bind:resolutions={prefixResolutions}
            bind:isValid={prefixResolutionsValid}
        />
    {:else if progress}
        <ImportProgressPanel {progress} />
    {:else}
        <div class="mx-2 flex h-full max-h-[80vh] flex-col">
            {#if !workspaceSelectionLocked}
                <label for={workspaceInputId} class="mb-1">
                    {forNewWorkspace ? "New workspace" : "Workspace"}
                </label>
                <input
                    class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
                    type="text"
                    id={workspaceInputId}
                    list={forNewWorkspace ? undefined : workspaceListId}
                    placeholder={forNewWorkspace
                        ? "Name of the workspace to create"
                        : DEFAULT_WORKSPACE_NAME}
                    bind:value={workspaceNameUserInput}
                />
                <!-- Suggesting existing workspaces would invite the import into one of them. -->
                {#if !forNewWorkspace}
                    <datalist id={workspaceListId}>
                        {#each modifiableWorkspaces as workspaceName}
                            <option value={workspaceName}>
                                {workspaceName}
                            </option>
                        {/each}
                    </datalist>
                {/if}
                {#if newWorkspaceNameError}
                    <div class="text-red mt-1 mb-1 h-6 text-sm">
                        {newWorkspaceNameError}
                    </div>
                {/if}
            {/if}
            <!-- Also when the workspace is fixed: the disabled Import button is otherwise the only
                 hint that this workspace takes nothing. -->
            {#if isWorkspaceReadOnly(workspaceNameUserInput)}
                <div class="text-red mt-1 mb-1 h-6 text-sm">
                    Cannot import into read-only workspace
                </div>
            {/if}
            <GraphFilePicker
                bind:files
                bind:isValid={filesValid}
                {droppedItems}
            />
        </div>
    {/if}
</ActionDialog>
