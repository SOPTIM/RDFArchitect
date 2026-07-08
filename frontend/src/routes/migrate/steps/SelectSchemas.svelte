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
    import { faRightLeft } from "@fortawesome/free-solid-svg-icons";
    import { onMount } from "svelte";
    import { get } from "svelte/store";
    import { Fa } from "svelte-fa";

    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import FileSelectButton from "$lib/components/FileSelectButton.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { CGMESVersion } from "$lib/models/cgmes-constants.js";
    import { migrationState } from "$lib/sharedState.svelte.js";

    let { disableNext = $bindable() } = $props();

    const CompareMode = Object.freeze({
        STORED_TO_STORED: 0,
        FILE_TO_STORED: 1,
        FILE_TO_FILE: 2,
        STORED_TO_FILE: 3,
    });

    let compareMode = $state(CompareMode.FILE_TO_STORED);
    let ignorePrefixes = $state(false);
    let cgmesVersionA = $state(CGMESVersion.V3_0);
    let cgmesVersionB = $state(CGMESVersion.V3_0);

    let datasetA = $state(null);
    let graphA = $state(null);

    let datasetB = $state(null);
    let graphB = $state(null);

    let fileA = $state(null);
    let fileB = $state(null);

    const compareModeOptions = $derived([
        {
            value: CompareMode.STORED_TO_STORED,
            label: "Stored → Stored",
            disabled: false,
        },
        {
            value: CompareMode.FILE_TO_STORED,
            label: "Uploaded → Stored",
            disabled: false,
        },
        {
            value: CompareMode.STORED_TO_FILE,
            label: "Stored → Uploaded",
            disabled: false,
        },
        {
            value: CompareMode.FILE_TO_FILE,
            label: "Uploaded → Uploaded",
            disabled: false,
        },
    ]);

    const cgmesVersionOptions = $derived([
        { value: CGMESVersion.V3_0, label: "3.0" },
        { value: CGMESVersion.V2_4_15, label: "2.4.15" },
    ]);

    $effect(() => {
        if (compareMode === CompareMode.FILE_TO_FILE) {
            disableNext = !fileA || !fileB;
        }

        if (compareMode === CompareMode.FILE_TO_STORED) {
            disableNext = !fileA || !datasetB || !graphB;
        }

        if (compareMode === CompareMode.STORED_TO_FILE) {
            disableNext = !datasetA || !graphA || !fileA;
        }

        if (compareMode === CompareMode.STORED_TO_STORED) {
            disableNext = !datasetA || !graphA || !datasetB || !graphB;
        }
    });

    onMount(async () => {
        let storedState = get(migrationState);

        compareMode = storedState.compareMode ?? CompareMode.FILE_TO_STORED;
        cgmesVersionA = storedState.cgmesVersionA ?? CGMESVersion.V3_0;
        cgmesVersionB = storedState.cgmesVersionB ?? CGMESVersion.V3_0;
        datasetA = storedState.datasetA;
        graphA = storedState.graphA;
        datasetB = storedState.datasetB;
        graphB = storedState.graphB;
        fileA = storedState.fileA;
        fileB = storedState.fileB;
        ignorePrefixes = storedState.ignorePrefixes ?? false;
    });

    function onCompareModeChange() {
        datasetB = null;
        graphB = null;

        fileA = null;
        fileB = null;
    }

    function swapSelections() {
        if (compareMode === CompareMode.STORED_TO_STORED) {
            [datasetA, datasetB] = [datasetB, datasetA];
            [graphA, graphB] = [graphB, graphA];
        } else if (compareMode === CompareMode.FILE_TO_FILE) {
            [fileA, fileB] = [fileB, fileA];
        } else if (compareMode === CompareMode.FILE_TO_STORED) {
            datasetA = datasetB;
            graphA = graphB;
            datasetB = null;
            graphB = null;
            compareMode = CompareMode.STORED_TO_FILE;
        } else if (compareMode === CompareMode.STORED_TO_FILE) {
            datasetB = datasetA;
            graphB = graphA;
            datasetA = null;
            graphA = null;
            compareMode = CompareMode.FILE_TO_STORED;
        }

        // The version stays tied to its schema, so it swaps along with it.
        [cgmesVersionA, cgmesVersionB] = [cgmesVersionB, cgmesVersionA];
    }

    export async function onNext() {
        let body = new FormData();

        if (compareMode === CompareMode.STORED_TO_STORED) {
            body.append("datasetA", datasetA);
            body.append("graphA", graphA);
            body.append("datasetB", datasetB);
            body.append("graphB", graphB);
        } else if (compareMode === CompareMode.FILE_TO_STORED) {
            body.append("fileA", fileA);
            body.append("datasetB", datasetB);
            body.append("graphB", graphB);
        } else if (compareMode === CompareMode.STORED_TO_FILE) {
            body.append("datasetA", datasetA);
            body.append("graphA", graphA);
            body.append("fileA", fileA);
        } else if (compareMode === CompareMode.FILE_TO_FILE) {
            body.append("fileA", fileA);
            body.append("fileB", fileB);
        }

        body.append("ignorePrefixes", ignorePrefixes);

        let url = `${PUBLIC_BACKEND_URL}/migrations/context`;
        try {
            let res = await fetch(url, {
                method: "POST",
                body: body,
                credentials: "include",
            });
            if (res.ok) {
                console.log("established migration context in backend");
                migrationState.set({
                    compareMode,
                    cgmesVersionA,
                    cgmesVersionB,
                    datasetA,
                    graphA,
                    datasetB,
                    graphB,
                    fileA,
                    fileB,
                    ignorePrefixes,
                });
            } else {
                toastStore.error(
                    "Migration setup failed",
                    "Could not establish the migration context.",
                );
            }
        } catch (e) {
            console.log("failed to establish migration context:");
            console.log(e);
            toastStore.error(
                "Migration setup failed",
                "Could not establish the migration context.",
            );
        }
    }
