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
        faFileExport,
    } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { DiagramType, editorState } from "$lib/sharedState.svelte.js";

    import CrossProfileColorDialog from "./custom-diagram-dialogs/CrossProfileColorDialog.svelte";

    let { datasetNavEntry } = $props();

    let showColorDialog = $state(false);
</script>

<div
    class="bg-border my-1 ml-14 h-0.5"
    role="presentation"
    oncontextmenu={e => e.stopPropagation()}
></div>

<ContextMenu.Root>
    <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
        <NavigationEntry
            level={2}
            label="Merged View"
            icon={faObjectGroup}
            hasChildren={false}
            isSelected={!editorState.selectedGraph.getValue() &&
                editorState.selectedDataset.getValue() ===
                    datasetNavEntry.label &&
                editorState.selectedDiagram.getProperty("type") ===
                    DiagramType.CROSS_PROFILE}
            onclick={() => {
                editorState.selectedDataset.updateValue(datasetNavEntry.label);
                editorState.selectedGraph.updateValue(null);
                editorState.selectedDiagram.updateValue({
                    type: DiagramType.CROSS_PROFILE,
                    id: datasetNavEntry.crossProfileID,
                });
            }}
        />
    </ContextMenu.TriggerArea>
    <ContextMenu.Content>
        <ContextMenu.Item.Button
            onSelect={() => {
                showColorDialog = true;
            }}
            faIcon={faFileExport}
        >
            Edit Schema Colors
        </ContextMenu.Item.Button>
    </ContextMenu.Content>
</ContextMenu.Root>

<CrossProfileColorDialog
    bind:showDialog={showColorDialog}
    datasetName={datasetNavEntry.id}
/>
