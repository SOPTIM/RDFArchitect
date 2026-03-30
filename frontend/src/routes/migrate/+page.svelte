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
    import { onDestroy } from "svelte";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { migrationState } from "$lib/sharedState.svelte.js";

    import ConfirmClassRenames from "./steps/ConfirmClassRenames.svelte";
    import ConfirmDefaults from "./steps/ConfirmDefaults.svelte";
    import ConfirmPropertyRenames from "./steps/ConfirmPropertyRenames.svelte";
    import ConfirmAssociationDefaults from "./steps/defaults/ConfirmAssociationDefaults.svelte";
    import ConfirmAttributeDefaults from "./steps/defaults/ConfirmAttributeDefaults.svelte";
    import ConfirmEnumEntryReplacements from "./steps/defaults/ConfirmEnumEntryReplacements.svelte";
    import Export from "./steps/Export.svelte";
    import ConfirmAssociationRenames from "./steps/propertyrenames/ConfirmAssociationRenames.svelte";
    import ConfirmAttributeRenames from "./steps/propertyrenames/ConfirmAttributeRenames.svelte";
    import ConfirmEnumEntryRenames from "./steps/propertyrenames/ConfirmEnumEntryRenames.svelte";
    import SelectSchemas from "./steps/SelectSchemas.svelte";

    const steps = [
        { title: "Step 1: Select Schemas", component: SelectSchemas },
        {
            title: "Step 2: Review Class Renames",
            component: ConfirmClassRenames,
        },
        {
            title: "Step 3: Review Property Renames",
            component: ConfirmPropertyRenames,
            substeps: [
                { title: "Attributes", component: ConfirmAttributeRenames },
                { title: "Associations", component: ConfirmAssociationRenames },
                { title: "Enum Entries", component: ConfirmEnumEntryRenames },
            ],
        },
        {
            title: "Step 4: Review Default Values",
            component: ConfirmDefaults,
            substeps: [
                {
                    title: "Attributes",
                    component: ConfirmAttributeDefaults,
                },
                {
                    title: "Associations",
                    component: ConfirmAssociationDefaults,
                },
                {
                    title: "Enum Entries",
                    component: ConfirmEnumEntryReplacements,
                },
            ],
        },
        { title: "Step 5: Generate Script", component: Export },
    ];

    let disableNext = $state(false);
    let currentStepIndex = $state(0);
    let currentSubstepIndex = $state(0);
    let stepInstance;
    let currentStep = $derived(steps[currentStepIndex]);

    onDestroy(() => {
        migrationState.set({
            compareMode: null,
            datasetA: null,
            graphA: null,
            datasetB: null,
            graphB: null,
            fileA: null,
            fileB: null,
        });

        fetch(PUBLIC_BACKEND_URL + "/migrations/context", {
            method: "DELETE",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
        });
    });

    async function handleContinueButton() {
        if (currentStep.substeps) {
            if (currentSubstepIndex < currentStep.substeps.length - 1) {
                currentSubstepIndex++;
                return;
            }
            currentSubstepIndex = 0;
        }

        await stepInstance.onNext();
        disableNext = false;
        currentStepIndex++;
    }

    function handleBackButton() {
        if (currentStep.substeps && currentSubstepIndex > 0) {
            currentSubstepIndex--;
            return;
        }

        if (currentStepIndex > 0) {
            currentStepIndex--;
            currentSubstepIndex = 0;
            disableNext = false;
        }
    }
</script>

<div class="flex h-full flex-col overflow-auto">
    <div
        class="m-auto flex h-9/10 w-4/5 flex-col justify-between space-y-4 rounded-lg bg-white p-6 shadow-md"
    >
        <div class="flex justify-center space-x-4">
            {#each steps as step, i}
                <span
                    class={`px-2 text-sm ${i === currentStepIndex ? "border-b-2 font-bold text-blue-700" : "text-button-disabled-background"}`}
                >
                    {step.title}
                </span>
            {/each}
        </div>

        <div class="h-full overflow-y-scroll">
            <currentStep.component
                bind:this={stepInstance}
                substeps={currentStep.substeps}
                {currentSubstepIndex}
                bind:disableNext
            />
        </div>

        <div class="flex w-full justify-between space-x-4">
            <div class="h-9">
                <ButtonControl
                    callOnClick={handleBackButton}
                    disabled={currentStepIndex === 0}
                >
                    Back
                </ButtonControl>
            </div>
            <div class="h-9">
                <ButtonControl
                    disabled={disableNext}
                    callOnClick={handleContinueButton}
                >
                    {currentStepIndex === steps.length - 1
                        ? "Leave"
                        : "Continue"}
                </ButtonControl>
            </div>
        </div>
    </div>
</div>
