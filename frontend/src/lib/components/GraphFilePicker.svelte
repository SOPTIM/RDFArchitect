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

    import FilePickerZone from "$lib/components/FilePickerZone.svelte";
    import SelectedFileRow from "$lib/components/SelectedFileRow.svelte";
    import SkippedFilesNotice from "$lib/components/SkippedFilesNotice.svelte";
    import {
        isSupportedGraphFile,
        isZipFile,
        supportedRDFMediaTypes,
        xmiFileExtensions,
    } from "$lib/utils/fileUtils";
    import { graphUriFrom } from "$lib/utils/graphUri.js";

    let {
        /**
         * The files to import and what to call each one's schema.
         *
         * @type {{file: File, graphUri: string, isZip: boolean}[]}
         */
        files = $bindable([]),
        /** Files dropped on the way in, taken as if they had been chosen here. */
        droppedItems = null,
    } = $props();

    const supportedFileExtensions = supportedRDFMediaTypes.map(
        type => type.fileExtension,
    );
    const allowedFileExtensions = supportedFileExtensions.join(", ");

    let rejectedFiles = $state([]);

    const rejectedAnExport = $derived(
        rejectedFiles.some(fileName =>
            xmiFileExtensions.some(extension =>
                fileName.toLowerCase().endsWith(extension),
            ),
        ),
    );

    onMount(() => {
        if (droppedItems) {
            addFiles(droppedItems.files, droppedItems.directoryNames);
        }
    });

    function addFiles(newFiles, directoryNames = []) {
        rejectedFiles = directoryNames.map(name => `${name} (folder)`);
        const mappedFiles = Array.from(newFiles ?? [])
            .map(file => {
                if (!isZipFile(file.name) && !isSupportedGraphFile(file.name)) {
                    rejectedFiles.push(file.name);
                    return null;
                }
                return {
                    file,
                    graphUri: isZipFile(file.name)
                        ? ""
                        : graphUriFrom(file.name),
                    isZip: isZipFile(file.name),
                };
            })
            .filter(Boolean);

        if (mappedFiles.length > 0) {
            files = [...files, ...mappedFiles];
        }
    }

    function removeFile(index) {
        files = files.filter((_, at) => at !== index);
    }

    function updateGraphUri(index, graphUri) {
        if (files[index]?.isZip) {
            return;
        }
        files = files.map((entry, at) =>
            at === index ? { ...entry, graphUri } : entry,
        );
    }
</script>

<div class="mt-4">
    <FilePickerZone
        multiple
        accept={`${supportedFileExtensions.join(",")},.zip`}
        prompt="or drag and drop files or a .zip archive"
        onFiles={addFiles}
    >
        <p class="text-font-secondary mt-2 text-xs">
            Each file becomes a schema named after the file. ZIP files are
            unpacked and imported automatically.
            <br />
            Supported file extensions:
            <b>{allowedFileExtensions}</b>
            . In ZIP files, schemas must be located at the root level; folders are
            ignored.
            <br />
            Dropped folders are skipped; pack them into a ZIP.
        </p>
        <SkippedFilesNotice
            heading="Skipped, these will not be imported:"
            names={rejectedFiles}
        >
            {#if rejectedAnExport}
                <p class="mt-2">
                    An Enterprise Architect XMI export has an import of its own,
                    under File ▸ Import ▸ Model, or by dropping it on the +
                    beside the workspace tabs.
                </p>
            {/if}
        </SkippedFilesNotice>
    </FilePickerZone>

    {#if files.length > 0}
        <div class="mt-3 max-h-[55vh] space-y-2 overflow-y-auto">
            {#each files as fileEntry, index}
                <SelectedFileRow
                    value={fileEntry.isZip
                        ? fileEntry.file.name
                        : fileEntry.graphUri}
                    disabled={fileEntry.isZip}
                    onValueChange={value => updateGraphUri(index, value)}
                    onRemove={() => removeFile(index)}
                />
            {/each}
        </div>
    {:else}
        <p class="text-font-secondary mt-2 text-sm">No files selected yet.</p>
    {/if}
</div>
