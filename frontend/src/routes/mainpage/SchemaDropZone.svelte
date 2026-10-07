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
    import { faPlus } from "@fortawesome/free-solid-svg-icons";

    import FileDropZone from "$lib/components/FileDropZone.svelte";
    import { supportedRDFMediaTypes } from "$lib/utils/fileUtils";

    import { acceptSchemaDrop } from "./schemaDrop.js";
    import ImportDialog from "../ImportDialog.svelte";

    let {
        workspaceName = null,
        readonly = false,
        class: className = "",
        children,
    } = $props();

    const info = `${supportedRDFMediaTypes
        .map(type => type.fileExtension)
        .join(", ")} or .zip`;

    let droppedItems = $state(null);
    let showImportDialog = $state(false);

    const message = $derived(
        workspaceName
            ? `Add schemas to "${workspaceName}"`
            : "Add schemas to a new workspace",
    );

    function openImport(items) {
        const accepted = acceptSchemaDrop(items);
        if (!accepted) {
            return;
        }
        droppedItems = accepted;
        showImportDialog = true;
    }
</script>

<FileDropZone
    overlay
    class={className}
    disabled={readonly}
    icon={faPlus}
    disabledMessage={`"${workspaceName}" is read-only`}
    {message}
    {info}
    onFiles={openImport}
>
    {@render children?.()}
</FileDropZone>

<ImportDialog
    bind:showDialog={showImportDialog}
    lockedWorkspaceName={workspaceName}
    {droppedItems}
/>
