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
        faExclamation,
        faFloppyDisk,
        faRotateLeft,
        faXmark,
    } from "@fortawesome/free-solid-svg-icons";
    import { AlertDialog as BitsUiAlertDialog } from "bits-ui";
    import { Fa } from "svelte-fa";

    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import AlertDialog from "$lib/dialog/AlertDialog.svelte";

    let {
        showDialog = $bindable(),
        onCancel = () => {},
        onDiscard = () => {},
        onSave = () => {},
        disableSave = false,
        ...restProps
    } = $props();

    function closeDialog(beforeClosingAction) {
        showDialog = false;
        beforeClosingAction();
    }

    function handleKeyDown(event) {
        if (event.key === "Enter" && !disableSave) {
            event.preventDefault();
            closeDialog(onSave);
        }
    }
</script>

<AlertDialog
    bind:showDialog
    {...restProps}
    size="w-full max-w-md"
    onkeydown={handleKeyDown}
>
    <div class="flex items-start gap-3 p-2">
        <div
            class="bg-red flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-white"
        >
            <Fa icon={faExclamation} />
        </div>

        <div class="min-w-0">
            <BitsUiAlertDialog.Title
                class="text-default-text text-lg leading-9 font-semibold"
            >
                Unsaved changes
            </BitsUiAlertDialog.Title>

            <BitsUiAlertDialog.Description
                class="text-text-subtle space-y-1 pt-2 pb-1"
            >
                <p class="text-sm leading-relaxed">
                    Unsaved changes will be lost.
                </p>
                <p class="text-sm leading-relaxed">
                    {#if disableSave}
                        Cannot save because the changes are invalid.
                    {:else}
                        Do you want to save before continuing?
                    {/if}
                </p>
            </BitsUiAlertDialog.Description>
        </div>
    </div>
    <div class="flex flex-row justify-end gap-2 px-2 pb-2">
        <div>
            <FaIconButton
                callOnClick={() => closeDialog(onCancel)}
                icon={faXmark}
                text="Cancel"
            />
        </div>

        <div>
            <FaIconButton
                callOnClick={() => closeDialog(onDiscard)}
                icon={faRotateLeft}
                variant="danger"
                text="Discard"
                title="Discard changes"
            />
        </div>

        <div>
            <FaIconButton
                callOnClick={() => closeDialog(onSave)}
                icon={faFloppyDisk}
                disabled={disableSave}
                text="Save"
                title="Save changes"
            />
        </div>
    </div>
</AlertDialog>
