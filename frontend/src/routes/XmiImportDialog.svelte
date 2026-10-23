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
    import ImportProgressPanel from "$lib/components/ImportProgressPanel.svelte";
    import XmiExportPicker from "$lib/components/XmiExportPicker.svelte";
    import XmiImportConfirm from "$lib/components/XmiImportConfirm.svelte";
    import XmiSchemaChoice from "$lib/components/XmiSchemaChoice.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
    import { graphStore } from "$lib/stores/graphStore";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";
    import { importChoiceOf } from "$lib/utils/importChoice.svelte.js";
    import { ImportProgress } from "$lib/utils/importProgress.svelte.js";
    import { ImportStatusPoller } from "$lib/utils/importStatusPoller.svelte.js";
    import { selectionOf } from "$lib/utils/packageTree.svelte.js";
    import { hasInvalidCharacters } from "$lib/utils/workspaceName.js";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        initialWorkspaceName = "",
        droppedFile = null,
    } = $props();

    const poller = new ImportStatusPoller();

    let workspaceNameUserInput = $state("");
    let selectedFile = $state(null);
    let progress = $state(null);
    let jobId = $state(null);
    let readJobId = $state(null);
    let scanWorkspaceName = $state(null);
    let importing = $state(false);
    let selectedIds = $state(new Set());
    // What was ticked away rather than what is ticked: which diagrams are available follows the
    // packages, and an import takes every available one unless it was turned down.
    let deselectedDiagramIds = $state(new Set());
    /** The workspaces there already are; an export becomes one of its own, not one of these. */
    let takenNames = $state([]);
    let modelNamespace = $state("");
    let extensionNamespace = $state("");
    // Held here rather than in the views, so that stepping away and back leaves the tree opened
    // as it was and the summary showing what it showed.
    let expandedPackages = $state(new Set());
    let openedSummaryRows = $state(new Set());
    /** Whether the choice has been made and only the namespaces are left to confirm. */
    let confirming = $state(false);

    const trimmedName = $derived(workspaceNameUserInput.trim());
    const running = $derived(progress !== null && !progress.finished);
    const enableSubmit = $derived(
        !!trimmedName &&
            !hasInvalidCharacters(trimmedName) &&
            !takenNames.includes(trimmedName) &&
            selectedFile !== null &&
            progress === null,
    );
    /** Between the read and the import: the export has been read and nothing is stored yet. */
    const picking = $derived(
        !importing &&
            progress !== null &&
            progress.finished &&
            progress.packages.length > 0,
    );
    const choosing = $derived(picking && !confirming);
    const onLastStep = $derived(picking && confirming);
    const choice = $derived(
        importChoiceOf(progress, selectedIds, deselectedDiagramIds),
    );
    const primaryLabel = $derived(
        choosing
            ? "Next"
            : onLastStep
              ? "Import"
              : progress?.finished
                ? "Close"
                : "Read",
    );
    const primaryAction = $derived(
        choosing
            ? () => (confirming = true)
            : onLastStep
              ? importChosen
              : progress?.finished
                ? closeAfterRead
                : readExport,
    );
    const primaryDisabled = $derived(
        picking
            ? choice.schemas.length === 0
            : !progress?.finished && !enableSubmit,
    );
    /** Going back a step while choosing, and cancelling a job while one runs. */
    const secondaryLabel = $derived(
        onLastStep ? "Back" : running ? "Cancel" : undefined,
    );

    /**
     * Opens on nothing, whatever was left of a previous run. The dialog is mounted once and
     * reopened, so without this a run that was not torn down would be on screen again — and a
     * dialog closing itself is not told about it, {@link closeDialog} being the only caller of
     * {@link onClose}.
     */
    function onOpen() {
        teardown();
        workspaceNameUserInput = initialWorkspaceName ?? "";
        void loadTakenNames();
    }

    /** What the workspaces are called, which is what keeps an export out of an existing one. */
    async function loadTakenNames() {
        takenNames = ((await workspaceStore.getWorkspaces()) ?? []).map(
            workspace => workspace.label,
        );
    }

    function onClose() {
        teardown();
    }

    /**
     * Closes the dialog from within, which has to tear the import down itself: setting the flag
     * takes the dialog off screen without anything calling {@link onClose}.
     */
    function close() {
        showDialog = false;
        teardown();
    }

    /**
     * Lets go of the import, stopping the job where one is still running. Written to be callable
     * at any time: it is what both ending the import and starting another one go through.
     */
    function teardown() {
        poller.stop();
        if (running) {
            progress?.cancel();
            void cancelJob(scanWorkspaceName, jobId);
        }
        progress = null;
        jobId = null;
        readJobId = null;
        importing = false;
        confirming = false;
        selectedFile = null;
        selectedIds = new Set();
        deselectedDiagramIds = new Set();
        expandedPackages = new Set();
        openedSummaryRows = new Set();
        modelNamespace = "";
        extensionNamespace = "";
    }

    /** Asks the backend to stop a job, where there is one to stop. */
    async function cancelJob(workspaceName, id) {
        if (!workspaceName || !id) {
            return;
        }
        await graphStore.cancelImport(workspaceName, id);
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
        // Worked out against the read that was ticked, which the progress of the import is about
        // to take the place of. Asked afterwards it would be empty.
        const read = progress;
        const chosen = importChoiceOf(read, selectedIds, deselectedDiagramIds);
        const selection = selectionOf(read?.packages ?? [], selectedIds, {
            model: modelNamespace,
            extension: extensionNamespace,
        });
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
            chosen.diagramIds,
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
     * Asks the job to stop and closes. The job notices between packages and between diagrams,
     * which can take a moment on a large export, and waiting for it with the dialog open would
     * leave a cancel button that does not appear to do anything. {@link onClose} does the rest.
     */
    function requestCancel() {
        close();
    }

    function closeAfterRead() {
        // Read before closing, which lets go of the progress the two are taken from.
        const imported = progress?.importedGraphUris ?? [];
        const workspaceName = scanWorkspaceName;
        close();
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
    {secondaryLabel}
    onSecondary={onLastStep ? () => (confirming = false) : requestCancel}
    disableSecondary={progress?.cancelling}
    closeOnPrimary={false}
    title="Import Model (Enterprise Architect XMI)"
    size={choosing ? "w-5/6 h-4/5" : onLastStep ? "w-1/2" : "w-1/3"}
>
    {#if choosing}
        <XmiSchemaChoice
            read={progress}
            {choice}
            bind:selectedIds
            bind:deselectedDiagramIds
            bind:expandedPackages
            bind:openedSummaryRows
        />
    {:else if onLastStep}
        <XmiImportConfirm
            read={progress}
            {choice}
            bind:modelNamespace
            bind:extensionNamespace
        />
    {:else if progress}
        <ImportProgressPanel {progress} />
    {:else}
        <XmiExportPicker
            bind:workspaceName={workspaceNameUserInput}
            bind:selectedFile
            {droppedFile}
            {takenNames}
        />
    {/if}
</ActionDialog>
