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
    import { faPaste, faPlus } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import {
        copyState,
        DiagramType,
        editorState,
    } from "$lib/sharedState.svelte.js";
    import { packageStore } from "$lib/stores/PackageStore.ts";

    import {
        getContextMenuTriggerStyle,
        handleContextMenuOpenChange,
        syncContextMenuTrigger,
    } from "./contextMenuUtils.js";
    import { saveCopyClass } from "../../../../routes/mainpage/packageNavigation/save-copy-class-to-backend.js";
    import NewClassDialog from "../../../../routes/NewClassDialog.svelte";

    let {
        request = null,
        disabled = false,
        lockedDatasetName = "",
        lockedGraphUri = "",
        onClassCreated = () => {},
        onClose = () => {},
    } = $props();

    let triggerRef = $state(null);
    let open = $state(false);
    let showNewClassDialog = $state(false);
    let classLayoutPosition = $state(null);

    let triggerStyle = $derived(getContextMenuTriggerStyle(request));

    let disablePasteButton = $derived(
        !copyState.classUUID.getValue() ||
            !copyState.graphURI.getValue() ||
            !copyState.datasetName.getValue(),
    );

    $effect(() => {
        syncContextMenuTrigger({
            disabled,
            request,
            triggerRef,
            setOpen: nextOpen => (open = nextOpen),
        });
    });

    function handleOpenChange(nextOpen) {
        handleContextMenuOpenChange(nextOpen, value => (open = value), onClose);
    }

    function openNewClassDialog() {
        classLayoutPosition = request?.flowPosition
            ? {
                  xPosition: request.flowPosition.x,
                  yPosition: request.flowPosition.y,
              }
            : null;
        showNewClassDialog = true;
        onClose();
    }

    async function pasteClass(
        copyAsAbstract,
        copyAttributes,
        copyAssociations,
    ) {
        const dataset = editorState.selectedDataset.getValue();
        const graph = editorState.selectedGraph.getValue();
        await packageStore.load(dataset, graph);
        const res = await packageStore.getPackages(dataset, graph);
        let packages = [...res.internal, ...res.external];

        const selectedPackageUUID =
            editorState.selectedDiagram.getProperty("id") === "default"
                ? null
                : editorState.selectedDiagram.getProperty("id");

        let packageDTO = selectedPackageUUID
            ? (packages.find(pkg => pkg.uuid === selectedPackageUUID) ?? null)
            : null;
        await saveCopyClass(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
            packageDTO,
            copyAsAbstract,
            copyAttributes,
            copyAssociations,
        );
    }
</script>

<ContextMenu.Root bind:open onOpenChange={handleOpenChange}>
    <ContextMenu.TriggerArea
        bind:ref={triggerRef}
        class="fixed h-px w-px opacity-0"
        style={triggerStyle}
        {disabled}
    />
    <ContextMenu.Content>
        <ContextMenu.Item.Button
            onSelect={openNewClassDialog}
            {disabled}
            faIcon={faPlus}
        >
            Add class
        </ContextMenu.Item.Button>
        {#if editorState.selectedDiagram.getProperty("type") === DiagramType.PACKAGE}
            <ContextMenu.Separator />
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
        {/if}
    </ContextMenu.Content>
</ContextMenu.Root>

<NewClassDialog
    bind:showDialog={showNewClassDialog}
    {lockedDatasetName}
    {lockedGraphUri}
    {classLayoutPosition}
    {onClassCreated}
/>
