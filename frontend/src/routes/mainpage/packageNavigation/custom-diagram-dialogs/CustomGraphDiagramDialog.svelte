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
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { isValidDiagramName } from "$lib/models/reactive/validity-rules/validityFunctions.js";
    import {
        DiagramType,
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { customDiagramStore } from "$lib/stores/DiagramStore.ts";

    import { getPackageId } from "../packageNavigationUtils.svelte.js";
    import {
        createPackageListForGraph,
        createClassListForGraph,
    } from "./customDiagramDialogUtils.js";
    import PackageSelectSection from "./PackageSelectSection.svelte";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
        diagramName = "",
        diagramId,
        selectedClasses = [],
        allDiagrams,
    } = $props();

    let localDiagramName = $state("");
    let localDiagramId = $state();

    let packages = $state([]);
    let classesByPackage = $state({});

    let violations = $derived(
        isValidDiagramName(localDiagramName, allDiagrams),
    );
    let disableSubmit = $derived(violations.length > 0);

    async function onOpen() {
        localDiagramName = diagramName;
        localDiagramId = diagramId ? diagramId : crypto.randomUUID();

        packages = await createPackageListForGraph(
            lockedDatasetName,
            lockedGraphUri,
        );
        classesByPackage = await createClassListForGraph(
            lockedDatasetName,
            lockedGraphUri,
            selectedClasses,
        );
        initializePackageSelectionState();
    }

    function onClose() {
        localDiagramName = "";
        localDiagramId = crypto.randomUUID();
    }

    function toggleAll(newState) {
        packages.forEach(pack => {
            pack.selected = newState;
            classesByPackage[getPackageId(pack)]?.forEach(cls => {
                cls.selected = newState;
            });
        });
    }

    function initializePackageSelectionState() {
        if (!selectedClasses.length) {
            return;
        }

        packages.forEach(pack => {
            const packageId = pack.uuid;
            const classesInPackage = classesByPackage[packageId] ?? [];

            if (classesInPackage.length > 0) {
                pack.expanded =
                    classesInPackage.find(cls => cls.selected) !== undefined;
            }
        });
    }

    async function submitDiagramClasses() {
        const selectedClassList = Object.values(classesByPackage)
            .flat()
            .filter(cls => cls.selected === true)
            .map(cls => ({
                uuid: cls.uuid,
                graphUri: lockedGraphUri,
            }));
        const diagramData = {
            diagramId: localDiagramId,
            name: localDiagramName,
            classes: selectedClassList,
        };

        const { error } = await customDiagramStore.saveGraphDiagram(
            lockedDatasetName,
            lockedGraphUri,
            localDiagramId,
            diagramData,
        );
        if (error) return;

        editorState.selectedDataset.updateValue(lockedDatasetName);
        editorState.selectedGraph.updateValue(lockedGraphUri);
        editorState.selectedDiagram.updateValue({
            type: DiagramType.CUSTOM_GRAPH_DIAGRAM,
            id: localDiagramId,
        });
        forceReloadTrigger.trigger();
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    title={"Create/Edit Custom Diagram for Profile"}
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
            {#each packages as pack, index (pack.uuid)}
                <PackageSelectSection
                    bind:pack={packages[index]}
                    classes={classesByPackage[pack.uuid] ?? []}
                />
            {/each}
        </div>
    </div>
</ActionDialog>
