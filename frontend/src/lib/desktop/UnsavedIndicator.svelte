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
    import { faCircle, faFloppyDisk } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import { desktopSession } from "./desktopSession.svelte.js";
    import { saveWorkspace } from "./saveWorkspace.js";

    /** Whether the workspace shown in the editor has changes no file has, with a way to save it. */
    let { workspaceName } = $props();
    let saving = $state(false);

    let unsaved = $derived(
        !!workspaceName &&
            desktopSession.unsavedWorkspaces.includes(workspaceName),
    );

    async function save() {
        saving = true;
        try {
            await saveWorkspace(workspaceName);
        } finally {
            saving = false;
        }
    }
</script>

{#if unsaved}
    <button
        type="button"
        class="text-default-text hover:bg-nav-hover-background inline-flex h-8 items-center gap-2 rounded px-2 text-sm whitespace-nowrap"
        title={`"${workspaceName}" has changes that are not saved to a file yet. Save it (Ctrl+S) before closing RDFArchitect.`}
        disabled={saving}
        onclick={save}
    >
        <Fa icon={faCircle} class="text-orange" size="xs" />
        <span>Unsaved</span>
        <Fa icon={faFloppyDisk} />
    </button>
{/if}
