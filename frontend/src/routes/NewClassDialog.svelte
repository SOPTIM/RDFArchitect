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
    import { untrack } from "svelte";
    import { v4 as uuidv4 } from "uuid";

    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { ReactiveValueWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-value-wrapper.svelte.js";
    import { isInvalidClassLabel } from "$lib/models/reactive/validity-rules/validityFunctions.js";
    import { classStore } from "$lib/stores/ClassStore.ts";
    import { datasetStore } from "$lib/stores/DatasetStore.ts";
    import { packageStore } from "$lib/stores/PackageStore.ts";
    import { getPackageDisplayLabel } from "$lib/utils/package-label.js";

    import {
        ClassType,
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";
    import { getClasses } from "./mainpage/classEditor/fetch-class-editor-context.js";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
        lockedPackage,
        classLayoutPosition = null,
        onClassCreated = () => {},
    } = $props();

    const uuid = uuidv4();
    const domIds = {
        datasetName: "datasetNameNewClass" + uuid,
        graphURI: "graphUriNewClass" + uuid,
        classPackage: "classPackageNewClass" + uuid,
        classURINamespace: "classURINamespaceNewClass" + uuid,
        className: "classNameNewClass" + uuid,
    };

    const DEFAULT_PACKAGE = Object.freeze({
        uuid: null,
        prefix: null,
        label: "default",
    });

    let datasetName = $state(null);
    let graphURI = $state(null);

    let classPackage = $state(null);
    let classURINamespace = $state(null);

    let className = $state(null);
    let packages = $state([]);
    let namespaces = $state([]);

    let compareClasses = $state([]);

    let disableSubmit = $derived(
        !datasetName ||
            !graphURI ||
            !classPackage ||
            !classURINamespace?.value ||
            !className?.value ||
            (className?.violations.length ?? 0) > 0,
    );

    const normalizedLockedPackage = $derived(normalizePackage(lockedPackage));
    const packageSelectionLocked = $derived(!!normalizedLockedPackage);

    $effect(async () => {
        const ds = datasetName;
        const graph = graphURI;

        await untrack(() => onDatasetOrGraphChanged(ds, graph));
    });

    async function onDatasetOrGraphChanged(ds, graph) {
        namespaces = await datasetStore.getNamespaces(ds);
        if (classURINamespace) classURINamespace.value = null;
        classPackage = null;

        if (!ds || !graph) {
            packages = [];
            compareClasses = [];
            return;
        }

        if (!packageSelectionLocked) {
            await getPackages(ds, graph);
        }

        compareClasses = await getClasses(ds, graph);
        refreshClassNameValidation();
    }

    function refreshClassNameValidation() {
        if (className && classURINamespace) {
            className = new ReactiveValueWrapper(className.value, label =>
                isInvalidClassLabel(label, classURINamespace, compareClasses),
            );
        }
    }

    function normalizePackage(pkg) {
        if (!pkg) return null;
        if (typeof pkg === "string") {
            return pkg === "default" ? { ...DEFAULT_PACKAGE } : null;
        }
        if (pkg.uuid == null) return { ...DEFAULT_PACKAGE };
        return pkg;
    }

    async function onOpen() {
        datasetName =
            lockedDatasetName ?? editorState.selectedDataset.getValue();
        graphURI = lockedGraphUri ?? editorState.selectedGraph.getValue();

        classURINamespace = new ReactiveValueWrapper(null);
        className = new ReactiveValueWrapper("", label =>
            isInvalidClassLabel(label, classURINamespace, compareClasses),
        );

        if (!datasetName || !graphURI) {
            return;
        }
        namespaces = datasetStore.getNamespaces(datasetName);

        await getPackages(datasetName, graphURI);
        compareClasses = await getClasses(datasetName, graphURI);

        classPackage = packageSelectionLocked
            ? applyLockedPackage()
            : findInitiallySelectedPackage();
    }

    function applyLockedPackage() {
        packages = [normalizedLockedPackage];
        return normalizedLockedPackage;
    }

    function findInitiallySelectedPackage() {
        const diagramId = editorState.selectedDiagram.getProperty("id");
        const selectedPackageUUID = diagramId === "default" ? null : diagramId;
        return packages.find(pkg => pkg.uuid === selectedPackageUUID) ?? null;
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

        await packageStore.load(datasetName, graphURI);
        const result = await packageStore.getPackages(datasetName, graphURI);
        packages = [...result.internal, ...result.external];
    }

    function snapshotFormState() {
        return {
            datasetName,
            graphURI,
            className: className?.value,
            classURIPrefix: classURINamespace?.value,
            packageDTO: classPackage?.uuid == null ? null : classPackage,
            packageUUID: classPackage?.uuid ?? "default",
        };
    }

    function postNewClass(form) {
        const requestBody = {
            packageDTO: form.packageDTO,
            classURIPrefix: form.classURIPrefix,
            className: form.className,
        };
        if (classLayoutPosition) {
            requestBody.classLayoutPosition = classLayoutPosition;
        }

        return classStore.addClass(form.datasetName, form.graphURI, requestBody);
    }

    function updateEditorSelection(form, classUUID) {
        editorState.selectedDataset.updateValue(form.datasetName);
        editorState.selectedGraph.updateValue(form.graphURI);
        editorState.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: form.packageUUID,
        });
        editorState.selectedClassDataset.updateValue(form.datasetName);
        editorState.selectedClassGraph.updateValue(form.graphURI);
        editorState.selectedClass.updateValue({
            type: ClassType.SINGLE_CLASS,
            id: classUUID,
        });
    }

    function handleClassCreated(form, classUUID) {
        onClassCreated({
            classUUID,
            datasetName: form.datasetName,
            graphURI: form.graphURI,
            packageUUID: form.packageUUID,
            className: form.className,
        });
        updateEditorSelection(form, classUUID);
    }

    async function newClass() {
        const form = snapshotFormState();

        const { data, error } = await postNewClass(form);
        if (!error) {
            const classUUID = data;
            handleClassCreated(form, classUUID);
        }
        forceReloadTrigger.trigger();
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Add Class"
    onPrimary={newClass}
    disablePrimary={disableSubmit}
    title="Add Class"
>
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
                : "Select a schema first"}
            getOptionLabel={pkg => getPackageDisplayLabel(pkg.label)}
        />

        <label for={domIds.classURINamespace} class="mt-3 mb-1 block text-sm">
            Namespace
        </label>
        {#if className && classURINamespace}
            <SelectEditControl
                id={domIds.classURINamespace}
                bind:value={classURINamespace.value}
                options={namespaces}
                disabled={!datasetName}
                placeholder={datasetName
                    ? "Select namespace"
                    : "Select a dataset first"}
                getOptionValue={namespace => namespace.prefix}
                getOptionLabel={namespace =>
                    `${namespace.substitutedPrefix} (${namespace.prefix})`}
            />
            <label for={domIds.className} class="mt-3 mb-1 block text-sm">
                Name
            </label>

            <TextEditControl
                id={domIds.className}
                placeholder="..."
                bind:value={className.value}
                warn={!className.isValid}
            />
            <ViolationMessages violations={className.violations} />
        {/if}
    </div>
</ActionDialog>
