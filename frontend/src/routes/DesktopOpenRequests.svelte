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
    import { fetchOpenRequest } from "$lib/desktop/desktopApi.js";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";

    import WorkspaceImportDialog from "./WorkspaceImportDialog.svelte";

    import { afterNavigate, replaceState } from "$app/navigation";
    import { page } from "$app/state";

    /**
     * Opens the workspace files the operating system handed to the desktop app, by a double-click
     * on an `.rdfa` file: the app opens a window at `/mainpage?open=<ids>`, and each file is shown
     * in the Open dialog in turn.
     */

    let queue = [];
    let current = $state(null);
    let showDialog = $state(false);

    $effect(() => {
        if (!showDialog && current) {
            openNext();
        }
    });

    async function open(ids) {
        for (const id of ids) {
            try {
                queue.push({ id, file: await fetchOpenRequest(id) });
            } catch (error) {
                console.error(
                    "[DesktopOpenRequests] Could not read",
                    id,
                    error,
                );
                toastStore.error(
                    "Could not open the file",
                    "The workspace file could not be read.",
                );
            }
        }
        if (!current) {
            openNext();
        }
    }

    function openNext() {
        current = queue.shift() ?? null;
        showDialog = current !== null;
    }

    // Not onMount: the address can only be changed once the router has started, which it has by
    // the time the first navigation is reported.
    afterNavigate(() => {
        const ids = (page.url.searchParams.get("open") ?? "")
            .split(",")
            .filter(Boolean);
        if (ids.length === 0) {
            return;
        }
        // A reload must not open the files again.
        const url = new URL(page.url);
        url.searchParams.delete("open");
        replaceState(url, page.state);
        void open(ids);
    });
</script>

{#if current}
    {#key current.id}
        <WorkspaceImportDialog
            bind:showDialog
            initialArchive={current.file}
            openRequestId={current.id}
        />
    {/key}
{/if}
