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
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import TextAreaControl from "$lib/components/TextAreaControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { Package } from "$lib/models/dto";
    import { DiagramType } from "$lib/sharedState.svelte.js";
    import { classStore } from "$lib/stores/ClassStore.ts";
    import { datasetStore } from "$lib/stores/DatasetStore.ts";
    import { packageStore } from "$lib/stores/PackageStore.ts";

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

    let selectedDatasetName = $state(null);
    let selectedGraphURI = $state(null);
    let packageLabel = $state(null);
    let packageComment = $state(null);
    let packageURINamespace = $state(null);

    let namespaces = $state([]);
    let packages = $state([]);
    let classes = $state([]);

    let packageIri = $derived(getPackageIri(packageURINamespace, packageLabel));
    let resourceIriAlreadyExists = $derived(
        !!packageIri &&
            [...packages, ...classes].some(
                resource => getResourceIri(resource) === packageIri,
            ),
    );

    let disableSubmit = $derived(
        !selectedDatasetName ||
            !selectedGraphURI ||
            !packageURINamespace ||
            !packageLabel ||
            resourceIriAlreadyExists,
    );

    $effect(async () => {
        namespaces = datasetStore.getNamespaces(selectedDatasetName);
        packageURINamespace = null;
    });

    $effect(async () => {
        await getResources(selectedDatasetName, selectedGraphURI);
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
        namespaces = datasetStore.getNamespaces(selectedDatasetName);

        if (selectedGraphURI) {
            await getResources(selectedDatasetName, selectedGraphURI);
        } else {
            packages = [];
            classes = [];
        }
    }

    function onClose() {
        selectedDatasetName = null;
        selectedGraphURI = null;
        namespaces = [];
        packageURINamespace = null;
        packages = [];
        classes = [];
        packageLabel = null;
        packageComment = null;
    }

    async function getResources(datasetName, graphURI) {
        await Promise.all([
            getPackages(datasetName, graphURI),
            getClasses(datasetName, graphURI),
        ]);
    }

    async function getPackages(datasetName, graphURI) {
        if (!datasetName || !graphURI) {
            packages = [];
            return;
        }

        await packageStore.load(datasetName, graphURI);
        const result = await packageStore.getPackages(datasetName, graphURI);
        packages = [
            ...result.internal,
            ...result.external,
        ]
    }

    async function getClasses(datasetName, graphURI) {
        if (!datasetName || !graphURI) {
            classes = [];
            return;
        }
        await classStore.load(datasetName, graphURI);
        classes = classStore.getClasses(datasetName, graphURI);
    }

    function getExpandedNamespace(namespace) {
        return (
            namespaces.find(n => n.substitutedPrefix === namespace)?.prefix ??
            namespace
        );
    }

    function getPackageIri(namespace, label) {
        if (!namespace || !label) {
            return null;
        }
        if (!label?.startsWith("Package_")) {
            label = "Package_" + label;
        }
        return getExpandedNamespace(namespace) + label;
    }

    function getResourceIri(resource) {
        return (resource.prefix ?? "") + (resource.label ?? "");
    }

    async function newPackage(
        ds,
        graph,
        packageLabel,
        packageComment,
        packageURINamespace,
    ) {
        if (!packageLabel.startsWith("Package_")) {
            packageLabel = "Package_" + packageLabel;
        }
        const body = new Package({
            prefix: packageURINamespace,
            label: packageLabel,
            comment: packageComment,
        });
        const { data, error } = await packageStore.addPackage(ds, graph, body);

        if (error) return;

        editorState.selectedDataset.updateValue(ds);
        editorState.selectedGraph.updateValue(graph);
        editorState.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: data,
        });
        forceReloadTrigger.trigger();
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
            warn={resourceIriAlreadyExists}
        />
        <ViolationMessages
            violations={resourceIriAlreadyExists
                ? ["IRI already exists as a class or package"]
                : []}
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
