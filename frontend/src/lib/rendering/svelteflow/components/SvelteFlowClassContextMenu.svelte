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
        faFileExport,
        faLayerGroup,
        faCopy,
        faMinus,
        faTrash,
        faDiagramProject,
    } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import ContextMenuSeparator from "$lib/components/bitsui/contextmenu/ContextMenuSeparator.svelte";
    import {
        copyState,
        DiagramType,
        editorState,
    } from "$lib/sharedState.svelte.js";

    import {
        getContextMenuTriggerStyle,
        handleContextMenuOpenChange,
        syncContextMenuTrigger,
    } from "./contextMenuUtils.js";
    import DeleteDependenciesDialog from "../../../../routes/delete-relations-dialog/DeleteDependenciesDialog.svelte";
    import RemoveFromDiagramDialog from "../../../../routes/mainpage/packageNavigation/custom-diagram-dialogs/RemoveFromDiagramDialog.svelte";
    import ExtendClassDialog from "../../../../routes/mainpage/packageNavigation/ExtendClassDialog.svelte";
    import SHACLClassSpecificPopUp from "../../../../routes/shacl/shaclclassspecific/SHACLClassSpecificPopUp.svelte";

    let {
        request = null,
        disabled = false,
        readOnly = false,
        contextMenuClass = null,
        datasetName = "",
        graphUri = "",
        nodeOrder = [],
        nodeCount = 0,
        onClose = () => {},
        onMoveClass = () => {},
        onSetLayer = () => {},
        onPersistLayer = () => {},
    } = $props();

    let triggerRef = $state(null);
    let open = $state(false);
    let dialogClass = $state(null);
    let showDeleteDependenciesDialog = $state(false);
    let showSHACLDialog = $state(false);
    let showExtendClassDialog = $state(false);
    let showRemoveFromDiagramDialog = $state(false);

    let triggerStyle = $derived(getContextMenuTriggerStyle(request));

    let classZIndex = $derived(
        contextMenuClass ? nodeOrder.indexOf(contextMenuClass.uuid) : -1,
    );
    let isAtFront = $derived(classZIndex >= nodeCount - 1);
    let isAtBack = $derived(classZIndex <= 0);
    let classActionsDisabled = $derived(disabled || readOnly);

    const shaclClass = $derived({
        uuid: { value: contextMenuClass?.uuid },
        label: { value: contextMenuClass?.label ?? "" },
    });

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
        if (classActionsDisabled || !contextMenuClass) {
            return;
        }
        dialogClass = contextMenuClass;
        showDeleteDependenciesDialog = true;
        onClose();
    }

    function openRemoveFromDiagramDialog() {
        if (!contextMenuClass) {
            return;
        }
        showRemoveFromDiagramDialog = true;
        onClose();
    }

    function openExtendClassDialog() {
        if (!contextMenuClass) {
            return;
        }
        dialogClass = contextMenuClass;
        showExtendClassDialog = true;
        onClose();
    }

    function handleMoveUp() {
        if (classActionsDisabled || !contextMenuClass) return;
        onMoveClass({ classUuid: contextMenuClass.uuid, direction: "up" });
    }

    function handleMoveDown() {
        if (classActionsDisabled || !contextMenuClass) return;
        onMoveClass({ classUuid: contextMenuClass.uuid, direction: "down" });
    }

    function handleMoveToTop() {
        if (classActionsDisabled || !contextMenuClass) return;
        onMoveClass({ classUuid: contextMenuClass.uuid, direction: "top" });
    }

    function handleMoveToBottom() {
        if (classActionsDisabled || !contextMenuClass) return;
        onMoveClass({ classUuid: contextMenuClass.uuid, direction: "bottom" });
    }

    function handleLayerChange(newLayer) {
        if (classActionsDisabled || !contextMenuClass) return;
        const clamped = Math.max(0, Math.min(nodeCount - 1, newLayer));
        // Immediate local update
        onSetLayer({ classUuid: contextMenuClass.uuid, layer: clamped });
    }

    function handleLayerPersist(newLayer) {
        if (classActionsDisabled || !contextMenuClass) return;
        const clamped = Math.max(0, Math.min(nodeCount - 1, newLayer));
        // Debounced API call
        onPersistLayer({ classUuid: contextMenuClass.uuid, layer: clamped });
    }

    function copyClass() {
        copyState.classUUID.updateValue(contextMenuClass.uuid);
        copyState.graphURI.updateValue(editorState.selectedGraph.getValue());
        copyState.datasetName.updateValue(
            editorState.selectedDataset.getValue(),
        );
    }
