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
        faFileExport,
        faMinus,
        faObjectGroup,
        faTrash,
        faCopy,
    } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import {
        DiagramType,
        SelectionLevel,
        copyState,
        editorState,
        ClassType,
        mergeSelections,
        multiSelectState,
    } from "$lib/sharedState.svelte.js";
    import { shortenIri } from "$lib/utils/iri.js";

    import AddToDatasetDiagramDialog from "./custom-diagram-dialogs/AddToDatasetDiagramDialog.svelte";
    import AddToGraphDiagramDialog from "./custom-diagram-dialogs/AddToGraphDiagramDialog.svelte";
    import RemoveFromDiagramDialog from "./custom-diagram-dialogs/RemoveFromDiagramDialog.svelte";
    import ExtendClassDialog from "./ExtendClassDialog.svelte";
    import { classHighlight } from "./packageNavigationUtils.svelte.js";
    import DeleteDependenciesDialog from "../../delete-relations-dialog/DeleteDependenciesDialog.svelte";
    import SHACLClassSpecificPopUp from "../../shacl/shaclclassspecific/SHACLClassSpecificPopUp.svelte";

    let {
        datasetNavEntry,
        graphNavEntry,
        classNavEntry,
        diagramId,
        diagramGraphUri,
        rangeSiblings = null,
        namespaces = [],
        readonly = false,
        onPackChange = () => {},
        classType = ClassType.SINGLE_CLASS,
        diagramType = DiagramType.PACKAGE,
    } = $props();

    let showDeleteDependenciesDialog = $state(false);
    let showSHACLDialog = $state(false);
    let showExtendClassDialog = $state(false);
    let showAddToGraphDiagramDialog = $state(false);
    let showAddToDatasetDiagramDialog = $state(false);
    let showRemoveFromDiagramDialog = $state(false);

    const highlightLabel = $derived(shortenIri(namespaces, classNavEntry.id));

    const classState = $derived(
        classHighlight(datasetNavEntry.id, graphNavEntry.id, classNavEntry.id),
    );
    const shaclClass = $derived({
        uuid: { value: classNavEntry?.id },
        label: { value: classNavEntry?.label ?? "" },
    });

    const isMultiSelected = $derived(
        multiSelectState.isSelected(
            datasetNavEntry.id,
            graphNavEntry.id,
            classNavEntry.id,
        ),
    );

    const multiActive = $derived(
        multiSelectState.isMultiSelect && isMultiSelected,
    );

    const crossGraphDisabled = $derived(
        multiActive && !multiSelectState.isSingleGraph,
    );
    const selectedClassNavEntries = $derived(
        multiActive
            ? multiSelectState.getSelected().map(e => e.classNavEntry)
            : [classNavEntry],
    );
    const selectedClassIds = $derived(
        multiActive
            ? multiSelectState.getSelected().map(e => e.classUuid)
            : [classNavEntry.id],
    );
    const selectedClassLabels = $derived(
        multiActive
            ? multiSelectState.getSelected().map(e => e.classLabel)
            : [classNavEntry.label],
    );

    function buildSelectionEntry(
        navEntry = classNavEntry,
        graphUri = graphNavEntry?.id,
    ) {
        return {
            datasetName: datasetNavEntry.id,
            graphUri,
            classUuid: navEntry.id,
            classLabel: navEntry.label,
            packageId: navEntry.parent?.id ?? null,
            classNavEntry: navEntry,
        };
    }

    function onEntryClick(event) {
        const additive = event?.ctrlKey || event?.metaKey;
        const entry = buildSelectionEntry();
        if (event?.shiftKey) {
            editorState.markClassActive();
            selectRange(additive);
            return;
        }
        if (additive) {
            if (deselectActiveSingleClass(entry)) {
                return;
            }
            editorState.markClassActive();
            const anchor = multiSelectState.anchor;
            if (
                multiSelectState.getSelected().length === 0 &&
                anchor &&
                isOpenPackageClass(anchor) &&
                !(
                    anchor.datasetName === entry.datasetName &&
                    anchor.graphUri === entry.graphUri &&
                    anchor.classUuid === entry.classUuid
                )
            ) {
                multiSelectState.setSelection([anchor]);
            }
            multiSelectState.toggle(entry);
            keepOpenClassLightWhenSelectionEmpty();
            return;
        }
        editorState.markClassActive();
        multiSelectState.clear();
        multiSelectState.anchor = entry;
        selectClass();
    }

    function deselectActiveSingleClass(entry) {
        const isActiveSingle =
            multiSelectState.getSelected().length === 0 &&
            editorState.activeSelectionKind.getValue() ===
                SelectionLevel.CLASS &&
            isOpenPackageClass(entry);
        if (!isActiveSingle) {
            return false;
        }
        editorState.activeSelectionKind.updateValue(SelectionLevel.PACKAGE);
        return true;
    }

    function keepOpenClassLightWhenSelectionEmpty() {
        if (
            multiSelectState.getSelected().length === 0 &&
            isOpenPackageClass()
        ) {
            editorState.activeSelectionKind.updateValue(SelectionLevel.PACKAGE);
        }
    }

    function isOpenPackageClass(entry = null) {
        const uuid = editorState.selectedClass.getProperty("id");
        if (
            !uuid ||
            editorState.selectedDiagram.getProperty("type") !==
                DiagramType.PACKAGE
        ) {
            return false;
        }
        if (!entry) {
            return true;
        }
        return (
            uuid === entry.classUuid &&
            editorState.selectedClassDataset.getValue() === entry.datasetName &&
            editorState.selectedClassGraph.getValue() === entry.graphUri
        );
    }

    function rangeUnits() {
        if (rangeSiblings) {
            return rangeSiblings.map(s => ({
                navEntry: s.classNavEntry,
                graphUri: s.graphNavEntry?.id,
            }));
        }
        const siblings = classNavEntry.parent?.children ?? [];
        return siblings.map(c => ({
            navEntry: c,
            graphUri: graphNavEntry?.id,
        }));
    }

    function selectRange(additive = false) {
        const anchor = multiSelectState.anchor;
        const units = rangeUnits();
        const anchorIdx = anchor
            ? units.findIndex(
                  u =>
                      u.navEntry.id === anchor.classUuid &&
                      u.graphUri === anchor.graphUri &&
                      anchor.datasetName === datasetNavEntry.id,
              )
            : -1;
        const targetIdx = units.findIndex(
            u =>
                u.navEntry.id === classNavEntry.id &&
                u.graphUri === graphNavEntry?.id,
        );
        if (anchorIdx === -1 || targetIdx === -1) {
            multiSelectState.toggle(buildSelectionEntry());
            return;
        }
        const [start, end] =
            anchorIdx <= targetIdx
                ? [anchorIdx, targetIdx]
                : [targetIdx, anchorIdx];
        const range = units
            .slice(start, end + 1)
            .map(u => buildSelectionEntry(u.navEntry, u.graphUri));
        if (additive) {
            multiSelectState.setSelection(
                mergeSelections(multiSelectState.getSelected(), range),
            );
            return;
        }
        multiSelectState.selectRange(range);
    }

    function onEntryContextMenu() {
        if (
            multiSelectState.count > 0 &&
            !multiSelectState.isSelected(
                datasetNavEntry.id,
                graphNavEntry.id,
                classNavEntry.id,
            )
        ) {
            multiSelectState.clear();
        }
    }

    function selectClass() {
        if (!diagramId && !editorState.selectedDiagram.getProperty("id")) {
            showClassInPackage();
            return;
        }
        if (!diagramId) {
            classNavEntry.parent?.open();
        }
        onPackChange();
        if (!editorState.selectedClass.getProperty("id")) {
            eventStack.executeNewestEvent(classNavEntry.id);
            editorState.selectedClassDataset.updateValue(datasetNavEntry.id);
            editorState.selectedClassGraph.updateValue(graphNavEntry.id);
            editorState.selectedClass.updateValue({
                type: classType,
                id: classNavEntry.id,
            });
            return;
        }
        //The event executed to open the discard confirm delete dialog
        eventStack.executeNewestEvent({
            datasetName: datasetNavEntry.id,
            graphUri: graphNavEntry?.id ?? null,
            classUuid: classNavEntry.id,
            classType: classType,
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
        editorState.selectedDiagram.updateValue({
            type: diagramType,
            id: classNavEntry.parent?.id ?? "default",
        });
        selectClass();
        focusClassInDiagram();
    }

    function copyClass() {
        copyState.set(
            multiSelectState.copyEntriesOr({
                classUUID: classNavEntry.id,
                graphURI: graphNavEntry.id,
                datasetName: datasetNavEntry.id,
            }),
        );
    }
</script>

<ContextMenu.Root>
    <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
        <NavigationEntry
            level={4}
            label={classNavEntry.label}
            icon={faFileLines}
            isSelected={classState === "active"}
            classOpen={classState === "secondary"}
            title={classNavEntry.tooltip}
            {highlightLabel}
            onclick={onEntryClick}
            oncontextmenu={onEntryContextMenu}
        />
    </ContextMenu.TriggerArea>
    <ContextMenu.Content>
        {#if classType === ClassType.SINGLE_CLASS}
            <ContextMenu.Item.Button
                onSelect={copyClass}
                disabled={crossGraphDisabled}
                faIcon={faCopy}
                altText="Ctrl+C"
            >
                {multiActive
                    ? `Copy ${selectedClassIds.length} classes`
                    : "Copy"}
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
        {/if}
        <ContextMenu.Item.Button
            onSelect={showClassInPackage}
            disabled={multiActive}
            faIcon={faArrowUpRightFromSquare}
        >
            Show in diagram
        </ContextMenu.Item.Button>
        {#if classType === ClassType.SINGLE_CLASS}
            <ContextMenu.Item.Button
                onSelect={() => {
                    showSHACLDialog = true;
                }}
                disabled={multiActive}
                faIcon={faDiagramProject}
            >
                Constraints
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    showExtendClassDialog = true;
                }}
                disabled={multiActive}
                faIcon={faFileExport}
            >
                Extend Class
            </ContextMenu.Item.Button>
            {#if !diagramId}
                <ContextMenu.Item.Button
                    onSelect={() => {
                        showAddToGraphDiagramDialog = true;
                    }}
                    disabled={crossGraphDisabled}
                    faIcon={faObjectGroup}
                >
                    Add to Profile Diagram
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Button
                    onSelect={() => {
                        showAddToDatasetDiagramDialog = true;
                    }}
                    disabled={crossGraphDisabled}
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
                    disabled={!!diagramGraphUri && crossGraphDisabled}
                    faIcon={faMinus}
                    variant="danger"
                >
                    Remove from Diagram
                </ContextMenu.Item.Button>
            {/if}
            <ContextMenu.Item.Button
                onSelect={() => {
                    if (!multiActive) {
                        selectClass();
                    }
                    showDeleteDependenciesDialog = true;
                }}
                disabled={readonly || crossGraphDisabled}
                faIcon={faTrash}
                altText="Del"
                variant="danger"
            >
                {multiActive
                    ? `Delete ${selectedClassIds.length} classes`
                    : "Delete Class"}
            </ContextMenu.Item.Button>
        {/if}
    </ContextMenu.Content>
</ContextMenu.Root>

{#if showDeleteDependenciesDialog}
    <DeleteDependenciesDialog
        bind:showDialog={showDeleteDependenciesDialog}
        datasetName={datasetNavEntry.id}
        graphUri={graphNavEntry.id}
        resourceUuids={selectedClassIds}
    />
{/if}

{#if showSHACLDialog}
    <SHACLClassSpecificPopUp
        datasetName={datasetNavEntry.id}
        graphUri={graphNavEntry.id}
        reactiveClass={shaclClass}
        bind:showDialog={showSHACLDialog}
    />
{/if}

{#if showAddToGraphDiagramDialog}
    <AddToGraphDiagramDialog
        bind:showDialog={showAddToGraphDiagramDialog}
        lockedDatasetName={datasetNavEntry.id}
        lockedGraphUri={graphNavEntry.id}
        classes={selectedClassNavEntries}
    />
{/if}

{#if showAddToDatasetDiagramDialog}
    <AddToDatasetDiagramDialog
        bind:showDialog={showAddToDatasetDiagramDialog}
        lockedDatasetName={datasetNavEntry.id}
        lockedGraphUri={graphNavEntry.id}
        classes={selectedClassNavEntries}
    />
{/if}

{#if showRemoveFromDiagramDialog}
    <RemoveFromDiagramDialog
        bind:showDialog={showRemoveFromDiagramDialog}
        lockedDatasetName={datasetNavEntry.id}
        graphUri={diagramGraphUri}
        {diagramId}
        classIds={selectedClassIds}
        classLabels={selectedClassLabels}
    />
{/if}

{#if showExtendClassDialog}
    <ExtendClassDialog
        datasetName={datasetNavEntry.id}
        graphUri={graphNavEntry.id}
        classUUID={classNavEntry.id}
        bind:showDialog={showExtendClassDialog}
    />
{/if}
