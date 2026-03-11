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
        faFolder,
        faFolderOpen
    } from "@fortawesome/free-regular-svg-icons";
    import {
        faPencil,
        faTrash
    } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import {
        editorState,
        forceReloadTrigger
    } from "$lib/sharedState.svelte.js";

    import ClassEntry from "./ClassEntry.svelte";
    import CustomDiagramDeleteDialog from "./custom-diagram-dialog/CustomDiagramDeleteDialog.svelte";
    import CustomDiagramDialog from "./custom-diagram-dialog/CustomDiagramDialog.svelte";
    import {
        getUri, isSelectedCustomDiagram
    } from "./packageNavigationUtils.svelte.js";

    let {
        dataset,
        graph,
        diagram,
        readOnly
    } = $props();

    let showEditDiagramDialog = $state(false);
    let showDeleteDiagramDialog = $state(false);

    let packageIcon = $derived(diagram.showContents ? faFolderOpen : faFolder);
    const hasClasses = $derived(diagram.classes?.length > 0);

    function toggleDiagramContentsVisibility() {
        const next = !diagram.showContents;

        diagram.showContents = next;
        diagram.userCollapsed = !next;
    }

    function selectDiagram() {
        editorState.selectedDataset.updateValue(dataset.label);
        editorState.selectedGraph.updateValue(getUri(graph));
        editorState.selectedPackageUUID.updateValue(null);
        editorState.selectedCustomDiagramUUID.updateValue(diagram.diagramId);
    }
</script>

<div class="flex w-full flex-col items-stretch gap-[0.1rem]">
    <ContextMenu.Root>
        <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
            <NavigationEntry
                level={4}
                label={diagram.name}
                icon={packageIcon}
                isSelected={isSelectedCustomDiagram(dataset, graph, diagram)}
                hasChildren={hasClasses}
                expanded={diagram.showContents}
                title={diagram.name}
                onclick={selectDiagram}
                onToggle={toggleDiagramContentsVisibility}
            />
        </ContextMenu.TriggerArea>
        <ContextMenu.Content>
            <ContextMenu.Item.Button
                onSelect={() => {
                    showEditDiagramDialog = true;
                }}
                faIcon={faPencil}
            >
                Edit Diagram
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    showDeleteDiagramDialog = true;
                }}
                faIcon={faTrash}
                variant="danger"
            >
                Delete Diagram
            </ContextMenu.Item.Button>
        </ContextMenu.Content>
    </ContextMenu.Root>
    {#if diagram.showContents && hasClasses}
        <div
            class="flex w-full flex-col items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each diagram.classes as cls (cls.uuid)}
                <ClassEntry
                    {dataset}
                    {graph}
                    {cls}
                    {readOnly}
                />
            {/each}
        </div>
    {/if}
</div>

<CustomDiagramDialog
    bind:showDialog={showEditDiagramDialog}
    lockedDatasetName={dataset.label}
    lockedGraphUri={getUri(graph)}
    diagramName={diagram.name}
    diagramId={diagram.diagramId}
/>

<CustomDiagramDeleteDialog
    bind:showDialog={showDeleteDiagramDialog}
    datasetName={dataset.label}
    graphUri={getUri(graph)}
    {diagram}
/>