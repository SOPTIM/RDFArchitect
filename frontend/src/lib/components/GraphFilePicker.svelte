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
        isInvalidSchemaName,
        schemaNameFromFileName,
    } from "$lib/models/reactive/validity-rules/validityFunctions.js";
    import {
        isSupportedGraphFile,
        isZipFile,
        supportedRDFMediaTypes,
        xmiFileExtensions,
    } from "$lib/utils/fileUtils";

    let {
        /**
         * The files to import and what to call each one's schema.
         *
         * @type {{file: File, schemaName: string, isZip: boolean}[]}
         */
        files = $bindable([]),
        /** Whether every file can be imported under the name it was given. */
        isValid = $bindable(true),
        /** Files dropped on the way in, taken as if they had been chosen here. */
        droppedItems = null,
    } = $props();

    const supportedFileExtensions = supportedRDFMediaTypes.map(
        type => type.fileExtension,
    );
    const allowedFileExtensions = supportedFileExtensions.join(", ");
    const DEFAULT_SCHEMA_NAME = "graph";

    let rejectedFiles = $state([]);

    const rejectedAnExport = $derived(
        rejectedFiles.some(fileName =>
            xmiFileExtensions.some(extension =>
                fileName.toLowerCase().endsWith(extension),
            ),
        ),
    );

    const fileNameViolations = $derived(
        files.map((_, index) => schemaNameViolations(index)),
    );

    $effect(() => {
        isValid = fileNameViolations.every(
            violations => violations.length === 0,
        );
    });

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
                    schemaName: isZipFile(file.name)
                        ? ""
                        : buildSchemaName(file.name),
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

    function updateSchemaName(index, schemaName) {
        if (files[index]?.isZip) {
            return;
        }
        files = files.map((entry, at) =>
            at === index ? { ...entry, schemaName } : entry,
        );
    }

    /**
     * The file name without its extension, made a valid schema name, which is how the backend names
     * the entries of a zip as well.
     */
    function buildSchemaName(fileName) {
        if (!fileName) {
            return DEFAULT_SCHEMA_NAME;
        }
        const normalized = fileName.split(/[\\/]/).pop() ?? fileName;
        const lastDotIndex = normalized.lastIndexOf(".");
        const baseName =
            lastDotIndex === -1
                ? normalized
                : normalized.substring(0, lastDotIndex);
        return schemaNameFromFileName(baseName, DEFAULT_SCHEMA_NAME);
    }

    /**
     * What is wrong with the name a file is to be imported under. Two files of the upload may not
     * share a name, but one may take the name of an existing schema: the backend then imports it
     * under that name with a number appended.
     */
    function schemaNameViolations(index) {
        const entry = files[index];
        if (entry.isZip) {
            return [];
        }
        const otherNames = files
            .filter((other, at) => at !== index && !other.isZip)
            .map(other => other.schemaName.trim());
        return isInvalidSchemaName(entry.schemaName, otherNames);
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
                        : fileEntry.schemaName}
                    disabled={fileEntry.isZip}
                    label={fileEntry.isZip
                        ? "Selected archive"
                        : `Schema name for ${fileEntry.file.name}`}
                    violations={fileNameViolations[index] ?? []}
                    onValueChange={value => updateSchemaName(index, value)}
                    onRemove={() => removeFile(index)}
                />
            {/each}
        </div>
    {:else}
        <p class="text-font-secondary mt-2 text-sm">No files selected yet.</p>
    {/if}
</div>
