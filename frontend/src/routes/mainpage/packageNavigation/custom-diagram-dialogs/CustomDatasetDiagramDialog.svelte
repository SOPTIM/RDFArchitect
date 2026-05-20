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
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { isValidDiagramName } from "$lib/models/reactive/validity-rules/validityFunctions.js";
    import {
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import { getUri } from "../packageNavigationUtils.svelte.js";
    import {
        createClassListForGraph,
        createPackageListForGraph,
    } from "./customDiagramDialogUtils.js";
    import GraphSelectSection from "./GraphSelectSection.svelte";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        diagramName = "",
        diagramId,
        selectedClasses = [],
        allDiagrams = [],
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    let localDiagramName = $state();
    let localDiagramId = $state();

    let graphs = $state([]);
    let classesByPackageAndGraph = $state({});
    let packagesByGraph = $state({});

    let violations = $derived(
        isValidDiagramName(localDiagramName, allDiagrams),
    );
    let disableSubmit = $derived(violations.length > 0);

    async function onOpen() {
        localDiagramName = diagramName;
        localDiagramId = diagramId ? diagramId : crypto.randomUUID();

        await fetchGraphs();
        await createPackageMap();
        await createClassMap();
        initialiseSelectionState();
    }

    function onClose() {
        localDiagramName = "";
        localDiagramId = crypto.randomUUID();
    }

    async function getGraphs(datasetName) {
        const result = await bec.getGraphNames(datasetName);
        return await result.json();
    }

    async function fetchGraphs() {
        try {
            const res = await getGraphs(lockedDatasetName);
            graphs = res
                .map(graph => {
                    return {
                        ...graph,
                        selected: false,
                        expanded: false,
                    };
                })
                .sort((a, b) => getUri(a).localeCompare(getUri(b)));
        } catch (err) {
            console.error("Failed to load graphs:", err);
            graphs = [];
        }
    }

    async function createPackageMap() {
        for (const graph of graphs) {
            const graphUri = getUri(graph);
            packagesByGraph[graphUri] = await createPackageListForGraph(
                lockedDatasetName,
                graphUri,
            );
        }
    }

    async function createClassMap() {
        const graphURIs = graphs.map(graph => getUri(graph));
        const result = {};

        await Promise.all(
            graphURIs.map(async graphUri => {
                result[graphUri] = await createClassListForGraph(
                    lockedDatasetName,
                    graphUri,
                    selectedClasses,
                );
            }),
        );

        classesByPackageAndGraph = result;
    }

    function toggleAll(newState) {
        graphs.forEach(g => (g.selected = newState));

        Object.entries(classesByPackageAndGraph).forEach(
            ([graphUri, packages]) => {
                const graphPackages = packagesByGraph[graphUri] ?? [];

                graphPackages.forEach(pack => (pack.selected = newState));

                Object.values(packages).forEach(classes =>
                    classes.forEach(cls => (cls.selected = newState)),
                );
            },
        );
    }

    function initialiseSelectionState() {
        if (!selectedClasses.length) {
            return;
        }

        graphs.forEach(graph => {
            const graphURI = getUri(graph);
            const packages = packagesByGraph[graphURI];
            let classesSelectedInGraph = false;
            packages.forEach(pack => {
                const packageId = pack.uuid;
                const classesInPackage =
                    classesByPackageAndGraph[graphURI][packageId] ?? [];

                if (classesInPackage.length > 0) {
                    const classesSelectedInPackage =
                        classesInPackage.find(cls => cls.selected) !==
                        undefined;
                    pack.expanded = classesSelectedInPackage;
                    if (classesSelectedInPackage) {
                        classesSelectedInGraph = true;
                    }
                }
            });
            graph.expanded = classesSelectedInGraph;
        });
    }

    async function submitDiagramClasses() {
        const selectedClassList = Object.entries(
            classesByPackageAndGraph,
        ).flatMap(([graphUri, packages]) =>
            Object.values(packages)
                .flat()
                .filter(cls => cls.selected === true)
                .map(cls => ({
                    uuid: cls.uuid,
                    graphUri: graphUri,
                })),
        );

        const diagramData = {
            diagramId: localDiagramId,
            name: localDiagramName,
            classes: selectedClassList,
        };

        try {
            const res = await bec.putCustomDatasetDiagram(
                lockedDatasetName,
                localDiagramId,
                diagramData,
            );

            if (res.ok) {
                editorState.selectedDataset.updateValue(lockedDatasetName);
                editorState.selectedGraph.updateValue(null);
                editorState.selectedDiagram.updateValue({
                    type: DiagramType.CUSTOM_DATASET_DIAGRAM,
                    id: localDiagramId,
                });
            } else {
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
    title="Create/Edit custom diagram for dataset"
    primaryLabel="Save"
    onPrimary={submitDiagramClasses}
    disablePrimary={disableSubmit}
>
    <div class="mx-2 flex h-full flex-col space-y-4">
        <label for="diagram-name-input" class="mt-2 mb-1">Diagram Name</label>
        <TextEditControl
            id="diagram-name-input"
            placeholder="Enter diagram name"
            bind:value={localDiagramName}
            warn={violations.length > 0}
        />
        <ViolationMessages {violations} />

        <div class="flex justify-between">
            <label for="class-tree" class="mt-2 mb-1">Selected Classes</label>
            <div class="flex space-x-2">
                <div class="w-26">
                    <ButtonControl callOnClick={() => toggleAll(true)}>
                        Select All
                    </ButtonControl>
                </div>
                <div class="w-26">
                    <ButtonControl callOnClick={() => toggleAll(false)}>
                        Deselect All
                    </ButtonControl>
                </div>
            </div>
        </div>
        <div
            id="class-tree"
            class="h-full max-h-[55vh] items-stretch gap-[0.1rem] overflow-y-auto empty:hidden"
        >
            {#each graphs as graph, index (getUri(graph))}
                <GraphSelectSection
                    bind:graph={graphs[index]}
                    bind:packages={packagesByGraph[getUri(graph)]}
                    classesByPackage={classesByPackageAndGraph[getUri(graph)] ??
                        []}
                />
            {/each}
        </div>
    </div>
</ActionDialog>
