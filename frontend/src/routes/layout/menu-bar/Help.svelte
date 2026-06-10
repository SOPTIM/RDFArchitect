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
        faArrowsRotate,
        faCircleInfo,
        faCircleQuestion,
        faCommentDots,
    } from "@fortawesome/free-solid-svg-icons";

    import { BackendConnection } from "$lib/api/backend.js";
    import { Menubar } from "$lib/components/bitsui/menubar";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    import { goto } from "$app/navigation";

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    const externalResources = {
        help: "https://github.com/SOPTIM/RDFArchitect#readme",
        feedback: "https://github.com/SOPTIM/RDFArchitect/discussions",
    };

    let showResetSessionDialog = $state(false);

    function openExternalResource(key) {
        const target = externalResources[key];
        if (!target) return;
        if (typeof window !== "undefined") {
            window.open(target, "_blank", "noopener,noreferrer");
        }
    }

    function navigateHomepage() {
        editorState.reset();
        goto("/");
    }

    async function resetSession() {
        try {
            await bec.resetSession();
        } catch {
            toastStore.error("Reset failed", "Could not reset the session.");
            return;
        }
        window.location.reload();
    }
</script>

<Menubar.Menu value="help">
    <Menubar.Trigger>Help</Menubar.Trigger>
    <Menubar.Content side="bottom" sideOffset={8}>
        <Menubar.Item.Button
            onSelect={() => openExternalResource("help")}
            faIcon={faCircleQuestion}
        >
            Help
        </Menubar.Item.Button>
        <Menubar.Item.Button
            onSelect={() => openExternalResource("feedback")}
            faIcon={faCommentDots}
        >
            Submit Feedback
        </Menubar.Item.Button>
        <Menubar.Item.Button onSelect={navigateHomepage} faIcon={faCircleInfo}>
            About
        </Menubar.Item.Button>
        <Menubar.Separator />
        <Menubar.Item.Button
            onSelect={() => (showResetSessionDialog = true)}
            faIcon={faArrowsRotate}
            variant="danger"
        >
            Reset Session
        </Menubar.Item.Button>
    </Menubar.Content>
</Menubar.Menu>

<ActionDialog
    bind:showDialog={showResetSessionDialog}
    title="Reset Session?"
    primaryLabel="Reset Session"
    primaryVariant="danger"
    onPrimary={resetSession}
>
    <div class="space-y-2 px-3 py-3">
        <p class="text-default-text text-sm leading-relaxed">
            This will discard all unsaved changes and reload all data from the
            database.
        </p>
        <p class="text-default-text text-sm leading-relaxed">
            Use this to recover if the application is not responding correctly.
        </p>
    </div>
</ActionDialog>
