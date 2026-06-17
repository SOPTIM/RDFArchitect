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
    import {
        faPencil,
        faPlus,
        faLink,
        faTrash,
        faEye,
        faPaste,
        faObjectGroup,
    } from "@fortawesome/free-solid-svg-icons";
    import { getContext } from "svelte";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { Package } from "$lib/models/dto/index.ts";
    import {
        DiagramType,
        copyState,
        editorState,
    } from "$lib/sharedState.svelte.js";
    import { shortenIri } from "$lib/utils/iri.js";

    import ClassEntry from "./ClassEntry.svelte";
    import { isSelectedPackage } from "./packageNavigationUtils.svelte.js";
    import DeleteDependenciesDialog from "../../delete-relations-dialog/DeleteDependenciesDialog.svelte";
    import NewClassDialog from "../../NewClassDialog.svelte";
    import PackageEditorDialog from "../packageEditorDialog.svelte";
    import AddToDatasetDiagramDialog from "./custom-diagram-dialogs/AddToDatasetDiagramDialog.svelte";
    import AddToGraphDiagramDialog from "./custom-diagram-dialogs/AddToGraphDiagramDialog.svelte";
    import { saveCopyClass } from "./save-copy-class-to-backend.js";

    let {
        datasetNavEntry,
        graphNavEntry,
        packageNavEntry,
        namespaces = [],
        readonly,
    } = $props();
    let showNewClassDialog = $state(false);
    let showAddToGraphDiagramDialog = $state(false);
    let showAddToDatasetDiagramDialog = $state(false);
    let showPackageEditorDialog = $state(false);
    let showDeleteDependenciesDialog = $state(false);

    let wasPackageSelected = false;

    let disablePasteButton = $derived(
        readonly ||
            !copyState.classUUID.getValue() ||
            !copyState.graphURI.getValue() ||
            !copyState.datasetName.getValue(),
    );

    let isProtectedPackage = $derived(
        packageNavEntry?.data.uuid == null || packageNavEntry?.data.external,
    );

    const selectionTrigger = $derived([
        editorState.selectedDataset.subscribe(),
        editorState.selectedGraph.subscribe(),
        editorState.selectedDiagram.subscribe(),
        getContext("packageNavigation").reloadTrigger?.subscribe(),
    ]);

    let isPackageSelected = $derived(
        selectionTrigger &&
            isSelectedPackage(
                datasetNavEntry.id,
                graphNavEntry.id,
                packageNavEntry.id,
            ),
    );

    let packageHighlightLabel = $derived(
        shortenIri(namespaces, packageNavEntry.tooltip),
    );
    const packageActionLabel = $derived(readonly ? "View" : "Edit");
    const packageActionIcon = $derived(readonly ? faEye : faPencil);
    const disablePackageAction = $derived(
        readonly ? false : isProtectedPackage,
    );
    const hasClasses = $derived(packageNavEntry?.children?.length > 0);
    $effect(() => {
        if (selectionTrigger && isPackageSelected && !wasPackageSelected) {
            packageNavEntry.parent?.open();
        }
        wasPackageSelected = isPackageSelected;
    });

    function copyDatasetUrl() {
        const params = new URLSearchParams({
            dataset: datasetNavEntry.id,
            graph: graphNavEntry.id,
            package: packageNavEntry.id,
        });
        const url = `${window.location.origin}/mainpage?${params}`;
        navigator.clipboard
            .writeText(url)
            .catch(err =>
                console.error("Writing to the clipboard is not allowed: ", err),
            );
    }

    function selectPackage() {
        editorState.selectedDataset.updateValue(datasetNavEntry.id);
        editorState.selectedGraph.updateValue(graphNavEntry.id);
        editorState.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: packageNavEntry.id,
        });
    }

    function pasteClass(copyAbstract, copyAttributes, copyAssociations) {
        let packageDTO = new Package({
            uuid: packageNavEntry.data.uuid,
            label: packageNavEntry.data.label,
            prefix: packageNavEntry.data?.prefix,
        });
        saveCopyClass(
            datasetNavEntry.id,
            graphNavEntry.id,
            packageDTO,
            copyAbstract,
            copyAttributes,
            copyAssociations,
        );
    }
</script>

