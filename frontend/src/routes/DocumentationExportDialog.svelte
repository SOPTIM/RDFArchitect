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
    import ExportProgressPanel from "$lib/components/ExportProgressPanel.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { URI } from "$lib/models/dto/index.ts";
    import { editorState } from "$lib/sharedState.svelte.js";
    import { ExportProgress } from "$lib/utils/exportProgress.svelte.js";
    import { saveFile } from "$lib/utils/fileUtils.ts";
    import { generatePackageImages } from "$lib/utils/packageImageExport.svelte.js";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const zipMediaType = {
        mimeType: "application/zip",
        name: "ZIP",
        fileExtension: ".zip"
    };

    const supportedMediaTypes = [
        { name: "PNG", ending: "png" },
        { name: "SVG", ending: "svg" }
    ];

    const supportedDocumentFormats = [
        {
            name: "HTML",
            ending: "html",
            fetch: (dataset, graph, imageEnding, embedDiagrams, signal) => {
                const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(dataset)}/graphs/${encodeURIComponent(graph)}/htmlexport/${encodeURIComponent(imageEnding)}?embedDiagrams=${embedDiagrams}`;
                return fetch(url, {
                    method: "GET",
                    headers: new Headers({ "Content-Type": "application/json" }),
                    mode: "cors",
                    credentials: "include",
                    signal
                });
            }
        },
        {
            name: "AsciiDoc",
            ending: "adoc",
            fetch: (dataset, graph, imageEnding, embedDiagrams, signal) => {
                const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(dataset)}/graphs/${encodeURIComponent(graph)}/asciidocexport/${encodeURIComponent(imageEnding)}?embedDiagrams=${embedDiagrams}`;
                return fetch(url, {
                    method: "GET",
                    headers: new Headers({ "Content-Type": "application/json" }),
                    mode: "cors",
                    credentials: "include",
                    signal
                });
            }
        }
    ];

    const supportedDiagramPlacements = [
        { key: "link", name: "Link", embed: false },
        { key: "picture", name: "Picture in the document", embed: true }
    ];

    let selectedDatasetName = $state(null);
    let graphURI = $state(null);
    let selectedDocumentEnding = $state(supportedDocumentFormats[0].ending);
    let selectedImageEnding = $state(supportedMediaTypes[0].ending);
    let selectedPlacementKey = $state(supportedDiagramPlacements[0].key);

    /** Set while an export runs; drives the progress panel and the cancellation. */
    let progress = $state(null);

    let selectedDocumentFormat = $derived(
        supportedDocumentFormats.find(
            format => format.ending === selectedDocumentEnding
        )
    );
    let selectedMediaType = $derived(
        supportedMediaTypes.find(type => type.ending === selectedImageEnding)
    );
    let embedDiagrams = $derived(
        supportedDiagramPlacements.find(
            placement => placement.key === selectedPlacementKey
        )?.embed ?? false
    );
    let isExporting = $derived(progress !== null);
    let disablePrimary = $derived(
        !selectedDatasetName || !graphURI || isExporting
    );

    function onOpen() {
        selectedDatasetName =
            lockedDatasetName ?? editorState.selectedDataset.getValue();
        graphURI = lockedGraphUri ?? editorState.selectedGraph.getValue();
    }

    function onClose() {
        progress?.cancel();
    }

    async function onPrimary() {
        if (!selectedDatasetName || !graphURI || isExporting) return;
        const documentFormat = selectedDocumentFormat;
        const mediaType = selectedMediaType;
        const currentProgress = new ExportProgress();
        progress = currentProgress;
        try {
            const response = await documentFormat.fetch(
                selectedDatasetName,
                graphURI,
                mediaType.ending,
                embedDiagrams,
                currentProgress.signal
            );
            if (!response.ok) {
                toastStore.error(
                    "Export failed",
                    `Could not export "${graphURI}" as ${documentFormat.name}.`
                );
                return;
            }
            const contentType =
                response.headers.get("content-type") ?? "text/plain";
            const suggestedFilename = response.headers.get(
                "content-disposition"
            );
            let documentText = await response.text();
            currentProgress.documentReady();

            const images = await generatePackageImages(
                selectedDatasetName,
                graphURI,
                mediaType,
                currentProgress
            );
            if (currentProgress.cancelled) {
                notifyCancelled();
                return;
            }

            for (const { sourceFilename, filename } of images) {
                if (filename !== sourceFilename) {
                    documentText = documentText
                        .split(`images/${sourceFilename}`)
                        .join(`images/${filename}`);
                }
            }

            const zip = new JSZip();
            zip.file(
                getDocumentFilename(suggestedFilename, documentFormat),
                new Blob([documentText], { type: contentType })
            );

            const imagesFolder = zip.folder("images");
            for (const { filename, blob } of images) {
                imagesFolder.file(filename, blob);
            }

            currentProgress.startArchive();
            const zipBlob = await zip.generateAsync(
                { type: "blob" },
                metadata => currentProgress.archiveProgress(metadata.percent)
            );
            if (currentProgress.cancelled) {
                notifyCancelled();
                return;
            }
            currentProgress.finish();

            saveFile(
                zipBlob,
                `${getGraphLabel(graphURI)}-${documentFormat.ending}-export.zip`,
                zipMediaType
            );

            toastStore.success(
                "Export ready",
                `"${graphURI}" downloaded as ${documentFormat.name} export (zip).`
            );
            notifyFailedPackages(currentProgress);
            showDialog = false;
        } catch (e) {
            if (currentProgress.cancelled) {
                notifyCancelled();
                return;
            }
            console.error("Failed to download documentation export:", e);
            toastStore.error(
                "Export failed",
                "An unexpected error occurred while exporting."
            );
        } finally {
            progress = null;
        }
    }

    function notifyCancelled() {
        toastStore.info(
            "Export cancelled",
            "The documentation export was stopped, nothing was downloaded."
        );
    }

    /**
     * A package whose diagram could not be rendered is left out of the archive.
     * Without this the document would reference an image that is not there.
     */
    function notifyFailedPackages(currentProgress) {
        const failed = currentProgress.failed;
        if (failed.length === 0) return;
        toastStore.warning(
            "Diagrams missing",
            `The diagram of ${failed.map(pkg => `"${pkg.label}"`).join(", ")} could not be rendered and is missing from the archive.`
        );
    }

    function getDocumentFilename(contentDisposition, documentFormat) {
        const match = contentDisposition?.match(/filename="?([^"]+)"?/);
        return match?.[1] ?? `export.${documentFormat.ending}`;
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
    {onClose}
    secondaryLabel={isExporting ? "Cancel" : undefined}
    onSecondary={() => progress?.cancel()}
    disableSecondary={progress?.cancelled}
    closeOnPrimary={false}
    title="Export Documentation"
