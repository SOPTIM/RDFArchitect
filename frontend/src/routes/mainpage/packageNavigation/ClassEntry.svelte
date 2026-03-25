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

    import {
        getUri,
        getPackageId,
        isSelectedClass,
    } from "./packageNavigationUtils.svelte.js";
    import DeleteClassConfirmDialog from "../../DeleteClassConfirmDialog.svelte";
    import SHACLClassSpecificPopUp from "../../shacl/shaclclassspecific/SHACLClassSpecificPopUp.svelte";

    let {
        dataset,
        graph,
        pack,
        cls,
        prefixes = [],
        readOnly = false,
        onPackChange = () => {},
    } = $props();

    let showDeleteDialog = $state(false);
    let showSHACLDialog = $state(false);

    const highlightLabel = $derived(
        shortenIri(
            prefixes,
            cls?.prefix ? `${cls.prefix}${cls.label}` : (cls?.label ?? ""),
        ),
    );
    const shaclClass = $derived({
        uuid: { value: cls?.uuid },
        label: { value: cls?.label ?? "" },
    });

    function selectClass() {
        onPackChange({
            ...pack,
            showContents: true,
            userCollapsed: false,
        });
        if (!editorState.selectedClassUUID.getValue()) {
            eventStack.executeNewestEvent(cls.uuid);
            editorState.selectedClassDataset.updateValue(dataset.label);
            editorState.selectedClassGraph.updateValue(getUri(graph));
            editorState.selectedClassUUID.updateValue(cls.uuid);
            return;
        }
        eventStack.executeNewestEvent({
            datasetName: dataset.label,
            graphUri: getUri(graph),
            classUuid: cls.uuid,
        });
    }

    function focusClassInDiagram() {
        if (editorState.focusedClassUUID.getValue() === cls.uuid) {
            editorState.focusedClassUUID.trigger();
            return;
        }
        editorState.focusedClassUUID.updateValue(cls.uuid);
    }

    function showClassInPackage() {
        editorState.selectedDataset.updateValue(dataset.label);
        editorState.selectedGraph.updateValue(getUri(graph));
        editorState.selectedPackageUUID.updateValue(getPackageId(pack));
        selectClass();
        focusClassInDiagram();
    }
</script>

<ContextMenu.Root>
    <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
        <NavigationEntry
            level={4}
            label={cls.label}
            icon={faFileLines}
            isSelected={isSelectedClass(dataset, graph, cls)}
            title={cls.label}
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
            SHACL
        </ContextMenu.Item.Button>
        <ContextMenu.Separator />
        <ContextMenu.Item.Button
            onSelect={() => {
                selectClass();
                showDeleteDialog = true;
            }}
            disabled={readOnly}
            faIcon={faTrash}
            variant="danger"
        >
            Delete Class
        </ContextMenu.Item.Button>
    </ContextMenu.Content>
</ContextMenu.Root>

<DeleteClassConfirmDialog
    bind:showDialog={showDeleteDialog}
    datasetName={dataset.label}
    graphUri={getUri(graph)}
    classUuid={cls.uuid}
    classLabel={cls.label}
/>
<SHACLClassSpecificPopUp
    datasetName={dataset.label}
    graphUri={getUri(graph)}
    reactiveClass={shaclClass}
    bind:showDialog={showSHACLDialog}
    class={shaclClass}
/>
