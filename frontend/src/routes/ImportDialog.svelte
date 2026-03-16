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
    import { faMinus } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";
    import { v4 as uuidv4 } from "uuid";

    import { getDatasetNames } from "$lib/api/apiDatasetUtils.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { supportedRDFMediaTypes } from "$lib/utils/fileUtils";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let { showDialog = $bindable(), lockedDatasetName } = $props();

    const defaultDatasetName = "default";
    const GRAPH_NAMESPACE_URI = "http://graph#"; // Keep in sync with RDFA.GRAPH_URI (backend)
    const DEFAULT_GRAPH_NAME = "graph";
    const supportedFileExtensions = supportedRDFMediaTypes.map(
        type => type.fileExtension,
    );
    const allowedFileExtensions = supportedFileExtensions.join(", ");
    const uniqueId = uuidv4();
    const datasetInputId = `datasetNameImport-${uniqueId}`;
    const datasetListId = `datasetNamesImport-${uniqueId}`;
    const fileInputId = `actual-file-input-${uniqueId}`;
    let datasetNameUserInput = $state("");
    let files = $state([]);
    let dragActive = $state(false);
    let fileInputValue = $state("");
    let rejectedFiles = $state([]);

    let readOnlyDatasets = $state([]);
    let modifiableDatasets = $state([]);

    let enableSubmit = $derived(
        files.length > 0 && !isDatasetReadOnly(datasetNameUserInput),
    );

    const datasetSelectionLocked = $derived(!!lockedDatasetName);

    async function onOpen() {
        clearInputs();
        datasetNameUserInput =
            lockedDatasetName ?? editorState.selectedDataset.getValue();

        const datasetNames = await getDatasetNames();
        modifiableDatasets = datasetNames.modifiable;
        readOnlyDatasets = datasetNames.readonly;
    }

    function onClose() {
        clearInputs();
    }
    function clearInputs() {
        datasetNameUserInput = "";
        files = [];
        dragActive = false;
        fileInputValue = "";
        rejectedFiles = [];
    }

    function setDefaultIfNotSet() {
        if (!datasetNameUserInput) {
            datasetNameUserInput = defaultDatasetName;
        }
    }

    function isDatasetReadOnly(datasetName) {
        const targetDataset = datasetName || defaultDatasetName;
        return readOnlyDatasets.includes(targetDataset);
    }

    function getSanitizedGraphName(fileName) {
        if (!fileName) {
            return DEFAULT_GRAPH_NAME;
        }
        const normalized = fileName.split(/[\\/]/).pop() ?? fileName;
        const lastDotIndex = normalized.lastIndexOf(".");
        const baseName =
            lastDotIndex === -1
                ? normalized
                : normalized.substring(0, lastDotIndex);
        const sanitized = baseName.replace(/\W/g, "_");
        return sanitized.trim() ? sanitized : DEFAULT_GRAPH_NAME;
    }

    function buildGraphUri(fileName) {
        const sanitized = getSanitizedGraphName(fileName);
        return `${GRAPH_NAMESPACE_URI}${sanitized}`;
    }

    function isZipFile(fileName) {
        return fileName.toLowerCase().endsWith(".zip");
    }

    function isSupportedGraphFile(fileName) {
        if (!fileName) {
            return false;
        }
        const lowered = fileName.toLowerCase();
        return supportedFileExtensions.some(extension =>
            lowered.endsWith(extension.toLowerCase()),
        );
    }

    function ensureGraphNamespaceUri(graphUri, fallbackName) {
        const trimmed = graphUri?.trim();
        if (!trimmed) {
            return buildGraphUri(fallbackName);
        }
        if (trimmed.includes("://")) {
            return trimmed;
        }
        return `${GRAPH_NAMESPACE_URI}${trimmed}`;
    }

    function addFiles(newFiles) {
        rejectedFiles = [];
        const mappedFiles = Array.from(newFiles)
            .map(file => {
                if (!isZipFile(file.name) && !isSupportedGraphFile(file.name)) {
                    rejectedFiles.push(file.name);
                    return null;
                }
                return {
                    file,
                    graphUri: isZipFile(file.name)
                        ? ""
                        : buildGraphUri(file.name),
                    isZip: isZipFile(file.name),
                };
            })
            .filter(Boolean);

        if (mappedFiles.length > 0) {
            files = [...files, ...mappedFiles];
        }
        fileInputValue = "";
    }

    function removeFile(index) {
        files = files.filter((_, idx) => idx !== index);
    }

    function updateGraphUri(index, graphUri) {
        if (files[index]?.isZip) {
            return;
        }
        files = files.map((entry, idx) =>
            idx === index ? { ...entry, graphUri } : entry,
        );
    }

    function handleDrop(event) {
        dragActive = false;
        if (event.dataTransfer?.files?.length) {
            addFiles(event.dataTransfer.files);
        }
    }

    async function importGraphs() {
        setDefaultIfNotSet();
        let datasetNameLocal = datasetNameUserInput;
        const datasetNameEncoded = encodeURIComponent(datasetNameUserInput);
        const filesLocal = files;

        let formData = new FormData();
        filesLocal.forEach(fileEntry => {
            formData.append("files", fileEntry.file);
            formData.append(
                "graphUris",
                fileEntry.isZip
                    ? ""
                    : ensureGraphNamespaceUri(
                          fileEntry.graphUri,
                          fileEntry.file.name,
                      ),
            );
        });

        try {
            const res = await fetch(
                `${PUBLIC_BACKEND_URL}/datasets/${datasetNameEncoded}/graphs/content`,
                {
                    method: "PUT",
                    body: formData,
                    credentials: "include",
                },
            );
            if (res.ok) {
                console.log("successfully inserted data");

                let importedGraphUris = null;
                try {
                    const body = await res.json();
                    importedGraphUris = body?.importedGraphUris ?? null;
                } catch {
                    importedGraphUris = null;
                }

                editorState.selectedDataset.updateValue(datasetNameLocal);

                if (
                    Array.isArray(importedGraphUris) &&
                    importedGraphUris.length === 1
                ) {
                    editorState.selectedGraph.updateValue(importedGraphUris[0]);
                } else {
                    const nonZipFiles = filesLocal.filter(
                        entry => !entry.isZip,
                    );
                    if (nonZipFiles.length === 1) {
                        editorState.selectedGraph.updateValue(
                            ensureGraphNamespaceUri(
                                nonZipFiles[0].graphUri,
                                nonZipFiles[0].file.name,
                            ),
                        );
                    } else {
                        editorState.selectedGraph.updateValue(null);
                    }
                }

                editorState.selectedPackageUUID.updateValue(null);
                editorState.selectedClassDataset.updateValue(null);
                editorState.selectedClassGraph.updateValue(null);
                editorState.selectedClassUUID.updateValue(null);
            } else {
                console.log("failed to insert data");
            }
        } catch (e) {
            console.log("failed to insert data:");
            console.log(e);
        } finally {
            forceReloadTrigger.trigger();
        }
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Import"
    onPrimary={importGraphs}
    disablePrimary={!enableSubmit}
    title="Import Graphs"
>
    <div class="mx-2 flex h-full max-h-[80vh] flex-col">
        {#if !datasetSelectionLocked}
            <label for={datasetInputId} class="mb-1">Dataset</label>
            <input
                class="border-border bg-window-background focus:border-orange ring-none h-9 w-full rounded border-2 p-2 outline-none"
                type="text"
                id={datasetInputId}
                list={datasetListId}
                placeholder={defaultDatasetName}
                bind:value={datasetNameUserInput}
            />
            <datalist id={datasetListId}>
                {#each modifiableDatasets as datasetName}
                    <option value={datasetName}>{datasetName}</option>
                {/each}
            </datalist>

            {#if isDatasetReadOnly(datasetNameUserInput)}
                <div class="mt-1 mb-1 h-6 text-sm">
                    Cannot import into read-only dataset
                </div>
            {/if}
        {:else}
            <p class="mb-1 font-semibold">Dataset</p>
            <div
                class="border-border bg-default-background text-default-text h-9 w-full rounded border-2 px-3 py-1.5"
            >
                {lockedDatasetName}
            </div>
        {/if}
        <div class="mt-4">
            <input
                class="hidden"
                type="file"
                id={fileInputId}
                multiple
                accept={`${supportedFileExtensions.join(",")},.zip`}
                onchange={event => {
                    addFiles(event.target.files);
                    event.target.value = "";
                }}
                bind:value={fileInputValue}
            />
            <div
                class={`border-border hover:border-orange flex w-full flex-col rounded border-2 border-dashed px-4 py-6 transition-colors  ${dragActive ? "border-orange bg-orange/10" : "bg-window-background"}`}
                role="group"
                ondragover={event => {
                    event.preventDefault();
                    dragActive = true;
                }}
                ondragleave={event => {
                    event.preventDefault();
                    dragActive = false;
                }}
                ondrop={event => {
                    event.preventDefault();
                    handleDrop(event);
                }}
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
                            select file
                        </ButtonControl>
                    </div>
                    <p class="text-font-secondary text-sm">
                        or drag and drop files or a .zip archive
                    </p>
                </div>
                <p class="text-font-secondary mt-2 text-xs">
                    Each file becomes a graph named after the file. ZIP files
                    are unpacked and imported automatically.
                    <br />
                    Supported file extensions:
                    <b>{allowedFileExtensions}</b>
                    . In ZIP files, graphs must be located at the root level; folders
                    are ignored.
                </p>
                {#if rejectedFiles.length > 0}
                    <div
                        class="bg-red-background text-red-text border-red-border mt-3 rounded border px-3 py-2 text-xs"
                    >
                        <p class="font-semibold">Skipped unsupported files:</p>
                        <ul class="list-disc pl-5">
                            {#each rejectedFiles as fileName}
                                <li>{fileName}</li>
                            {/each}
                        </ul>
                    </div>
                {/if}
            </div>

            {#if files.length > 0}
                <div class="mt-3 max-h-[55vh] space-y-2 overflow-y-auto">
                    {#each files as fileEntry, index}
                        <div
                            class="border-border flex items-center space-x-3 rounded border px-3 py-2"
                        >
                            <div class="flex-1">
                                <input
                                    id={`graph-uri-${index}`}
                                    class="border-border bg-window-background focus:border-orange ring-none w-full rounded border-2 p-2 text-sm outline-none"
                                    type="text"
                                    value={fileEntry.isZip
                                        ? fileEntry.file.name
                                        : fileEntry.graphUri}
                                    disabled={fileEntry.isZip}
                                    oninput={event =>
                                        updateGraphUri(
                                            index,
                                            event.target.value,
                                        )}
                                />
                            </div>
                            <div
                                class="flex size-10 items-center justify-center p-0"
                            >
                                <ButtonControl
                                    height={10}
                                    callOnClick={() => removeFile(index)}
                                    title="Remove file"
                                >
                                    <Fa
                                        icon={faMinus}
                                        ariaLabel="Remove file"
                                    />
                                </ButtonControl>
                            </div>
                        </div>
                    {/each}
                </div>
            {:else}
                <p class="text-font-secondary mt-2 text-sm">
                    No files selected yet.
                </p>
            {/if}
        </div>
    </div>
</ActionDialog>
