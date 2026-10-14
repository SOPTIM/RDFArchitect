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
    import SelectedFileRow from "$lib/components/SelectedFileRow.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { graphStore } from "$lib/stores/graphStore";
    import { xmiFileExtensions } from "$lib/utils/fileUtils";
    import { ImportProgress } from "$lib/utils/importProgress.svelte.js";
    import { ImportStatusPoller } from "$lib/utils/importStatusPoller.svelte.js";

    let { showDialog = $bindable(), initialWorkspaceName = "" } = $props();

    const uniqueId = uuidv4();
    const workspaceInputId = `workspaceNameXmi-${uniqueId}`;
    const fileInputId = `actual-export-input-${uniqueId}`;
    const invalidCharacters = /[/\\?#]/;
    const allowedFileExtensions = xmiFileExtensions.join(", ");
    const poller = new ImportStatusPoller();

    let workspaceNameUserInput = $state("");
    let selectedFile = $state(null);
    let fileInputValue = $state("");
    let rejectedFiles = $state([]);
    let progress = $state(null);
    let jobId = $state(null);
    let scanWorkspaceName = $state(null);
    let suggestedName = $state("");

    const trimmedName = $derived(workspaceNameUserInput.trim());
    const nameHasInvalidCharacters = $derived(
        invalidCharacters.test(trimmedName),
    );
    const reading = $derived(progress !== null && !progress.finished);
    const enableSubmit = $derived(
        !!trimmedName &&
            !nameHasInvalidCharacters &&
            selectedFile !== null &&
            progress === null,
    );
    const primaryLabel = $derived(progress?.finished ? "Close" : "Read");
    const primaryAction = $derived(
        progress?.finished ? closeAfterRead : readExport,
    );
    const primaryDisabled = $derived(!progress?.finished && !enableSubmit);

    function onOpen() {
        workspaceNameUserInput = initialWorkspaceName ?? "";
        suggestedName = "";
        clearInputs();
    }

    function onClose() {
        poller.stop();
        if (reading) {
            progress?.cancel();
            void graphStore.cancelImport(scanWorkspaceName, jobId);
        }
        progress = null;
        jobId = null;
    }

    function clearInputs() {
        selectedFile = null;
        fileInputValue = "";
        rejectedFiles = [];
        progress = null;
        jobId = null;
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

    function isExportFile(fileName) {
        if (!fileName) {
            return false;
        }
        const lowered = fileName.toLowerCase();
        return xmiFileExtensions.some(extension => lowered.endsWith(extension));
    }

    /** Takes the first export of a selection; one export is read at a time. */
    function addFiles(newFiles, directoryNames = []) {
        const candidates = Array.from(newFiles ?? []);
        const accepted = candidates.filter(file => isExportFile(file.name));
        rejectedFiles = [
            ...directoryNames.map(name => `${name} (folder)`),
            ...candidates
                .filter(file => !isExportFile(file.name))
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
     * profiles have been chosen.
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
        poller.follow(
            () => graphStore.getImportStatus(scanWorkspaceName, jobId),
            current,
        );
    }

    function requestCancel() {
        progress?.cancel();
        void graphStore.cancelImport(scanWorkspaceName, jobId);
    }

    function closeAfterRead() {
        poller.stop();
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
    secondaryLabel={reading ? "Cancel" : undefined}
    onSecondary={requestCancel}
    disableSecondary={progress?.cancelling}
    closeOnPrimary={false}
    title="Import Model (Enterprise Architect XMI)"
    size="w-1/3"
>
    {#if progress}
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
                        The export is read, not imported: its profiles are
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