<div class="flex w-full flex-col items-stretch gap-[0.1rem]">
    <ContextMenu.Root>
        <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
            <NavigationEntry
                level={3}
                label={packageNavEntry.label}
                icon={packageNavEntry?.isOpen ? faFolderOpen : faFolder}
                isSelected={isPackageSelected}
                hasChildren={hasClasses}
                expanded={packageNavEntry.isOpen}
                title={packageNavEntry.tooltip}
                highlightLabel={packageHighlightLabel}
                badgeText={packageNavEntry.data.external ? "External" : ""}
                badgeVariant={packageNavEntry.data.external
                    ? "external"
                    : "default"}
                onclick={selectPackage}
                onToggle={() => packageNavEntry.toggle()}
            />
        </ContextMenu.TriggerArea>
        <ContextMenu.Content>
            <ContextMenu.Item.Button
                onSelect={() => {
                    showNewClassDialog = true;
                }}
                disabled={readonly}
                faIcon={faPlus}
                altText="Shift+N"
            >
                New Class
            </ContextMenu.Item.Button>
            <ContextMenu.SubMenu.Root>
                <ContextMenu.SubMenu.Trigger faIcon={faPaste} disabled={false}>
                    Paste
                </ContextMenu.SubMenu.Trigger>
                <ContextMenu.SubMenu.Content>
                    <ContextMenu.Item.Button
                        onSelect={() => pasteClass(false, true, true)}
                        faIcon={faPaste}
                        disabled={disablePasteButton}
                        altText="Ctrl+V"
                    >
                        Paste
                    </ContextMenu.Item.Button>
                    <ContextMenu.Item.Button
                        onSelect={() => pasteClass(false, false, true)}
                        faIcon={faPaste}
                        disabled={disablePasteButton}
                        altText="Ctrl+Shift+V"
                    >
                        Paste without attributes/enum entries
                    </ContextMenu.Item.Button>
                    <ContextMenu.Item.Button
                        onSelect={() => pasteClass(false, true, false)}
                        faIcon={faPaste}
                        disabled={disablePasteButton}
                        altText="Ctrl+Alt+V"
                    >
                        Paste without associations
                    </ContextMenu.Item.Button>
                    <ContextMenu.Item.Button
                        onSelect={() => pasteClass(true, false, false)}
                        faIcon={faPaste}
                        disabled={disablePasteButton}
                        altText="Ctrl+Shift+Alt+V"
                    >
                        Paste bare
                    </ContextMenu.Item.Button>
                </ContextMenu.SubMenu.Content>
            </ContextMenu.SubMenu.Root>
            <ContextMenu.Separator />
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
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    showPackageEditorDialog = true;
                }}
                disabled={disablePackageAction}
                faIcon={packageActionIcon}
                altText="Ctrl+Shift+K"
            >
                {packageActionLabel}
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button onSelect={copyDatasetUrl} faIcon={faLink}>
                Copy URL
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    showDeleteDependenciesDialog = true;
                }}
                disabled={readonly || isProtectedPackage}
                faIcon={faTrash}
                variant="danger"
            >
                Delete Package
            </ContextMenu.Item.Button>
        </ContextMenu.Content>
    </ContextMenu.Root>
    {#if packageNavEntry.isOpen && hasClasses}
        <div
            class="flex w-full flex-col items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each packageNavEntry.children as classNavEntry (classNavEntry.id)}
                <ClassEntry
                    {datasetNavEntry}
                    {graphNavEntry}
                    {classNavEntry}
                    {namespaces}
                    {readonly}
                />
            {/each}
        </div>
    {/if}
</div>

<NewClassDialog
    bind:showDialog={showNewClassDialog}
    lockedDatasetName={datasetNavEntry.id}
    lockedGraphUri={graphNavEntry.id}
    lockedPackage={packageNavEntry.data}
/>

<AddToGraphDiagramDialog
    bind:showDialog={showAddToGraphDiagramDialog}
    lockedDatasetName={datasetNavEntry.id}
    lockedGraphUri={graphNavEntry.id}
    classes={packageNavEntry.children}
/>

<AddToDatasetDiagramDialog
    bind:showDialog={showAddToDatasetDiagramDialog}
    lockedDatasetName={datasetNavEntry.id}
    graphUri={graphNavEntry.id}
    classes={packageNavEntry.children}
/>

<PackageEditorDialog
    bind:showDialog={showPackageEditorDialog}
    datasetName={datasetNavEntry.id}
    graphUri={graphNavEntry.id}
    pack={packageNavEntry.data}
    {readonly}
/>

<DeleteDependenciesDialog
    bind:showDialog={showDeleteDependenciesDialog}
    datasetName={datasetNavEntry.id}
    graphUri={graphNavEntry.id}
    resourceUuid={packageNavEntry.data.uuid}
/>
