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
    import { Dialog as BitsUiDialog } from "bits-ui";
    import { onDestroy } from "svelte";
    import { untrack } from "svelte";

    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";

    let {
        showDialog = $bindable(),
        onOpen = () => {},
        onClose = () => {},
        size = "w-2/5 max-w-2/5",
        onkeydown,
        children,
    } = $props();

    let dialogWasOpen = false;

    $effect(() => {
        if (showDialog) {
            if (!dialogWasOpen) {
                eventStack.addEvent(closeDialog);
                untrack(onOpen);
                dialogWasOpen = true;
            }
        } else if (dialogWasOpen) {
            eventStack.removeEvent(closeDialog);
            dialogWasOpen = false;
        }
    });

    onDestroy(() => {
        eventStack.removeEvent(closeDialog);
    });

    function closeDialog() {
        const onCloseReturn = onClose();
        if (onCloseReturn === undefined) {
            showDialog = false;
        } else {
            showDialog = !onCloseReturn;
        }
    }

    function handleEscapeKeydown(event) {
        event.preventDefault();
        closeDialog();
    }

    function handleInteractOutside(event) {
        event.preventDefault();
        closeDialog();
    }
</script>

<BitsUiDialog.Root bind:open={showDialog}>
    <BitsUiDialog.Portal>
        <BitsUiDialog.Overlay class="bg-dialog-backlight fixed inset-0 z-40" />
        <BitsUiDialog.Content
            class="border-border bg-window-background fixed top-1/2 left-1/2 z-40 flex -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded border border-solid p-2 shadow outline-none {size}"
            {onkeydown}
            onEscapeKeydown={handleEscapeKeydown}
            onInteractOutside={handleInteractOutside}
        >
            {@render children?.()}
        </BitsUiDialog.Content>
    </BitsUiDialog.Portal>
</BitsUiDialog.Root>
