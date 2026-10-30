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
    import { onMount } from "svelte";
    import { v4 as uuidv4 } from "uuid";

    import FilePickerZone from "$lib/components/FilePickerZone.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import SelectedFileRow from "$lib/components/SelectedFileRow.svelte";
    import SkippedFilesNotice from "$lib/components/SkippedFilesNotice.svelte";
    import { isXmiExportFile, xmiFileExtensions } from "$lib/utils/fileUtils";
    import {
        hasInvalidCharacters,
        workspaceNameFrom,
    } from "$lib/utils/workspaceName.js";

    let {
        /** What the workspace is to be called. */
        workspaceName = $bindable(""),
        /** The export to read, {@code null} while none is chosen. */
        selectedFile = $bindable(null),
        /** An export dropped on the way in, taken as if it had been chosen here. */
        droppedFile = null,
        /**
         * The workspaces there already are. An export becomes a workspace of its own, so one of
         * these is not a name it can be given.
         *
         * @type {string[]}
         */
        takenNames = [],
    } = $props();

    const workspaceInputId = `workspaceNameXmi-${uuidv4()}`;
    const allowedFileExtensions = xmiFileExtensions.join(", ");

    let rejectedFiles = $state([]);
    /** The name this picker put there, which it may replace; one someone typed, it may not. */
    let suggestedName = $state("");

    const nameIsInvalid = $derived(hasInvalidCharacters(workspaceName.trim()));
    const nameIsTaken = $derived(takenNames.includes(workspaceName.trim()));

    onMount(() => {
        if (droppedFile) {
            addFiles([droppedFile]);
        }
    });

    /**
     * Fills the workspace name from the chosen export, unless the name was typed or handed over.
     * A name this dialog suggested is replaced by the next one, a name someone else put there is
     * left alone.
     */
    function suggestName(fileName) {
        if (workspaceName.trim() && workspaceName !== suggestedName) {
            return;
        }
        suggestedName = workspaceNameFrom(fileName);
        workspaceName = suggestedName;
    }

    /** Takes the first export of a selection; one export is read at a time. */
    function addFiles(newFiles, directoryNames = []) {
        const candidates = Array.from(newFiles ?? []);
        const accepted = candidates.filter(file => isXmiExportFile(file.name));
        rejectedFiles = [
            ...directoryNames.map(name => `${name} (folder)`),
            ...candidates
                .filter(file => !isXmiExportFile(file.name))
                .map(file => file.name),
        ];
        if (accepted.length > 0) {
            selectedFile = accepted[0];
            suggestName(selectedFile.name);
        }
    }

    function removeFile() {
        selectedFile = null;
        if (workspaceName === suggestedName) {
            workspaceName = "";
            suggestedName = "";
        }
    }
</script>

<div class="mx-2 flex h-full max-h-[80vh] flex-col">
    <!-- Said ahead of the step rather than beside the drop zone, that being the only point at
         which it can still be acted on: what a package needs and the export does not hold cannot
         be imported later. -->
    <div class="mb-4">
        <InfoBox dense>
            We recommend uploading the entire model including any possible
            metadata to prevent missing references. Which packages are actually
            imported is chosen afterwards.
        </InfoBox>
    </div>

    <label for={workspaceInputId} class="mb-1">Workspace</label>
    <input
        class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
        type="text"
        id={workspaceInputId}
        placeholder="Workspace name"
        autocomplete="off"
        bind:value={workspaceName}
    />
    {#if nameIsTaken}
        <div class="text-red mt-1 mb-1 h-6 text-sm">
            Workspace already exists; an export becomes one of its own
        </div>
    {:else if nameIsInvalid}
        <div class="text-red mt-1 mb-1 h-6 text-sm">
            Name must not contain / \ ? or #
        </div>
    {/if}

    <div class="mt-4">
        <!-- Only while there is nothing chosen: one export is read at a time, so a drop zone
             beside a chosen file would offer something it would not do. -->
        {#if !selectedFile}
            <FilePickerZone
                accept={xmiFileExtensions.join(",")}
                prompt="or drag and drop an export"
                onFiles={addFiles}
            >
                <p class="text-font-secondary mt-2 text-xs">
                    The export is read, not imported: its packages are listed
                    and nothing is stored yet. A full model reaches 100 MB.
                    <br />
                    Supported:
                    <b>{allowedFileExtensions}</b>
                    . One export is read at a time.
                </p>
                <SkippedFilesNotice
                    heading="Skipped, these will not be read:"
                    names={rejectedFiles}
                />
            </FilePickerZone>
        {:else}
            <SelectedFileRow
                value={selectedFile.name}
                disabled
                framed={false}
                onRemove={removeFile}
            />
        {/if}
    </div>
</div>