>
    {#if progress}
        <ExportProgressPanel {progress} />
    {:else}
        <div class="mx-2 mt-2 flex h-full flex-col space-y-3">
            <DatasetAndGraphSelection
                bind:dataset={selectedDatasetName}
                bind:graph={graphURI}
                {lockedDatasetName}
                {lockedGraphUri}
                displayAsCard={false}
            />

            <div class="border-border bg-background-subtle rounded border p-3">
                <p class="text-text-subtle mb-2 text-xs font-medium">
                    Document
                </p>
                <label
                    for="document-format-Download"
                    class="mb-1 block text-sm"
                >
                    Format
                </label>
                <SelectEditControl
                    id="document-format-Download"
                    bind:value={selectedDocumentEnding}
                    options={supportedDocumentFormats}
                    getOptionValue={format => format.ending}
                    getOptionLabel={format => format.name}
                />
            </div>

            <div class="border-border bg-background-subtle rounded border p-3">
                <p class="text-text-subtle mb-2 text-xs font-medium">
                    Package diagrams
                </p>
                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <div>
                        <label
                            for="diagram-type-Download"
                            class="mb-1 block text-sm"
                        >
                            File type
                        </label>
                        <SelectEditControl
                            id="diagram-type-Download"
                            bind:value={selectedImageEnding}
                            options={supportedMediaTypes}
                            getOptionValue={type => type.ending}
                            getOptionLabel={type => type.name}
                        />
                    </div>
                    <div>
                        <label
                            for="diagram-placement-Download"
                            class="mb-1 block text-sm"
                        >
                            Shown as
                        </label>
                        <SelectEditControl
                            id="diagram-placement-Download"
                            bind:value={selectedPlacementKey}
                            options={supportedDiagramPlacements}
                            getOptionValue={placement => placement.key}
                            getOptionLabel={placement => placement.name}
                        />
                    </div>
                </div>
            </div>
        </div>
    {/if}
</ActionDialog>
