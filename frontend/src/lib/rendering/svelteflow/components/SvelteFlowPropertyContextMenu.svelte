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
    import { faEye, faGear } from "@fortawesome/free-solid-svg-icons";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import {
        propertyEditorRequest,
        PropertyKind,
    } from "$lib/propertyEditorRequest.svelte.js";

    import {
        getContextMenuTriggerStyle,
        handleContextMenuOpenChange,
        syncContextMenuTrigger,
    } from "./contextMenuUtils.js";

    let { request = null, readOnly = false, onClose = () => {} } = $props();

    const PROPERTY_NAMES = {
        [PropertyKind.ATTRIBUTE]: "Attribute",
        [PropertyKind.ASSOCIATION]: "Association",
        [PropertyKind.ENUM_ENTRY]: "Enum Entry",
    };

    let triggerRef = $state(null);
    let open = $state(false);

    let triggerStyle = $derived(getContextMenuTriggerStyle(request));
    let itemLabel = $derived(
        `${readOnly ? "View" : "Edit"} ${PROPERTY_NAMES[request?.target?.kind] ?? "Property"}`,
    );

    $effect(() => {
        syncContextMenuTrigger({
            disabled: !request,
            request,
            triggerRef,
            setOpen: value => (open = value),
        });
    });

    function handleOpenChange(nextOpen) {
        handleContextMenuOpenChange(nextOpen, value => (open = value), onClose);
    }

    function openPropertyEditor() {
        propertyEditorRequest.open(request.target);
        onClose();
    }
</script>

<ContextMenu.Root bind:open onOpenChange={handleOpenChange}>
    <ContextMenu.TriggerArea
        bind:ref={triggerRef}
        class="fixed h-px w-px opacity-0"
        style={triggerStyle}
        disabled={!request}
    />
    <ContextMenu.Content>
        <ContextMenu.Item.Button
            onSelect={openPropertyEditor}
            faIcon={readOnly ? faEye : faGear}
        >
            {itemLabel}
        </ContextMenu.Item.Button>
    </ContextMenu.Content>
</ContextMenu.Root>
