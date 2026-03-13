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
    import { v4 as uuidv4 } from "uuid";

    import { getNamespaces } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import Dialog from "$lib/dialog/Dialog.svelte";
    import DialogButtons from "$lib/dialog/DialogButtons.svelte";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
        lockedPackage,
    } = $props();

    const uuid = uuidv4();
    const domIds = {
        datasetName: "datasetNameNewClass" + uuid,
        graphURI: "graphUriNewClass" + uuid,
        classPackage: "classPackageNewClass" + uuid,
        classURINamespace: "classURINamespaceNewClass" + uuid,
        className: "classNameNewClass" + uuid,
    };
    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let datasetName = $state(null);
    let graphURI = $state(null);

    let classPackage = $state(null);
    let classURINamespace = $state(null);
    let className = $state(null);

    let packages = $state([]);
    let namespaces = $state([]);

    let disableSubmit = $derived(
        !datasetName ||
            !graphURI ||
            !classPackage ||
            !classURINamespace ||
            !className,
    );

    const packageSelectionLocked = $derived(!!lockedPackage);

    $effect(async () => {
        namespaces = await getNamespaces(datasetName);
        classURINamespace = null;
        if (!packageSelectionLocked) {
            packages = datasetName ? packages : [];
            classPackage = null;
        }
    });

    $effect(async () => {
        if (packageSelectionLocked) {
            return;
        }
        await getPackages(datasetName, graphURI);
        classPackage = null;
    });

    async function onOpen() {
        datasetName =
            lockedDatasetName ?? editorState.selectedDataset.getValue();
        graphURI = lockedGraphUri ?? editorState.selectedGraph.getValue();

        classURINamespace = null;
        className = null;
        if (!datasetName) {
            return;
        }
        namespaces = await getNamespaces(datasetName);

        if (!graphURI) {
            return;
        }
        namespaces = await getNamespaces(datasetName);

        if (graphURI) {
            await getPackages(datasetName, graphURI);
        } else {
            packages = [];
        }

        if (packageSelectionLocked) {
            classPackage = lockedPackage;
            packages = [lockedPackage];
            return;
        }
        await getPackages(datasetName, graphURI);
        const selectedPackageUUID =
            editorState.selectedPackageUUID.getValue() === "default"
                ? null
                : editorState.selectedPackageUUID.getValue();
        classPackage = packages.find(pkg => pkg.uuid === selectedPackageUUID);
    }

    function onClose() {
        datasetName = null;
        clearOnDatasetChange();
        className = null;
    }

    function clearOnDatasetChange() {
        namespaces = [];
        classURINamespace = null;
        graphURI = null;
        packages = [];
        classPackage = null;
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

    async function newClass() {
        const datasetNameLocal = datasetName;
        const graphURILocal = graphURI;
        const selectedPackageUUID = classPackage?.uuid ?? "default";
        const packageDTO = classPackage?.uuid ? classPackage : null;
        let promise = fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(datasetNameLocal) +
                "/graphs/" +
                encodeURIComponent(graphURILocal) +
                "/classes",
            {
                method: "POST",
                headers: new Headers({ "Content-Type": "application/json" }),
                body: JSON.stringify({
                    packageDTO,
                    classURIPrefix: classURINamespace,
                    className: className,
                }),
                credentials: "include",
            },
        ).then(res => {
            if (res.ok) {
                console.log("successfully added class");
                editorState.selectedDataset.updateValue(datasetNameLocal);
                editorState.selectedGraph.updateValue(graphURILocal);
                editorState.selectedPackageUUID.updateValue(
                    selectedPackageUUID,
                );
                editorState.selectedClassDataset.updateValue(null);
                editorState.selectedClassGraph.updateValue(null);
                editorState.selectedClassUUID.updateValue(null);
            } else {
                console.log("failed to insert data");
            }
        });
        promise
            .catch(e => {
                console.log("failed to add class:", e);
            })
            .finally(() => {
                forceReloadTrigger.trigger();
                editorState.selectedDataset.trigger();
                editorState.selectedGraph.trigger();
                editorState.selectedPackageUUID.trigger();
                editorState.selectedClassUUID.trigger();
            });
    }
</script>

<Dialog bind:showDialog {onOpen} {onClose}>
    <div class="mx-2 flex h-full flex-col">
        <DatasetAndGraphSelection
            bind:dataset={datasetName}
            bind:graph={graphURI}
            {lockedDatasetName}
            {lockedGraphUri}
            allowSelectionOfReadonlyDatasets={false}
            displayAsCard={false}
        />
        <label for={domIds.classPackage} class="mt-3 mb-1 block text-sm">
            Package
        </label>
        <SelectEditControl
            id={domIds.classPackage}
            bind:value={classPackage}
            options={packages}
            disabled={packageSelectionLocked || !datasetName || !graphURI}
            placeholder={datasetName && graphURI
                ? "Select package"
                : "Select a graph first"}
            getOptionLabel={pkg => pkg.label}
        />

        <label for={domIds.classURINamespace} class="mt-3 mb-1 block text-sm">
            Namespace
        </label>
        <SelectEditControl
            id={domIds.classURINamespace}
            bind:value={classURINamespace}
            options={namespaces}
            disabled={!datasetName}
            placeholder={datasetName
                ? "Select namespace"
                : "Select a dataset first"}
            getOptionValue={namespace => namespace.substitutedPrefix}
            getOptionLabel={namespace =>
                `${namespace.substitutedPrefix} (${namespace.prefix})`}
        />
        <label for={domIds.className} class="mt-3 mb-1 block text-sm">
            Name
        </label>
        <TextEditControl
            id={domIds.className}
            placeholder="..."
            bind:value={className}
        />
    </div>
    //TODO: RDFA-403 finish refactoring
    <DialogButtons
        bind:showDialog
        submitLabel="Add Class"
        onSubmit={newClass}
        {disableSubmit}
    />
</Dialog>
