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
    import { faFileLines } from "@fortawesome/free-regular-svg-icons";
    import {
        faArrowUpRightFromSquare,
        faDiagramProject,
        faMinus,
        faObjectGroup,
        faTrash,
    } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";
    import { shortenIri } from "$lib/utils/iri.js";

    import { isSelectedClass } from "./packageNavigationUtils.svelte.js";
    import DeleteClassConfirmDialog from "../../DeleteClassConfirmDialog.svelte";
    import AddToDatasetDiagramDialog from "./custom-diagram-dialogs/AddToDatasetDiagramDialog.svelte";
    import AddToGraphDiagramDialog from "./custom-diagram-dialogs/AddToGraphDiagramDialog.svelte";
    import RemoveFromDiagramDialog from "./custom-diagram-dialogs/RemoveFromDiagramDialog.svelte";
    import SHACLClassSpecificPopUp from "../../shacl/shaclclassspecific/SHACLClassSpecificPopUp.svelte";

    let {
        datasetNavEntry,
        graphNavEntry,
        classNavEntry,
        diagramId,
        namespaces = [],
        readonly = false,
        onPackChange = () => {},
    } = $props();

    let showDeleteDialog = $state(false);
    let showSHACLDialog = $state(false);
    let showAddToGraphDiagramDialog = $state(false);
    let showAddToDatasetDiagramDialog = $state(false);
    let showRemoveFromDiagramDialog = $state(false);

    const highlightLabel = $derived(shortenIri(namespaces, classNavEntry.id));
    const shaclClass = $derived({
        uuid: { value: classNavEntry?.id },
        label: { value: classNavEntry?.label ?? "" },
    });

    function selectClass() {
        classNavEntry.parent?.open();
        onPackChange();
        if (!editorState.selectedClassUUID.getValue()) {
            eventStack.executeNewestEvent(classNavEntry.id);
            editorState.selectedClassDataset.updateValue(datasetNavEntry.id);
            editorState.selectedClassGraph.updateValue(graphNavEntry.id);
            editorState.selectedClassUUID.updateValue(classNavEntry.id);
            return;
        }
        //The event executed to open the discard confirm delete dialog
        eventStack.executeNewestEvent({
            datasetName: datasetNavEntry.id,
            graphUri: graphNavEntry.id,
            classUuid: classNavEntry.id,
        });
    }

    function focusClassInDiagram() {
        if (editorState.focusedClassUUID.getValue() === classNavEntry.id) {
            editorState.focusedClassUUID.trigger();
            return;
        }
        editorState.focusedClassUUID.updateValue(classNavEntry.id);
    }

    function showClassInPackage() {
        editorState.selectedDataset.updateValue(datasetNavEntry.id);
        editorState.selectedGraph.updateValue(graphNavEntry.id);
        editorState.selectedPackageUUID.updateValue(
            classNavEntry.parent?.id ?? "default",
        );
        selectClass();
        focusClassInDiagram();
    }
</script>

<ContextMenu.Root>
    <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
        <NavigationEntry
            level={4}
            label={classNavEntry.label}
            icon={faFileLines}
            isSelected={isSelectedClass(
                datasetNavEntry.id,
                graphNavEntry.id,
                classNavEntry.id,
            )}
            title={classNavEntry.tooltip}
            {highlightLabel}
            onclick={selectClass}
        />
    </ContextMenu.TriggerArea>
    <ContextMenu.Content>
        <ContextMenu.Item.Button
            onSelect={showClassInPackage}
            faIcon={faArrowUpRightFromSquare}
        >
            Show in diagram
        </ContextMenu.Item.Button>
        <ContextMenu.Item.Button
            onSelect={() => {
                showSHACLDialog = true;
            }}
            faIcon={faDiagramProject}
        >
            Constrains
        </ContextMenu.Item.Button>
        {#if !diagramId}
            <ContextMenu.Item.Button
                onSelect={() => {
                    showAddToGraphDiagramDialog = true;
                }}
                faIcon={faObjectGroup}
            >
                Add to Profile Diagram
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    showAddToDatasetDiagramDialog = true;
                }}
                faIcon={faObjectGroup}
            >
                Add to Dataset Diagram
            </ContextMenu.Item.Button>
        {/if}
        <ContextMenu.Separator />
        {#if diagramId}
            <ContextMenu.Item.Button
                onSelect={() => {
                    showRemoveFromDiagramDialog = true;
                }}
                faIcon={faMinus}
                variant="danger"
            >
                Remove from Diagram
            </ContextMenu.Item.Button>
        {/if}
        <ContextMenu.Item.Button
            onSelect={() => {
                selectClass();
                showDeleteDialog = true;
            }}
            disabled={readonly}
            faIcon={faTrash}
            variant="danger"
        >
            Delete Class
        </ContextMenu.Item.Button>
    </ContextMenu.Content>
</ContextMenu.Root>

<DeleteClassConfirmDialog
    bind:showDialog={showDeleteDialog}
    datasetName={datasetNavEntry.id}
    graphUri={graphNavEntry.id}
    classUuid={classNavEntry.id}
    classLabel={classNavEntry.label}
/>
<SHACLClassSpecificPopUp
    datasetName={datasetNavEntry.id}
    graphUri={graphNavEntry.id}
    reactiveClass={shaclClass}
    bind:showDialog={showSHACLDialog}
/>
<AddToGraphDiagramDialog
    bind:showDialog={showAddToGraphDiagramDialog}
    lockedDatasetName={datasetNavEntry.id}
    lockedGraphUri={graphNavEntry.id}
    classes={[classNavEntry]}
/>
<AddToDatasetDiagramDialog
    bind:showDialog={showAddToDatasetDiagramDialog}
    lockedDatasetName={datasetNavEntry.id}
    lockedGraphUri={graphNavEntry.id}
    classes={[classNavEntry]}
/>
<RemoveFromDiagramDialog
    bind:showDialog={showRemoveFromDiagramDialog}
    lockedDatasetName={datasetNavEntry.id}
    graphUri={graphNavEntry.id}
    {diagramId}
    cls={classNavEntry}
/>
