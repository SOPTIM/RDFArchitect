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

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import FileDropZone from "$lib/components/FileDropZone.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import SelectedFileRow from "$lib/components/SelectedFileRow.svelte";
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

    const uniqueId = uuidv4();
    const workspaceInputId = `workspaceNameXmi-${uniqueId}`;
    const fileInputId = `actual-export-input-${uniqueId}`;
    const allowedFileExtensions = xmiFileExtensions.join(", ");

    let fileInputValue = $state("");
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
            Workspace already exists — an export becomes one of its own
        </div>
    {:else if nameIsInvalid}
        <div class="text-red mt-1 mb-1 h-6 text-sm">
            Name must not contain / \ ? or #
        </div>
    {/if}

    <!-- Said before the export is chosen, that being the only point at which it can still be
         acted on: what a package needs and the export does not hold cannot be imported later. -->
    <InfoBox>
        We recommend importing the entire model including any possible metadata,
        to ensure that as few references as possible are missing after the
        import.
    </InfoBox>

    <div class="mt-4">
        <input
            class="hidden"
            type="file"
            id={fileInputId}
            accept={xmiFileExtensions.join(",")}
            onchange={event => {
                addFiles(event.target.files);
                event.target.value = "";
            }}
            bind:value={fileInputValue}
        />
        <FileDropZone
            class="hover:border-blue flex w-full flex-col px-4 py-6"
            onFiles={items => addFiles(items.files, items.directoryNames)}
        >
            <div
                class="flex flex-col items-start space-y-2 md:flex-row md:items-center md:space-y-0 md:space-x-3"
            >
                <div class="h-9 w-24">
                    <ButtonControl
                        height={9}
                        callOnClick={() => {
                            document.getElementById(fileInputId).click();
                        }}
                    >
                        Select File
                    </ButtonControl>
                </div>
                <p class="text-font-secondary text-sm">
                    or drag and drop an export
                </p>
            </div>
            <p class="text-font-secondary mt-2 text-xs">
                The export is read, not imported: its packages are listed and
                nothing is stored yet. A full model reaches 100 MB.
                <br />
                Supported:
                <b>{allowedFileExtensions}</b>
                . One export is read at a time.
            </p>
            {#if rejectedFiles.length > 0}
                <div
                    class="bg-red-background text-red-text border-red-border mt-3 rounded border px-3 py-2 text-xs"
                >
                    <p class="font-semibold">
                        Skipped, these will not be read:
                    </p>
                    <ul class="list-disc pl-5">
                        {#each rejectedFiles as fileName (fileName)}
                            <li>{fileName}</li>
                        {/each}
                    </ul>
                </div>
            {/if}
        </FileDropZone>

        {#if selectedFile}
            <div class="mt-3">
                <SelectedFileRow
                    value={selectedFile.name}
                    disabled
                    onRemove={removeFile}
                />
            </div>
        {:else}
            <p class="text-font-secondary mt-2 text-sm">
                No file selected yet.
            </p>
        {/if}
    </div>
</div>
