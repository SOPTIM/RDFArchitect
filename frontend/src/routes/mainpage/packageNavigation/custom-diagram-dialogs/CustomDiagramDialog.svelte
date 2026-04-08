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
    import PackageSelectSection from "../PackageSelectSection.svelte";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
        diagramName = "",
        diagramId = crypto.randomUUID(),
        selectedClasses = [],
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    let packages = $state([]);
    let classesByPackage = $state({});

    let disableSubmit = $derived(diagramName.trim() === "");

    async function onOpen() {
        await createPackageList(lockedDatasetName, lockedGraphUri);
        await fetchClasses();
        updatePackageSelectionState();
    }

    function onClose() {
        diagramName = null;
    }

    async function getPackages(datasetName, graphURI) {
        const res = await bec.getPackages(datasetName, graphURI);
        return await res.json();
    }

    async function createPackageList(datasetName, graphURI) {
        const res = await getPackages(datasetName, graphURI);

        packages = [
            ...res.internalPackageList,
            ...res.externalPackageList
        ].map(pack => {
            const packageId = getPackageId(pack);

            return {
                uuid: packageId,
                prefix: pack.prefix,
                label: pack.label,
                selected: false,
                expanded: false
            };
        }).sort((a, b) => {
            if (a.label === "default") return 1;
            if (b.label === "default") return -1;
            return a.label.localeCompare(b.label);
        });
    }

    async function getClasses(datasetName, graphURI) {
        const res = await bec.getClasses(datasetName, graphURI);
        return await res.json();
    }

    async function fetchClasses() {
        try {
            const classList = await getClasses(lockedDatasetName, lockedGraphUri) ?? [];

            const grouped = {};

            for (const cls of classList) {
                const packageId = getPackageId(cls.package);
                if (!grouped[packageId]) {
                    grouped[packageId] = [];
                }

                cls.selected = !!selectedClasses.find(selected => selected.uuid === cls.uuid);

                grouped[packageId].push({
                    ...cls,
                    packageUUID: packageId
                });
            }

            for (const key of Object.keys(grouped)) {
                grouped[key].sort((a, b) =>
                    (a.label ?? "").localeCompare(b.label ?? "", undefined, {
                        sensitivity: "base"
                    })
                );
            }

            classesByPackage = grouped;
        } catch (err) {
            console.error("Failed to load classes:", err);
            classesByPackage = {};
        }
    }

    function deselectAll() {
        packages.forEach((pack) => {
            pack.selected = false;
            classesByPackage[getPackageId(pack)]?.forEach((cls) => {
                cls.selected = false;
            });
        })
    }

    function updatePackageSelectionState() {
        if (!selectedClasses.length) {
            return;
        }

        packages.forEach((pack) => {
            const packageId = pack.uuid;
            const classesInPackage = classesByPackage[packageId] ?? [];

            if (classesInPackage.length > 0) {
                pack.expanded = classesInPackage.find(cls => cls.selected) !== undefined;
                pack.selected = classesInPackage.every(cls => cls.selected === true);
            }
        });
    }

    async function submitDiagramClasses() {
        const selectedClassList = Object.values(classesByPackage)
            .flat()
            .filter((cls) => cls.selected === true)
            .map((cls) => ({
                uuid: cls.uuid,
                graphUri: lockedGraphUri,
            }));
        const diagramData = {
            diagramId: diagramId,
            name: diagramName,
            classes: selectedClassList
        }

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
            {#each packages as pack (getPackageId(pack))}
                <PackageSelectSection
                    {pack}
                    classes={classesByPackage[getPackageId(pack)] ?? []}
                />
            {/each}
        </div>
    </div>
</ActionDialog>