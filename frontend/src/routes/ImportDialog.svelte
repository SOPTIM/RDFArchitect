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
    import { faMinus } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";
    import { v4 as uuidv4 } from "uuid";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import ImportProgressPanel from "$lib/components/ImportProgressPanel.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";
    import { supportedRDFMediaTypes } from "$lib/utils/fileUtils";
    import {
        ImportProgress,
        JobState,
    } from "$lib/utils/importProgress.svelte.js";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable(), lockedWorkspaceName } = $props();

    const DEFAULT_WORKSPACE_NAME = "default";
    const POLL_INTERVAL_MS = 300;
    const MAX_FAILED_POLLS = 3;
    const GRAPH_NAMESPACE_URI = "http://graph#"; // Keep in sync with RDFA.GRAPH_URI (backend)
    const DEFAULT_GRAPH_NAME = "graph";
    const supportedFileExtensions = supportedRDFMediaTypes.map(
        type => type.fileExtension,
    );
    const allowedFileExtensions = supportedFileExtensions.join(", ");
    const uniqueId = uuidv4();
    const workspaceInputId = `workspaceNameImport-${uniqueId}`;
    const workspaceListId = `workspaceNamesImport-${uniqueId}`;
    const fileInputId = `actual-file-input-${uniqueId}`;
    let workspaceNameUserInput = $state("");
    let files = $state([]);
    let dragActive = $state(false);
    let fileInputValue = $state("");
    let rejectedFiles = $state([]);

    let readOnlyWorkspaces = $state([]);
    let modifiableWorkspaces = $state([]);

    /** Set while an import runs and until its result is dismissed; drives the progress panel. */
    let progress = $state(null);
    let pollTimeout = null;
    let failedPolls = 0;
    /** The workspace the running import writes into, applied to the editor once it is done. */
    let importWorkspaceName = null;
    let importJobId = null;

    let importing = $derived(progress !== null && !progress.finished);
    let enableSubmit = $derived(
        files.length > 0 &&
            !isWorkspaceReadOnly(workspaceNameUserInput) &&
            progress === null,
    );

    const workspaceSelectionLocked = $derived(!!lockedWorkspaceName);

    async function onOpen() {
        clearInputs();
        workspaceNameUserInput =
            lockedWorkspaceName ?? editorState.selectedWorkspace.getValue();

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
        dismissImport();
        clearInputs();
    }

    function clearInputs() {
        workspaceNameUserInput = "";
        files = [];
        dragActive = false;
        fileInputValue = "";
        rejectedFiles = [];
        modifiableWorkspaces = [];
        readOnlyWorkspaces = [];
    }

    function isWorkspaceReadOnly(workspaceName) {
        const targetWorkspace = workspaceName || DEFAULT_WORKSPACE_NAME;
        return readOnlyWorkspaces.includes(targetWorkspace);
    }

    function getSanitizedGraphName(fileName) {
        if (!fileName) {
            return DEFAULT_GRAPH_NAME;
        }
        const normalized = fileName.split(/[\\/]/).pop() ?? fileName;
        const lastDotIndex = normalized.lastIndexOf(".");
        const baseName =
            lastDotIndex === -1
                ? normalized
                : normalized.substring(0, lastDotIndex);
        const sanitized = baseName.replace(/\W/g, "_");
        return sanitized.trim() ? sanitized : DEFAULT_GRAPH_NAME;
    }

    function buildGraphUri(fileName) {
        const sanitized = getSanitizedGraphName(fileName);
        return `${GRAPH_NAMESPACE_URI}${sanitized}`;
    }

    function isZipFile(fileName) {
        return fileName.toLowerCase().endsWith(".zip");
    }

    function isSupportedGraphFile(fileName) {
        if (!fileName) {
            return false;
        }
        const lowered = fileName.toLowerCase();
        return supportedFileExtensions.some(extension =>
            lowered.endsWith(extension.toLowerCase()),
        );
    }

    function ensureGraphNamespaceUri(graphUri, fallbackName) {
        const trimmed = graphUri?.trim();
        if (!trimmed) {
            return buildGraphUri(fallbackName);
        }
        if (trimmed.includes("://")) {
            return trimmed;
        }
        return `${GRAPH_NAMESPACE_URI}${trimmed}`;
    }

    function addFiles(newFiles) {
        rejectedFiles = [];
        const mappedFiles = Array.from(newFiles)
            .map(file => {
                if (!isZipFile(file.name) && !isSupportedGraphFile(file.name)) {
                    rejectedFiles.push(file.name);
                    return null;
                }
                return {
                    file,
                    graphUri: isZipFile(file.name)
                        ? ""
                        : buildGraphUri(file.name),
                    isZip: isZipFile(file.name),
                };
            })
            .filter(Boolean);

        if (mappedFiles.length > 0) {
            files = [...files, ...mappedFiles];
        }
        fileInputValue = "";
    }

    function removeFile(index) {
        files = files.filter((_, idx) => idx !== index);
    }

    function updateGraphUri(index, graphUri) {
        if (files[index]?.isZip) {
            return;
        }
        files = files.map((entry, idx) =>
            idx === index ? { ...entry, graphUri } : entry,
        );
    }

    function handleDrop(event) {
        dragActive = false;
        if (event.dataTransfer?.files?.length) {
            addFiles(event.dataTransfer.files);
        }
    }

    function getUserInputWorkspaceName() {
        return workspaceNameUserInput || DEFAULT_WORKSPACE_NAME;
    }

    async function importGraphs() {
        if (progress !== null) {
            return;
        }
        const workspaceName = getUserInputWorkspaceName();
        const filesToImport = files.map(entry => entry.file);
        const graphUris = files.map(entry =>
            entry.isZip
                ? ""
                : ensureGraphNamespaceUri(entry.graphUri, entry.file.name),
        );

        const currentProgress = new ImportProgress();
        progress = currentProgress;
        failedPolls = 0;
        importWorkspaceName = workspaceName;

        const { data, error } = await graphStore.startImport(
            workspaceName,
            filesToImport,
            graphUris,
            {
                onUploadProgress: percent =>
                    currentProgress.uploadProgress(percent),
                signal: currentProgress.signal,
            },
        );

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
            // Cancelled while the files were still going up, so the job needs to hear about it.
            await cancelImportJob(workspaceName, data.jobId);
        }
        pollImportStatus();
    }

    /**
     * Polls the backend until the job is done. A single timeout is kept in flight, so closing the
     * dialog or finishing the import ends the polling.
     */
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
                // A single missed poll is not worth giving up on, the import keeps running.
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
            if (data.state === JobState.RUNNING) {
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
        void cancelImportJob(importWorkspaceName, importJobId);
    }

    /**
     * Ends the dialog's part in the current import: cancels it while it still runs, opens what it
     * imported once it is done, and forgets it either way.
     */
    function dismissImport() {
        stopPolling();
        const currentProgress = progress;
        const jobId = importJobId;
        const workspaceName = importWorkspaceName;
        progress = null;
        importJobId = null;
        importWorkspaceName = null;

        if (!currentProgress) {
            return;
        }
        if (!currentProgress.finished) {
            currentProgress.cancel();
            void cancelImportJob(workspaceName, jobId);
            return;
        }
        if (currentProgress.importedGraphUris.length > 0) {
            applyImportResult(currentProgress.importedGraphUris, workspaceName);
        }
    }

    function closeAfterImport() {
        dismissImport();
        clearInputs();
        showDialog = false;
    }

    async function cancelImportJob(workspaceName, jobId) {
        if (!workspaceName || !jobId) {
            return;
        }
        await graphStore.cancelImport(workspaceName, jobId);
    }

    function applyImportResult(importedGraphUris, workspaceName) {
        editorState.selectedWorkspace.updateValue(workspaceName);
        editorState.selectedGraph.updateValue(importedGraphUris[0] || null);
        editorState.selectedDiagram.updateValue({ type: null, id: null });
        editorState.selectedClassWorkspace.updateValue(null);
        editorState.selectedClassGraph.updateValue(null);
        editorState.selectedClass.updateValue({ type: null, id: null });

        graphStore.invalidateWorkspace(workspaceName);
        workspaceStore.invalidate();
        crossProfileStore.invalidateWorkspace(workspaceName);
        forceReloadTrigger.trigger();
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel={progress?.finished ? "Close" : "Import"}
    onPrimary={progress?.finished ? closeAfterImport : importGraphs}
    disablePrimary={!progress?.finished && !enableSubmit}
    secondaryLabel={importing ? "Cancel" : undefined}
    onSecondary={requestCancel}
    disableSecondary={progress?.cancelling}
    closeOnPrimary={false}
    title="Import Schema (RDFS)"
    size="w-1/3"
>
    {#if progress}
        <ImportProgressPanel {progress} />
    {:else}
        <div class="mx-2 flex h-full max-h-[80vh] flex-col">
            {#if !workspaceSelectionLocked}
                <label for={workspaceInputId} class="mb-1">Workspace</label>
                <input
                    class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
                    type="text"
                    id={workspaceInputId}
                    list={workspaceListId}
                    placeholder={DEFAULT_WORKSPACE_NAME}
                    bind:value={workspaceNameUserInput}
                />
                <datalist id={workspaceListId}>
                    {#each modifiableWorkspaces as workspaceName}
                        <option value={workspaceName}>{workspaceName}</option>
                    {/each}
                </datalist>

                {#if isWorkspaceReadOnly(workspaceNameUserInput)}
                    <div class="text-red mt-1 mb-1 h-6 text-sm">
                        Cannot import into read-only workspace
                    </div>
                {/if}
            {/if}
            <div class="mt-4">
                <input
                    class="hidden"
                    type="file"
                    id={fileInputId}
                    multiple
                    accept={`${supportedFileExtensions.join(",")},.zip`}
                    onchange={event => {
                        addFiles(event.target.files);
                        event.target.value = "";
                    }}
                    bind:value={fileInputValue}
                />
                <div
                    class={`border-border hover:border-blue flex w-full flex-col rounded border-2 border-dashed px-4 py-6 transition-colors  ${dragActive ? "border-blue bg-blue/10" : "bg-window-background"}`}
                    role="group"
                    ondragover={event => {
                        event.preventDefault();
                        dragActive = true;
                    }}
                    ondragleave={event => {
                        event.preventDefault();
                        dragActive = false;
                    }}
                    ondrop={event => {
                        event.preventDefault();
                        handleDrop(event);
                    }}
                >
                    <div
                        class="flex flex-col items-start space-y-2 md:flex-row md:items-center md:space-y-0 md:space-x-3"
                    >
                        <div class="h-9 w-24">
                            <ButtonControl
                                height={9}
                                callOnClick={() => {
                                    document
                                        .getElementById(fileInputId)
                                        .click();
                                }}
                            >
                                Select File
                            </ButtonControl>
                        </div>
                        <p class="text-font-secondary text-sm">
                            or drag and drop files or a .zip archive
                        </p>
                    </div>
                    <p class="text-font-secondary mt-2 text-xs">
                        Each file becomes a schema named after the file. ZIP
                        files are unpacked and imported automatically.
                        <br />
                        Supported file extensions:
                        <b>{allowedFileExtensions}</b>
                        . In ZIP files, schemas must be located at the root level;
                        folders are ignored.
                    </p>
                    {#if rejectedFiles.length > 0}
                        <div
                            class="bg-red-background text-red-text border-red-border mt-3 rounded border px-3 py-2 text-xs"
                        >
                            <p class="font-semibold">
                                Skipped unsupported files:
                            </p>
                            <ul class="list-disc pl-5">
                                {#each rejectedFiles as fileName}
                                    <li>{fileName}</li>
                                {/each}
                            </ul>
                        </div>
                    {/if}
                </div>

                {#if files.length > 0}
                    <div class="mt-3 max-h-[55vh] space-y-2 overflow-y-auto">
                        {#each files as fileEntry, index}
                            <div
                                class="border-border flex items-center space-x-3 rounded border px-3 py-2"
                            >
                                <div class="flex-1">
                                    <input
                                        id={`graph-uri-${index}`}
                                        class="border-border bg-window-background focus:border-blue ring-none w-full rounded border-2 p-2 text-sm outline-none"
                                        type="text"
                                        value={fileEntry.isZip
                                            ? fileEntry.file.name
                                            : fileEntry.graphUri}
                                        disabled={fileEntry.isZip}
                                        oninput={event =>
                                            updateGraphUri(
                                                index,
                                                event.target.value,
                                            )}
                                    />
                                </div>
                                <div
                                    class="flex size-10 items-center justify-center p-0"
                                >
                                    <ButtonControl
                                        height={10}
                                        callOnClick={() => removeFile(index)}
                                        title="Remove file"
                                    >
                                        <Fa
                                            icon={faMinus}
                                            ariaLabel="Remove file"
                                        />
                                    </ButtonControl>
                                </div>
                            </div>
                        {/each}
                    </div>
                {:else}
                    <p class="text-font-secondary mt-2 text-sm">
                        No files selected yet.
                    </p>
                {/if}
            </div>
        </div>
    {/if}
</ActionDialog>
