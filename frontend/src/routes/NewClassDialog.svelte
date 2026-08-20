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

    import WorkspaceAndGraphSelection from "$lib/components/WorkspaceAndGraphSelection.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { ReactiveValueWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-value-wrapper.svelte.js";
    import { isInvalidClassLabel } from "$lib/models/reactive/validity-rules/validityFunctions.js";
    import { classStore } from "$lib/stores/classStore.ts";
    import { crossProfileStore } from "$lib/stores/crossProfileStore.ts";
    import { datasetStore } from "$lib/stores/datasetStore.ts";
    import { packageStore } from "$lib/stores/packageStore.ts";
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
        lockedWorkspaceName,
        lockedGraphUri,
        lockedPackage,
        classLayoutPosition = null,
        onClassCreated = () => {},
    } = $props();

    const uuid = uuidv4();
    const domIds = {
        workspaceName: "workspaceNameNewClass" + uuid,
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

    let workspaceName = $state(null);
    let graphURI = $state(null);

    let classPackage = $state(null);
    let classURINamespace = $state(null);

    let className = $state(null);
    let packages = $state([]);
    let namespaces = $state([]);

    let compareClasses = $state([]);

    let disableSubmit = $derived(
        !workspaceName ||
            !graphURI ||
            !classPackage ||
            !classURINamespace?.value ||
            !className?.value ||
            (className?.violations.length ?? 0) > 0,
    );

    const normalizedLockedPackage = $derived(normalizePackage(lockedPackage));
    const packageSelectionLocked = $derived(!!normalizedLockedPackage);

    $effect(async () => {
        const ds = workspaceName;
        const graph = graphURI;

        await untrack(() => onWorkspaceOrGraphChanged(ds, graph));
    });

    async function onWorkspaceOrGraphChanged(ds, graph) {
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

        // Only classes defined in this graph block a name; a name that is merely
        // referenced can be created and takes over that reference.
        compareClasses = await getClasses(ds, graph, false);
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
        workspaceName =
            lockedWorkspaceName ?? editorState.selectedWorkspace.getValue();
        graphURI = lockedGraphUri ?? editorState.selectedGraph.getValue();

        classURINamespace = new ReactiveValueWrapper(null);
        className = new ReactiveValueWrapper("", label =>
            isInvalidClassLabel(label, classURINamespace, compareClasses),
        );

        if (!workspaceName || !graphURI) {
            return;
        }
        namespaces = await datasetStore.getNamespaces(datasetName);

        await getPackages(workspaceName, graphURI);
        compareClasses = await getClasses(workspaceName, graphURI, false);

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
        workspaceName = null;
        clearOnWorkspaceChange();
        className = null;
    }

    function clearOnWorkspaceChange() {
        namespaces = [];
        classURINamespace = null;
        graphURI = null;
        packages = [];
        classPackage = null;
    }

    async function getPackages(workspaceName, graphURI) {
        if (!workspaceName || !graphURI) {
            packages = [];
            return;
        }

        const result = (await packageStore.getPackages(
            workspaceName,
            graphURI,
        )) ?? { internal: [], external: [] };
        packages = [...result.internal, ...result.external];
    }

    function snapshotFormState() {
        return {
            workspaceName,
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

        return classStore.addClass(
            form.workspaceName,
            form.graphURI,
            requestBody,
        );
    }

    function updateEditorSelection(form, classUUID) {
        editorState.selectedWorkspace.updateValue(form.workspaceName);
        editorState.selectedGraph.updateValue(form.graphURI);
        editorState.selectedDiagram.updateValue({
            type: DiagramType.PACKAGE,
            id: form.packageUUID,
        });
        editorState.selectedClassWorkspace.updateValue(form.workspaceName);
        editorState.selectedClassGraph.updateValue(form.graphURI);
        editorState.selectedClass.updateValue({
            type: ClassType.SINGLE_CLASS,
            id: classUUID,
        });
    }

    function handleClassCreated(form, classUUID) {
        onClassCreated({
            classUUID,
            workspaceName: form.workspaceName,
            graphURI: form.graphURI,
            packageUUID: form.packageUUID,
            className: form.className,
        });
        updateEditorSelection(form, classUUID);
        crossProfileStore.invalidateDataset(form.datasetName);
    }

    async function newClass() {
        const form = snapshotFormState();

        const { data, error } = await postNewClass(form);
        if (!error) {
            handleClassCreated(form, data);
        }
        forceReloadTrigger.trigger();
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Create Class"
    onPrimary={newClass}
    disablePrimary={disableSubmit}
    title="New Class"
>
    <div class="mx-2 flex h-full flex-col">
        <WorkspaceAndGraphSelection
            bind:workspace={workspaceName}
            bind:graph={graphURI}
            {lockedWorkspaceName}
            {lockedGraphUri}
            allowSelectionOfReadonlyWorkspaces={false}
            displayAsCard={false}
        />
        <label for={domIds.classPackage} class="mt-3 mb-1 block text-sm">
            Package
        </label>
        <SelectEditControl
            id={domIds.classPackage}
            bind:value={classPackage}
            options={packages}
            disabled={packageSelectionLocked || !workspaceName || !graphURI}
            placeholder={workspaceName && graphURI
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
                disabled={!workspaceName}
                placeholder={workspaceName
                    ? "Select namespace"
                    : "Select a workspace first"}
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
