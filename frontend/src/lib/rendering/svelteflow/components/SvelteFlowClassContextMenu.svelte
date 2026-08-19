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
        faLayerGroup,
        faCopy,
        faMinus,
        faObjectGroup,
        faTrash,
        faDiagramProject,
    } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import ContextMenuSeparator from "$lib/components/bitsui/contextmenu/ContextMenuSeparator.svelte";
    import {
        copyState,
        DiagramType,
        editorState,
        multiSelectState,
    } from "$lib/sharedState.svelte.js";

    import {
        getContextMenuTriggerStyle,
        handleContextMenuOpenChange,
        syncContextMenuTrigger,
    } from "./contextMenuUtils.js";
    import DeleteDependenciesDialog from "../../../../routes/delete-relations-dialog/DeleteDependenciesDialog.svelte";
    import AddToGraphDiagramDialog from "../../../../routes/mainpage/packageNavigation/custom-diagram-dialogs/AddToGraphDiagramDialog.svelte";
    import AddToWorkspaceDiagramDialog from "../../../../routes/mainpage/packageNavigation/custom-diagram-dialogs/AddToWorkspaceDiagramDialog.svelte";
    import RemoveFromDiagramDialog from "../../../../routes/mainpage/packageNavigation/custom-diagram-dialogs/RemoveFromDiagramDialog.svelte";
    import ExtendSchemaSubMenu from "../../../../routes/mainpage/packageNavigation/ExtendSchemaSubMenu.svelte";
    import SHACLClassSpecificPopUp from "../../../../routes/shacl/shaclclassspecific/SHACLClassSpecificPopUp.svelte";

    let {
        request = null,
        disabled = false,
        readOnly = false,
        contextMenuClass = null,
        workspaceName = "",
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
    let showDeleteDependenciesDialog = $state(false);
    let showSHACLDialog = $state(false);
    let showRemoveFromDiagramDialog = $state(false);
    let showAddToGraphDiagramDialog = $state(false);
    let showAddToWorkspaceDiagramDialog = $state(false);

    let dialogClassIds = $state([]);
    let dialogClassLabels = $state([]);
    let dialogGraphUri = $state(null);
    let dialogClasses = $state([]);

    /** The classes of the selection as the schema extension expects them: in
     * the cross-profile diagram they carry a merged uuid instead of a graph. */
    const extendClasses = $derived(
        multiActive
            ? multiSelectState.getSelectedClassRefs(isCrossProfileDiagram)
            : contextMenuClass
              ? [
                    {
                        uuid: contextMenuClass.uuid,
                        graphUri: isCrossProfileDiagram
                            ? null
                            : (contextMenuClass.graphUri ?? graphUri),
                    },
                ]
              : [],
    );

    /** The schema of the diagram the class was right-clicked in, if any. */
    const currentGraphUri = $derived(
        isCrossProfileDiagram
            ? null
            : (contextMenuClass?.graphUri ?? graphUri) || null,
    );

    let triggerStyle = $derived(getContextMenuTriggerStyle(request));

    const multiActive = $derived(multiSelectState.isMultiSelect);
    const selectionUuids = $derived(
        multiActive
            ? multiSelectState.getSelected().map(e => e.classUuid)
            : contextMenuClass
              ? [contextMenuClass.uuid]
              : [],
    );
    const selectionLabels = $derived(
        multiActive
            ? multiSelectState.getSelected().map(e => e.classLabel)
            : contextMenuClass
              ? [contextMenuClass.label]
              : [],
    );

    const selectionClasses = $derived(
        multiActive
            ? multiSelectState.getSelected().map(entry => ({
                  id: entry.classUuid,
                  graphUri: entry.graphUri ?? graphUri,
              }))
            : contextMenuClass
              ? [
                    {
                        id: contextMenuClass.uuid,
                        graphUri: contextMenuClass.graphUri ?? graphUri,
                    },
                ]
              : [],
    );

    const crossGraphDisabled = $derived(
        multiActive && !multiSelectState.isSingleGraph,
    );

    const isCrossProfileDiagram = $derived(
        editorState.selectedDiagram.getProperty("type") ===
            DiagramType.CROSS_PROFILE,
    );

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
        if (classActionsDisabled || crossGraphDisabled || !contextMenuClass) {
            return;
        }
        dialogClassIds = selectionUuids;
        dialogGraphUri = multiActive
            ? (multiSelectState.getSelected()[0]?.graphUri ?? graphUri)
            : (contextMenuClass.graphUri ?? graphUri);
        showDeleteDependenciesDialog = true;
        onClose();
    }

    function openRemoveFromDiagramDialog() {
        if (!contextMenuClass) {
            return;
        }
        dialogClassIds = selectionUuids;
        dialogClassLabels = selectionLabels;
        showRemoveFromDiagramDialog = true;
        onClose();
    }

    function openAddToGraphDiagramDialog() {
        dialogClasses = selectionClasses;
        showAddToGraphDiagramDialog = true;
        onClose();
    }

    function openAddToWorkspaceDiagramDialog() {
        dialogClasses = selectionClasses;
        showAddToWorkspaceDiagramDialog = true;
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
        copyState.set(
            multiSelectState.copyEntriesOr({
                classUUID: contextMenuClass.uuid,
                graphURI:
                    contextMenuClass.graphUri ??
                    editorState.selectedGraph.getValue(),
                workspaceName: editorState.selectedWorkspace.getValue(),
            }),
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
    <ContextMenu.Content>
        {#if editorState.selectedDiagram.getProperty("type") !== DiagramType.CROSS_PROFILE}
            <ContextMenu.Item.Button
                onSelect={copyClass}
                faIcon={faCopy}
                altText="Ctrl+C"
            >
                {multiActive
                    ? `Copy ${selectionUuids.length} Classes`
                    : "Copy Class"}
            </ContextMenu.Item.Button>
        {/if}
        <ExtendSchemaSubMenu
            label="Extend with Inheritance"
            withInheritance
            {workspaceName}
            classes={extendClasses}
            {currentGraphUri}
            selectedClassUuid={multiActive ? null : contextMenuClass?.uuid}
            {readOnly}
            onDone={onClose}
        />
        <ExtendSchemaSubMenu
            label="Extend in Schema"
            {workspaceName}
            classes={extendClasses}
            {currentGraphUri}
            selectedClassUuid={multiActive ? null : contextMenuClass?.uuid}
            {readOnly}
            onDone={onClose}
        />
        {#if editorState.selectedDiagram.getProperty("type") !== DiagramType.CROSS_PROFILE}
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    showSHACLDialog = true;
                }}
                faIcon={faDiagramProject}
                disabled={multiActive}
            >
                View Constraints (SHACL)
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            {#if graphUri}
                <ContextMenu.Item.Button
                    onSelect={openAddToGraphDiagramDialog}
                    disabled={crossGraphDisabled}
                    faIcon={faObjectGroup}
                >
                    Add to Schema Diagram
                </ContextMenu.Item.Button>
            {/if}
            <ContextMenu.Item.Button
                onSelect={openAddToWorkspaceDiagramDialog}
                faIcon={faObjectGroup}
            >
                Add to Workspace Diagram
            </ContextMenu.Item.Button>
        {/if}
        <ContextMenu.SubMenu.Root>
            <ContextMenu.SubMenu.Trigger
                faIcon={faLayerGroup}
                disabled={classActionsDisabled || multiActive}
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
                    Move to Front
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Button
                    onSelect={e => {
                        e.preventDefault();
                        handleMoveUp();
                    }}
                    faIcon={faAngleUp}
                    disabled={classActionsDisabled || isAtFront}
                >
                    Move Up
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
                    Move Down
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Button
                    onSelect={e => {
                        e.preventDefault();
                        handleMoveToBottom();
                    }}
                    faIcon={faAnglesDown}
                    disabled={classActionsDisabled || isAtBack}
                >
                    Move to Bottom
                </ContextMenu.Item.Button>
            </ContextMenu.SubMenu.Content>
        </ContextMenu.SubMenu.Root>
        {#if editorState.selectedDiagram.getProperty("type") !== DiagramType.CROSS_PROFILE}
            <ContextMenuSeparator />
            {#if editorState.selectedDiagram.getProperty("type") !== DiagramType.PACKAGE}
                <ContextMenu.Item.Button
                    onSelect={openRemoveFromDiagramDialog}
                    faIcon={faMinus}
                    variant="danger"
                >
                    {multiActive
                        ? `Remove ${selectionUuids.length} from Diagram`
                        : "Remove from Diagram"}
                </ContextMenu.Item.Button>
            {/if}
            <ContextMenu.Item.Button
                onSelect={openDeleteClassDialog}
                disabled={classActionsDisabled || crossGraphDisabled}
                faIcon={faTrash}
                variant="danger"
                altText="Del"
            >
                {multiActive
                    ? `Delete ${selectionUuids.length} Classes`
                    : "Delete Class"}
            </ContextMenu.Item.Button>
        {/if}
    </ContextMenu.Content>
</ContextMenu.Root>

<DeleteDependenciesDialog
    bind:showDialog={showDeleteDependenciesDialog}
    {workspaceName}
    graphUri={dialogGraphUri ?? graphUri}
    resourceUuids={dialogClassIds}
/>
<SHACLClassSpecificPopUp
    workspaceName={editorState.selectedWorkspace.getValue()}
    graphUri={editorState.selectedGraph.getValue()}
    reactiveClass={shaclClass}
    bind:showDialog={showSHACLDialog}
/>

<RemoveFromDiagramDialog
    bind:showDialog={showRemoveFromDiagramDialog}
    lockedWorkspaceName={workspaceName}
    {graphUri}
    diagramId={editorState.selectedDiagram.getProperty("id")}
    classIds={dialogClassIds}
    classLabels={dialogClassLabels}
/>

{#if showAddToGraphDiagramDialog}
    <AddToGraphDiagramDialog
        bind:showDialog={showAddToGraphDiagramDialog}
        lockedWorkspaceName={workspaceName}
        lockedGraphUri={graphUri}
        classes={dialogClasses}
    />
{/if}

{#if showAddToWorkspaceDiagramDialog}
    <AddToWorkspaceDiagramDialog
        bind:showDialog={showAddToWorkspaceDiagramDialog}
        lockedWorkspaceName={workspaceName}
        lockedGraphUri={graphUri}
        classes={dialogClasses}
    />
{/if}
