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

    let localSettings = $state({});
    let isModified = $derived(
        JSON.stringify(localSettings) !== JSON.stringify(userSettings.all),
    );

    function onOpen() {
        localSettings = { ...userSettings.all };
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
    discardChanges={() => (localSettings = { ...userSettings.all })}
    hasChanges={isModified}
    size="w-1/3"
    title="Settings"
>
    <div class="mx-2 flex h-80 flex-col gap-4 overflow-y-auto py-2">
        <USC.Section title="Export">
            <CheckBoxEditControl
                label="Use 'Package_' prefix"
                value={localSettings["usePackagePrefix"]}
                callOnInputTrue={() =>
                    (localSettings["usePackagePrefix"] = true)}
                callOnInputFalse={() =>
                    (localSettings["usePackagePrefix"] = false)}
                labelFirst={false}
            />
            <USC.Item.SingleSelect
                label="Default Export Format"
                options={supportedRDFMediaTypes}
                getOptionLabel={v => v.name}
                getOptionValue={v => v.mimeType}
                value={localSettings["defaultExportFormat"] ??
                    supportedRDFMediaTypes[0].mimeType}
                onChange={v => (localSettings["defaultExportFormat"] = v)}
            />
        </USC.Section>
        <USC.Section title="Visualization">
            <CheckBoxEditControl
                label="Show 'Package_' Prefix"
                value={localSettings["showPackagePrefix"] ?? false}
                callOnInputTrue={() =>
                    (localSettings["showPackagePrefix"] = true)}
                callOnInputFalse={() =>
                    (localSettings["showPackagePrefix"] = false)}
                labelFirst={false}
            />
        </USC.Section>
        <USC.Section title="Normalization">
            <CheckBoxEditControl
                label="Normalize comments to xsd:String"
                value={localSettings["normalizeComments"] ?? true}
                callOnInputTrue={() =>
                    (localSettings["normalizeComments"] = true)}
                callOnInputFalse={() =>
                    (localSettings["normalizeComments"] = false)}
                labelFirst={false}
            />
        </USC.Section>
    </div>
</ModifyDataDialog>
