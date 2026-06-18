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

    import { computeMigrationContext } from "$lib/api/generated/index.ts";
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import FileSelectButton from "$lib/components/FileSelectButton.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { migrationState } from "$lib/sharedState.svelte.js";

    let { disableNext = $bindable() } = $props();

    const CompareMode = Object.freeze({
        STORED_TO_STORED: 0,
        FILE_TO_STORED: 1,
        FILE_TO_FILE: 2,
        STORED_TO_FILE: 3,
    });

    let compareMode = $state(CompareMode.FILE_TO_STORED);

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
        datasetA = storedState.datasetA;
        graphA = storedState.graphA;
        datasetB = storedState.datasetB;
        graphB = storedState.graphB;
        fileA = storedState.fileA;
        fileB = storedState.fileB;
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

        try {
            let { error } = await computeMigrationContext({ body });
            if (!error) {
                console.log("established migration context in backend");
                migrationState.set({
                    compareMode,
                    datasetA,
                    graphA,
                    datasetB,
                    graphB,
                    fileA,
                    fileB,
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
        <div class="border-border bg-background-subtle rounded border p-3">
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
            </div>

            <div class="flex items-center gap-3">
                <div class="bg-border h-px w-full"></div>
                <button
                    onclick={swapSelections}
                    class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs transition-colors text-nowrap"
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
            </div>

            <div class="flex items-center gap-3">
                <div class="bg-border h-px w-full"></div>
                <button
                    onclick={swapSelections}
                    class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs transition-colors text-nowrap"
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
            </div>

            <div class="flex items-center gap-3">
                <div class="bg-border h-px w-full"></div>
                <button
                    onclick={swapSelections}
                    class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs transition-colors text-nowrap"
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
            </div>

            <div class="flex items-center gap-3">
                <div class="bg-border h-px w-full"></div>
                <button
                    onclick={swapSelections}
                    class="text-text-subtle hover:bg-background-subtle flex items-center gap-1.5 rounded px-2 py-1 text-xs transition-colors text-nowrap"
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
            </div>
        {/if}
    </div>
</div>
