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
        faEraser,
        faEye,
        faGear,
        faPlus,
        faTrash,
    } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";

    import {
        getContextMenuTriggerStyle,
        handleContextMenuOpenChange,
        syncContextMenuTrigger,
    } from "./contextMenuUtils.js";

    let {
        request = null,
        disabled = false,
        readOnly = false,
        edgeKind = null,
        canEdit = false,
        onClose = () => {},
        onEditEdge = () => {},
        onAddBendPoint = () => {},
        onDeleteEdgePoint = () => {},
        onRemoveBendPoints = () => {},
        onRemoveDockingPoints = () => {},
        onRemoveAllEdgePoints = () => {},
    } = $props();

    let triggerRef = $state(null);
    let open = $state(false);

    let triggerStyle = $derived(getContextMenuTriggerStyle(request));

    let editLabel = $derived(
        `${readOnly ? "View" : "Edit"} ${edgeKind === "inheritance" ? "Inheritance" : "Association"}`,
    );

    let onEdgePoint = $derived(!!request?.hitEdgePointId);
    let hasBendPoints = $derived((request?.bendPointCount ?? 0) > 0);
    let hasDockingPoints = $derived((request?.dockingPointCount ?? 0) > 0);

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

    function addBendPoint() {
        if (!request) return;
        onAddBendPoint({
            edgeId: request.edgeId,
            flowPosition: request.flowPosition,
        });
        onClose();
    }

    function deleteEdgePoint() {
        if (!request?.hitEdgePointId) return;
        onDeleteEdgePoint({
            edgeId: request.edgeId,
            pointId: request.hitEdgePointId,
        });
        onClose();
    }

    function editEdge() {
        if (!request) return;
        onEditEdge({ edgeId: request.edgeId });
        onClose();
    }

    function removeBendPoints() {
        if (!request) return;
        onRemoveBendPoints({ edgeId: request.edgeId });
        onClose();
    }

    function removeDockingPoints() {
        if (!request) return;
        onRemoveDockingPoints({ edgeId: request.edgeId });
        onClose();
    }

    function removeAllEdgePoints() {
        if (!request) return;
        onRemoveAllEdgePoints({ edgeId: request.edgeId });
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
        {#if onEdgePoint}
            <ContextMenu.Item.Button
                onSelect={deleteEdgePoint}
                faIcon={faTrash}
                altText={"Ctrl+Shift+Q"}
                variant="danger"
                disabled={readOnly}
            >
                Delete edge point
            </ContextMenu.Item.Button>
        {:else}
            <ContextMenu.Item.Button
                onSelect={addBendPoint}
                faIcon={faPlus}
                altText={"Ctrl+Q"}
                disabled={readOnly}
            >
                Add bend point here
            </ContextMenu.Item.Button>
        {/if}
        <ContextMenu.Separator />
        <ContextMenu.Item.Button
            onSelect={removeBendPoints}
            faIcon={faEraser}
            variant="danger"
            disabled={readOnly || !hasBendPoints}
        >
            Remove all bend points
        </ContextMenu.Item.Button>
        <ContextMenu.Item.Button
            onSelect={removeDockingPoints}
            faIcon={faEraser}
            variant="danger"
            disabled={readOnly || !hasDockingPoints}
        >
            Remove docking points
        </ContextMenu.Item.Button>
        <ContextMenu.Item.Button
            onSelect={removeAllEdgePoints}
            faIcon={faEraser}
            variant="danger"
            disabled={readOnly || !(hasBendPoints || hasDockingPoints)}
        >
            Remove all edge points
        </ContextMenu.Item.Button>
        {#if canEdit}
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={editEdge}
                faIcon={readOnly ? faEye : faGear}
            >
                {editLabel}
            </ContextMenu.Item.Button>
        {/if}
    </ContextMenu.Content>
</ContextMenu.Root>
