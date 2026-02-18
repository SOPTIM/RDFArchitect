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
    import { faDiagramProject } from "@fortawesome/free-solid-svg-icons";
    import {
        faFileExport,
        faTrash,
        faPlus,
        faClockRotateLeft,
        faCodeBranch,
        faFileImport,
        faUpload,
        faDownload,
        faEye,
        faRotateLeft,
        faRotateRight,
        faGear,
    } from "@fortawesome/free-solid-svg-icons";
    import { onMount } from "svelte";

    import {
        undo,
        fetchCanUndo,
        redo,
        fetchCanRedo,
    } from "$lib/actions/versionControlActions.js";
    import { isReadOnly } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { shortenIri } from "$lib/utils/iri.js";

    import PackageButton from "./PackageButton.svelte";
    import {
        getUri,
        isSelectedGraph,
        getPackageId,
    } from "./packageNavigationUtils.svelte.js";
    import CompareDialog from "../../compare/CompareDialog.svelte";
    import DeleteDatasetDialog from "../../DeleteDatasetDialog.svelte";
    import ExportDialog from "../../ExportDialog.svelte";
    import NewPackageDialog from "../../NewPackageDialog.svelte";
    import OntologyDialog from "./ontology-editor-dialog/OntologyDialog.svelte";
    import SHACLExportDialog from "../../shacl/SHACLExportDialog.svelte";
    import SHACLFullViewDialog from "../../shacl/SHACLFullViewDialog.svelte";
    import SHACLUploadDialog from "../../shacl/SHACLUploadDialog.svelte";

    import { goto } from "$app/navigation";

    let {
        dataset,
        graph,
        onExpandDataset = () => {},
        prefixes = [],
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let packages = $state([]);
    let ontology = $state();
    let packagesLoading = $state(false);
    let packagesRequestId = 0;
    let classesByPackage = $state({});
    let classPackageByUuid = $state({});
    let selectedClassPackageId = $state(null);
    let classesRequestId = 0;
    let showExportDialog = $state(false);
    let showDeleteDialog = $state(false);
    let showNewPackageDialog = $state(false);
    let showCompareDialog = $state(false);
    let showSHACLUploadDialog = $state(false);
    let showSHACLExportDialog = $state(false);
    let showSHACLFullViewDialog = $state(false);
    let readOnly = $state(false);
    let canUndo = $state(false);
    let canRedo = $state(false);
    let showEditOntologyDialog = $state(false);

    let graphHighlightLabel = $derived(shortenIri(prefixes, getUri(graph)));

    $effect(async () => {
        forceReloadTrigger.subscribe();
        await loadGraphData();
    });

    $effect(() => {
        editorState.selectedClassUUID.subscribe();
        editorState.selectedClassDataset.subscribe();
        editorState.selectedClassGraph.subscribe();
        updateSelectedClassPackageId();
        ensureExpandedPackages();
    });

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        ensureExpandedPackages();
    });

    onMount(() => {
        loadGraphData();
    });

    async function getPackages(datasetName, graphURI) {
        const res = await bec.getPackages(datasetName, graphURI);
        return await res.json();
    }

    async function getClasses(datasetName, graphURI) {
        const res = await bec.getClasses(datasetName, graphURI);
        return await res.json();
    }

    async function loadGraphData() {
        await fetchClasses();
        ontology = await getOntology();
        await createPackageList();
        await updateReadOnly();
        await updateUndoRedo();
        ensureExpandedPackages();
    }

    async function createPackageList() {
        const requestId = ++packagesRequestId;
        packagesLoading = true;

        try {
            const packageStructure = await getPackages(
                dataset.label,
                getUri(graph),
            );
            if (requestId !== packagesRequestId) {
                return;
            }

            const localPackagesList = [];
            const previous = packages ?? [];
            const selectedPackageId =
                editorState.selectedPackageUUID.getValue();
            const selectedClassPackageIdSnapshot = selectedClassPackageId;

            packageStructure.internalPackageList.forEach(pack => {
                localPackagesList.push({
                    uuid: pack.uuid,
                    prefix: pack.prefix,
                    label: pack.label,
                    comment: pack.comment,
                    external: false,
                });
            });
            packageStructure.externalPackageList.forEach(pack => {
                localPackagesList.push({
                    uuid: pack.uuid,
                    prefix: pack.prefix,
                    label: pack.label,
                    comment: pack.comment,
                    external: true,
                });
            });
            packages = localPackagesList.map(pack => {
                const packageId = getPackageId(pack);
                const prev = previous.find(p => getPackageId(p) === packageId);
                const keepExpanded = prev?.showContents ?? false;
                const userCollapsed = prev?.userCollapsed ?? !keepExpanded;
                const isSelected =
                    isSelectedGraph(dataset, graph) &&
                    selectedPackageId === packageId;
                const hasSelectedClass =
                    selectedClassPackageIdSnapshot === packageId;
                return {
                    ...pack,
                    userCollapsed,
                    showContents: userCollapsed
                        ? false
                        : keepExpanded || isSelected || hasSelectedClass,
                };
            });
        } catch (err) {
            console.error("Failed to load packages:", err);
        } finally {
            if (requestId === packagesRequestId) {
                packagesLoading = false;
            }
        }
    }

    async function getOntology() {
        const res = await bec.getOntology(dataset.label, getUri(graph));
        let content = await res.text();
        if (!content) {
            return null;
        }
        return JSON.parse(content);
    }

    async function fetchClasses() {
        const requestId = ++classesRequestId;

        try {
            const classList =
                (await getClasses(dataset.label, getUri(graph))) ?? [];
            if (requestId !== classesRequestId) {
                return;
            }

            const grouped = {};
            const uuidMap = {};

            for (const cls of classList) {
                const packageId = getPackageId(cls.package);
                uuidMap[cls.uuid] = packageId;
                if (!grouped[packageId]) {
                    grouped[packageId] = [];
                }
                grouped[packageId].push({
                    ...cls,
                    packageUUID: packageId,
                });
            }

            for (const key of Object.keys(grouped)) {
                grouped[key].sort((a, b) =>
                    (a.label ?? "").localeCompare(b.label ?? "", undefined, {
                        sensitivity: "base",
                    }),
                );
            }

            classesByPackage = grouped;
            classPackageByUuid = uuidMap;
            updateSelectedClassPackageId();
        } catch (err) {
            console.error("Failed to load classes:", err);
            classesByPackage = {};
            classPackageByUuid = {};
            selectedClassPackageId = null;
        }
    }

    function updateSelectedClassPackageId() {
        const selectedClassUUID = editorState.selectedClassUUID.getValue();
        const selectedClassDataset =
            editorState.selectedClassDataset.getValue();
        const selectedClassGraph = editorState.selectedClassGraph.getValue();

        if (
            !selectedClassUUID ||
            selectedClassDataset !== dataset.label ||
            selectedClassGraph !== getUri(graph)
        ) {
            selectedClassPackageId = null;
            return;
        }

        selectedClassPackageId = classPackageByUuid[selectedClassUUID] ?? null;
    }

    function ensureExpandedPackages() {
        const selectedPackageId = editorState.selectedPackageUUID.getValue();
        const selectedClassPackageIdSnapshot = selectedClassPackageId;

        let updated = false;
        const nextPackages = packages.map(pack => {
            const packageId = getPackageId(pack);
            const shouldBeExpanded = pack.userCollapsed
                ? false
                : pack.showContents ||
                  packageId === selectedPackageId ||
                  packageId === selectedClassPackageIdSnapshot;
            if (shouldBeExpanded !== pack.showContents) {
                updated = true;
                return { ...pack, showContents: shouldBeExpanded };
            }
            return pack;
        });

        if (updated) {
            packages = nextPackages;
        }
    }

    function toggleGraphContentsVisibility(graph) {
        graph.showContents = !graph.showContents;
    }

    async function updateReadOnly() {
        readOnly = await isReadOnly(dataset.label);
    }

    function updatePackage(updatedPack) {
        ensureGraphIsExpanded();
        if (!updatedPack) {
            return;
        }
        const updatedId = getPackageId(updatedPack);
        packages = packages.map(existingPack =>
            getPackageId(existingPack) === updatedId
                ? updatedPack
                : existingPack,
        );
    }

    function ensureGraphIsExpanded() {
        if (!graph?.showContents) {
            graph.showContents = true;
        }
        onExpandDataset();
    }

    async function updateUndoRedo() {
        canUndo = await fetchCanUndo(dataset.label, getUri(graph));
        canRedo = await fetchCanRedo(dataset.label, getUri(graph));
    }

    function focusGraphContext() {
        const nextDataset = dataset.label;
        const nextGraph = getUri(graph);
        const previousDataset = editorState.selectedDataset.getValue();
        const previousGraph = editorState.selectedGraph.getValue();
        const graphChanged =
            previousDataset !== nextDataset || previousGraph !== nextGraph;

        editorState.selectedDataset.updateValue(nextDataset);
        editorState.selectedGraph.updateValue(nextGraph);
        if (graphChanged) {
            editorState.selectedPackageUUID.updateValue(null);
        }
    }

    function triggerReload() {
        editorState.selectedDataset.trigger();
        editorState.selectedGraph.trigger();
        editorState.selectedClassUUID.trigger();
        forceReloadTrigger.trigger();
    }
