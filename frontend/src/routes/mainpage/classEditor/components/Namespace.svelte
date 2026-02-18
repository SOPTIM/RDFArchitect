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
    import { getContext } from "svelte";
    import { v4 as uuid } from "uuid";

    import SearchableSelect from "$lib/components/SearchableSelect.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/reactive-utils.js";
    import { getNsPrefixNsUriString } from "$lib/utils/namespace.js";

    let { namespace } = $props();

    const classEditorContext = getContext("classEditor");
    const readonly = classEditorContext.readonly;
    const namespaces = classEditorContext.namespaces;
    const reactiveClass = classEditorContext.reactiveClass;
    const id = uuid();

    function trickleDownNamespaceChange(newNamespaceUri) {
        const oldNamespaceUri = namespace.value;
        namespace.value = newNamespaceUri;
        replaceNamespaceIfMatching(
            reactiveClass.attributes,
            oldNamespaceUri,
            newNamespaceUri,
        );
        replaceNamespaceIfMatching(
            reactiveClass.associations,
            oldNamespaceUri,
            newNamespaceUri,
        );
        replaceNamespaceIfMatching(
            reactiveClass.enumEntries,
            oldNamespaceUri,
            newNamespaceUri,
        );
    }

    function replaceNamespaceIfMatching(
        reactiveObjectsArray,
        oldNamespaceUri,
        newNamespace,
    ) {
        reactiveObjectsArray.values.forEach(obj => {
            if (obj.namespace.value === oldNamespaceUri) {
                obj.namespace.value = newNamespace;
            }
        });
    }
</script>

<tr>
    <td class="text-blue pt-1 align-top whitespace-nowrap">
        <label for={id}>Namespace</label>
    </td>
    <td class="flex w-full flex-col space-x-1">
        <SearchableSelect
            {id}
            value={classEditorContext.getSubstitutedNamespace(namespace.value)}
            highlight={namespace.isModified}
            warn={!namespace.isValid}
            optionObjectList={namespaces}
            accessDisplayData={namespace => namespace.substitutedPrefix}
            accessIdentifier={getNsPrefixNsUriString}
            callOnValidChange={namespace =>
                trickleDownNamespaceChange(namespace.prefix)}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                namespace,
                readonly,
                trickleDownNamespaceChange,
            )}
            tooltip={namespace.value}
        />
        <ViolationMessages violations={namespace.violations} />
    </td>
</tr>
