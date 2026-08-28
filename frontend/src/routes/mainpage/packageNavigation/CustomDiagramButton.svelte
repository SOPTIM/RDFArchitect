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
    import {
        ClassType,
        DiagramType,
        editorState,
    } from "$lib/sharedState.svelte.js";

    import ClassEntry from "./ClassEntry.svelte";
    import CustomDiagramDeleteDialog from "./custom-diagram-dialogs/CustomDiagramDeleteDialog.svelte";
    import CustomGraphDiagramDialog from "./custom-diagram-dialogs/CustomGraphDiagramDialog.svelte";
    import CustomWorkspaceDiagramDialog from "./custom-diagram-dialogs/CustomWorkspaceDiagramDialog.svelte";
    import { isSelectedCustomDiagram } from "./packageNavigationUtils.svelte.js";

    let {
        workspaceNavEntry,
        graphNavEntry,
        diagram = $bindable(),
        classes,
        readonly,
        level = 3,
        onToggle,
    } = $props();

    let showEditDiagramDialog = $state(false);
    let showDeleteDiagramDialog = $state(false);

    const diagramType = $derived(
        graphNavEntry
            ? DiagramType.CUSTOM_GRAPH_DIAGRAM
            : DiagramType.CUSTOM_WORKSPACE_DIAGRAM,
    );

    const classGraphNavEntry = $derived(graphNavEntry ?? { id: null });
    const classType = $derived(
        graphNavEntry ? ClassType.SINGLE_CLASS : ClassType.MERGED_CLASS,
    );

    let packageIcon = $derived(diagram.showContents ? faFolderOpen : faFolder);
    const hasClasses = $derived(diagram.classes?.length > 0);

    const rangeSiblings = $derived(
        classes?.map(cls => ({
            classNavEntry: cls,
            graphNavEntry: classGraphNavEntry,
        })) ?? [],
    );

    async function toggleDiagramContentsVisibility() {
        try {
            await onToggle();
        } finally {
            const next = !diagram.showContents;

            diagram.showContents = next;
            diagram.userCollapsed = !next;
        }
    }

    function selectDiagram() {
        editorState.selectCustomDiagram(
            workspaceNavEntry.label,
            graphNavEntry ? graphNavEntry.id : null,
            diagram.diagramId,
            diagramType,
        );
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
                    workspaceNavEntry.id,
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
    {#if diagram.showContents && classes?.length}
        <div
            class="flex w-full flex-col items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each classes as cls (cls.id)}
                <ClassEntry
                    {workspaceNavEntry}
                    graphNavEntry={classGraphNavEntry}
                    classNavEntry={cls}
                    diagramId={diagram.diagramId}
                    diagramGraphUri={graphNavEntry?.id}
                    {classType}
                    {diagramType}
                    {rangeSiblings}
                    {readonly}
                    level={level + 1}
                />
            {/each}
        </div>
    {/if}
</div>

{#if graphNavEntry}
    <CustomGraphDiagramDialog
        bind:showDialog={showEditDiagramDialog}
        lockedWorkspaceName={workspaceNavEntry.id}
        lockedGraphUri={graphNavEntry.id}
        diagramName={diagram.name}
        diagramId={diagram.diagramId}
        selectedClasses={diagram.classes}
    />
{:else}
    <CustomWorkspaceDiagramDialog
        bind:showDialog={showEditDiagramDialog}
        lockedWorkspaceName={workspaceNavEntry.id}
        diagramName={diagram.name}
        diagramId={diagram.diagramId}
        selectedClasses={diagram.classes}
    />
{/if}

<CustomDiagramDeleteDialog
    bind:showDialog={showDeleteDiagramDialog}
    workspaceName={workspaceNavEntry.id}
    graphUri={graphNavEntry ? graphNavEntry.id : null}
    {diagram}
/>
