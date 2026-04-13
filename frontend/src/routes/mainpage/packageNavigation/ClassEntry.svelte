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
        faTrash,
    } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";
    import { shortenIri } from "$lib/utils/iri.js";

    import { isSelectedClass } from "./packageNavigationUtils.svelte.js";
    import DeleteDependenciesDialog from "../../delete-relations-dialog/DeleteDependenciesDialog.svelte";
    import SHACLClassSpecificPopUp from "../../shacl/shaclclassspecific/SHACLClassSpecificPopUp.svelte";

    let {
        datasetNavEntry,
        graphNavEntry,
        classNavEntry,
        namespaces = [],
        readonly = false,
        onPackChange = () => {},
    } = $props();

    let showDeleteDependenciesDialog = $state(false);
    let showSHACLDialog = $state(false);

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
        <ContextMenu.Separator />
        <ContextMenu.Item.Button
            onSelect={() => {
                selectClass();
                showDeleteDependenciesDialog = true;
            }}
            disabled={readonly}
            faIcon={faTrash}
            variant="danger"
        >
            Delete Class
        </ContextMenu.Item.Button>
    </ContextMenu.Content>
</ContextMenu.Root>

<DeleteDependenciesDialog
    bind:showDialog={showDeleteDependenciesDialog}
    datasetName={datasetNavEntry.id}
    graphUri={graphNavEntry.id}
    resourceUuid={classNavEntry.id}
/>

<SHACLClassSpecificPopUp
    datasetName={datasetNavEntry.id}
    graphUri={graphNavEntry.id}
    reactiveClass={shaclClass}
    bind:showDialog={showSHACLDialog}
/>
