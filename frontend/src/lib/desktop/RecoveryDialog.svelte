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
    import { faClockRotateLeft } from "@fortawesome/free-solid-svg-icons";
    import { onMount } from "svelte";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { PUBLIC_DESKTOP } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import {
        discardRecoveredWorkspace,
        listRecoveredWorkspaces,
    } from "./desktopApi.js";
    import {
        RecoveredWorkspaceGoneError,
        restoreRecoveredWorkspace,
    } from "./recovery.js";

    const savedAtFormat = new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
    });

    /** Offers the workspaces the desktop app kept when it last stopped with unsaved changes. */

    let showDialog = $state(false);
    let recovered = $state([]);
    /** The id of the kept workspace being restored or discarded right now. */
    let busy = $state(null);

    onMount(async () => {
        if (!PUBLIC_DESKTOP) {
            return;
        }
        try {
            recovered = await listRecoveredWorkspaces();
            showDialog = recovered.length > 0;
        } catch (error) {
            console.warn(
                "[RecoveryDialog] Could not list kept workspaces",
                error,
            );
        }
    });

    async function restore(entry) {
        busy = entry.id;
        try {
            const existing = (await workspaceStore.getWorkspaces(true)) ?? [];
            const name = await restoreRecoveredWorkspace(
                entry,
                existing.map(workspace => workspace.label),
            );
            toastStore.success(
                "Workspace restored",
                `"${name}" is back. Save it to keep it.`,
            );
            forget(entry);
        } catch (error) {
            if (error instanceof RecoveredWorkspaceGoneError) {
                toastStore.info(
                    "Already restored",
                    `"${entry.workspaceName}" was restored or discarded in another window.`,
                );
                forget(entry);
                return;
            }
            console.error("[RecoveryDialog] Restoring failed", error);
            toastStore.error(
                "Restore failed",
                `Could not restore "${entry.workspaceName}". ${error?.message ?? ""}`,
            );
        } finally {
            busy = null;
        }
    }

    async function discard(entry) {
        busy = entry.id;
        try {
            await discardRecoveredWorkspace(entry.id);
            forget(entry);
        } catch (error) {
            toastStore.error(
                "Discard failed",
                `Could not discard "${entry.workspaceName}". ${error?.message ?? ""}`,
            );
        } finally {
            busy = null;
        }
    }

    function forget(entry) {
        recovered = recovered.filter(other => other.id !== entry.id);
        if (recovered.length === 0) {
            showDialog = false;
        }
    }
</script>

<ActionDialog
    bind:showDialog
    title="Restore Unsaved Workspaces"
    titleIcon={faClockRotateLeft}
    primaryLabel="Later"
    onPrimary={() => (showDialog = false)}
    size="w-1/3"
>
    <div class="mx-2 flex flex-col gap-3 text-sm">
        <p>
            RDFArchitect stopped while these workspaces had changes that were
            not saved. Restore them to carry on, then save them to a file.
            “Later” asks again at the next start.
        </p>
        <ul class="flex flex-col gap-2">
            {#each recovered as entry (entry.id)}
                <li
                    class="border-border flex items-center gap-3 rounded border px-3 py-2"
                >
                    <div class="min-w-0 flex-1">
                        <div class="truncate font-medium">
                            {entry.workspaceName}
                        </div>
                        <div class="text-font-secondary text-xs">
                            Kept {savedAtFormat.format(new Date(entry.savedAt))}
                        </div>
                    </div>
                    <div class="h-8 w-24">
                        <ButtonControl
                            callOnClick={() => restore(entry)}
                            disabled={busy !== null}
                        >
                            {busy === entry.id ? "Restoring…" : "Restore"}
                        </ButtonControl>
                    </div>
                    <div class="h-8 w-24">
                        <ButtonControl
                            variant="danger"
                            callOnClick={() => discard(entry)}
                            disabled={busy !== null}
                        >
                            Discard
                        </ButtonControl>
                    </div>
                </li>
            {/each}
        </ul>
    </div>
</ActionDialog>