</script>

<ContextMenu.Root bind:open onOpenChange={handleOpenChange}>
    <ContextMenu.TriggerArea
        bind:ref={triggerRef}
        class="fixed h-px w-px opacity-0"
        style={triggerStyle}
        {disabled}
    />
    {#if editorState.selectedDiagram.getProperty("type") !== DiagramType.CROSS_PROFILE}
    <ContextMenu.Content>
        <ContextMenu.Item.Button
            onSelect={copyClass}
            faIcon={faCopy}
            altText="Ctrl+C"
        >
            Copy
        </ContextMenu.Item.Button>
        <ContextMenu.Item.Button
            onSelect={openExtendClassDialog}
            faIcon={faFileExport}
        >
            Extend Class
        </ContextMenu.Item.Button>
        <ContextMenu.Separator />
        <ContextMenu.Item.Button
            onSelect={() => {
                showSHACLDialog = true;
            }}
            faIcon={faDiagramProject}
        >
            Constraints
        </ContextMenu.Item.Button>
        <ContextMenu.SubMenu.Root>
            <ContextMenu.SubMenu.Trigger
                faIcon={faLayerGroup}
                disabled={classActionsDisabled}
            >
                Move
            </ContextMenu.SubMenu.Trigger>
            <ContextMenu.SubMenu.Content>
                <ContextMenu.Item.Button
                    onSelect={e => {
                        e.preventDefault();
                        handleMoveToTop();
                    }}
                    faIcon={faAnglesUp}
                    disabled={classActionsDisabled || isAtFront}
                >
                    Move to front
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Button
                    onSelect={e => {
                        e.preventDefault();
                        handleMoveUp();
                    }}
                    faIcon={faAngleUp}
                    disabled={classActionsDisabled || isAtFront}
                >
                    Move up
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Counter
                    value={classZIndex}
                    min={0}
                    max={nodeCount - 1}
                    disabled={classActionsDisabled}
                    onchange={handleLayerChange}
                    onpersist={handleLayerPersist}
                >
                    Layer
                </ContextMenu.Item.Counter>
                <ContextMenu.Item.Button
                    onSelect={e => {
                        e.preventDefault();
                        handleMoveDown();
                    }}
                    faIcon={faAngleDown}
                    disabled={classActionsDisabled || isAtBack}
                >
                    Move down
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Button
                    onSelect={e => {
                        e.preventDefault();
                        handleMoveToBottom();
                    }}
                    faIcon={faAnglesDown}
                    disabled={classActionsDisabled || isAtBack}
                >
                    Move to bottom
                </ContextMenu.Item.Button>
            </ContextMenu.SubMenu.Content>
        </ContextMenu.SubMenu.Root>
        <ContextMenuSeparator />
        {#if editorState.selectedDiagram.getProperty("type") !== DiagramType.PACKAGE}
        <ContextMenu.Item.Button
            onSelect={openRemoveFromDiagramDialog}
            faIcon={faMinus}
            variant="danger"
        >
            Remove from Diagram
        </ContextMenu.Item.Button>
        {/if}
        <ContextMenu.Item.Button
            onSelect={openDeleteClassDialog}
            disabled={classActionsDisabled}
            faIcon={faTrash}
            variant="danger"
        >
            Delete class
        </ContextMenu.Item.Button>
    </ContextMenu.Content>
    {/if}
</ContextMenu.Root>

<DeleteDependenciesDialog
    bind:showDialog={showDeleteDependenciesDialog}
    {datasetName}
    {graphUri}
    resourceUuid={dialogClass?.uuid}
/>
<ExtendClassDialog
    {datasetName}
    {graphUri}
    classUUID={dialogClass?.uuid}
    bind:showDialog={showExtendClassDialog}
/>

<SHACLClassSpecificPopUp
    datasetName={editorState.selectedDataset.getValue()}
    graphUri={editorState.selectedGraph.getValue()}
    reactiveClass={shaclClass}
    bind:showDialog={showSHACLDialog}
/>

<RemoveFromDiagramDialog
    bind:showDialog={showRemoveFromDiagramDialog}
    lockedDatasetName={datasetName}
    {graphUri}
    diagramId={editorState.selectedDiagram.getProperty("id")}
    classId={contextMenuClass.uuid}
    classLabel={contextMenuClass.label}
/>
