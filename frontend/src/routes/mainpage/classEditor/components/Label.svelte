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

    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/reactive-utils.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    let { label } = $props();

    const id = uuid();

    const classEditorContext = getContext("classEditor");
    let readonly = $state(false);

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        readonly = classEditorContext.readonly;
    });

    onMount(() => (readonly = classEditorContext.readonly));
</script>

<tr>
    <td class="text-blue pt-1 align-top whitespace-nowrap">
        <label for={id}>Label</label>
    </td>

    <td class="w-full align-top">
        <div class="flex flex-col space-y-1">
            <TextEditControl
                {id}
                bind:value={label.value}
                highlight={label.isModified}
                warn={!label.isValid}
                {readonly}
                buttons={getControlButtonsForReactiveObject(label, readonly)}
            />
            <ViolationMessages violations={label.violations} />
        </div>
    </td>
</tr>
