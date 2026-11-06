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
        faFileShield,
        faFloppyDisk,
        faLock,
    } from "@fortawesome/free-solid-svg-icons";
    import { untrack } from "svelte";
    import { Fa } from "svelte-fa";
    import { Pane, Splitpanes } from "svelte-splitpanes";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import DiscardCancelConfirmDialog from "$lib/dialog/DiscardCancelConfirmDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { guardUnsavedChanges } from "$lib/eventhandling/unsavedChanges.js";
    import TurtleEditor from "$lib/monaco/TurtleEditor.svelte";
    import { onOpenClass } from "$lib/monaco/turtleLanguageFeatures.js";
    import { showClass } from "$lib/shacl/classNavigation.js";
    import { ConformanceView } from "$lib/shacl/conformanceState.svelte.js";
    import { newDocumentText } from "$lib/shacl/newDocument.js";
    import { SchemaTermSource } from "$lib/shacl/schemaTermSource.svelte.js";
    import { parsePrefixes } from "$lib/shacl/turtleTerms.js";
    import {
        leavingNeedsConfirmation,
        WORKBENCH_PATH,
        workbenchTarget,
    } from "$lib/shacl/workbenchLink.js";
    import { ShapesWorkbench } from "$lib/shacl/workbenchState.svelte.js";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";
    import { graphUri as uriOf } from "$lib/utils/graph-label.js";

    import ConformanceReportView from "./workbench/ConformanceReportView.svelte";
    import DocumentInspector from "./workbench/DocumentInspector.svelte";
    import DocumentList from "./workbench/DocumentList.svelte";
    import ProblemsPanel from "./workbench/ProblemsPanel.svelte";
    import WorkspacePicker from "./workbench/WorkspacePicker.svelte";

    import { beforeNavigate, goto } from "$app/navigation";
    import { page } from "$app/state";

    let editor = $state(null);
    let problemsExpanded = $state(true);

    /** The link already followed, so selecting its document does not re-trigger the follow. */
    let followedLink = null;

    /** Resolved when the user has answered the unsaved-changes dialog. */
    let pendingSwitch = $state(null);
    let showUnsavedDialog = $state(false);
    /** Off when the caller would undo a save made from the dialog, as an undo would. */
    let unsavedDialogOffersSave = $state(true);
    let showStaleDialog = $state(false);
    let staleReason = $state(null);

    /**
     * Set while a navigation the user has already agreed to is being re-issued, so the guard
     * below lets it through instead of asking the same question twice.
     */
    let leaveConfirmed = false;

    /**
     * Set when the workbench itself fires the app-wide reload after a change, so the workbench does
     * not answer its own signal by reading everything it has just written back in.
     */
    let reloadIsOurs = false;

    /**
     * Follows changes made elsewhere in the app: an undo or redo, an import from the File menu,
     * editing enabled or disabled. All of them fire the app-wide reload; the first run is only the
     * subscription, which the load above already covers.
     */
    let reloadSubscribed = false;

    let selectedWorkspace = $derived(editorState.selectedWorkspace.getValue());

    /**
     * One workbench per workspace, thrown away when the selection changes.
     *
     * Rebuilding rather than reusing keeps the loaded documents, the editor buffer and the
     * validation report from ever describing different workspaces. The selected schema only says
     * what opens first, so it is read once.
     */
    const workbench = $derived(
        selectedWorkspace
            ? new ShapesWorkbench({
                  datasetName: selectedWorkspace,
                  preferredSchema: untrack(() =>
                      editorState.selectedGraph.getValue(),
                  ),
              })
            : null,
    );

    /** Compares a schema with the constraints documents applying to it. */
    const conformance = $derived(
        selectedWorkspace
            ? new ConformanceView({ datasetName: selectedWorkspace })
            : null,
    );

    /** The workspace's CIM terms, which the editor completes and explains against. */
    const termSource = $derived(
        selectedWorkspace
            ? new SchemaTermSource({ datasetName: selectedWorkspace })
            : null,
    );

    const comparedSchema = $derived(
        workbench?.schemas.find(
            schema => schema.uri === workbench.comparedSchema,
        ) ?? null,
    );

    /** What the open comparison reads: the enabled documents applying to its schema. */
    const comparedDocuments = $derived(
        comparedSchema
            ? workbench
                  .documentsOf(comparedSchema.uri)
                  .filter(document => document.enabled)
            : [],
    );

    const generatedSchemaLabel = $derived(
        workbench?.schemas.find(
            schema => schema.uri === workbench.generatedSchema,
        )?.label ?? "",
    );

    $effect(() => {
        editorState.selectedWorkspace.subscribe();
    });

    // A change to the documents is also a step the menu bar can undo, and clears what it could redo.
    // It only rereads those on a reload.
    $effect(() => {
        if (workbench?.documentsRevision) {
            untrack(() => {
                reloadIsOurs = true;
                forceReloadTrigger.trigger();
            });
        }
    });

    // Any change to the documents can change what a comparison says, however it was made.
    $effect(() => {
        if (workbench?.documentsRevision) {
            untrack(() => conformance?.forget());
        }
    });

    // The cleanup holds on to the workbench it started: read at cleanup time, `workbench` is
    // already the next one.
    $effect(() => {
        const current = workbench;
        if (!current) {
            return;
        }
        current.load();
        current.watchReadOnly();
        return () => current.dispose();
    });
    $effect(() => {
        forceReloadTrigger.subscribe();
        untrack(() => {
            const ours = reloadIsOurs;
            reloadIsOurs = false;
            if (!reloadSubscribed) {
                reloadSubscribed = true;
                return;
            }
            if (!ours && workbench && !workbench.loading) {
                workbench.reload();
                // Whatever happened may have changed the schema this was read from.
                termSource?.invalidate();
                termSource?.load();
            }
        });
    });

    // Everything that replaces what this page shows without navigating asks here first.
    $effect(() => guardUnsavedChanges(confirmDiscard));

    /**
     * Opens the document a link asked for, once the workbench has finished loading.
     *
     * Followed at most once per link. Selecting the document changes state this effect reads, so
     * without the guard the effect re-enters and reveals the line a second time; and the query is
     * cleared afterwards so a reload does not drag the user back to the rule either.
     */
    $effect(() => {
        const target = workbenchTarget(page.url);
        if (!target.documentId || !workbench || workbench.loading) {
            return;
        }
        const link = `${target.documentId}:${target.line ?? ""}`;
        if (followedLink === link) {
            return;
        }
        followedLink = link;
        followLink(target);
    });

    /**
     * Following a term opens the class it belongs to and puts it on screen.
     *
     * The same three steps the search bar takes, because "go to definition" and "search for a
     * class" are the same request: select the package so the right diagram is drawn, select the
     * class so the editor fills, and set the focused class so the diagram scrolls to it and
     * highlights it. Opening the class editor alone left the user looking at whatever diagram they
     * had been on.
     *
     * Registered here because the workbench is where the navigation makes sense; the editor
     * component only knows it was asked to follow something.
     */
    $effect(() => {
        return onOpenClass(async (graphUri, classUUID, packageUUID) => {
            // Asked before the selection moves: once it has, the workbench can be rebuilt for
            // another workspace and the edits are gone before any navigation guard could see them.
            if (!(await confirmDiscard())) {
                return;
            }
            showClass(selectedWorkspace, graphUri, classUUID, packageUUID);
            goto("/mainpage");
        });
    });

    /** Ctrl+S saves wherever the focus is on this page, not only inside the editor. */
    function onKeydown(event) {
        const key = event.key?.toLowerCase();
        if (
            key !== "s" ||
            !(event.ctrlKey || event.metaKey) ||
            event.altKey ||
            event.shiftKey ||
            event.defaultPrevented
        ) {
            return;
        }
        // The browser's own Save-page dialog would otherwise open over the workbench.
        event.preventDefault();
        if (workbench && !showUnsavedDialog) {
            save();
        }
    }

    /** Saves the open document. Returns whether it was written, which the switch dialog needs. */
    async function save({ overwrite = false } = {}) {
        const { saved, reason, unchanged, stale } = await workbench.save({
            overwrite,
        });
        if (saved && unchanged) {
            return true;
        }
        if (saved) {
            toastStore.success("Constraints saved");
            return true;
        }
        if (stale) {
            // Not an error to report and forget: both versions are worth something, and only the
            // user knows which to keep.
            staleReason = reason;
            showStaleDialog = true;
            return false;
        }
        // The reason is almost always a syntax error with a line and column. Saying only that the
        // save failed leaves the user hunting for something the server already located.
        toastStore.error(
            "Not saved",
            reason ??
                "The constraints could not be saved. The document is unchanged on the server.",
        );
        return false;
    }

    /**
     * Asks about unsaved changes before the document list switches away.
     *
     * Returns a promise the list awaits, so the switch either happens after the answer or not at
     * all. Without it, opening another document would silently discard the buffer.
     *
     * @param {{ allowSave?: boolean }} [options] whether Save is offered as an answer
     */
    async function confirmSwitch({ allowSave = true } = {}) {
        if (!workbench?.dirty) {
            return true;
        }
        return new Promise(resolve => {
            pendingSwitch = resolve;
            unsavedDialogOffersSave = allowSave;
            showUnsavedDialog = true;
        });
    }

    /**
     * `confirmSwitch` for anything that replaces the buffer from outside: a discarded answer
     * reverts it, so what follows — a reload after an undo, say — is free to replace it.
     */
    async function confirmDiscard(options) {
        const agreed = await confirmSwitch(options);
        if (agreed) {
            workbench?.revert();
        }
        return agreed;
    }

    /** What a new document starts with: the prefixes its schema is written in. */
    async function seedText(schemaUri) {
        const [namespaces, graphs] = await Promise.all([
            workspaceStore.getNamespaces(selectedWorkspace),
            graphStore.getGraphs(selectedWorkspace),
        ]);
        return newDocumentText({
            graphUri: schemaUri,
            namespaces,
            keyword: (graphs ?? []).find(graph => uriOf(graph) === schemaUri)
                ?.keyword,
        });
    }

    function answerSwitch(answer) {
        const resolve = pendingSwitch;
        pendingSwitch = null;
        resolve?.(answer);
    }

    async function followLink(target) {
        if (workbench.selectedId !== target.documentId) {
            if (!(await confirmSwitch())) {
                return;
            }
            await workbench.select(target.documentId);
        }
        workbench.closeComparison();
        if (target.line) {
            // Held by the editor until it exists and holds the document, so a link followed on
            // the first load, while Monaco is still arriving, still lands on its line.
            reveal(target.line);
        }
        goto(WORKBENCH_PATH, { replaceState: true, noScroll: true });
    }

    function onTextChanged() {
        workbench?.scheduleValidation();
    }

    function reveal(line, column = 1) {
        editor?.reveal(line, column);
    }

    /** A click in the outline or the problems panel from a comparison needs a visible cursor. */
    function showLine(line, column = 1) {
        reveal(line, column);
        workbench.closeComparison();
    }

    /** Opens a document from a comparison, asking about unsaved changes on the way. */
    async function openDocument(target) {
        if (!target) {
            return;
        }
        if (target === workbench.selectedId) {
            workbench.closeComparison();
            return;
        }
        if (await confirmSwitch()) {
            await workbench.select(target);
        }
    }

    async function jumpTo(problem) {
        if (problem.documentId && problem.documentId !== workbench.selectedId) {
            if (!(await confirmSwitch())) {
                return;
            }
            await workbench.select(problem.documentId);
        }
        if (problem.line) {
            showLine(problem.line, problem.column ?? 1);
        }
    }

    /**
     * Asks about unsaved changes before the user leaves the workbench altogether.
     *
     * `confirmSwitch` only covers opening another document; without this, the same buffer was
     * thrown away silently by anything that navigated — the menu bar, the browser's back button,
     * a reload, or the editor's own Ctrl+click, which opens the class the cursor is on.
     *
     * `beforeNavigate` runs synchronously and the dialog does not, so the navigation is cancelled
     * first and re-issued once the user has answered. A full unload is the exception: the browser
     * will not wait for anything of ours, and cancelling is what asks its own question instead.
     */
    beforeNavigate(navigation => {
        const needsAsking = leavingNeedsConfirmation({
            dirty: workbench?.dirty ?? false,
            toPathname: navigation.to?.url?.pathname ?? null,
            alreadyConfirmed: leaveConfirmed,
        });
        if (!needsAsking) {
            return;
        }
        navigation.cancel();
        if (navigation.willUnload) {
            return;
        }
        const destination = navigation.to?.url;
        confirmSwitch().then(agreed => {
            if (!agreed || !destination) {
                return;
            }
            leaveConfirmed = true;
            goto(destination).finally(() => (leaveConfirmed = false));
        });
    });
