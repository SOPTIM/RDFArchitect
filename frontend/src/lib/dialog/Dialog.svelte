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

    let dialogElement = $state();

    $effect(() => {
        if (showDialog) {
            eventStack.addEvent(closeDialog);
            untrack(onOpen);
            dialogElement?.focus();
        } else {
            eventStack.removeEvent(closeDialog);
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
</script>

{#if showDialog}
    <div
        bind:this={dialogElement}
        class="bg-dialog-backlight fixed top-0 left-0 z-40 flex h-screen w-screen items-center justify-center"
        role="dialog"
        aria-modal="true"
        tabindex="-1"
        {onkeydown}
    >
        <div class="flex size-full items-center justify-center">
            <div
                class="border-border bg-window-background rounded border border-solid p-2 shadow {size}"
            >
                {@render children?.()}
            </div>
        </div>
    </div>
{/if}
