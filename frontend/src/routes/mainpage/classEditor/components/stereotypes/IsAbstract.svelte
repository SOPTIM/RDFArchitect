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
    import { v4 as uuidv4 } from "uuid";

    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import { editorState } from "$lib/sharedState.svelte.js";

    const { classStereotypes } = $props();

    const concreteStereotype = "http://iec.ch/TC57/NonStandard/UML#concrete";
    const classEditorContext = getContext("classEditor");
    const id = uuidv4();

    let readonly = $derived(classEditorContext.readonly);

    let isAbstract = $derived(!classStereotypes.contains(concreteStereotype));

    $effect(() => {
        editorState.selectedPackageUUID.subscribe();
        readonly = classEditorContext.readonly;
    });

    onMount(() => {
        readonly = classEditorContext.readonly;
    });
</script>

<tr>
    <td>
        <label class="text-blue text-nowrap" for={id}>Abstract</label>
    </td>
    <td>
        <div class="flex w-full items-center justify-start text-left">
            <CheckBoxEditControl
                {id}
                indicateChanges={isAbstract.isModified}
                value={isAbstract}
                callOnInputTrue={() =>
                    classStereotypes.remove(concreteStereotype)}
                callOnInputFalse={() =>
                    classStereotypes.append(concreteStereotype)}
                disabled={readonly}
            />
        </div>
    </td>
</tr>
