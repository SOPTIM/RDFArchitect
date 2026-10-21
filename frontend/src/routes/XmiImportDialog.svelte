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

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import FileDropZone from "$lib/components/FileDropZone.svelte";
    import ImportProgressPanel from "$lib/components/ImportProgressPanel.svelte";
    import PackageTreeSelect from "$lib/components/PackageTreeSelect.svelte";
    import SelectedFileRow from "$lib/components/SelectedFileRow.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
    import { graphStore } from "$lib/stores/graphStore";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";
    import { isXmiExportFile, xmiFileExtensions } from "$lib/utils/fileUtils";
    import { ImportProgress } from "$lib/utils/importProgress.svelte.js";
    import { ImportStatusPoller } from "$lib/utils/importStatusPoller.svelte.js";
    import {
        PackageScope,
        selectedSchemas,
        selectionOf,
        sizeOf,
    } from "$lib/utils/packageTree.svelte.js";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        initialWorkspaceName = "",
        droppedFile = null,
    } = $props();

    const uniqueId = uuidv4();
    const workspaceInputId = `workspaceNameXmi-${uniqueId}`;
    const fileInputId = `actual-export-input-${uniqueId}`;
    const extensionInputId = `extension-namespace-${uniqueId}`;
    const invalidCharacters = /[/\\?#]/;
    const allowedFileExtensions = xmiFileExtensions.join(", ");
    const poller = new ImportStatusPoller();

    let workspaceNameUserInput = $state("");
    let selectedFile = $state(null);
    let fileInputValue = $state("");
    let rejectedFiles = $state([]);
    let progress = $state(null);
    let jobId = $state(null);
    let readJobId = $state(null);
    let scanWorkspaceName = $state(null);
    let suggestedName = $state("");
    let importing = $state(false);
    let selectedIds = $state(new Set());
    let extensionNamespace = $state("");

    const trimmedName = $derived(workspaceNameUserInput.trim());
    const nameHasInvalidCharacters = $derived(
        invalidCharacters.test(trimmedName),
    );
    const running = $derived(progress !== null && !progress.finished);
    const enableSubmit = $derived(
        !!trimmedName &&
            !nameHasInvalidCharacters &&
            selectedFile !== null &&
            progress === null,
    );
    /** Between the read and the import: the export has been read and nothing chosen yet. */
    const choosing = $derived(
        !importing &&
            progress !== null &&
            progress.finished &&
            progress.packages.length > 0,
    );
    const chosenSchemas = $derived(
        selectedSchemas(progress?.packages ?? [], selectedIds),
    );
    const chosenCount = $derived(chosenSchemas.length);
    const chosenClasses = $derived(
        chosenSchemas.reduce(
            (sum, row) => sum + sizeOf(row.node, row.scope),
            0,
        ),
    );
    const primaryLabel = $derived(
        choosing ? "Import" : progress?.finished ? "Close" : "Read",
    );
    const primaryAction = $derived(
        choosing
            ? importChosen
            : progress?.finished
              ? closeAfterRead
              : readExport,
    );
    const primaryDisabled = $derived(
        choosing ? chosenCount === 0 : !progress?.finished && !enableSubmit,
    );

    function onOpen() {
        workspaceNameUserInput = initialWorkspaceName ?? "";
        suggestedName = "";
        clearInputs();
        if (droppedFile) {
            addFiles([droppedFile]);
        }
    }

    function onClose() {
        poller.stop();
        if (running) {
            progress?.cancel();
            void graphStore.cancelImport(scanWorkspaceName, jobId);
        }
        progress = null;
        jobId = null;
        readJobId = null;
        importing = false;
    }

    function clearInputs() {
        selectedFile = null;
        fileInputValue = "";
        rejectedFiles = [];
        progress = null;
        jobId = null;
        readJobId = null;
        importing = false;
        selectedIds = new Set();
        extensionNamespace = "";
    }

    /** The workspace name an export suggests: its file name without path and extension. */
    function nameFrom(fileName) {
        const lastSegment = fileName.split(/[\\/]/).pop() ?? fileName;
        const lastDot = lastSegment.lastIndexOf(".");
        const base = lastDot > 0 ? lastSegment.slice(0, lastDot) : lastSegment;
        return base.replace(/[/\\?#]/g, "_").trim();
    }

    /**
     * Fills the workspace name from the chosen export, unless the name was typed or handed over.
     * A name this dialog suggested is replaced by the next one, a name someone else put there is
     * left alone.
     */
    function suggestName(fileName) {
        if (trimmedName && workspaceNameUserInput !== suggestedName) {
            return;
        }
        suggestedName = nameFrom(fileName);
        workspaceNameUserInput = suggestedName;
    }

    /** Takes the first export of a selection; one export is read at a time. */
    function addFiles(newFiles, directoryNames = []) {
        const candidates = Array.from(newFiles ?? []);
        const accepted = candidates.filter(file => isXmiExportFile(file.name));
        rejectedFiles = [
            ...directoryNames.map(name => `${name} (folder)`),
            ...candidates
                .filter(file => !isXmiExportFile(file.name))
                .map(file => file.name),
        ];
        if (accepted.length > 0) {
            selectedFile = accepted[0];
            suggestName(selectedFile.name);
        }
    }

    function removeFile() {
        selectedFile = null;
        if (workspaceNameUserInput === suggestedName) {
            workspaceNameUserInput = "";
            suggestedName = "";
        }
    }

    /**
     * Reads the chosen export. The workspace is not created here: nothing is stored until the
     * packages have been chosen.
     */
    async function readExport() {
        if (!enableSubmit) {
            return;
        }
        const current = new ImportProgress();
        progress = current;
        scanWorkspaceName = trimmedName;

        const { data, error } = await graphStore.startXmiScan(
            trimmedName,
            selectedFile,
            {
                onUploadProgress: percent => current.uploadProgress(percent),
                signal: current.signal,
            },
        );
        if (error || !data) {
            current.fail(error?.message ?? "The export could not be read.");
            return;
        }
        current.uploaded();
        jobId = data.jobId;
        readJobId = data.jobId;
        poller.follow(
            () => graphStore.getImportStatus(scanWorkspaceName, jobId),
            current,
        );
    }

    /**
     * Imports what was ticked. The export stays where it is — the read job holds it — so this
     * starts a second job rather than uploading anything again.
     */
    async function importChosen() {
        const selection = selectionOf(
            progress?.packages ?? [],
            selectedIds,
            extensionNamespace,
        );
        if (selection.length === 0) {
            return;
        }
        poller.stop();
        const current = new ImportProgress();
        current.uploaded();
        importing = true;
        progress = current;

        const { data, error } = await graphStore.importSelectedPackages(
            scanWorkspaceName,
            readJobId,
            selection,
        );
        if (error || !data) {
            current.fail(
                error?.message ?? "The packages could not be imported.",
            );
            return;
        }
        jobId = data.jobId;
        poller.follow(
            () => graphStore.getImportStatus(scanWorkspaceName, jobId),
            current,
        );
    }

    /**
     * Asks the job to stop and closes. The job notices between files and between diagrams, which
     * can take a moment on a large export, and waiting for it with the dialog open would leave a
     * cancel button that does not appear to do anything.
     */
    function requestCancel() {
        poller.stop();
        progress?.cancel();
        void graphStore.cancelImport(scanWorkspaceName, jobId);
        clearInputs();
        showDialog = false;
    }

    function closeAfterRead() {
        poller.stop();
        const imported = progress?.importedGraphUris ?? [];
        const workspaceName = scanWorkspaceName;
        clearInputs();
        showDialog = false;
        if (imported.length > 0) {
            applyImportResult(imported, workspaceName);
        }
    }

    /** Shows what was imported and has everything reading the workspace load it again. */
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
    {primaryLabel}
    onPrimary={primaryAction}
    disablePrimary={primaryDisabled}
    secondaryLabel={running ? "Cancel" : undefined}
    onSecondary={requestCancel}
    disableSecondary={progress?.cancelling}
    closeOnPrimary={false}
    title="Import Model (Enterprise Architect XMI)"
    size={choosing ? "w-3/4 h-4/5" : "w-1/3"}
>
    {#if choosing}
        <div class="mx-2 flex h-full min-h-0 flex-col">
            <p class="text-sm">
                Pick what becomes a schema. A package marked
                <span
                    class="border-border text-text-subtle rounded border px-1 text-[10px]"
                >
                    profile
                </span>
                declares a namespace of its own and is taken whole, the way a release
                ships it. Any other package can be picked too; one that holds others
                picks what is inside.
            </p>

            <div class="mt-3 flex min-h-0 flex-1 gap-3">
                <div
                    class="border-border min-h-0 flex-1 overflow-y-auto rounded border p-2"
                >
                    <PackageTreeSelect
                        packages={progress.packages}
                        bind:selectedIds
                    />
                </div>

                <div
                    class="border-border bg-background-subtle flex min-h-0 w-2/5 flex-col rounded border"
                >
                    <p
                        class="border-border border-b px-3 py-2 text-xs font-semibold"
                    >
                        These schemas are created
                    </p>
                    <div class="min-h-0 flex-1 overflow-y-auto px-3 py-2">
                        {#if chosenSchemas.length === 0}
                            <p class="text-font-secondary text-xs">
                                Nothing picked yet.
                            </p>
                        {:else}
                            <ul class="flex flex-col gap-1 text-xs">
                                {#each chosenSchemas as row (row.id)}
                                    <li class="flex items-baseline gap-2">
                                        <span class="truncate">
                                            {row.node.name}
                                            {#if row.scope === PackageScope.PACKAGE_ONLY}
                                                <span
                                                    class="text-text-subtle italic"
                                                >
                                                    (itself only)
                                                </span>
                                            {/if}
                                        </span>
                                        <span
                                            class="text-text-subtle ml-auto shrink-0"
                                        >
                                            {sizeOf(row.node, row.scope)}
                                        </span>
                                    </li>
                                {/each}
                            </ul>
                        {/if}
                    </div>
                    <p
                        class="border-border text-text-subtle border-t px-3 py-2 text-xs"
                    >
                        {chosenCount} schema{chosenCount === 1 ? "" : "s"},
                        {chosenClasses} class{chosenClasses === 1 ? "" : "es"}
                    </p>
                </div>
            </div>

            <label class="mt-3 text-xs" for={extensionInputId}>
                Extension namespace
                <span class="text-font-secondary">
                    — only needed when the export states none of its own
                </span>
            </label>
            <input
                class="border-border bg-window-background focus:border-blue mt-1 h-8 w-full rounded border-2 p-2 text-xs outline-none"
                type="text"
                id={extensionInputId}
                placeholder="https://cim.ucaiug.io/ns/eu#"
                autocomplete="off"
                bind:value={extensionNamespace}
            />
        </div>
    {:else if progress}
        <ImportProgressPanel {progress} />
    {:else}
        <div class="mx-2 flex h-full max-h-[80vh] flex-col">
            <label for={workspaceInputId} class="mb-1">Workspace</label>
            <input
                class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
                type="text"
                id={workspaceInputId}
                placeholder="Workspace name"
                autocomplete="off"
                bind:value={workspaceNameUserInput}
            />
            {#if nameHasInvalidCharacters}
                <div class="text-red mt-1 mb-1 h-6 text-sm">
                    Name must not contain / \ ? or #
                </div>
            {/if}

            <div class="mt-4">
                <input
                    class="hidden"
                    type="file"
                    id={fileInputId}
                    accept={xmiFileExtensions.join(",")}
                    onchange={event => {
                        addFiles(event.target.files);
                        event.target.value = "";
                    }}
                    bind:value={fileInputValue}
                />
                <FileDropZone
                    class="hover:border-blue flex w-full flex-col px-4 py-6"
                    onFiles={items =>
                        addFiles(items.files, items.directoryNames)}
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
                            or drag and drop an export
                        </p>
                    </div>
                    <p class="text-font-secondary mt-2 text-xs">
                        The export is read, not imported: its packages are
                        listed and nothing is stored yet. A full model reaches
                        100 MB.
                        <br />
                        Supported:
                        <b>{allowedFileExtensions}</b>
                        . One export is read at a time.
                    </p>
                    {#if rejectedFiles.length > 0}
                        <div
                            class="bg-red-background text-red-text border-red-border mt-3 rounded border px-3 py-2 text-xs"
                        >
                            <p class="font-semibold">
                                Skipped, these will not be read:
                            </p>
                            <ul class="list-disc pl-5">
                                {#each rejectedFiles as fileName}
                                    <li>{fileName}</li>
                                {/each}
                            </ul>
                        </div>
                    {/if}
                </FileDropZone>

                {#if selectedFile}
                    <div class="mt-3">
                        <SelectedFileRow
                            value={selectedFile.name}
                            disabled
                            onRemove={removeFile}
                        />
                    </div>
                {:else}
                    <p class="text-font-secondary mt-2 text-sm">
                        No file selected yet.
                    </p>
                {/if}
            </div>
        </div>
    {/if}
</ActionDialog>
