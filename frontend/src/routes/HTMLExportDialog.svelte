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
    import JSZip from "jszip";

    import { BackendConnection } from "$lib/api/backend.js";
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { URI } from "$lib/models/dto/index.ts";
    import { editorState } from "$lib/sharedState.svelte.js";
    import { saveFile } from "$lib/utils/fileUtils.ts";
    import { generatePackageImages } from "$lib/utils/packageImageExport.svelte.js";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const zipMediaType = {
        mimeType: "application/zip",
        name: "ZIP",
        fileExtension: ".zip",
    };

    const supportedMediaTypes = [
        { name: "PNG", ending: "png" },
        { name: "SVG", ending: "svg" },
    ];

    let selectedDatasetName = $state(null);
    let graphURI = $state(null);
    let isExporting = $state(false);
    let selectedMediaType = $state();
    let disablePrimary = $derived(
        !selectedDatasetName || !graphURI || isExporting,
    );

    function onOpen() {
        selectedDatasetName =
            lockedDatasetName ?? editorState.selectedDataset.getValue();
        graphURI = lockedGraphUri ?? editorState.selectedGraph.getValue();
    }

    async function onPrimary() {
        if (!selectedDatasetName || !graphURI) return;
        isExporting = true;
        try {
            const response = await bec.getHTMLExport(
                selectedDatasetName,
                graphURI,
                selectedMediaType.ending,
            );
            if (!response.ok) {
                toastStore.error(
                    "Export failed",
                    `Could not export "${graphURI}" as HTML.`,
                );
                return;
            }
            const htmlBlob = await response.blob();
            const suggestedFilename = response.headers.get(
                "content-disposition",
            );

            const zip = new JSZip();
            zip.file(getHtmlFilename(suggestedFilename), htmlBlob);

            const images = await generatePackageImages(
                selectedDatasetName,
                graphURI,
                selectedMediaType,
            );
            const imagesFolder = zip.folder("images");
            for (const { filename, blob } of images) {
                imagesFolder.file(filename, blob);
            }

            const zipBlob = await zip.generateAsync({ type: "blob" });
            saveFile(
                zipBlob,
                `${getGraphLabel(graphURI)}-html-export.zip`,
                zipMediaType,
            );

            toastStore.success(
                "Export ready",
                `"${graphURI}" downloaded as HTML export (zip).`,
            );
            showDialog = false;
        } catch (e) {
            console.error("Failed to download HTML export:", e);
            toastStore.error(
                "Export failed",
                "An unexpected error occurred while exporting.",
            );
        } finally {
            isExporting = false;
        }
    }

    function getHtmlFilename(contentDisposition) {
        const match = contentDisposition?.match(/filename="?([^"]+)"?/);
        return match?.[1] ?? "export.html";
    }

    function getGraphLabel(graphURI) {
        try {
            return new URI(graphURI).suffix;
        } catch {
            return graphURI;
        }
    }
</script>

<ActionDialog
    bind:showDialog
    primaryLabel="Export"
    {disablePrimary}
    {onPrimary}
    {onOpen}
    closeOnPrimary={false}
    title="Export as HTML"
>
    {#if isExporting}
        <div
            class="absolute inset-0 z-50 flex items-center justify-center bg-white/50"
        >
            <LoadingSpinner ariaLabel="Exporting HTML" />
        </div>
    {/if}
    <div class="mx-2 mt-2 flex h-full flex-col">
        <DatasetAndGraphSelection
            bind:dataset={selectedDatasetName}
            bind:graph={graphURI}
            {lockedDatasetName}
            {lockedGraphUri}
            displayAsCard={false}
        />
        <label for="media-types-Download" class="mt-2 mb-1">
            Diagram File Type
        </label>
        <select
            class=" border-border bg-window-background focus:border-blue h-9 w-fit rounded border-2 p-2"
            id="media-types-Download"
            bind:value={selectedMediaType}
        >
            {#each supportedMediaTypes as mediaType}
                <option value={mediaType}>{mediaType.name}</option>
            {/each}
        </select>
    </div>
</ActionDialog>
