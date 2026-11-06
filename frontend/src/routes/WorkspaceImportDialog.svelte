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
    import JSZip from "jszip";
    import { v4 as uuidv4 } from "uuid";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import FileDropZone from "$lib/components/FileDropZone.svelte";
    import ImportProgressPanel from "$lib/components/ImportProgressPanel.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";
    import {
        isWorkspaceArchiveFile,
        workspaceArchiveMediaType,
        workspaceNameOfArchiveFile,
    } from "$lib/utils/fileUtils";
    import {
        ImportProgress,
        JobState,
    } from "$lib/utils/importProgress.svelte.js";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable() } = $props();

    const POLL_INTERVAL_MS = 300;
    const MAX_FAILED_POLLS = 3;
    const uniqueId = uuidv4();
    const nameInputId = `workspaceArchiveName-${uniqueId}`;
    const fileInputId = `workspaceArchiveFile-${uniqueId}`;

    let archive = $state(null);
    let rejectedFileName = $state(null);
    let workspaceName = $state("");
    let existingWorkspaces = $state([]);

    let progress = $state(null);
    let importJobId = null;
    let importWorkspaceName = null;
    let pollTimeout = null;
    let failedPolls = 0;

    let importing = $derived(progress !== null && !progress.finished);
    let nameError = $derived.by(() => {
        const name = workspaceName.trim();
        if (name === "") {
            return "Enter a name for the new workspace";
        }
        if (existingWorkspaces.includes(name)) {
            return `A workspace named "${name}" already exists`;
        }
        return "";
    });

    async function onOpen() {
        clearInputs();
        const workspaces = (await workspaceStore.getWorkspaces()) ?? [];
        existingWorkspaces = workspaces.map(workspace => workspace.label);
    }

    function onClose() {
        void dismissImport();
        clearInputs();
    }

    function clearInputs() {
        archive = null;
        rejectedFileName = null;
        workspaceName = "";
    }

    async function selectArchive(files) {
        const file = Array.from(files ?? [])[0];
        if (!file) {
            return;
        }
        if (!isWorkspaceArchiveFile(file.name)) {
            rejectedFileName = file.name;
            return;
        }
        rejectedFileName = null;
        archive = file;
        workspaceName = "";
        const name =
            (await nameInManifest(file)) ??
            workspaceNameOfArchiveFile(file.name);
        if (archive === file) {
            workspaceName = name;
        }
    }

    async function nameInManifest(file) {
        try {
            const zip = await JSZip.loadAsync(file);
            const manifest = zip.file("manifest.json");
            if (!manifest) {
                return null;
            }
            const name = JSON.parse(await manifest.async("string"))?.name;
            return typeof name === "string" && name.trim() ? name : null;
        } catch {
            return null;
        }
    }

    async function startImport() {
        if (progress !== null || !archive || nameError) {
            return;
        }
        const name = workspaceName.trim();
        const currentProgress = new ImportProgress();
        progress = currentProgress;
        failedPolls = 0;
        importWorkspaceName = name;

        const { data, error } = await graphStore.startWorkspaceImport(
            name,
            archive,
            {
                onUploadProgress: percent =>
                    currentProgress.uploadProgress(percent),
                signal: currentProgress.signal,
            },
        );
        if (progress !== currentProgress) {
            if (data) {
                await graphStore.cancelImport(name, data.jobId);
            }
            return;
        }
        if (error || !data) {
            if (currentProgress.cancelling) {
                currentProgress.cancelled();
            } else {
                currentProgress.fail(
                    error?.message ?? "The import could not be started.",
                );
            }
            return;
        }

        currentProgress.uploaded();
        importJobId = data.jobId;
        if (currentProgress.cancelling) {
            await graphStore.cancelImport(name, data.jobId);
        }
        pollImportStatus();
    }

    function pollImportStatus() {
        stopPolling();
        pollTimeout = setTimeout(async () => {
            const currentProgress = progress;
            const jobId = importJobId;
            if (!currentProgress || !jobId) {
                return;
            }
            const { data, error } = await graphStore.getImportStatus(
                importWorkspaceName,
                jobId,
            );
            if (progress !== currentProgress) {
                return;
            }
            if (error || !data) {
                failedPolls += 1;
                if (failedPolls > MAX_FAILED_POLLS) {
                    currentProgress.fail(
                        "The progress of the import could not be read.",
                    );
                    return;
                }
                pollImportStatus();
                return;
            }
            failedPolls = 0;
            currentProgress.apply(data);
            if (!currentProgress.finished) {
                pollImportStatus();
            }
        }, POLL_INTERVAL_MS);
    }

    function stopPolling() {
        if (pollTimeout !== null) {
            clearTimeout(pollTimeout);
            pollTimeout = null;
        }
    }

    function requestCancel() {
        progress?.cancel();
        if (importWorkspaceName && importJobId) {
            void graphStore.cancelImport(importWorkspaceName, importJobId);
        }
    }

    async function dismissImport() {
        stopPolling();
        const currentProgress = progress;
        const jobId = importJobId;
        const name = importWorkspaceName;
        progress = null;
        importJobId = null;
        importWorkspaceName = null;

        if (!currentProgress) {
            return;
        }
        let finalStatus = currentProgress.finished
            ? {
                  state: currentProgress.jobState,
                  importedGraphUris: currentProgress.importedGraphUris,
              }
            : null;
        if (!finalStatus) {
            currentProgress.cancel();
            if (!name || !jobId) {
                return;
            }
            await graphStore.cancelImport(name, jobId);
            // It may have completed before the cancellation arrived.
            finalStatus = (await graphStore.getImportStatus(name, jobId)).data;
        }
        if (finalStatus?.state === JobState.COMPLETED) {
            openImportedWorkspace(name, finalStatus.importedGraphUris ?? []);
        }
    }

    function closeAfterImport() {
        void dismissImport();
        clearInputs();
        showDialog = false;
    }

    function openImportedWorkspace(name, importedGraphUris) {
        editorState.selectedWorkspace.updateValue(name);
        editorState.selectedGraph.updateValue(importedGraphUris[0] ?? null);
        editorState.selectedDiagram.updateValue({ type: null, id: null });
        editorState.selectedClassWorkspace.updateValue(null);
        editorState.selectedClassGraph.updateValue(null);
        editorState.selectedClass.updateValue({ type: null, id: null });

        graphStore.invalidateWorkspace(name);
        workspaceStore.invalidate();
        crossProfileStore.invalidateWorkspace(name);
        forceReloadTrigger.trigger();
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel={progress?.finished ? "Close" : "Import"}
    onPrimary={progress?.finished ? closeAfterImport : startImport}
    disablePrimary={!progress?.finished &&
        (progress !== null || !archive || !!nameError)}
    secondaryLabel={importing ? "Cancel" : undefined}
    onSecondary={requestCancel}
    disableSecondary={progress?.cancelling}
    closeOnPrimary={false}
    title="Import Workspace (archive)"
    size="w-1/3"
>
    {#if progress}
        <ImportProgressPanel {progress} />
    {:else}
        <div class="mx-2 flex h-full max-h-[80vh] flex-col">
            <input
                class="hidden"
                type="file"
                id={fileInputId}
                accept={`${workspaceArchiveMediaType.fileExtension},.zip`}
                onchange={event => {
                    void selectArchive(event.target.files);
                    event.target.value = "";
                }}
            />
            <FileDropZone
                class="hover:border-blue flex w-full flex-col px-4 py-6"
                onFiles={items => selectArchive(items.files)}
            >
                <div
                    class="flex flex-col items-start space-y-2 md:flex-row md:items-center md:space-y-0 md:space-x-3"
                >
                    <div class="h-9 w-24">
                        <ButtonControl
                            height={9}
                            callOnClick={() =>
                                document.getElementById(fileInputId).click()}
                        >
                            Select File
                        </ButtonControl>
                    </div>
                    <p class="text-font-secondary text-sm">
                        or drag and drop a workspace archive
                    </p>
                </div>
                <p class="text-font-secondary mt-2 text-xs">
                    A <b>{workspaceArchiveMediaType.fileExtension}</b>
                    file written by File → Export → Workspace (archive). It is imported
                    as a new workspace, with all of its schemas, constraints, diagrams
                    and layouts.
                </p>
                {#if rejectedFileName}
                    <div
                        class="bg-red-background text-red-text border-red-border mt-3 rounded border px-3 py-2 text-xs"
                    >
                        {rejectedFileName} is not a workspace archive.
                    </div>
                {/if}
            </FileDropZone>

            {#if archive}
                <p class="text-default-text mt-3 truncate text-sm">
                    {archive.name}
                </p>
                <label for={nameInputId} class="mt-3 mb-1">New workspace</label>
                <input
                    class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
                    type="text"
                    id={nameInputId}
                    placeholder="Name of the workspace to create"
                    bind:value={workspaceName}
                />
                {#if nameError}
                    <div class="text-red mt-1 mb-1 h-6 text-sm">
                        {nameError}
                    </div>
                {/if}
            {:else}
                <p class="text-font-secondary mt-2 text-sm">
                    No file selected yet.
                </p>
            {/if}
        </div>
    {/if}
</ActionDialog>
