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
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";
    import { Pane, Splitpanes } from "svelte-splitpanes";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import DiscardCancelConfirmDialog from "$lib/dialog/DiscardCancelConfirmDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import TurtleEditor from "$lib/monaco/TurtleEditor.svelte";
    import { ShapesWorkbench } from "$lib/shacl/workbenchState.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    import DocumentInspector from "./workbench/DocumentInspector.svelte";
    import DocumentList from "./workbench/DocumentList.svelte";
    import ProblemsPanel from "./workbench/ProblemsPanel.svelte";

    let editor = $state(null);
    let problemsExpanded = $state(true);

    /** Resolved when the user has answered the unsaved-changes dialog. */
    let pendingSwitch = $state(null);
    let showUnsavedDialog = $state(false);

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

    $effect(() => {
        editorState.selectedWorkspace.subscribe();
        editorState.selectedGraph.subscribe();
    });

    $effect(() => {
        workbench?.load();
        return () => workbench?.cancelPendingValidation();
    });

    async function save() {
        if (await workbench.save()) {
            toastStore.success("Constraints saved");
        } else {
            toastStore.error(
                "Save failed",
                "The constraints could not be saved. The document is unchanged on the server.",
            );
        }
    }

    /**
     * Asks about unsaved changes before the document list switches away.
     *
     * Returns a promise the list awaits, so the switch either happens after the answer or not at
     * all. Without it, opening another document would silently discard the buffer.
     */
    function confirmSwitch() {
        if (!workbench?.dirty) {
            return Promise.resolve(true);
        }
        return new Promise(resolve => {
            pendingSwitch = resolve;
            showUnsavedDialog = true;
        });
    }

    function answerSwitch(answer) {
        const resolve = pendingSwitch;
        pendingSwitch = null;
        resolve?.(answer);
    }

    function onTextChanged() {
        workbench?.scheduleValidation();
    }

    function reveal(line, column = 1) {
        editor?.reveal(line, column);
    }

    async function jumpTo(problem) {
        if (problem.documentId && problem.documentId !== workbench.selectedId) {
            if (!(await confirmSwitch())) {
                return;
            }
            await workbench.select(problem.documentId);
        }
        if (problem.line) {
            reveal(problem.line, problem.column ?? 1);
        }
    }
</script>

<!--
  @component
  The constraints workbench: the graph's SHACL documents, an editor for the open one, and
  everything validation has to say about them.

  Replaces the read-one-blob-of-Turtle dialog. The documents are a list because a schema's
  constraints normally arrive as several official files plus whatever the user adds; every enabled
  one applies and none overrides another, so the list is about participation and reading order.
-->

<div class="bg-window-background flex h-full min-h-0 flex-col">
    {#if !workbench}
        <div class="flex h-full items-center justify-center p-6">
            <EmptyStateCard
                icon={faFileShield}
                title="No schema selected"
                description="Pick a workspace and a schema to edit its constraints."
            />
        </div>
    {:else}
        <div
            class="border-border flex shrink-0 items-center gap-3 border-b px-4 py-2"
        >
            <Fa icon={faFileShield} class="text-blue" />
            <h1
                class="text-default-text min-w-0 truncate text-sm font-semibold"
            >
                Constraints — {selectedWorkspace} / {selectedGraph}
            </h1>
            {#if workbench.dirty}
                <span class="text-orange shrink-0 text-xs">
                    unsaved changes
                </span>
            {/if}
            {#if workbench.validating}
                <span class="text-text-subtle shrink-0 text-xs">
                    validating…
                </span>
            {/if}
            <div class="ml-auto h-8 w-32 shrink-0">
                <ButtonControl
                    callOnClick={save}
                    disabled={!workbench.dirty || workbench.saving}
                >
                    <span class="flex items-center gap-2">
                        <Fa icon={faFloppyDisk} />
                        Save
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
                    <DocumentList {workbench} onbeforeswitch={confirmSwitch} />
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
                        <TurtleEditor
                            bind:this={editor}
                            bind:value={workbench.text}
                            findings={workbench.findings}
                            onSave={save}
                            onchange={onTextChanged}
                        />
                    {/if}
                </Pane>
                <Pane size={23} minSize={15} maxSize={40}>
                    <DocumentInspector {workbench} onreveal={reveal} />
                </Pane>
            </Splitpanes>

            <div
                class="flex min-h-0 shrink-0 flex-col"
                style={problemsExpanded ? "height: 33%" : undefined}
            >
                <ProblemsPanel
                    {workbench}
                    bind:expanded={problemsExpanded}
                    onselect={jumpTo}
                />
            </div>
        </div>
    {/if}
</div>

<DiscardCancelConfirmDialog
    bind:showDialog={showUnsavedDialog}
    onCancel={() => answerSwitch(false)}
    onDiscard={() => answerSwitch(true)}
    onSave={async () => {
        await save();
        answerSwitch(true);
    }}
/>
