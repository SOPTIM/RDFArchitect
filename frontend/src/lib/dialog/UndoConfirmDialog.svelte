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
    import { faRotateLeft, faXmark } from "@fortawesome/free-solid-svg-icons";

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import AlertDialog from "$lib/dialog/AlertDialog.svelte";
    import { undoConfirmStore } from "$lib/eventhandling/undoConfirmStore.svelte.js";

    let request = $derived(undoConfirmStore.getRequest());
    let showDialog = $derived(!!request);

    function handleKeyDown(event) {
        if (event.key === "Enter") {
            event.preventDefault();
            undoConfirmStore.respond(true);
        }
    }
</script>

{#if request}
    <AlertDialog
        {showDialog}
        size="w-full max-w-md"
        onkeydown={handleKeyDown}
        onOpenChange={open => {
            if (!open) undoConfirmStore.respond(false);
        }}
    >
        {#snippet title()}
            <span class="text-default-text text-lg leading-9 font-semibold">
                Undo removes data
            </span>
        {/snippet}

        {#snippet description()}
            <div class="text-text-subtle space-y-2 pt-2 pb-1">
                <p class="text-sm leading-relaxed">
                    Undoing &ldquo;{request.message}&rdquo; removes the
                    following from the workspace:
                </p>
                <ul class="list-disc space-y-0.5 pl-5 text-sm">
                    {#each request.removed as removed}
                        <li class="break-all">{removed}</li>
                    {/each}
                </ul>
                <p class="text-sm leading-relaxed">
                    Redo brings it back, as long as you make no other change
                    first.
                </p>
            </div>
        {/snippet}
        <div class="flex flex-row justify-end gap-2 px-2 pb-2">
            <div>
                <FaIconButton
                    callOnClick={() => undoConfirmStore.respond(false)}
                    icon={faXmark}
                    text="Cancel"
                />
            </div>
            <div>
                <FaIconButton
                    callOnClick={() => undoConfirmStore.respond(true)}
                    icon={faRotateLeft}
                    variant="danger"
                    text="Undo"
                    title="Undo and remove the listed data"
                />
            </div>
        </div>
    </AlertDialog>
{/if}
