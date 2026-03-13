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
    import {
        getDatasetNames,
        getNamespaces,
    } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import TextAreaControl from "$lib/components/TextAreaControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import Dialog from "$lib/dialog/Dialog.svelte";
    import DialogButtons from "$lib/dialog/DialogButtons.svelte";
    import { Package } from "$lib/models/dto";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
    } = $props();

    const uuid = crypto.randomUUID();
    const domIds = {
        datasetName: "datasetNameNewPackage" + uuid,
        graphURI: "graphUriNewPackage" + uuid,
        cimPackage: "cimPackageNewPackage" + uuid,
        classURINamespace: "packageURINamespaceNewPackage" + uuid,
        packageLabel: "packageNameNewPackage" + uuid,
        packageComment: "packageCommentNewPackage" + uuid,
    };
    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let selectedDatasetName = $state();
    let selectedGraphURI = $state();
    let packageLabel = $state(null);
    let packageComment = $state(null);
    let packageURINamespace = $state();

    let modifiableDatasets = $state([]);
    let readOnlyDatasets = $state([]);
    let graphURIs = $state([]);
    let namespaces = $state([]);
    let packages = $state([]);

    let disableSubmit = $derived(
        !selectedDatasetName ||
            !selectedGraphURI ||
            !packageURINamespace ||
            !packageLabel ||
            packages?.some(pkg => pkg.label.values === packageLabel),
    );

    const datasetSelectionLocked = $derived(!!lockedDatasetName);
    const graphSelectionLocked = $derived(!!lockedGraphUri);

    async function onOpen() {
        selectedDatasetName =
            lockedDatasetName ?? editorState.selectedDataset.getValue();
        selectedGraphURI =
            lockedGraphUri ?? editorState.selectedGraph.getValue();

        if (!datasetSelectionLocked) {
            const datasetNames = await getDatasetNames();
            modifiableDatasets = datasetNames.modifiable;
            readOnlyDatasets = datasetNames.readonly;
        }

        if (
            !selectedDatasetName ||
            readOnlyDatasets.includes(selectedDatasetName)
        ) {
            return;
        }

        await getGraphNames(selectedDatasetName);
        namespaces = await getNamespaces(selectedDatasetName);
        if (selectedGraphURI) {
            await getPackages(selectedDatasetName, selectedGraphURI);
        }
    }

    function onClose() {
        selectedDatasetName = null;
        modifiableDatasets = [];
        readOnlyDatasets = [];
        clearOnDatasetChange();
        packageLabel = null;
        packageComment = null;
    }

    // Clear variables that depend on a dataset
    function clearOnDatasetChange() {
        namespaces = [];
        packageURINamespace = null;
        if (!graphSelectionLocked) {
            selectedGraphURI = null;
        }
        graphURIs = [];
        packages = [];
    }

    async function getGraphNames(datasetName) {
        if (!modifiableDatasets.includes(datasetName)) {
            graphURIs = [];
            return;
        }
        const res = await bec.getGraphNames(datasetName);
        graphURIs = await res.json();
    }

    async function getPackages(datasetName, graphURI) {
        const res = await bec.getPackages(datasetName, graphURI);
        const packagesJSON = await res.json();
        packages = [
            ...packagesJSON.internalPackageList,
            ...packagesJSON.externalPackageList,
        ];
    }

    async function newPackage(
        ds,
        graph,
        packageLabel,
        packageComment,
        packageURINamespace,
    ) {
        let promise = fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(ds) +
                "/graphs/" +
                encodeURIComponent(graph) +
                "/packages",
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify(
                    new Package({
                        prefix: packageURINamespace,
                        label: packageLabel,
                        comment: packageComment,
                    }),
                ),
                credentials: "include",
            },
        )
            .then(res => {
                if (res.ok) {
                    console.log("successfully added package");
                    return res.json();
                } else {
                    console.log("failed to insert data");
                }
            })
            .then(uuid => {
                console.log(
                    `successfully added package ${packageLabel} with UUID ${uuid}`,
                );
                editorState.selectedDataset.updateValue(ds);
                editorState.selectedGraph.updateValue(graph);
                editorState.selectedPackageUUID.updateValue(uuid);
            });
        promise
            .catch(e => {
                console.log("failed to add package:");
                console.log(e);
            })
            .finally(() => {
                forceReloadTrigger.trigger();
            });
    }
