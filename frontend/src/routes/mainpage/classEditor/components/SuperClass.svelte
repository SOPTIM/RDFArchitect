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
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/utils/reactive-objects-control-button-utils.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    let { superClass } = $props();

    const classEditorContext = getContext("classEditor");
    const id = uuid();

    let classes = $derived(classEditorContext.classes);
    let readonly = $derived(classEditorContext.readonly);

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        readonly = classEditorContext.readonly;
    });

    $effect(() => {
        editorState.selectedContext.subscribe();
        classes = classEditorContext.classes;
    });

    onMount(() => {
        readonly = classEditorContext.readonly;
        classes = classEditorContext.classes;
    });

    function getSuperClassLabel(superClassUuid) {
        const cls = classEditorContext.getClassByUuid(superClassUuid);
        return cls ? cls.label : superClassUuid;
    }
</script>

<tr>
    <td class="text-blue pt-1 align-top whitespace-nowrap">
        <label for={id}>Derived from</label>
    </td>
    <td class="flex w-full flex-col space-x-1">
        <SearchableSelect
            {id}
            value={getSuperClassLabel(superClass.value)}
            highlight={superClass.isModified}
            warn={!superClass.isValid}
            optionObjectList={classes}
            accessDisplayData={cls => cls.label}
            accessIdentifier={cls =>
                classEditorContext.getSubstitutedNamespace(cls.prefix) +
                ":" +
                cls.label}
            callOnValidChange={newSuperClass =>
                (superClass.value = newSuperClass.uuid)}
            {readonly}
            buttons={getControlButtonsForReactiveObject(superClass, readonly)}
        />
        <ViolationMessages violations={superClass.violations} />
    </td>
</tr>
