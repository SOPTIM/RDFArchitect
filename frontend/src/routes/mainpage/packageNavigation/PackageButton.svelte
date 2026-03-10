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
    } from "@fortawesome/free-solid-svg-icons";
    import { onMount } from "svelte";

    import { isReadOnly } from "$lib/api/apiDatasetUtils.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { shortenIri } from "$lib/utils/iri.js";

    import ClassEntry from "./ClassEntry.svelte";
    import PackageDeleteDialog from "./PackageDeleteDialog.svelte";
    import {
        isSelectedPackage,
        getUri,
        getPackageId,
    } from "./packageNavigationUtils.svelte.js";
    import NewClassDialog from "../../NewClassDialog.svelte";
    import PackageEditorDialog from "../packageEditorDialog.svelte";

    let {
        dataset,
        graph,
        pack,
        packages = [],
        classes = [],
        prefixes = [],
        onPackChange = () => {},
    } = $props();
    let readOnly = $state(false);
    let showNewClassDialog = $state(false);
    let showPackageEditorDialog = $state(false);
    let showDeletePackageDialog = $state(false);

    // Ensure selection-dependent UI updates without remounting the component.
    const selectionTrigger = $derived([
        editorState.selectedDataset.subscribe(),
        editorState.selectedGraph.subscribe(),
        editorState.selectedPackageUUID.subscribe(),
        forceReloadTrigger.subscribe(),
    ]);

    let disablePackageEditing = $derived(
        pack?.label === "default" || pack?.external,
    );
    let disablePackageDelete = $derived(
        pack?.label === "default" || pack?.external,
    );
    let isPackageSelected = $derived(
        selectionTrigger && isSelectedPackage(dataset, graph, pack),
    );
    let packageIcon = $derived(pack.showContents ? faFolderOpen : faFolder);
    let packageHighlightLabel = $derived(
        shortenIri(
            prefixes,
            pack?.prefix ? `${pack.prefix}${pack.label}` : pack?.label,
        ),
    );
    const packageActionLabel = $derived(readOnly ? "View" : "Edit");
    const packageActionIcon = $derived(readOnly ? faEye : faPencil);
    const disablePackageAction = $derived(
        readOnly ? false : disablePackageEditing,
    );
    const hasClasses = $derived(classes?.length > 0);

    $effect(async () => {
        forceReloadTrigger.subscribe();
        await updateReadOnly();
    });

    onMount(async () => {
        await updateReadOnly();
    });

    function copyDatasetUrl() {
        const params = new URLSearchParams({
            dataset: dataset.label,
            graph: getUri(graph),
            package: getPackageId(pack),
        });
        const url = `${window.location.origin}/mainpage?${params}`;
        navigator.clipboard
            .writeText(url)
            .catch(err =>
                console.error("Writing to the clipboard is not allowed: ", err),
            );
    }

    async function updateReadOnly() {
        readOnly = await isReadOnly(dataset.label);
    }

    function updatePackState(updates) {
        if (!pack) return;
        onPackChange({
            ...pack,
            ...updates,
        });
    }

    function togglePackageContentsVisibility() {
        const next = !pack.showContents;
        updatePackState({
            showContents: next,
            userCollapsed: !next,
        });
    }

    function selectPackage() {
        const nextDataset = dataset.label;
        const nextGraph = getUri(graph);
        const nextPackage = getPackageId(pack);

        editorState.selectedDataset.updateValue(nextDataset);
        editorState.selectedGraph.updateValue(nextGraph);
        editorState.selectedPackageUUID.updateValue(nextPackage);
    }
</script>

<div class="flex w-full flex-col items-stretch gap-[0.1rem]">
    <ContextMenu.Root>
        <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
            <NavigationEntry
                level={3}
                label={pack.label}
                icon={packageIcon}
                isSelected={isPackageSelected}
                hasChildren={hasClasses}
                expanded={pack.showContents}
                title={pack.label}
                highlightLabel={packageHighlightLabel}
                badgeText={pack.external ? "External" : ""}
                badgeVariant={pack.external ? "external" : "default"}
                onclick={selectPackage}
                onToggle={togglePackageContentsVisibility}
            />
        </ContextMenu.TriggerArea>
        <ContextMenu.Content>
            <ContextMenu.Item.Button
                onSelect={() => {
                    showNewClassDialog = true;
                }}
                disabled={readOnly}
                faIcon={faPlus}
            >
                New Class
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    showPackageEditorDialog = true;
                }}
                disabled={disablePackageAction}
                faIcon={packageActionIcon}
            >
                {packageActionLabel}
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button onSelect={copyDatasetUrl} faIcon={faLink}>
                Copy URL
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    showDeletePackageDialog = true;
                }}
                disabled={readOnly || disablePackageDelete}
                faIcon={faTrash}
                variant="danger"
            >
                Delete Package
            </ContextMenu.Item.Button>
        </ContextMenu.Content>
    </ContextMenu.Root>
    {#if pack.showContents && hasClasses}
        <div
            class="flex w-full flex-col items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each classes as cls (cls.uuid)}
                <ClassEntry
                    {dataset}
                    {graph}
                    {pack}
                    {cls}
                    {prefixes}
                    {readOnly}
                    {onPackChange}
                />
            {/each}
        </div>
    {/if}
</div>

<NewClassDialog
    bind:showDialog={showNewClassDialog}
    lockedDatasetName={dataset.label}
    lockedGraphUri={getUri(graph)}
    lockedPackage={pack}
/>

<PackageEditorDialog
    bind:showDialog={showPackageEditorDialog}
    datasetName={dataset.label}
    graphUri={getUri(graph)}
    {packages}
    {pack}
    {readOnly}
/>

<PackageDeleteDialog
    bind:showDialog={showDeletePackageDialog}
    datasetName={dataset.label}
    graphUri={getUri(graph)}
    {pack}
/>
