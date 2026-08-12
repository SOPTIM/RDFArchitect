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
        faObjectGroup,
        faPaste,
        faPlus,
    } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
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
    import AddToDatasetDiagramDialog from "../../../../routes/mainpage/packageNavigation/custom-diagram-dialogs/AddToDatasetDiagramDialog.svelte";
    import AddToGraphDiagramDialog from "../../../../routes/mainpage/packageNavigation/custom-diagram-dialogs/AddToGraphDiagramDialog.svelte";
    import { startPaste } from "../../../../routes/mainpage/packageNavigation/paste-flow.svelte.js";
    import PasteMenuItems from "../../../../routes/mainpage/packageNavigation/PasteMenuItems.svelte";
    import NewClassDialog from "../../../../routes/NewClassDialog.svelte";

    let {
        request = null,
        disabled = false,
        lockedDatasetName = "",
        lockedGraphUri = "",
        lockedPackage = "",
        classes = [],
        onClassCreated = () => {},
        onClose = () => {},
    } = $props();

    let triggerRef = $state(null);
    let open = $state(false);
    let showNewClassDialog = $state(false);
    let showAddAllToGraphDiagramDialog = $state(false);
    let showAddAllToDatasetDiagramDialog = $state(false);
    let classLayoutPosition = $state(null);

    let triggerStyle = $derived(getContextMenuTriggerStyle(request));

    let disablePasteButton = $derived(copyState.isEmpty);

    let diagramType = $derived(editorState.selectedDiagram.getProperty("type"));
    let addAllDisabled = $derived(classes.length === 0);

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

    function openAddAllToGraphDiagramDialog() {
        showAddAllToGraphDiagramDialog = true;
        onClose();
    }

    function openAddAllToDatasetDiagramDialog() {
        showAddAllToDatasetDiagramDialog = true;
        onClose();
    }

    function openNewClassDialog() {
        classLayoutPosition = request?.flowPosition
            ? {
                  xPosition: request.flowPosition.x,
                  yPosition: request.flowPosition.y,
              }
            : null;
        showNewClassDialog = true;
        onClose();
    }

    async function pasteClass(options) {
        onClose();
        await startPaste(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
            editorState.selectedDiagram.getProperty("id"),
            options,
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
    {#if diagramType !== DiagramType.CROSS_PROFILE}
        <ContextMenu.Content>
            {#if diagramType === DiagramType.PACKAGE}
                <ContextMenu.Item.Button
                    onSelect={openNewClassDialog}
                    {disabled}
                    faIcon={faPlus}
                    altText="Shift+N"
                >
                    New Class
                </ContextMenu.Item.Button>

                <ContextMenu.Separator />
                <ContextMenu.SubMenu.Root>
                    <ContextMenu.SubMenu.Trigger
                        faIcon={faPaste}
                        disabled={false}
                    >
                        Paste
                    </ContextMenu.SubMenu.Trigger>
                    <ContextMenu.SubMenu.Content>
                        <PasteMenuItems
                            Item={ContextMenu.Item.Button}
                            disabled={disablePasteButton}
                            onPaste={pasteClass}
                        />
                    </ContextMenu.SubMenu.Content>
                </ContextMenu.SubMenu.Root>
                <ContextMenu.Separator />
            {/if}
            {#if lockedGraphUri}
                <ContextMenu.Item.Button
                    onSelect={openAddAllToGraphDiagramDialog}
                    disabled={addAllDisabled}
                    faIcon={faObjectGroup}
                >
                    Add all to Schema Diagram
                </ContextMenu.Item.Button>
            {/if}
            <ContextMenu.Item.Button
                onSelect={openAddAllToDatasetDiagramDialog}
                disabled={addAllDisabled}
                faIcon={faObjectGroup}
            >
                Add all to Workspace Diagram
            </ContextMenu.Item.Button>
        </ContextMenu.Content>
    {/if}
</ContextMenu.Root>

<NewClassDialog
    bind:showDialog={showNewClassDialog}
    {lockedDatasetName}
    {lockedGraphUri}
    {lockedPackage}
    {classLayoutPosition}
    {onClassCreated}
/>

{#if showAddAllToGraphDiagramDialog}
    <AddToGraphDiagramDialog
        bind:showDialog={showAddAllToGraphDiagramDialog}
        {lockedDatasetName}
        {lockedGraphUri}
        {classes}
    />
{/if}

{#if showAddAllToDatasetDiagramDialog}
    <AddToDatasetDiagramDialog
        bind:showDialog={showAddAllToDatasetDiagramDialog}
        {lockedDatasetName}
        {lockedGraphUri}
        {classes}
    />
{/if}