</script>

<div class="text-default-text flex h-full flex-col space-y-8 p-2">
    <InfoBox type="info">
        <p>
            Please select the two schemas you want to migrate between. You can
            either migrate between two schemas that are already stored in the
            system, or you can upload one or two files containing the schema(s)
            you want to migrate.
        </p>
    </InfoBox>

    <div class="flex h-full flex-col space-y-4">
        <div class="flex-col space-y-3 border-border bg-background-subtle rounded border p-3">
            <label for="compareMode" class="mb-1 block text-sm">
                Comparison type
            </label>
            <SelectEditControl
                id="compareMode"
                options={compareModeOptions}
                bind:value={compareMode}
                getOptionValue={o => o.value}
                getOptionLabel={o => o.label}
                onchange={onCompareModeChange}
            />
            <div class="flex space-x-2">
                <label for="ignorePrefixes" class="mb-1 block text-sm">
                    Ignore prefixes
                </label>
                <CheckBoxEditControl
                    id="ignorePrefixes"
                    bind:value={ignorePrefixes}
                />
            </div>
        </div>

        {#if compareMode === CompareMode.STORED_TO_STORED}
            <div class="flex flex-col gap-1.5">
                <span class="text-text-subtle px-1 text-xs font-medium">
                    Before
                </span>
                <DatasetAndGraphSelection
                    bind:dataset={datasetA}
                    bind:graph={graphA}
                />
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <label for="cgmesVersionA" class="mb-1 block text-sm">
                        CGMES Version
                    </label>
                    <SelectEditControl
                        id="cgmesVersionA"
                        options={cgmesVersionOptions}
                        bind:value={cgmesVersionA}
                        getOptionValue={o => o.value}
                        getOptionLabel={o => o.label}
                    />
                </div>
            </div>

            <div class="flex items-center gap-3">
                <div class="bg-border h-px w-full"></div>
                <button
                    onclick={swapSelections}
                    class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs text-nowrap transition-colors"
                    title="Swap Before and After"
                >
                    <Fa icon={faRightLeft} />
                    Swap
                </button>
                <div class="bg-border h-px w-full"></div>
            </div>

            <div class="flex flex-col gap-1.5">
                <span class="text-text-subtle px-1 text-xs font-medium">
                    After
                </span>
                <DatasetAndGraphSelection
                    bind:dataset={datasetB}
                    bind:graph={graphB}
                />
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <label for="cgmesVersionB" class="mb-1 block text-sm">
                        CGMES Version
                    </label>
                    <SelectEditControl
                        id="cgmesVersionB"
                        options={cgmesVersionOptions}
                        bind:value={cgmesVersionB}
                        getOptionValue={o => o.value}
                        getOptionLabel={o => o.label}
                    />
                </div>
            </div>
        {/if}

        {#if compareMode === CompareMode.FILE_TO_STORED}
            <div class="flex flex-col gap-1.5">
                <span class="text-text-subtle px-1 text-xs font-medium">
                    Before
                </span>
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <FileSelectButton bind:file={fileA} />
                </div>
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <label for="cgmesVersionA" class="mb-1 block text-sm">
                        CGMES Version
                    </label>
                    <SelectEditControl
                        id="cgmesVersionA"
                        options={cgmesVersionOptions}
                        bind:value={cgmesVersionA}
                        getOptionValue={o => o.value}
                        getOptionLabel={o => o.label}
                    />
                </div>
            </div>

            <div class="flex items-center gap-3">
                <div class="bg-border h-px w-full"></div>
                <button
                    onclick={swapSelections}
                    class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs text-nowrap transition-colors"
                    title="Swap Before and After"
                >
                    <Fa icon={faRightLeft} />
                    Swap
                </button>
                <div class="bg-border h-px w-full"></div>
            </div>

            <div class="flex flex-col gap-1.5">
                <span class="text-text-subtle px-1 text-xs font-medium">
                    After
                </span>
                <DatasetAndGraphSelection
                    bind:dataset={datasetB}
                    bind:graph={graphB}
                />
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <label for="cgmesVersionB" class="mb-1 block text-sm">
                        CGMES Version
                    </label>
                    <SelectEditControl
                        id="cgmesVersionB"
                        options={cgmesVersionOptions}
                        bind:value={cgmesVersionB}
                        getOptionValue={o => o.value}
                        getOptionLabel={o => o.label}
                    />
                </div>
            </div>
        {/if}

        {#if compareMode === CompareMode.STORED_TO_FILE}
            <div class="flex flex-col gap-1.5">
                <span class="text-text-subtle px-1 text-xs font-medium">
                    Before
                </span>
                <DatasetAndGraphSelection
                    bind:dataset={datasetA}
                    bind:graph={graphA}
                />
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <label for="cgmesVersionA" class="mb-1 block text-sm">
                        CGMES Version
                    </label>
                    <SelectEditControl
                        id="cgmesVersionA"
                        options={cgmesVersionOptions}
                        bind:value={cgmesVersionA}
                        getOptionValue={o => o.value}
                        getOptionLabel={o => o.label}
                    />
                </div>
            </div>

            <div class="flex items-center gap-3">
                <div class="bg-border h-px w-full"></div>
                <button
                    onclick={swapSelections}
                    class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs text-nowrap transition-colors"
                    title="Swap Before and After"
                >
                    <Fa icon={faRightLeft} />
                    Swap
                </button>
                <div class="bg-border h-px w-full"></div>
            </div>

            <div class="flex flex-col gap-1.5">
                <span class="text-text-subtle px-1 text-xs font-medium">
                    After
                </span>
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <FileSelectButton bind:file={fileA} />
                </div>
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <label for="cgmesVersionB" class="mb-1 block text-sm">
                        CGMES Version
                    </label>
                    <SelectEditControl
                        id="cgmesVersionB"
                        options={cgmesVersionOptions}
                        bind:value={cgmesVersionB}
                        getOptionValue={o => o.value}
                        getOptionLabel={o => o.label}
                    />
                </div>
            </div>
        {/if}

        {#if compareMode === CompareMode.FILE_TO_FILE}
            <div class="flex flex-col gap-1.5">
                <span class="text-text-subtle px-1 text-xs font-medium">
                    Before
                </span>
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <FileSelectButton bind:file={fileA} />
                </div>
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <label for="cgmesVersionA" class="mb-1 block text-sm">
                        CGMES Version
                    </label>
                    <SelectEditControl
                        id="cgmesVersionA"
                        options={cgmesVersionOptions}
                        bind:value={cgmesVersionA}
                        getOptionValue={o => o.value}
                        getOptionLabel={o => o.label}
                    />
                </div>
            </div>

            <div class="flex items-center gap-3">
                <div class="bg-border h-px w-full"></div>
                <button
                    onclick={swapSelections}
                    class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs text-nowrap transition-colors"
                    title="Swap Before and After"
                >
                    <Fa icon={faRightLeft} />
                    Swap
                </button>
                <div class="bg-border h-px w-full"></div>
            </div>

            <div class="flex flex-col gap-1.5">
                <span class="text-text-subtle px-1 text-xs font-medium">
                    After
                </span>
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <FileSelectButton
                        bind:file={fileB}
                        label="Select second file"
                    />
                </div>
                <div
                    class="border-border bg-background-subtle rounded border p-3"
                >
                    <label for="cgmesVersionB" class="mb-1 block text-sm">
                        CGMES Version
                    </label>
                    <SelectEditControl
                        id="cgmesVersionB"
                        options={cgmesVersionOptions}
                        bind:value={cgmesVersionB}
                        getOptionValue={o => o.value}
                        getOptionLabel={o => o.label}
                    />
                </div>
            </div>
        {/if}
    </div>
</div>