</script>

<div
    class={`flex w-full flex-col items-stretch gap-[0.1rem] ${packagesLoading ? "opacity-70" : ""}`}
>
    <ContextMenu.Root>
        <ContextMenu.TriggerArea class="flex w-full flex-col items-stretch">
            <NavigationEntry
                level={2}
                label={graph.uri.suffix}
                icon={faDiagramProject}
                hasChildren={packages.length > 0}
                expanded={graph.showContents}
                isSelected={isSelectedGraph(dataset, graph)}
                title={graph.uri.suffix}
                highlightLabel={graphHighlightLabel}
                onclick={() => {
                    focusGraphContext();
                }}
                onToggle={() => toggleGraphContentsVisibility(graph)}
            />
        </ContextMenu.TriggerArea>
        <ContextMenu.Content>
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    showNewPackageDialog = true;
                }}
                disabled={readOnly}
                faIcon={faPlus}
            >
                New Package
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    undo(dataset.label, getUri(graph)).then(success => {
                        if (success) triggerReload();
                    });
                }}
                disabled={readOnly || !canUndo}
                faIcon={faRotateLeft}
            >
                Undo
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    redo(dataset.label, getUri(graph)).then(success => {
                        if (success) triggerReload();
                    });
                }}
                disabled={readOnly || !canRedo}
                faIcon={faRotateRight}
            >
                Redo
            </ContextMenu.Item.Button>
            {#if !readOnly}
                <ContextMenu.Separator />
                {#if ontology}
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            showEditOntologyDialog = true;
                        }}
                        faIcon={faGear}
                    >
                        Edit Ontology
                    </ContextMenu.Item.Button>
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            bec.deleteOntology(dataset.label, getUri(graph));
                            forceReloadTrigger.trigger();
                        }}
                        variant="danger"
                        faIcon={faTrash}
                    >
                        Delete Ontology
                    </ContextMenu.Item.Button>
                {:else}
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            showEditOntologyDialog = true;
                        }}
                        faIcon={faPlus}
                    >
                        Create Ontology
                    </ContextMenu.Item.Button>
                {/if}
                <ContextMenu.Separator />
            {:else if ontology}
                <ContextMenu.Item.Button
                    onSelect={() => {
                        showEditOntologyDialog = true;
                    }}
                    faIcon={faEye}
                >
                    View Ontology
                </ContextMenu.Item.Button>
                <ContextMenu.Separator />
            {/if}
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    goto("/changelog");
                }}
                faIcon={faClockRotateLeft}
            >
                Changelog
            </ContextMenu.Item.Button>
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    showCompareDialog = true;
                }}
                faIcon={faCodeBranch}
            >
                Compare...
            </ContextMenu.Item.Button>
            <ContextMenu.SubMenu.Root>
                <ContextMenu.SubMenu.Trigger faIcon={faFileImport}>
                    SHACL
                </ContextMenu.SubMenu.Trigger>
                <ContextMenu.SubMenu.Content>
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            focusGraphContext();
                            showSHACLUploadDialog = true;
                        }}
                        disabled={readOnly}
                        faIcon={faUpload}
                    >
                        Import
                    </ContextMenu.Item.Button>
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            focusGraphContext();
                            showSHACLExportDialog = true;
                        }}
                        faIcon={faDownload}
                    >
                        Export
                    </ContextMenu.Item.Button>
                    <ContextMenu.Item.Button
                        onSelect={() => {
                            focusGraphContext();
                            showSHACLFullViewDialog = true;
                        }}
                        faIcon={faEye}
                    >
                        Full SHACL
                    </ContextMenu.Item.Button>
                </ContextMenu.SubMenu.Content>
            </ContextMenu.SubMenu.Root>
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    showExportDialog = true;
                }}
                faIcon={faFileExport}
            >
                Export Graph
            </ContextMenu.Item.Button>
            <ContextMenu.Separator />
            <ContextMenu.Item.Button
                onSelect={() => {
                    focusGraphContext();
                    showDeleteDialog = true;
                }}
                disabled={readOnly}
                faIcon={faTrash}
                variant="danger"
            >
                Delete Graph
            </ContextMenu.Item.Button>
        </ContextMenu.Content>
    </ContextMenu.Root>
    {#if graph.showContents}
        <div
            class="flex w-full flex-col items-stretch gap-[0.1rem] empty:hidden"
        >
            {#each packages as pack (getPackageId(pack))}
                <PackageButton
                    {dataset}
                    {graph}
                    {pack}
                    {prefixes}
                    classes={classesByPackage[getPackageId(pack)] ?? []}
                    onPackChange={updatePackage}
                />
            {/each}
        </div>
    {/if}
</div>

<ExportDialog
    bind:showDialog={showExportDialog}
    lockedDatasetName={dataset.label}
    lockedGraphUri={getUri(graph)}
/>
<DeleteDatasetDialog bind:showDialog={showDeleteDialog} />
<NewPackageDialog
    bind:showDialog={showNewPackageDialog}
    lockedDatasetName={dataset.label}
    lockedGraphUri={getUri(graph)}
/>
<CompareDialog
    bind:showDialog={showCompareDialog}
    lockedDatasetName={dataset.label}
    lockedGraphUri={getUri(graph)}
/>
<SHACLUploadDialog
    bind:showDialog={showSHACLUploadDialog}
    lockedDatasetName={dataset.label}
    lockedGraphUri={getUri(graph)}
/>
<SHACLExportDialog
    bind:showDialog={showSHACLExportDialog}
    lockedDatasetName={dataset.label}
    lockedGraphUri={getUri(graph)}
/>
<SHACLFullViewDialog bind:showDialog={showSHACLFullViewDialog} />
<OntologyDialog
    bind:showDialog={showEditOntologyDialog}
    graphUri={getUri(graph)}
    dataset={dataset.label}
    bind:ontology
    readonly={readOnly}
/>
