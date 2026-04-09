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
    import { BackendConnection } from "$lib/api/backend.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";

    import { getUri } from "../packageNavigationUtils.svelte.js";
    import { createClassListForGraph, createPackageListForGraph } from "./customDiagramDialogUtils.js";
    import GraphSelectSection from "./GraphSelectSection.svelte";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        diagramName = "",
        diagramId = crypto.randomUUID(),
        selectedClasses = []
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    let graphs = $state([]);
    let classesByPackageAndGraph = $state({});
    let packagesByGraph = $state({});


    let disableSubmit = $derived(diagramName.trim() === "");

    async function onOpen() {
        await fetchGraphs();
        await createPackageMap();
        await createClassMap();
        initialiseSelectionState();
    }

    function onClose() {
        diagramName = "";
    }

    async function getGraphs(datasetName) {
        const result = await bec.getGraphNames(datasetName);
        return await result.json();
    }

    async function fetchGraphs() {
        try {
            const res = await getGraphs(lockedDatasetName);
            graphs = res.map(graph => {
                return {
                    ...graph,
                    selected: false,
                    expanded: false
                };
            }).sort((a, b) => getUri(a).localeCompare(getUri(b)));
        } catch (err) {
            console.error("Failed to load graphs:", err);
            graphs = [];
        }
    }

    async function createPackageMap() {
        for (const graph of graphs) {
            const graphUri = getUri(graph);
            packagesByGraph[graphUri] = await createPackageListForGraph(lockedDatasetName, graphUri);
        }
    }

    async function createClassMap() {
        const graphURIs = graphs.map(graph => getUri(graph));
        const result = {};

        await Promise.all(graphURIs.map(async graphUri => {
            result[graphUri] = await createClassListForGraph(lockedDatasetName, graphUri, selectedClasses);
        }));

        classesByPackageAndGraph = result;
    }

    function deselectAll() {
        graphs.forEach(g => g.selected = false);

        Object.entries(classesByPackageAndGraph).forEach(([graphUri, packages]) => {
            const graphPackages = packagesByGraph[graphUri] ?? [];

            graphPackages.forEach(pack => pack.selected = false);

            Object.values(packages).forEach(classes =>
                classes.forEach(cls => cls.selected = false)
            );
        });
    }

    function initialiseSelectionState() {
        if (!selectedClasses.length) {
            return;
        }

        graphs.forEach((graph) => {
            const graphURI = getUri(graph);
            const packages = packagesByGraph[graphURI];
            packages.forEach((pack) => {
                const packageId = pack.uuid;
                const classesInPackage = classesByPackageAndGraph[graphURI][packageId] ?? [];

                if (classesInPackage.length > 0) {
                    pack.expanded = classesInPackage.find(cls => cls.selected) !== undefined;
                }
            });
            graph.expanded = packages.find(pack => pack.selected) !== undefined;
        });
    }

    async function submitDiagramClasses() {
        const selectedClassList = Object.entries(classesByPackageAndGraph)
            .flatMap(([graphUri, packages]) =>
                Object.values(packages)
                    .flat()
                    .filter(cls => cls.selected === true)
                    .map(cls => ({
                        uuid: cls.uuid,
                        graphUri: graphUri
                    }))
            );

        const diagramData = {
            diagramId: diagramId,
            name: diagramName,
            classes: selectedClassList
        };

        try {
            const res = await bec.putCustomDiagram(lockedDatasetName, diagramId, diagramData);

            if (!res.ok) {
                console.error("Failed to save diagram");
            }
        } finally {
            forceReloadTrigger.trigger();
        }
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Save"
    onPrimary={submitDiagramClasses}
    disablePrimary={disableSubmit}
>
    <div class="mx-2 flex h-full flex-col space-y-4">
        <label for="diagram-name-input" class="mt-2 mb-1">Diagram Name</label>
        <TextEditControl
            id="diagram-name-input"
            placeholder="Enter diagram name"
            bind:value={diagramName}
        />

        <div class="flex justify-between">
            <label for="class-tree" class="mt-2 mb-1">Selected Classes</label>
            <div class="w-26">
                <ButtonControl callOnClick={deselectAll}>
                    Deselect All
                </ButtonControl>
            </div>
        </div>
        <div
            id="class-tree" class="h-full overflow-y-auto max-h-[55vh] items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each graphs as graph (getUri(graph))}
                <GraphSelectSection
                    {graph}
                    packages={packagesByGraph[getUri(graph)] ?? []}
                    classesByPackage={classesByPackageAndGraph[getUri(graph)] ?? []}
                />
            {/each}
        </div>
    </div>
</ActionDialog>