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

    import { getPackageId } from "../packageNavigationUtils.svelte.js";
    import { createPackageListForGraph, createClassListForGraph } from "./customDiagramDialogUtils.js";
    import PackageSelectSection from "./PackageSelectSection.svelte";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
        diagramName = "",
        diagramId = crypto.randomUUID(),
        selectedClasses = []
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    let packages = $state([]);
    let classesByPackage = $state({});

    let disableSubmit = $derived(diagramName.trim() === "");

    async function onOpen() {
        packages = await createPackageListForGraph(lockedDatasetName, lockedGraphUri);
        classesByPackage = await createClassListForGraph(lockedDatasetName, lockedGraphUri, selectedClasses);
        initlialisePacakgeSelectionState();
    }

    function onClose() {
        diagramName = "";
    }

    function deselectAll() {
        packages.forEach((pack) => {
            pack.selected = false;
            classesByPackage[getPackageId(pack)]?.forEach((cls) => {
                cls.selected = false;
            });
        });
    }

    function initlialisePacakgeSelectionState() {
        if (!selectedClasses.length) {
            return;
        }

        packages.forEach((pack) => {
            const packageId = pack.uuid;
            const classesInPackage = classesByPackage[packageId] ?? [];

            if (classesInPackage.length > 0) {
                pack.expanded = classesInPackage.find(cls => cls.selected) !== undefined;
            }
        });
    }

    async function submitDiagramClasses() {
        const selectedClassList = Object.values(classesByPackage)
            .flat()
            .filter((cls) => cls.selected === true)
            .map((cls) => ({
                uuid: cls.uuid,
                graphUri: lockedGraphUri
            }));
        const diagramData = {
            diagramId: diagramId,
            name: diagramName,
            classes: selectedClassList
        };

        try {
            const res = await bec.putCustomDiagram(lockedDatasetName, lockedGraphUri, diagramId, diagramData);

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
            {#each packages as pack (pack.uuid)}
                <PackageSelectSection
                    {pack}
                    classes={classesByPackage[pack.uuid] ?? []}
                />
            {/each}
        </div>
    </div>
</ActionDialog>