</script>

<!--
  @component
  The constraints workbench: the workspace's schemas and SHACL documents, an editor for the open
  one, and everything validation has to say about them.

  Replaces the read-one-blob-of-Turtle dialog. A schema's constraints normally arrive as several
  official files plus whatever the user adds, and a file can be about several schemas; every enabled
  one applies and none overrides another, so the tree is about participation and reading order.
-->

<svelte:window onkeydown={onKeydown} />

<div class="bg-window-background flex h-full min-h-0 flex-col">
    {#if !workbench}
        <div class="flex h-full items-center justify-center p-6">
            <EmptyStateCard
                icon={faFileShield}
                title="No workspace selected"
                description="Pick a workspace to edit its constraints."
            >
                <WorkspacePicker />
            </EmptyStateCard>
        </div>
    {:else}
        <div
            class="border-border flex shrink-0 items-center gap-3 border-b px-4 py-2"
        >
            <Fa icon={faFileShield} class="text-blue" />
            <h1
                class="text-default-text min-w-0 truncate text-sm font-semibold"
            >
                Constraints — {selectedWorkspace}
            </h1>
            {#if workbench.readOnly}
                <!--
                  Said here as well as by the disabled controls: a buffer with edits in it can
                  become unsaveable when the workspace is switched to read-only, and a greyed-out
                  Save button alone does not say why.
                -->
                <span
                    class="text-text-subtle border-border flex shrink-0 items-center gap-1 rounded border px-1.5 py-0.5 text-xs"
                    title="The workspace is read-only. Enable editing to change its constraints."
                >
                    <Fa icon={faLock} />
                    read-only
                </span>
            {/if}
            {#if workbench.saving}
                <span class="text-text-subtle shrink-0 text-xs">saving…</span>
            {:else if workbench.dirty}
                <span class="text-orange shrink-0 text-xs">
                    unsaved changes
                </span>
            {/if}
            {#if workbench.validating}
                <span class="text-text-subtle shrink-0 text-xs">
                    validating…
                </span>
            {/if}
            {#if workbench.generating}
                <span class="text-text-subtle shrink-0 text-xs">
                    generating…
                </span>
            {/if}
            <div class="ml-auto h-8 w-32 shrink-0">
                <ButtonControl
                    callOnClick={() => save()}
                    disabled={!workbench.dirty ||
                        workbench.saving ||
                        workbench.readOnly}
                >
                    <span class="flex items-center gap-2">
                        <Fa icon={faFloppyDisk} />
                        {workbench.saving ? "Saving…" : "Save"}
                    </span>
                </ButtonControl>
            </div>
        </div>

        {#if workbench.error}
            <p
                class="bg-red-background border-red-border text-red-text shrink-0 border-b px-4 py-2 text-sm"
            >
                {workbench.error}
            </p>
        {/if}

        <div class="flex min-h-0 flex-1 flex-col">
            <Splitpanes theme="opencgmes-theme" class="flex min-h-0 flex-1">
                <Pane size={20} minSize={12} maxSize={35}>
                    <DocumentList
                        {workbench}
                        onbeforeswitch={confirmSwitch}
                        newDocumentText={seedText}
                    />
                </Pane>
                <Pane size={57} minSize={30}>
                    {#if workbench.loading}
                        <div class="flex h-full items-center justify-center">
                            <LoadingSpinner />
                        </div>
                    {:else}
                        <!--
                          The editor stays mounted behind a comparison so looking at one does not
                          throw away its undo history or scroll position.
                        -->
                        <div class="flex h-full min-h-0 flex-col">
                            {#if comparedSchema}
                                <div class="min-h-0 flex-1">
                                    <ConformanceReportView
                                        {conformance}
                                        schemaUri={comparedSchema.uri}
                                        schemaLabel={comparedSchema.label}
                                        documents={comparedDocuments}
                                        dirty={workbench.dirty}
                                        prefixes={parsePrefixes(workbench.text)}
                                        onopen={openDocument}
                                    />
                                </div>
                            {:else if workbench.selectedId === null}
                                <div
                                    class="flex h-full items-center justify-center p-6"
                                >
                                    <EmptyStateCard
                                        title="No document"
                                        description="Add a document or import a constraints file to start."
                                    />
                                </div>
                            {:else if workbench.showingGenerated}
                                <div
                                    class="border-border text-text-subtle flex h-9 shrink-0 items-center border-b px-3 text-xs"
                                >
                                    Generated from {generatedSchemaLabel} — read-only
                                </div>
                            {/if}
                            <div
                                class="min-h-0 flex-1 {comparedSchema ||
                                workbench.selectedId === null
                                    ? 'hidden'
                                    : ''}"
                            >
                                <TurtleEditor
                                    bind:this={editor}
                                    bind:value={workbench.text}
                                    documentKey={workbench.bufferKey}
                                    findings={workbench.findings}
                                    {termSource}
                                    readOnly={workbench.editorReadOnly}
                                    placeholder={workbench.editorReadOnly
                                        ? undefined
                                        : "Empty document — write shapes in Turtle here."}
                                    onSave={() => save()}
                                    onchange={onTextChanged}
                                />
                            </div>
                        </div>
                    {/if}
                </Pane>
                <Pane size={23} minSize={15} maxSize={40}>
                    <DocumentInspector {workbench} onreveal={showLine} />
                </Pane>
            </Splitpanes>

            <ProblemsPanel
                {workbench}
                bind:expanded={problemsExpanded}
                onselect={jumpTo}
            />
        </div>
    {/if}
</div>

<ActionDialog
    bind:showDialog={showStaleDialog}
    title="Changed elsewhere"
    secondaryLabel="Load the stored version"
    onSecondary={async () => {
        showStaleDialog = false;
        if (await workbench.loadStored()) {
            toastStore.success("Loaded the stored version");
        }
    }}
    primaryLabel="Overwrite"
    primaryVariant="danger"
    onPrimary={() => save({ overwrite: true })}
>
    <div class="space-y-2 px-3 py-3">
        <p class="text-default-text text-sm leading-relaxed">
            {staleReason ??
                "This document has changed since it was opened, in another tab or by an undo."}
        </p>
        <p class="text-default-text text-sm leading-relaxed">
            Overwrite it with your version, or load the stored one and lose your
            unsaved changes. Closing this keeps both: your changes stay in the
            editor, unsaved.
        </p>
    </div>
</ActionDialog>
<DiscardCancelConfirmDialog
    bind:showDialog={showUnsavedDialog}
    hideSave={!unsavedDialogOffersSave}
    onCancel={() => answerSwitch(false)}
    onDiscard={() => answerSwitch(true)}
    onSave={async () => {
        // Only leave the document once it is actually written. A save that failed on a syntax
        // error would otherwise switch away and take the buffer with it — losing exactly the
        // text the user had just asked to keep.
        answerSwitch(await save());
    }}
/>
