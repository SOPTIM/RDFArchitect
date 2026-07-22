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
    import { UserSettingsComponents as USC } from "$lib/components/bitsui/usersettings/index.js";
    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import ModifyDataDialog from "$lib/dialog/ModifyDataDialog.svelte";
    import { isInvalidCornerRoundingFactor } from "$lib/models/reactive/validity-rules/validityFunctions.js";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { userSettings } from "$lib/userSettings.svelte.js";
    import { supportedRDFMediaTypes } from "$lib/utils/fileUtils.ts";

    let { showDialog = $bindable() } = $props();

    const DEFAULT_SETTINGS = {
        usePackagePrefix: false,
        defaultExportFormat: supportedRDFMediaTypes[0].mimeType,
        showPackagePrefix: false,
        useColoredPropertiesInMergedView: true,
        normalizeComments: true,
        useRoundedEdges: false,
        cornerRoundingFactor: 50,
    };

    let localSettings = $state({});
    let savedSettings = $derived({ ...DEFAULT_SETTINGS, ...userSettings.all });
    let isModified = $derived(
        JSON.stringify(localSettings) !== JSON.stringify(savedSettings),
    );

    let cornerRoundingFactorViolations = $derived(
        localSettings["useRoundedEdges"]
            ? isInvalidCornerRoundingFactor(
                  localSettings["cornerRoundingFactor"],
              )
            : [],
    );

    let hasInvalidInput = $derived(cornerRoundingFactorViolations.length > 0);

    function onOpen() {
        localSettings = { ...DEFAULT_SETTINGS, ...userSettings.all };
    }

    function onClose() {
        showDialog = false;
    }

    function save() {
        for (const [key, value] of Object.entries(localSettings)) {
            userSettings.set(key, value);
        }
        showDialog = false;
        forceReloadTrigger.trigger();
    }
</script>

<ModifyDataDialog
    bind:showDialog
    {onOpen}
    {onClose}
    saveChanges={save}
    discardChanges={() =>
        (localSettings = { ...DEFAULT_SETTINGS, ...userSettings.all })}
    hasChanges={isModified && !hasInvalidInput}
    size="w-1/3"
    title="Settings"
>
    <div class="mx-2 flex h-80 flex-col gap-4 overflow-y-auto py-2">
        <USC.Section title="Export">
            <CheckBoxEditControl
                label="Use 'Package_' prefix"
                bind:value={localSettings["usePackagePrefix"]}
                labelFirst={false}
            />
            <USC.Item.SingleSelect
                label="Default Export Format"
                options={supportedRDFMediaTypes}
                getOptionLabel={v => v.name}
                getOptionValue={v => v.mimeType}
                value={localSettings["defaultExportFormat"]}
                onChange={v => (localSettings["defaultExportFormat"] = v)}
            />
        </USC.Section>
        <USC.Section title="Visualization">
            <CheckBoxEditControl
                label="Show 'Package_' prefix"
                bind:value={localSettings["showPackagePrefix"]}
                labelFirst={false}
            />
            <CheckBoxEditControl
                label="Use colored properties in merged view"
                bind:value={localSettings["useColoredPropertiesInMergedView"]}
                labelFirst={false}
            />
            <CheckBoxEditControl
                label="Smoothed edges"
                bind:value={localSettings["useRoundedEdges"]}
                labelFirst={false}
                title={"When enabled, the sharp corners of edges at their bend \n" +
                    "points are rounded off. The edges stay polylines with \n" +
                    "straight segments, only the corners are smoothed."}
            />
            <div class="flex flex-col gap-1">
                <TextEditControl
                    label="Corner rounding (%)"
                    bind:value={localSettings["cornerRoundingFactor"]}
                    placeholder="50"
                    disabled={!localSettings["useRoundedEdges"]}
                    warn={hasInvalidInput}
                    title={"Controls how strongly the edge corners are \n" +
                        "rounded, as a percentage from 0 to 100. \n" +
                        "0 keeps the corners sharp, 100 applies the maximum \n" +
                        "rounding. The edges always stay polylines, this only \n" +
                        "affects how soft the corners at the bend points look."}
                />
                <ViolationMessages
                    violations={cornerRoundingFactorViolations}
                />
            </div>
        </USC.Section>
        <USC.Section title="Normalization">
            <CheckBoxEditControl
                label="Normalize comments to xsd:string"
                bind:value={localSettings["normalizeComments"]}
                labelFirst={false}
            />
        </USC.Section>
    </div>
</ModifyDataDialog>
