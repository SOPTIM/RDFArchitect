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

    let {
        request = null,
        disabled = false,
        onAddClass = () => {},
        onClose = () => {},
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let triggerRef = $state(null);
    let open = $state(false);

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

    function handleAddClass() {
        onAddClass();
    }

    async function getPackages(datasetName, graphURI) {
        if (!datasetName || !graphURI) {
            return [];
        }
        const res = await bec.getPackages(datasetName, graphURI);
        const packagesJSON = await res.json();
        return [
            ...packagesJSON.internalPackageList,
            ...packagesJSON.externalPackageList,
        ];
    }

    async function pasteClass(copyAbstract) {
        let packages = await getPackages(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
        );
        let packageDTO =
            packages.find(
                pkg => pkg.uuid === editorState.selectedPackageUUID.getValue(),
            ) ?? null;
        await saveCopyClass(
            editorState.selectedDataset.getValue(),
            editorState.selectedGraph.getValue(),
            packageDTO,
            copyAbstract,
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
            onSelect={() => pasteClass(false)}
            disabled={disablePasteButton}
            faIcon={faPaste}
        >
            Paste class as duplicate
        </ContextMenu.Item.Button>
        <ContextMenu.Item.Button
            onSelect={() => pasteClass(true)}
            disabled={disablePasteButton}
            faIcon={faPaste}
        >
            Paste class as abstract
        </ContextMenu.Item.Button>
        <ContextMenu.Separator />
        <ContextMenu.Item.Button
            onSelect={handleAddClass}
            {disabled}
            faIcon={faPlus}
        >
            Add class
        </ContextMenu.Item.Button>
    </ContextMenu.Content>
</ContextMenu.Root>
