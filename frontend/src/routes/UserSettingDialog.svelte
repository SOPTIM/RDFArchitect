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
    import ModifyDataDialog from "$lib/dialog/ModifyDataDialog.svelte";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { userSettings } from "$lib/userSettings.svelte.js";
    import { supportedRDFMediaTypes } from "$lib/utils/fileUtils.ts";

    let { showDialog = $bindable() } = $props();

    const DEFAULT_SETTINGS = {
        usePackagePrefix: false,
        defaultExportFormat: supportedRDFMediaTypes[0].mimeType,
        showPackagePrefix: false,
        useColoredPropertiesInMergedView: true,
        showInheritedProperties: true,
        normalizeComments: true,
    };

    let localSettings = $state({});
    let savedSettings = $derived({ ...DEFAULT_SETTINGS, ...userSettings.all });
    let isModified = $derived(
        JSON.stringify(localSettings) !== JSON.stringify(savedSettings),
    );

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
    hasChanges={isModified}
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
                label="Show inherited properties"
                bind:value={localSettings["showInheritedProperties"]}
                labelFirst={false}
            />
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
