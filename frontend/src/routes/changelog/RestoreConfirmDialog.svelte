<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script>
    import {
        faArrowRotateRight,
        faClockRotateLeft,
        faXmark,
    } from "@fortawesome/free-solid-svg-icons";

    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import AlertDialog from "$lib/dialog/AlertDialog.svelte";
    import { counted } from "$lib/utils/plural.js";

    import { labelOf } from "./changeKinds.js";
    import { restoresChange } from "./changelogFilter.js";

    const { request, graphUri = null, onConfirm, onCancel } = $props();

    /**
     * Starts on, because the question was asked of one schema's history: restoring the whole
     * workspace from there would reach further than anything the user was looking at.
     */
    let thisSchemaOnly = $state(true);

    let showDialog = $derived(!!request);

    /** Whether the target lies ahead of the workspace, so that reaching it reapplies changes. */
    let goingForward = $derived(!!request?.reapplied);

    /** Whether the question on the table is a restore held to the schema in view. */
    let restoringOneSchema = $derived(
        thisSchemaOnly && !!graphUri && !goingForward,
    );

    /**
     * The changes between the workspace and the target, with what the user has held back already
     * taken off them. The question has to list what will actually be touched, not what could have
     * been.
     */
    let affected = $derived(
        (request?.reapplied ?? request?.takenBack ?? []).filter(
            change => !restoringOneSchema || restoresChange(change, graphUri),
        ),
    );

    /**
     * How many changes an undo has already stepped over. A restore held to one schema is recorded
     * as a new version, and nothing can be redone past one, so those changes go for good — which
     * the whole-workspace restore does not do, since it steps the history rather than writing to
     * it.
     */
    let undoneAhead = $derived(
        restoringOneSchema ? (request?.undoneAhead ?? 0) : 0,
    );

    /** What the move does to the changes between here and there, as a heading for the list. */
    let heading = $derived(
        `${goingForward ? "Redoes" : "Undoes"} ${counted(affected.length, "change")}:`,
    );

    function close() {
        thisSchemaOnly = true;
        onCancel();
    }

    function confirm() {
        const chosen = restoringOneSchema ? [graphUri] : [];
        thisSchemaOnly = true;
        onConfirm(chosen);
    }
</script>

{#if request}
    <AlertDialog
        {showDialog}
        size="w-full max-w-lg"
        onOpenChange={open => {
            if (!open) close();
        }}
    >
        {#snippet title()}
            <span class="text-default-text text-lg leading-9 font-semibold">
                {goingForward ? "Advance to" : "Restore to"}
                &ldquo;{request.change.message}&rdquo;
            </span>
        {/snippet}

        {#snippet description()}
            <div class="text-text-subtle space-y-2 pt-2 pb-1">
                {#if affected.length === 0}
                    <p class="text-sm leading-relaxed">
                        Nothing to take back in this schema: everything since
                        then happened elsewhere in the workspace.
                    </p>
                {:else}
                    <p class="text-sm leading-relaxed">{heading}</p>
                    <ul
                        class="no-scrollbar max-h-56 list-disc space-y-0.5 overflow-y-auto pl-5 text-sm"
                    >
                        {#each affected as change (change.changeId)}
                            <li class="break-words">
                                {change.message}
                                <span class="text-xs">
                                    ({(change.affectedKinds ?? [])
                                        .map(labelOf)
                                        .join(", ")})
                                </span>
                            </li>
                        {/each}
                    </ul>
                {/if}
                {#if graphUri && !goingForward}
                    <div class="border-border border-t pt-2">
                        <CheckBoxEditControl
                            label="Restore only this schema"
                            labelFirst={false}
                            bind:value={thisSchemaOnly}
                        />
                        {#if thisSchemaOnly && affected.length > 0}
                            <p class="pt-1 pl-6 text-xs">
                                Creates a new version that undoes {counted(
                                    affected.length,
                                    "change",
                                )}. Other schemas are untouched by this action.
                            </p>
                            {#if undoneAhead > 0}
                                <p class="text-red-text pt-1 pl-6 text-xs">
                                    The {counted(undoneAhead, "change")} ahead of
                                    the workspace cannot be redone afterwards.
                                </p>
                            {/if}
                        {:else if thisSchemaOnly}
                            <p class="pt-1 pl-6 text-xs">
                                Clear this to restore the whole workspace
                                instead.
                            </p>
                        {/if}
                    </div>
                {/if}
            </div>
        {/snippet}
        <div class="flex flex-row justify-end gap-2 px-2 pb-2">
            <div>
                <FaIconButton
                    callOnClick={close}
                    icon={faXmark}
                    text="Cancel"
                />
            </div>
            <div>
                <FaIconButton
                    callOnClick={confirm}
                    disabled={affected.length === 0}
                    icon={goingForward ? faArrowRotateRight : faClockRotateLeft}
                    variant={goingForward ? "default" : "danger"}
                    text={goingForward ? "Advance" : "Restore"}
                />
            </div>
        </div>
    </AlertDialog>
{/if}
