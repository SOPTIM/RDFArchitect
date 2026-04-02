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
    import { getNamespaces } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import TextAreaControl from "$lib/components/TextAreaControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
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
        packageURINamespace: "packageURINamespaceNewPackage" + uuid,
        packageLabel: "packageNameNewPackage" + uuid,
        packageComment: "packageCommentNewPackage" + uuid,
    };
    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let selectedDatasetName = $state(null);
    let selectedGraphURI = $state(null);
    let packageLabel = $state(null);
    let packageComment = $state(null);
    let packageURINamespace = $state(null);

    let namespaces = $state([]);
    let packages = $state([]);

    let disableSubmit = $derived(
        !selectedDatasetName ||
            !selectedGraphURI ||
            !packageURINamespace ||
            !packageLabel ||
            packages?.some(pkg => pkg.label.values === packageLabel),
    );

    $effect(async () => {
        namespaces = await getNamespaces(selectedDatasetName);
        packageURINamespace = null;
    });

    $effect(async () => {
        await getPackages(selectedDatasetName, selectedGraphURI);
    });

    async function onOpen() {
        selectedDatasetName =
            lockedDatasetName ?? editorState.selectedDataset.getValue();
        selectedGraphURI =
            lockedGraphUri ?? editorState.selectedGraph.getValue();

        packageURINamespace = null;
        packageLabel = null;
        packageComment = null;

        if (!selectedDatasetName) {
            return;
        }
        namespaces = await getNamespaces(selectedDatasetName);

        if (selectedGraphURI) {
            await getPackages(selectedDatasetName, selectedGraphURI);
        } else {
            packages = [];
        }
    }

    function onClose() {
        selectedDatasetName = null;
        selectedGraphURI = null;
        namespaces = [];
        packageURINamespace = null;
        packages = [];
        packageLabel = null;
        packageComment = null;
    }

    async function getPackages(datasetName, graphURI) {
        if (!datasetName || !graphURI) {
            packages = [];
            return;
        }
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

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Add Package"
    onPrimary={() =>
        newPackage(
            selectedDatasetName,
            selectedGraphURI,
            packageLabel,
            packageComment,
            packageURINamespace,
        )}
    disablePrimary={disableSubmit}
    title="Add Package"
>
    <div class="mx-2 flex h-full flex-col">
        <DatasetAndGraphSelection
            bind:dataset={selectedDatasetName}
            bind:graph={selectedGraphURI}
            {lockedDatasetName}
            {lockedGraphUri}
            allowSelectionOfReadonlyDatasets={false}
            displayAsCard={false}
        />

        <label for={domIds.packageURINamespace} class="mt-3 mb-1 block text-sm">
            Namespace
        </label>
        <SelectEditControl
            id={domIds.packageURINamespace}
            bind:value={packageURINamespace}
            options={namespaces}
            disabled={!selectedDatasetName}
            placeholder={selectedDatasetName
                ? "Select namespace"
                : "Select a dataset first"}
            getOptionValue={namespace => namespace.substitutedPrefix}
            getOptionLabel={namespace =>
                `${namespace.substitutedPrefix} (${namespace.prefix})`}
        />

        <label for={domIds.packageLabel} class="mt-3 mb-1 block text-sm">
            Package Label
        </label>
        <TextEditControl
            id={domIds.packageLabel}
            placeholder="Add a label"
            bind:value={packageLabel}
        />

        <label for={domIds.packageComment} class="mt-3 mb-1 block text-sm">
            Package Comment
        </label>
        <TextAreaControl
            id={domIds.packageComment}
            placeholder="Add a comment"
            bind:value={packageComment}
        />
    </div>
</ActionDialog>
