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
    import { ConformanceView } from "$lib/shacl/conformanceState.svelte.js";
    import { ShapesFormView } from "$lib/shacl/formState.svelte.js";
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
        ClassType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";
    import { graphLabelOf, graphUri as uriOf } from "$lib/utils/graph-label.js";
    import { uriSuffix } from "$lib/utils/iri.js";

    import ConformanceReportView from "./workbench/ConformanceReportView.svelte";
    import DocumentInspector from "./workbench/DocumentInspector.svelte";
    import DocumentList from "./workbench/DocumentList.svelte";
    import FormEditor from "./workbench/FormEditor.svelte";
    import ProblemsPanel from "./workbench/ProblemsPanel.svelte";
    import SchemaPicker from "./workbench/SchemaPicker.svelte";

    import { beforeNavigate, goto } from "$app/navigation";
    import { page } from "$app/state";

    const VIEWS = [
        { id: "ttl", label: "Turtle" },
        { id: "form", label: "Form" },
        { id: "conformance", label: "Schema check" },
    ];

    let editor = $state(null);
    let problemsExpanded = $state(true);

    /** The open schema's name, as the navigation writes it: its dcat:keyword, not its URI. */
    let schemaName = $state("");

    /** Which view of the open document is showing. Both edit the same unsaved buffer. */
    let view = $state("ttl");

    /** The link already followed, so selecting its document does not re-trigger the follow. */
    let followedLink = null;

    /** Resolved when the user has answered the unsaved-changes dialog. */
    let pendingSwitch = $state(null);
    let showUnsavedDialog = $state(false);
    let showStaleDialog = $state(false);
    let staleReason = $state(null);

    /**
     * Set while a navigation the user has already agreed to is being re-issued, so the guard
     * below lets it through instead of asking the same question twice.
     */
    let leaveConfirmed = false;

    /**
     * Set when the workbench itself fires the app-wide reload after a save, so the workbench does
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
    let selectedGraph = $derived(editorState.selectedGraph.getValue());

    /**
     * One workbench per graph, thrown away when the selection changes.
     *
     * Rebuilding rather than reusing keeps the loaded documents, the editor buffer and the
     * validation report from ever describing different graphs.
     */
    const workbench = $derived(
        selectedWorkspace && selectedGraph
            ? new ShapesWorkbench({
                  datasetName: selectedWorkspace,
                  graphUri: selectedGraph,
              })
            : null,
    );

    /** Compares the open document with the constraints the schema implies. */
    const conformance = $derived(
        selectedWorkspace && selectedGraph
            ? new ConformanceView({
                  datasetName: selectedWorkspace,
                  graphUri: selectedGraph,
              })
            : null,
    );

    /** Reads and writes the buffer as shapes, for the form view. */
    const formView = $derived(
        selectedWorkspace && selectedGraph
            ? new ShapesFormView({
                  datasetName: selectedWorkspace,
                  graphUri: selectedGraph,
              })
            : null,
    );

    /** The workspace's CIM terms, which the editor completes and explains against. */
    const termSource = $derived(
        selectedWorkspace && selectedGraph
            ? new SchemaTermSource({
                  datasetName: selectedWorkspace,
                  graphUri: selectedGraph,
              })
            : null,
    );

    /**
     * The generated rules are a view of the schema, so only the Turtle tab means anything for
     * them: there is no document to edit as a form, and comparing them with the schema they were
     * generated from would compare a thing with itself.
     */
    const views = $derived(
        workbench?.showingGenerated ? VIEWS.slice(0, 1) : VIEWS,
    );

    $effect(() => {
        editorState.selectedWorkspace.subscribe();
        editorState.selectedGraph.subscribe();
    });

    /**
     * Names the schema the way the rest of the app names it.
     *
     * The graph list is fetched, so the URI's tail stands in until it arrives rather than leaving
     * the heading to fill itself in a moment later.
     */
    $effect(() => {
        const workspace = selectedWorkspace;
        const uri = selectedGraph;
        if (!workspace || !uri) {
            schemaName = "";
            return;
        }
        let current = true;
        schemaName = uriSuffix(uri);
        graphStore.getGraphs(workspace).then(graphs => {
            if (current) {
                schemaName = graphLabelOf(graphs, uri);
            }
        });
        return () => (current = false);
    });

    $effect(() => {
        if (!views.some(option => option.id === view)) {
            view = "ttl";
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
        onOpenClass(async (graphUri, classUUID, packageUUID) => {
            // Asked before the selection moves: once it has, the workbench is rebuilt for the new
            // schema and the edits are gone before any navigation guard could see them.
            if (!(await confirmDiscard())) {
                return;
            }
            editorState.selectPackage(
                selectedWorkspace,
                graphUri,
                packageUUID ?? "default",
            );
            editorState.selectedClassWorkspace.updateValue(selectedWorkspace);
            editorState.selectedClassGraph.updateValue(graphUri);
            editorState.selectedClass.updateValue({
                type: ClassType.SINGLE_CLASS,
                id: classUUID,
            });
            editorState.focusedClassUUID.updateValue(classUUID);
            editorState.selectedWorkspace.trigger();
            editorState.selectedGraph.trigger();
            editorState.selectedDiagram.trigger();
            forceReloadTrigger.trigger();
            goto("/mainpage");
        });
        return () => onOpenClass(null);
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
        // A field typed in and saved straight away — with Ctrl+S, or by clicking Save — still has
        // its edit on the way to the buffer. Saving first would write the document without it and
        // then show it coming back as an unsaved change.
        await formView?.settle();
        const { saved, reason, unchanged, stale } = await workbench.save({
            overwrite,
        });
        if (saved && unchanged) {
            return true;
        }
        if (saved) {
            // A save moves the schema graph's version, so the terms and hovers cached against the
            // old one are describing a schema that no longer exists — and so does a comparison.
            termSource?.invalidate();
            termSource?.load();
            conformance?.forget();
            // The save is also a step the menu bar can undo; it only rereads that on a reload.
            reloadIsOurs = true;
            forceReloadTrigger.trigger();
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
     */
    async function confirmSwitch() {
        // Land anything typed in the form before asking, or an edit still on its way would decide
        // the question by arriving after the answer.
        await formView?.settle();
        if (!workbench?.dirty) {
            return true;
        }
        return new Promise(resolve => {
            pendingSwitch = resolve;
            showUnsavedDialog = true;
        });
    }

    /**
     * `confirmSwitch` for anything that replaces the buffer from outside: a discarded answer
     * reverts it, so what follows — a reload after an undo, say — is free to replace it.
     */
    async function confirmDiscard() {
        const agreed = await confirmSwitch();
        if (agreed) {
            workbench?.revert();
        }
        return agreed;
    }

    /** What a new document starts with: the prefixes its schema is written in. */
    async function seedText() {
        const [namespaces, graphs] = await Promise.all([
            workspaceStore.getNamespaces(selectedWorkspace),
            graphStore.getGraphs(selectedWorkspace),
        ]);
        return newDocumentText({
            graphUri: selectedGraph,
            namespaces,
            keyword: (graphs ?? []).find(
                graph => uriOf(graph) === selectedGraph,
            )?.keyword,
        });
    }

    /**
     * Takes the form's rewrite of the buffer — unless the buffer moved on while the form was
     * working. The rewrite is of the text it was given, and applied over a newer one it would
     * silently undo whatever was typed since. Returns false then, which has the form read the
     * buffer again.
     */
    function applyFormText(next, base) {
        if (base !== undefined && base !== workbench.text) {
            toastStore.info(
                "Form edit discarded",
                "The text changed while the form was applying the edit. Make it again if it is still wanted.",
            );
            return false;
        }
        workbench.text = next;
        return true;
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
        view = "ttl";
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

    /**
     * Shows a line in whichever view can show it: the form when that is where the reader is, and
     * otherwise the Turtle — a click in the outline or the problems panel from the schema check
     * would move a cursor nobody can see.
     */
    function showLine(line, column = 1) {
        reveal(line, column);
        if (view === "form" && formView) {
            formView.focusLine = line;
        } else {
            view = "ttl";
        }
    }

    /**
     * Shows a line of the document in the Turtle view, from a card in the form.
     *
     * The editor stays mounted behind the form, so it is already scrolled by the time the view
     * changes over.
     */
    function showInTurtle(line) {
        view = "ttl";
        reveal(line);
    }

    /**
     * The way back: the form card for the line the cursor is on.
     *
     * The line is left with the form view rather than resolved here, because which card holds a
     * line is a question about shapes the form has not necessarily read yet.
     */
    function showInForm(line) {
        if (!formView || !views.some(option => option.id === "form")) {
            return;
        }
        formView.focusLine = line;
        view = "form";
    }

    /** Opens another document from a report, asking about unsaved changes on the way. */
    async function openDocument(target) {
        if (!target || target === workbench.selectedId) {
            return;
        }
        if (!(await confirmSwitch())) {
            return;
        }
        await workbench.select(target);
        view = "ttl";
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
  The constraints workbench: the graph's SHACL documents, an editor for the open one, and
  everything validation has to say about them.

  Replaces the read-one-blob-of-Turtle dialog. The documents are a list because a schema's
  constraints normally arrive as several official files plus whatever the user adds; every enabled
  one applies and none overrides another, so the list is about participation and reading order.
-->

<svelte:window onkeydown={onKeydown} />

<div class="bg-window-background flex h-full min-h-0 flex-col">
    {#if !workbench}
        <div class="flex h-full items-center justify-center p-6">
            <EmptyStateCard
                icon={faFileShield}
                title="No schema selected"
                description="Pick a workspace and a schema to edit its constraints."
            >
                <SchemaPicker />
            </EmptyStateCard>
        </div>
    {:else}
        <div
            class="border-border flex shrink-0 items-center gap-3 border-b px-4 py-2"
        >
            <Fa icon={faFileShield} class="text-blue" />
            <h1
                class="text-default-text min-w-0 truncate text-sm font-semibold"
                title={selectedGraph}
            >
                Constraints — {selectedWorkspace} / {schemaName}
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
                    {:else if workbench.selectedId === null}
                        <div
                            class="flex h-full items-center justify-center p-6"
                        >
                            <EmptyStateCard
                                title="No document"
                                description="Add a document or import a constraints file to start."
                            />
                        </div>
                    {:else}
                        <div class="flex h-full min-h-0 flex-col">
                            <div
                                class="border-border flex h-9 shrink-0 items-center gap-2 border-b px-2"
                            >
                                <div
                                    class="flex items-center gap-2"
                                    role="tablist"
                                    aria-label="View of the document"
                                >
                                    {#each views as option (option.id)}
                                        <button
                                            class="cursor-pointer rounded px-3 py-1 text-sm {view ===
                                            option.id
                                                ? 'bg-background-select text-nav-active-text font-semibold'
                                                : 'text-text-subtle hover:text-default-text'}"
                                            role="tab"
                                            aria-selected={view === option.id}
                                            onclick={() => (view = option.id)}
                                        >
                                            {option.label}
                                        </button>
                                    {/each}
                                </div>
                                {#if view === "form" && formView?.applying}
                                    <span class="text-text-subtle text-xs">
                                        applying…
                                    </span>
                                {/if}
                                {#if workbench.showingGenerated}
                                    <span class="text-text-subtle text-xs">
                                        derived from the schema — read-only
                                    </span>
                                {/if}
                            </div>

                            <div class="min-h-0 flex-1">
                                <!--
                                  Both views are kept mounted so switching does not throw away the
                                  editor's undo history or scroll position; only one is shown.
                                -->
                                <div
                                    class="h-full {view === 'ttl'
                                        ? ''
                                        : 'hidden'}"
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
                                            : "Empty document — write shapes in Turtle here, or add one in the Form view."}
                                        onSave={() => save()}
                                        onchange={onTextChanged}
                                        onshowinform={showInForm}
                                    />
                                </div>
                                {#if view === "form" && formView}
                                    <FormEditor
                                        form={formView}
                                        documentId={workbench.selectedId}
                                        turtle={workbench.text}
                                        terms={termSource?.terms ?? []}
                                        readOnly={workbench.readOnly}
                                        onturtle={applyFormText}
                                        onvalidate={onTextChanged}
                                        onreveal={showInTurtle}
                                    />
                                {:else if view === "conformance" && conformance}
                                    <ConformanceReportView
                                        {conformance}
                                        documentId={workbench.selectedId}
                                        dirty={workbench.dirty}
                                        prefixes={parsePrefixes(workbench.text)}
                                        onopen={openDocument}
                                    />
                                {/if}
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
    onCancel={() => answerSwitch(false)}
    onDiscard={() => answerSwitch(true)}
    onSave={async () => {
        // Only leave the document once it is actually written. A save that failed on a syntax
        // error would otherwise switch away and take the buffer with it — losing exactly the
        // text the user had just asked to keep.
        answerSwitch(await save());
    }}
/>
