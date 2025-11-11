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
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import FaIconButton from "$lib/components/FaIconButton.svelte";

    let {
        showDialog = $bindable(),
        submitLabel = "Submit",
        submitIcon,
        submitVariant,
        onSubmit = () => {},
        cancelLabel = "Cancel",
        cancelVariant = "contrast",
        cancelIcon,
        disableSubmit,
        onCancel = () => {},
        readonly,
    } = $props();

    let boundToDialog = $derived(showDialog !== undefined);

    function closeDialog() {
        if (boundToDialog) {
            showDialog = false;
        }
    }

    function handleConfirmShortcut(event) {
        if (!showDialog) {
            return;
        }

        if (
            event.defaultPrevented ||
            event.key !== "Enter" ||
            event.repeat ||
            event.isComposing
        ) {
            return;
        }

        if (event.metaKey || event.ctrlKey || event.altKey) {
            return;
        }

        const target = event.target;
        if (
            target instanceof HTMLTextAreaElement ||
            target instanceof HTMLButtonElement ||
            target instanceof HTMLAnchorElement ||
            (target instanceof HTMLElement &&
                (target.getAttribute?.("role") === "button" ||
                    target.isContentEditable))
        ) {
            return;
        }

        if (disableSubmit) {
            return;
        }

        event.preventDefault();
        onSubmit();
        closeDialog();
    }
</script>

<svelte:window onkeydown|capture={handleConfirmShortcut} />

<div class="mx-2 my-1 mt-4 flex justify-end space-x-2">
    {#if readonly}
        <!-- In readonly mode, only show the cancel button -->
        <div>
            <ButtonControl
                variant="contrast"
                callOnClick={() => {
                    closeDialog();
                }}
            >
                Close
            </ButtonControl>
        </div>
    {:else}
        <div>
            <FaIconButton
                variant={cancelVariant}
                callOnClick={() => {
                    if (!readonly) onCancel();
                    closeDialog();
                }}
                text={cancelLabel}
                icon={cancelIcon}
            />
        </div>
        <div>
            <FaIconButton
                callOnClick={() => {
                    if (!readonly) onSubmit();
                    closeDialog();
                }}
                variant={submitVariant}
                disabled={disableSubmit}
                text={submitLabel}
                icon={submitIcon}
            />
        </div>
    {/if}
</div>
