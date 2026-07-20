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
        faPlus,
        faTrash,
        faEraser,
    } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import { EDGE_INTERACTION_CONFIG } from "$lib/rendering/svelteflow/interaction/edgeInteractionConfig.js";

    import {
        getContextMenuTriggerStyle,
        handleContextMenuOpenChange,
        syncContextMenuTrigger,
    } from "./contextMenuUtils.js";

    let {
        request = null,
        disabled = false,
        onClose = () => {},
        onAddBendPoint = () => {},
        onDeleteBendPoint = () => {},
        onDeleteEndPoint = () => {},
        onClearBendPoints = () => {},
    } = $props();

    let triggerRef = $state(null);
    let open = $state(false);

    let triggerStyle = $derived(getContextMenuTriggerStyle(request));

    let onBendPoint = $derived(!!request?.hitBendPointId);
    let onEndPoint = $derived(!!request?.hitEndPointId);
    let atLimit = $derived(
        (request?.bendPointCount ?? 0) >=
            EDGE_INTERACTION_CONFIG.maxBendPointsPerEdge,
    );

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
        if (atLimit || !request) return;
        onAddBendPoint({
            edgeId: request.edgeId,
            flowPosition: request.flowPosition,
        });
        onClose();
    }

    function deleteBendPoint() {
        if (!request?.hitBendPointId) return;
        onDeleteBendPoint({
            edgeId: request.edgeId,
            bendPointId: request.hitBendPointId,
        });
        onClose();
    }

    function deleteEndPoint() {
        if (!request?.hitEndPointId) return;
        onDeleteEndPoint({
            edgeId: request.edgeId,
            endPointId: request.hitEndPointId,
        });
        onClose();
    }

    function clearBendPoints() {
        if (!request) return;
        onClearBendPoints({ edgeId: request.edgeId });
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
        {#if onEndPoint}
            <ContextMenu.Item.Button
                onSelect={deleteEndPoint}
                faIcon={faTrash}
                variant="danger"
            >
                Delete end point
            </ContextMenu.Item.Button>
        {:else if onBendPoint}
            <ContextMenu.Item.Button
                onSelect={deleteBendPoint}
                faIcon={faTrash}
                variant="danger"
            >
                Delete bend point
            </ContextMenu.Item.Button>
        {:else}
            <ContextMenu.Item.Button
                onSelect={addBendPoint}
                faIcon={faPlus}
                disabled={atLimit}
                altText={atLimit ? "Max bend points reached" : "Ctrl+Q"}
            >
                Add bend point here
            </ContextMenu.Item.Button>
        {/if}
        <ContextMenu.Separator />
        <ContextMenu.Item.Button
            onSelect={clearBendPoints}
            faIcon={faEraser}
            variant="danger"
        >
            Remove all bend points
        </ContextMenu.Item.Button>
    </ContextMenu.Content>
</ContextMenu.Root>
