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
    import { faPlus } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";

    let {
        request = null,
        disabled = false,
        onAddClass = () => {},
        onClose = () => {},
    } = $props();

    let triggerRef = $state(null);
    let open = $state(false);

    let triggerStyle = $derived(
        request
            ? `position: fixed; left: ${request.x}px; top: ${request.y}px; width: 1px; height: 1px; opacity: 0; pointer-events: none;`
            : "position: fixed; left: 0; top: 0; width: 1px; height: 1px; opacity: 0; pointer-events: none;",
    );

    $effect(() => {
        if (disabled) {
            open = false;
            return;
        }
        if (!request) {
            open = false;
            return;
        }
        if (!triggerRef) {
            return;
        }

        queueMicrotask(() => {
            triggerRef.dispatchEvent(
                new MouseEvent("contextmenu", {
                    bubbles: true,
                    cancelable: true,
                    button: 2,
                    buttons: 2,
                    clientX: request.x,
                    clientY: request.y,
                    view: window,
                }),
            );
        });
    });

    function handleOpenChange(nextOpen) {
        open = nextOpen;
        if (!nextOpen) {
            onClose();
        }
    }

    function handleAddClass() {
        onAddClass();
    }
</script>

<ContextMenu.Root bind:open onOpenChange={handleOpenChange}>
    <ContextMenu.TriggerArea
        bind:ref={triggerRef}
        class="fixed h-px w-px opacity-0"
        style={triggerStyle}
        {disabled}
    />
    <ContextMenu.Content style="z-index: 1200;">
        <ContextMenu.Item.Button
            onSelect={handleAddClass}
            {disabled}
            faIcon={faPlus}
        >
            Add class
        </ContextMenu.Item.Button>
    </ContextMenu.Content>
</ContextMenu.Root>
