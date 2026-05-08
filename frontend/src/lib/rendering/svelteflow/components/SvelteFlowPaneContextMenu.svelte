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

    import { BackendConnection } from "$lib/api/backend.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";
    import { copyState, editorState } from "$lib/sharedState.svelte.js";

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

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let triggerRef = $state(null);
    let open = $state(false);
    let showNewClassDialog = $state(false);

    let triggerStyle = $derived(getContextMenuTriggerStyle(request));

    let disablePasteButton = $derived(
        !copyState.classUUID.getValue() ||
            !copyState.graphURI.getValue() ||
            !copyState.datasetName.getValue(),
    );

    let disablePasteAssociations = $derived(
        disablePasteButton ||
            editorState.selectedDataset !== copyState.datasetName ||
            editorState.selectedGraph !== copyState.graphURI,
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
        showNewClassDialog = true;
        onClose();
    }

    async function getPackage(datasetName, graphURI, packageUUID) {
        if (!datasetName || !graphURI) {
            return [];
        }
        const res = await bec.getPackage(datasetName, graphURI, packageUUID);
        return await res.json();
    }

    async function pasteClass(copyAbstract, copyAttributes, copyAssociations) {
        let packageDTO = await getPackage(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
            editorState.selectedPackageUUID.getValue(),
        );

        await saveCopyClass(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
            packageDTO,
            copyAbstract,
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
        <ContextMenu.SubMenu.Root>
            <ContextMenu.SubMenu.Trigger faIcon={faPaste} disabled={false}>
                Paste
            </ContextMenu.SubMenu.Trigger>
            <ContextMenu.SubMenu.Content>
                <ContextMenu.Item.Button
                    onSelect={() => pasteClass(false, true, true)}
                    faIcon={faPaste}
                    disabled={disablePasteAssociations}
                >
                    Paste
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Button
                    onSelect={() => pasteClass(false, false, true)}
                    faIcon={faPaste}
                    disabled={disablePasteAssociations}
                >
                    Paste without attributes
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Button
                    onSelect={() => pasteClass(false, true, false)}
                    faIcon={faPaste}
                    disabled={disablePasteButton}
                >
                    Paste without associations
                </ContextMenu.Item.Button>
                <ContextMenu.Item.Button
                    onSelect={() => pasteClass(true, false, false)}
                    faIcon={faPaste}
                    disabled={disablePasteButton}
                >
                    Paste bare
                </ContextMenu.Item.Button>
            </ContextMenu.SubMenu.Content>
        </ContextMenu.SubMenu.Root>
        <ContextMenu.Separator />
        <ContextMenu.Item.Button
            onSelect={openNewClassDialog}
            {disabled}
            faIcon={faPlus}
        >
            Add class
        </ContextMenu.Item.Button>
    </ContextMenu.Content>
</ContextMenu.Root>

<NewClassDialog
    bind:showDialog={showNewClassDialog}
    {lockedDatasetName}
    {lockedGraphUri}
    {onClassCreated}
/>
