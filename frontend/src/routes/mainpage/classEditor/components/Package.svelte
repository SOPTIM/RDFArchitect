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
    import { getContext, onMount } from "svelte";
    import { v4 as uuid } from "uuid";

    import SearchableSelect from "$lib/components/SearchableSelect.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/reactive-utils.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    let { pack } = $props();

    const classEditorContext = getContext("classEditor");
    const packages = classEditorContext.packages;
    const id = uuid();
    let readonly = $state(false);

    onMount(() => readonly = classEditorContext.readonly);

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        readonly = classEditorContext.readonly;
    })

    function getPackageLabel(packageUUID) {
        const pkg = classEditorContext.getPackageByUuid(packageUUID);
        return pkg ? pkg.label : packageUUID;
    }
</script>

<tr>
    <td class="text-blue pt-1 align-top whitespace-nowrap">
        <label for={id}>Package</label>
    </td>
    <td class="flex w-full flex-col space-x-1">
        <SearchableSelect
            {id}
            placeholder="default"
            value={getPackageLabel(pack.value)}
            highlight={pack.isModified}
            warn={!pack.isValid}
            optionObjectList={packages}
            accessDisplayData={pack => pack.label}
            accessIdentifier={pack =>
                (pack.prefix
                    ? classEditorContext.getSubstitutedNamespace(pack.prefix)
                    : "") + pack.label}
            callOnValidChange={newPack =>
                (pack.value = newPack ? newPack.uuid : null)}
            {readonly}
            buttons={getControlButtonsForReactiveObject(pack, readonly)}
        />
        <ViolationMessages violations={pack.violations} />
    </td>
</tr>
