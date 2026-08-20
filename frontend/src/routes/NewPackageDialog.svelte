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
    import WorkspaceAndGraphSelection from "$lib/components/WorkspaceAndGraphSelection.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import TextAreaControl from "$lib/components/TextAreaControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { Package } from "$lib/models/dto";
    import { DiagramType } from "$lib/sharedState.svelte.js";
    import { classStore } from "$lib/stores/classStore.ts";
    import { datasetStore } from "$lib/stores/datasetStore.ts";
    import { packageStore } from "$lib/stores/packageStore.ts";

    import {
        editorState,
        forceReloadTrigger,
    } from "../lib/sharedState.svelte.js";

    let {
        showDialog = $bindable(),
        lockedWorkspaceName,
        lockedGraphUri,
    } = $props();

    const uuid = crypto.randomUUID();
    const domIds = {
        packageURINamespace: "packageURINamespaceNewPackage" + uuid,
        packageLabel: "packageNameNewPackage" + uuid,
        packageComment: "packageCommentNewPackage" + uuid,
    };

    let selectedWorkspaceName = $state(null);
    let selectedGraphURI = $state(null);
    let packageLabel = $state(null);
    let packageComment = $state(null);
    let packageURINamespace = $state(null);

    let namespaces = $state([]);
    let packages = $state([]);
    let classes = $state([]);

    let packageIri = $derived(packageURINamespace + packageLabel);
    let resourceIriAlreadyExists = $derived(
        !!packageIri &&
            [...packages, ...classes].some(
                resource => getResourceIri(resource) === packageIri,
            ),
    );

    let disableSubmit = $derived(
        !selectedWorkspaceName ||
            !selectedGraphURI ||
            !packageURINamespace ||
            !packageLabel ||
            resourceIriAlreadyExists,
    );

    $effect(async () => {
        namespaces = await datasetStore.getNamespaces(selectedWorkspaceName);
        packageURINamespace = null;
    });

    $effect(async () => {
        await getResources(selectedWorkspaceName, selectedGraphURI);
    });

    async function onOpen() {
        selectedWorkspaceName =
            lockedWorkspaceName ?? editorState.selectedWorkspace.getValue();
        selectedGraphURI =
            lockedGraphUri ?? editorState.selectedGraph.getValue();

        packageURINamespace = null;
        packageLabel = null;
        packageComment = null;

        if (!selectedWorkspaceName) {
            return;
        }
        namespaces = await datasetStore.getNamespaces(selectedWorkspaceName);

        if (selectedGraphURI) {
            await getResources(selectedWorkspaceName, selectedGraphURI);
        } else {
            packages = [];
            classes = [];
        }
    }

    function onClose() {
        selectedWorkspaceName = null;
        selectedGraphURI = null;
        namespaces = [];
        packageURINamespace = null;
        packages = [];
        classes = [];
        packageLabel = null;
        packageComment = null;
    }

    async function getResources(workspaceName, graphURI) {
        await Promise.all([
            getPackages(workspaceName, graphURI),
            getClasses(workspaceName, graphURI),
        ]);
    }

    async function getPackages(workspaceName, graphURI) {
        if (!workspaceName || !graphURI) {
            packages = [];
            return;
        }

        const result = await packageStore.getPackages(workspaceName, graphURI);
        packages = result ? [...result.internal, ...result.external] : [];
    }

    async function getClasses(workspaceName, graphURI) {
        if (!workspaceName || !graphURI) {
            classes = [];
            return;
        }
        classes = (await classStore.getClasses(workspaceName, graphURI)) ?? [];
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

        editorState.selectedWorkspace.updateValue(ds);
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
    primaryLabel="Create Package"
    onPrimary={() =>
        newPackage(
            selectedWorkspaceName,
            selectedGraphURI,
            packageLabel,
            packageComment,
            packageURINamespace,
        )}
    disablePrimary={disableSubmit}
    title="New Package"
>
    <div class="mx-2 flex h-full flex-col">
        <WorkspaceAndGraphSelection
            bind:workspace={selectedWorkspaceName}
            bind:graph={selectedGraphURI}
            {lockedWorkspaceName}
            {lockedGraphUri}
            allowSelectionOfReadonlyWorkspaces={false}
            displayAsCard={false}
        />

        <label for={domIds.packageURINamespace} class="mt-3 mb-1 block text-sm">
            Namespace
        </label>
        <SelectEditControl
            id={domIds.packageURINamespace}
            bind:value={packageURINamespace}
            options={namespaces}
            disabled={!selectedWorkspaceName}
            placeholder={selectedWorkspaceName
                ? "Select namespace"
                : "Select a workspace first"}
            getOptionValue={namespace => namespace.prefix}
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
