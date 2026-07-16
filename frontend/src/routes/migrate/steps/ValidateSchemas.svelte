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
    import { onMount } from "svelte";
    import { get } from "svelte/store";

    import { validateFile, validateSchema } from "$lib/api/generated/index.ts";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import ValidationSection from "$lib/components/ValidationSection.svelte";
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

    const CGMES_VERSION_LABELS = {
        [CGMESVersion.V3_0]: "3.0",
        [CGMESVersion.V2_4_15]: "2.4.15",
    };

    let isLoading = $state(true);

    let resultA = $state(null);
    let resultB = $state(null);
    let cgmesVersionLabelA = $state(CGMES_VERSION_LABELS[CGMESVersion.V3_0]);
    let cgmesVersionLabelB = $state(CGMES_VERSION_LABELS[CGMESVersion.V3_0]);

    onMount(async () => {
        const s = get(migrationState);
        const cgmesVersionA = s.cgmesVersionA ?? CGMESVersion.V3_0;
        const cgmesVersionB = s.cgmesVersionB ?? CGMESVersion.V3_0;
        cgmesVersionLabelA = CGMES_VERSION_LABELS[cgmesVersionA];
        cgmesVersionLabelB = CGMES_VERSION_LABELS[cgmesVersionB];

        // Resolve which side is a file and which is stored, per compare mode.
        let sideA;
        let sideB;
        switch (s.compareMode) {
            case CompareMode.STORED_TO_STORED:
                sideA = { dataset: s.datasetA, graph: s.graphA };
                sideB = { dataset: s.datasetB, graph: s.graphB };
                break;
            case CompareMode.FILE_TO_STORED:
                sideA = { file: s.fileA };
                sideB = { dataset: s.datasetB, graph: s.graphB };
                break;
            case CompareMode.STORED_TO_FILE:
                sideA = { dataset: s.datasetA, graph: s.graphA };
                sideB = { file: s.fileA };
                break;
            case CompareMode.FILE_TO_FILE:
                sideA = { file: s.fileA };
                sideB = { file: s.fileB };
                break;
            default:
                sideA = null;
                sideB = null;
        }

        try {
            [resultA, resultB] = await Promise.all([
                sideA
                    ? validateSide(sideA, cgmesVersionA)
                    : Promise.resolve(null),
                sideB
                    ? validateSide(sideB, cgmesVersionB)
                    : Promise.resolve(null),
            ]);
        } catch (e) {
            console.log("Failed to validate schemas:", e);
            toastStore.error("Failed to validate schemas");
        } finally {
            isLoading = false;
        }
    });

    /**
     * Validates one side of the migration based on whether it is a stored
     * schema (dataset + graph) or an uploaded file.
     */
    async function validateSide({ dataset, graph, file }, cgmesVersion) {
        let response;
        if (file) {
            response = await validateFile({
                path: { cgmesVersion: cgmesVersion },
                body: file,
            });
        } else if (dataset && graph) {
            response = await validateSchema({
                path: { datasetName: dataset, graphUri: graph, cgmesVersion: cgmesVersion },
            })
        } else {
            return null;
        }
        return await response.json();
    }

    // Validation is informational only; it never blocks continuing.
    export async function onNext() {}
</script>

<div class="text-default-text flex h-full flex-col space-y-6 p-2">
    <InfoBox type="info">
        <p>
            Here are the validation results for both schemas you selected.
            Review any reported issues before continuing with the migration.
        </p>
    </InfoBox>

    {#if isLoading}
        <div class="flex flex-1 items-center justify-center">
            <LoadingSpinner />
        </div>
    {:else}
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <div class="flex flex-col gap-2">
                <ValidationSection
                    title={`Before (CGMES ${cgmesVersionLabelA})`}
                    result={resultA}
                    errorMessage="No validation result available."
                />
            </div>

            <div class="flex flex-col gap-2">
                <ValidationSection
                    title={`After (CGMES ${cgmesVersionLabelB})`}
                    result={resultB}
                    errorMessage="No validation result available."
                />
            </div>
        </div>
    {/if}
</div>
