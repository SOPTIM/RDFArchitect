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
        faAngleDown,
        faAnglesDown,
        faAnglesUp,
        faAngleUp,
        faPlus,
        faTrash,
    } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";

    import {
        getContextMenuTriggerStyle,
        handleContextMenuOpenChange,
        syncContextMenuTrigger,
    } from "./contextMenuUtils.js";
    import DeleteClassConfirmDialog from "../../../../routes/DeleteClassConfirmDialog.svelte";

    let {
        request = null,
        disabled = false,
        contextMenuClass = null,
        datasetName = "",
        graphUri = "",
        nodeOrder = [],
        nodeCount = 0,
        onClose = () => {},
        onMoveClass = () => {},
    } = $props();

    let triggerRef = $state(null);
    let open = $state(false);
    let deleteClassTarget = $state(null);
    let showDeleteClassDialog = $state(false);

    let triggerStyle = $derived(getContextMenuTriggerStyle(request));

    let classZIndex = $derived(
        contextMenuClass ? nodeOrder.indexOf(contextMenuClass.uuid) : -1,
    );
    let isAtFront = $derived(classZIndex >= nodeCount - 1);
    let isAtBack = $derived(classZIndex <= 0);

    $effect(() => {
        syncContextMenuTrigger({
            disabled,
            request,
            triggerRef,
            setOpen: nextOpen => (open = nextOpen),
        });
    });

    function handleOpenChange(nextOpen) {
        handleContextMenuOpenChange(nextOpen, value => (open = value), onClose);
    }

    function openDeleteClassDialog() {
        if (!contextMenuClass) {
            return;
        }
        deleteClassTarget = contextMenuClass;
        showDeleteClassDialog = true;
        onClose();
    }

    function handleMoveUp() {
        if (!contextMenuClass) return;
        onMoveClass({ classUuid: contextMenuClass.uuid, direction: "up" });
        onClose();
    }

    function handleMoveDown() {
        if (!contextMenuClass) return;
        onMoveClass({ classUuid: contextMenuClass.uuid, direction: "down" });
        onClose();
    }

    function handleMoveToTop() {
        if (!contextMenuClass) return;
        onMoveClass({ classUuid: contextMenuClass.uuid, direction: "top" });
        onClose();
    }

    function handleMoveToBottom() {
        if (!contextMenuClass) return;
        onMoveClass({ classUuid: contextMenuClass.uuid, direction: "bottom" });
        onClose();
    }
</script>

<ContextMenu.Root bind:open onOpenChange={handleOpenChange}>
    <ContextMenu.TriggerArea
        bind:ref={triggerRef}
        class="fixed h-px w-px opacity-0"
        style={triggerStyle}
        {disabled}
    />
    <ContextMenu.Content>
        <ContextMenu.Item.Button
            onSelect={openDeleteClassDialog}
            {disabled}
            faIcon={faTrash}
            variant="danger"
        >
            Delete class
        </ContextMenu.Item.Button>
        <ContextMenu.SubMenu.Root>
            <ContextMenu.SubMenu.Trigger faIcon={faPlus}>
                Move ({classZIndex})
            </ContextMenu.SubMenu.Trigger>
            <ContextMenu.SubMenu.Content>
                <ContextMenu.Item.Button
                    onSelect={handleMoveToTop}
                    faIcon={faAnglesUp}
                    disabled={isAtFront}
                >
                    Move to front
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Button
                    onSelect={handleMoveUp}
                    faIcon={faAngleUp}
                    disabled={isAtFront}
                >
                    Move up
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Button
                    onSelect={handleMoveDown}
                    faIcon={faAngleDown}
                    disabled={isAtBack}
                >
                    Move down
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Button
                    onSelect={handleMoveToBottom}
                    faIcon={faAnglesDown}
                    disabled={isAtBack}
                >
                    Move to bottom
                </ContextMenu.Item.Button>
            </ContextMenu.SubMenu.Content>
        </ContextMenu.SubMenu.Root>
    </ContextMenu.Content>
</ContextMenu.Root>

<DeleteClassConfirmDialog
    bind:showDialog={showDeleteClassDialog}
    {datasetName}
    {graphUri}
    classUuid={deleteClassTarget?.uuid}
    classLabel={deleteClassTarget?.label}
/>