</script>

<Dialog bind:showDialog {onOpen} {onClose}>
    <div class="mx-2 flex h-full flex-col">
        {#if !datasetSelectionLocked}
            <label for={domIds.datasetName} class="mb-1">Dataset</label>
            {#key modifiableDatasets}
                <select
                    class="border-border bg-window-background focus:border-orange h-9 w-full rounded border-2 p-2"
                    id={domIds.datasetName}
                    bind:value={selectedDatasetName}
                    onchange={async () => {
                        clearOnDatasetChange();
                        await getGraphNames(selectedDatasetName);
                        namespaces = await getNamespaces(selectedDatasetName);
                    }}
                >
                    {#each modifiableDatasets as datasetName}
                        <option
                            selected={datasetName === selectedDatasetName}
                            value={datasetName}
                        >
                            {datasetName}
                        </option>
                    {/each}
                    {#each readOnlyDatasets as datasetName}
                        <option disabled value={datasetName}>
                            {datasetName}
                        </option>
                    {/each}
                </select>
            {/key}
        {:else}
            <p class="mb-1 font-semibold">Dataset</p>
            <div
                class="border-border bg-default-background text-default-text h-9 w-full rounded border-2 px-3 py-1.5"
            >
                {selectedDatasetName}
            </div>
        {/if}

        {#if graphSelectionLocked}
            <p class="mt-2 mb-1 font-semibold">Graph</p>
            <div
                class="border-border bg-default-background text-default-text h-9 w-full rounded border-2 px-3 py-1.5"
            >
                {selectedGraphURI ?? "No graph selected"}
            </div>
        {:else}
            <label for={domIds.graphURI} class="mt-2 mb-1">Graph</label>
            {#key graphURIs}
                <select
                    class="border-border bg-window-background focus:border-orange h-9 w-full rounded border-2 p-2"
                    id={domIds.graphURI}
                    bind:value={selectedGraphURI}
                    disabled={graphURIs.length === 0}
                    onchange={() => {
                        getPackages(selectedDatasetName, selectedGraphURI);
                    }}
                >
                    {#each graphURIs as graph_uri}
                        <option
                            selected={graph_uri.suffix ===
                                editorState.selectedGraph.getValue()}
                            value={(graph_uri.prefix == null
                                ? ""
                                : graph_uri.prefix) + graph_uri.suffix}
                        >
                            {graph_uri.suffix}
                        </option>
                    {/each}
                </select>
            {/key}{/if}
        <label for={domIds.classURINamespace} class="mt-2 mb-1">
            Namespace
        </label>
        {#key namespaces}
            <select
                class="border-border bg-window-background focus:border-orange h-9 w-full rounded border-2 p-2"
                id={domIds.classURINamespace}
                bind:value={packageURINamespace}
                disabled={namespaces.length === 0}
            >
                {#each namespaces as namespace}
                    <option value={namespace.substitutedPrefix}>
                        {`${namespace.substitutedPrefix} (${namespace.prefix})`}
                    </option>
                {/each}
            </select>
        {/key}
        <label for={domIds.packageLabel} class="mt-2 mb-1">Package Label</label>
        <TextEditControl
            id={domIds.packageLabel}
            placeholder="Add a label"
            bind:value={packageLabel}
        />
        <label for={domIds.packageComment} class="mt-2 mb-1">
            Package Comment
        </label>
        <TextAreaControl
            id={domIds.packageComment}
            placeholder="Add a comment"
            bind:value={packageComment}
        />
    </div>
    //TODO: RDFA-403 finish refactoring
    <DialogButtons
        bind:showDialog
        submitLabel="Add Package"
        onSubmit={() =>
            newPackage(
                selectedDatasetName,
                selectedGraphURI,
                packageLabel,
                packageComment,
                packageURINamespace,
            )}
        {disableSubmit}
    />
</Dialog>
