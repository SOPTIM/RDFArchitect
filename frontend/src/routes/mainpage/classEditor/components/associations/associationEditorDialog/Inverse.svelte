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

    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import NumberInputControl from "$lib/components/NumberInputControl.svelte";
    import SearchableSelect from "$lib/components/SearchableSelect.svelte";
    import TextAreaControl from "$lib/components/TextAreaControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/utils/reactive-objects-control-button-utils.js";
    import { getNsPrefixNsUriString } from "$lib/utils/namespace.js";

    const { association } = $props();

    const classEditorContext = getContext("classEditor");
    const readonly = classEditorContext.readonly;
</script>

<div class="contents">
    <!-- Row 1: Title -->
    <div class="col-start-2 row-start-1">
        <span class="text-lg">
            Inverse Association
            <b>
                {classEditorContext.getClassByUuid(association.target.value)
                    ? classEditorContext.getClassByUuid(
                          association.target.value,
                      ).label
                    : "not yet defined"}
            </b>
            to
            <b>
                {classEditorContext.getClassByUuid(association.domain.value)
                    .label}
            </b>
        </span>
    </div>

    <!-- Row 2: UUID -->
    <div class="col-start-2 row-start-2 pl-2">
        <span class="mb-1">UUID:</span>
        <p class="w-full">{association.inverse.uuid.value}</p>
    </div>

    <!-- Row 3: Namespace -->
    <div class="col-start-2 row-start-3 pl-2">
        <span class="mb-1">Inverse Namespace:</span>
        <SearchableSelect
            placeholder="namespace..."
            value={classEditorContext.getSubstitutedNamespace(
                association.inverse.namespace.value,
            )}
            optionObjectList={classEditorContext.namespaces}
            accessDisplayData={namespace => namespace.substitutedPrefix}
            accessIdentifier={getNsPrefixNsUriString}
            callOnValidChange={newNamespace =>
                (association.inverse.namespace.value = newNamespace?.prefix)}
            highlight={association.inverse.namespace.isModified}
            warn={!association.inverse.namespace.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                association.inverse.namespace,
                readonly,
            )}
            tooltip={association.inverse.namespace.value}
        />
        <ViolationMessages
            violations={association.inverse.namespace.violations}
        />
    </div>

    <!-- Row 4: Label -->
    <div class="col-start-2 row-start-4 pl-2">
        <TextEditControl
            label="Inverse Label:"
            placeholder="association label..."
            bind:value={association.inverse.label.value}
            highlight={association.inverse.label.isModified}
            warn={!association.inverse.label.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                association.inverse.label,
                readonly,
            )}
        />
        <ViolationMessages violations={association.inverse.label.violations} />
    </div>

    <!-- Row 5: Target (disabled) -->
    <div class="col-start-2 row-start-5 pl-2">
        <TextEditControl
            label="Inverse Target:"
            disabled={true}
            value={classEditorContext.reactiveClass.label.value}
        />
    </div>

    <!-- Row 6: Multiplicity LowerBound -->
    <div class="col-start-2 row-start-6 pl-2">
        <NumberInputControl
            label="Inverse Multiplicity LowerBound:"
            placeholder="multiplicity LowerBound..."
            bind:value={association.inverse.multiplicityLowerBound.value}
            highlight={association.inverse.multiplicityLowerBound.isModified}
            warn={!association.inverse.multiplicityLowerBound.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                association.inverse.multiplicityLowerBound,
                readonly,
            )}
        />
        <ViolationMessages
            violations={association.inverse.multiplicityLowerBound.violations}
        />
    </div>

    <!-- Row 7: Multiplicity UpperBound -->
    <div class="col-start-2 row-start-7 pl-2">
        <NumberInputControl
            label="Inverse Multiplicity UpperBound:"
            placeholder="multiplicity UpperBound..."
            bind:value={association.inverse.multiplicityUpperBound.value}
            highlight={association.inverse.multiplicityUpperBound.isModified}
            warn={!association.inverse.multiplicityUpperBound.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                association.inverse.multiplicityUpperBound,
                readonly,
            )}
        />
        <ViolationMessages
            violations={association.inverse.multiplicityUpperBound.violations}
        />
    </div>

    <!-- Row 8: Use inverse association checkbox -->
    <div class="col-start-2 row-start-8 pl-2">
        <div class="relative flex items-end space-x-1">
            <CheckBoxEditControl
                label="Use inverse association?"
                bind:value={association.inverse.isUsed.value}
                highlight={association.inverse.isUsed.isModified}
                warn={!association.inverse.isUsed.isValid}
                {readonly}
                buttons={getControlButtonsForReactiveObject(
                    association.inverse.isUsed,
                    readonly,
                )}
            />
        </div>
        <ViolationMessages violations={association.inverse.isUsed.violations} />
    </div>

    <!-- Row 9: Comment -->
    <div class="col-start-2 row-start-9 pl-2">
        <label for="association-edit-dialog-inverse-comment-text-area">
            Inverse Comment:
        </label>
        <TextAreaControl
            id="association-edit-dialog-inverse-comment-text-area"
            placeholder="comment..."
            bind:value={association.inverse.comment.value}
            highlight={association.inverse.comment.isModified}
            warn={!association.inverse.comment.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                association.inverse.comment,
                readonly,
            )}
        />
        <ViolationMessages
            violations={association.inverse.comment.violations}
        />
    </div>
</div>
