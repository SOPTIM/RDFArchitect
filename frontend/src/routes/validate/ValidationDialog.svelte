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
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import FileSelectButton from "$lib/components/FileSelectButton.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { CGMESVersion } from "$lib/models/cgmes-constants.js";
    import { editorState, validationState } from "$lib/sharedState.svelte.js";

    import { goto } from "$app/navigation";

    let {
        showDialog = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const ValidationMode = Object.freeze({
        STORED: 0,
        FILE: 1,
    });

    let validationMode = $state(ValidationMode.STORED);

    let dataset = $state(null);
    let graph = $state(null);
    let file = $state(null);
    let cgmesVersion = $state(CGMESVersion.V3_0);

    const validationModeOptions = $derived([
        {
            value: ValidationMode.STORED,
            label: "Stored schema",
            disabled: false,
        },
        {
            value: ValidationMode.FILE,
            label: "Uploaded schema",
            disabled: !!lockedDatasetName || !!lockedGraphUri,
        },
    ]);

    const cgmesVersionOptions = $derived([
        { value: CGMESVersion.V3_0, label: "3.0" },
        { value: CGMESVersion.V2_4_15, label: "2.4.15" },
    ]);

    const cgmesVersionLabel = $derived(
        cgmesVersionOptions.find(o => o.value === cgmesVersion)?.label ?? "",
    );

    const disableSubmit = $derived.by(() => {
        if (validationMode === ValidationMode.FILE) {
            return !file;
        }
        if (validationMode === ValidationMode.STORED) {
            return !dataset || !graph;
        }
        return true;
    });

    function onOpen() {
        dataset = lockedDatasetName ?? editorState.selectedDataset.getValue();
        graph = lockedGraphUri ?? editorState.selectedGraph.getValue();
        file = null;
        cgmesVersion = CGMESVersion.V3_0;
    }

    function onClose() {
        validationMode = ValidationMode.STORED;
        dataset = null;
        graph = null;
        file = null;
        cgmesVersion = CGMESVersion.V3_0;
    }

    function onValidationModeChange(mode) {
        validationMode = mode;
        file = null;
    }

    async function runValidation() {
        let response;
        switch (validationMode) {
            case ValidationMode.FILE:
                response = await bec.validateFile(file, cgmesVersion);
                break;
            case ValidationMode.STORED:
                response = await bec.validateSchema(
                    dataset,
                    graph,
                    cgmesVersion,
                );
                break;
            default:
                throw new Error(`Unknown validationMode: ${validationMode}`);
        }

        const result = await response.json();

        showDialog = false;

        if (!response.ok) {
            console.log(result);
            toastStore.error(
                "Something went wrong while validating the schema.",
            );
            return;
        }

        validationState.result.updateValue(result);
        await goto("/validate");
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Validate"
    onPrimary={runValidation}
    disablePrimary={disableSubmit}
    title={`Validate Schema (CGMES ${cgmesVersionLabel})`}
>
    <div class="mx-2 flex h-full flex-col font-[350]">
        <div class="mb-3">
            <p class="text-text-subtle mt-1 text-sm">
                Select a schema to validate
            </p>
        </div>

        <div class="mx-2 flex h-full flex-col space-y-4">
            <div class="border-border bg-background-subtle rounded border p-3">
                <label for="cgmesVersion" class="mb-1 block text-sm">
                    CGMES Version
                </label>
                <SelectEditControl
                    id="cgmesVersion"
                    options={cgmesVersionOptions}
                    bind:value={cgmesVersion}
                    getOptionValue={o => o.value}
                    getOptionLabel={o => o.label}
                />
            </div>

            <div class="border-border bg-background-subtle rounded border p-3">
                <label for="validationMode" class="mb-1 block text-sm">
                    Schema source
                </label>
                <SelectEditControl
                    id="validationMode"
                    options={validationModeOptions}
                    bind:value={validationMode}
                    getOptionValue={o => o.value}
                    getOptionLabel={o => o.label}
                    getOptionIsDisabled={o => o.disabled}
                    onchange={value => onValidationModeChange(Number(value))}
                />
            </div>

            {#if validationMode === ValidationMode.STORED}
                <div class="flex flex-col gap-1.5">
                    <span class="text-text-subtle px-1 text-xs font-medium">
                        Schema
                    </span>
                    <DatasetAndGraphSelection
                        bind:dataset
                        bind:graph
                        {lockedDatasetName}
                        {lockedGraphUri}
                    />
                </div>
            {/if}

            {#if validationMode === ValidationMode.FILE}
                <div class="flex flex-col gap-1.5">
                    <span class="text-text-subtle px-1 text-xs font-medium">
                        Schema
                    </span>
                    <div
                        class="border-border bg-background-subtle rounded border p-3"
                    >
                        <FileSelectButton bind:file />
                    </div>
                </div>
            {/if}
        </div>
    </div>
</ActionDialog>
