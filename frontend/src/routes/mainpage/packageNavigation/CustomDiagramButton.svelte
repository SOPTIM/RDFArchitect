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
        faFolderOpen,
    } from "@fortawesome/free-regular-svg-icons";
    import { faPencil, faTrash } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { editorState } from "$lib/sharedState.svelte.js";

    import ClassEntry from "./ClassEntry.svelte";
    import CustomDatasetDiagramDialog from "./custom-diagram-dialogs/CustomDatasetDiagramDialog.svelte";
    import CustomDiagramDeleteDialog from "./custom-diagram-dialogs/CustomDiagramDeleteDialog.svelte";
    import CustomGraphDiagramDialog from "./custom-diagram-dialogs/CustomGraphDiagramDialog.svelte";
    import { isSelectedCustomDiagram } from "./packageNavigationUtils.svelte.js";

    let {
        datasetNavEntry,
        graphNavEntry,
        allGraphNavEntries,
        diagram,
        classes,
        readOnly,
        level = 4,
        onToggle,
    } = $props();

    let showEditDiagramDialog = $state(false);
    let showDeleteDiagramDialog = $state(false);
    let graphNavEntryByClass = $derived.by(() => {
        const map = {};

        classes?.forEach(diagramClass => {
            const graph = allGraphNavEntries.find(g =>
                g.children.some(pack =>
                    pack.children.some(cls => cls.id === diagramClass.id),
                ),
            );
            if (graph) {
                map[diagramClass.id] = graph;
            } else {
                console.warn(
                    "Could not find graph for class ",
                    diagramClass.id,
                );
            }
        });
        return map;
    });

    let packageIcon = $derived(diagram.showContents ? faFolderOpen : faFolder);
    const hasClasses = $derived(diagram.classes?.length > 0);

    async function toggleDiagramContentsVisibility() {
        await onToggle();
        const next = !diagram.showContents;

        diagram.showContents = next;
        diagram.userCollapsed = !next;
    }

    function getGraphNavEntryForClass(classUUID) {
        if (graphNavEntry) {
            return graphNavEntry;
        }
        const navEntry = graphNavEntryByClass[classUUID];
        if (navEntry) {
            return navEntry;
        }
    }

    function selectDiagram() {
        editorState.selectedDataset.updateValue(datasetNavEntry.label);
        editorState.selectedGraph.updateValue(
            graphNavEntry ? graphNavEntry.id : null,
        );
        editorState.selectedPackageUUID.updateValue(null);
        editorState.selectedCustomDiagramUUID.updateValue(diagram.diagramId);
    }
</script>

<div class="flex w-full flex-col items-stretch gap-[0.1rem]">
    <ContextMenu.Root>
        <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
            <NavigationEntry
                {level}
                label={diagram.name}
                icon={packageIcon}
                isSelected={isSelectedCustomDiagram(
                    datasetNavEntry.id,
                    graphNavEntry?.id,
                    diagram,
                )}
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
            {#each classes as cls (cls.id)}
                <ClassEntry
                    {datasetNavEntry}
                    graphNavEntry={getGraphNavEntryForClass(cls.id)}
                    classNavEntry={cls}
                    diagramId={diagram.diagramId}
                    {readOnly}
                />
            {/each}
        </div>
    {/if}
</div>

{#if graphNavEntry}
    <CustomGraphDiagramDialog
        bind:showDialog={showEditDiagramDialog}
        lockedDatasetName={datasetNavEntry.id}
        lockedGraphUri={graphNavEntry.id}
        diagramName={diagram.name}
        diagramId={diagram.diagramId}
        selectedClasses={diagram.classes}
    />
{:else}
    <CustomDatasetDiagramDialog
        bind:showDialog={showEditDiagramDialog}
        lockedDatasetName={datasetNavEntry.id}
        diagramName={diagram.name}
        diagramId={diagram.diagramId}
        selectedClasses={diagram.classes}
    />
{/if}

<CustomDiagramDeleteDialog
    bind:showDialog={showDeleteDiagramDialog}
    datasetName={datasetNavEntry.id}
    graphUri={graphNavEntry ? graphNavEntry.id : null}
    {diagram}
/>
