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
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { fileDragState } from "$lib/fileDragState.svelte.js";
    import {
        supportedRDFMediaTypes,
        xmiFileExtensions,
    } from "$lib/utils/fileUtils";

    import { acceptNewWorkspaceDrop, acceptSchemaDrop } from "./schemaDrop.js";
    import ImportDialog from "../ImportDialog.svelte";
    import XmiImportDialog from "../XmiImportDialog.svelte";

    let {
        workspaceName = null,
        readonly = false,
        hideContentOnDrag = false,
        class: className = "",
        children,
    } = $props();

    const rdfInfo = `${supportedRDFMediaTypes
        .map(type => type.fileExtension)
        .join(", ")} or .zip`;

    let droppedItems = $state(null);
    let droppedExport = $state(null);
    let showImportDialog = $state(false);
    let showXmiImportDialog = $state(false);
    // Where the drop would create the workspace, an export is one of the things that can create
    // it, so saying so is the difference between it working and it looking as if it cannot.
    const info = $derived(
        workspaceName
            ? rdfInfo
            : `${rdfInfo}, or an Enterprise Architect export (${xmiFileExtensions.join(", ")})`,
    );

    const message = $derived(
        workspaceName
            ? `Add schemas to "${workspaceName}"`
            : "Add schemas to a new workspace",
    );
    // An empty state puts its card where the drop box lands; the two of them stacked read as one
    // card too many.
    const contentClasses = $derived(
        hideContentOnDrag && fileDragState.active
            ? "pointer-events-none opacity-0"
            : "opacity-100",
    );

    /**
     * Opens what the drop asks for. An Enterprise Architect export becomes a workspace of its
     * own, so it is only taken where a workspace is what the drop would create anyway; dropped
     * onto one that exists it is refused, with the + as the place to put it.
     */
    function openImport(items) {
        if (!workspaceName) {
            const accepted = acceptNewWorkspaceDrop(items);
            if (accepted?.kind === "export") {
                droppedExport = accepted.file;
                showXmiImportDialog = true;
                return;
            }
            if (accepted) {
                droppedItems = accepted.items;
                showImportDialog = true;
            }
            return;
        }
        const accepted = acceptSchemaDrop(items);
        if (!accepted) {
            return;
        }
        droppedItems = accepted;
        showImportDialog = true;
    }

    function reportReadOnly() {
        toastStore.error(
            "Nothing imported",
            `"${workspaceName}" is read-only. Enable editing for it, or import into another workspace.`,
        );
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
    onRefused={reportReadOnly}
>
    {#if hideContentOnDrag}
        <div
            class={`flex min-h-0 flex-1 items-center justify-center transition-opacity duration-150 ${contentClasses}`}
        >
            {@render children?.()}
        </div>
    {:else}
        {@render children?.()}
    {/if}
</FileDropZone>

<ImportDialog
    bind:showDialog={showImportDialog}
    lockedWorkspaceName={workspaceName}
    {droppedItems}
/>

<XmiImportDialog
    bind:showDialog={showXmiImportDialog}
    droppedFile={droppedExport}
/>
