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
    const classes = classEditorContext.classes;
</script>

<div class="contents">
    <!-- Row 1: Title -->
    <div class="col-start-1 row-start-1">
        <span class="text-lg">
            Association
            <b>
                {classEditorContext.getClassByUuid(association.domain.value)
                    ?.label}
            </b>
            to
            <b>
                {classEditorContext.getClassByUuid(association.target.value)
                    ? classEditorContext.getClassByUuid(
                          association.target.value,
                      ).label
                    : "not yet defined"}
            </b>
        </span>
    </div>

    <!-- Row 2: Namespace -->
    <div class="col-start-1 row-start-3 pl-2">
        <span class="mb-1">Namespace:</span>
        <SearchableSelect
            placeholder="namespace..."
            value={classEditorContext.getSubstitutedNamespace(
                association.namespace.value,
            )}
            optionObjectList={classEditorContext.namespaces}
            accessDisplayData={namespace => namespace.substitutedPrefix}
            accessIdentifier={getNsPrefixNsUriString}
            callOnValidChange={newNamespace =>
                (association.namespace.value = newNamespace?.prefix)}
            highlight={association.namespace.isModified}
            warn={!association.namespace.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                association.namespace,
                readonly,
            )}
            tooltip={association.namespace.value}
        />
        <ViolationMessages violations={association.namespace.violations} />
    </div>

    <!-- Row 3: Label -->
    <div class="col-start-1 row-start-4 pl-2">
        <TextEditControl
            label="Label:"
            placeholder="association label..."
            bind:value={association.label.value}
            highlight={association.label.isModified}
            warn={!association.label.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                association.label,
                readonly,
            )}
        />
        <ViolationMessages violations={association.label.violations} />
    </div>

    <!-- Row 4: Target -->
    <div class="col-start-1 row-start-5 pl-2">
        <SearchableSelect
            label="Target:"
            placeholder="Target"
            value={classEditorContext.getClassByUuid(association.target.value)
                ?.label}
            optionObjectList={classes}
            accessDisplayData={cls => {
                return cls.label;
            }}
            accessIdentifier={cls =>
                classEditorContext.getSubstitutedNamespace(cls.prefix) +
                ":" +
                cls.label}
            callOnValidChange={newTarget =>
                (association.target.value = newTarget.uuid)}
            highlight={association.target.isModified}
            warn={!association.target.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                association.target,
                readonly,
            )}
            tooltip={association.target.value}
        />
        <ViolationMessages violations={association.target.violations} />
    </div>

    <!-- Row 5: Multiplicity LowerBound -->
    <div class="col-start-1 row-start-6 pl-2">
        <NumberInputControl
            label="Multiplicity LowerBound:"
            placeholder="multiplicity LowerBound..."
            bind:value={association.multiplicityLowerBound.value}
            highlight={association.multiplicityLowerBound.isModified}
            warn={!association.multiplicityLowerBound.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                association.multiplicityLowerBound,
                readonly,
            )}
        />
        <ViolationMessages
            violations={association.multiplicityLowerBound.violations}
        />
    </div>

    <!-- Row 6: Multiplicity UpperBound -->
    <div class="col-start-1 row-start-7 pl-2">
        <NumberInputControl
            label="Multiplicity UpperBound:"
            placeholder="multiplicity UpperBound..."
            bind:value={association.multiplicityUpperBound.value}
            highlight={association.multiplicityUpperBound.isModified}
            warn={!association.multiplicityUpperBound.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                association.multiplicityUpperBound,
                readonly,
            )}
        />
        <ViolationMessages
            violations={association.multiplicityUpperBound.violations}
        />
    </div>

    <!-- Row 7: Use association checkbox -->
    <div class="col-start-1 row-start-8 pl-2">
        <div class="relative flex items-end space-x-1">
            <CheckBoxEditControl
                label="Use association?"
                bind:value={association.isUsed.value}
                highlight={association.isUsed.isModified}
                warn={!association.isUsed.isValid}
                {readonly}
                buttons={getControlButtonsForReactiveObject(
                    association.isUsed,
                    readonly,
                )}
            />
        </div>
        <ViolationMessages violations={association.isUsed.violations} />
    </div>

    <!-- Row 8: Comment -->
    <div class="col-start-1 row-start-9 pl-2">
        <label for="association-edit-dialog-direct-comment-text-area">
            Comment:
        </label>
        <TextAreaControl
            id="association-edit-dialog-direct-comment-text-area"
            placeholder="comment..."
            bind:value={association.comment.value}
            highlight={association.comment.isModified}
            warn={!association.comment.isValid}
            {readonly}
            buttons={getControlButtonsForReactiveObject(
                association.comment,
                readonly,
            )}
        />
        <ViolationMessages violations={association.comment.violations} />
    </div>
</div>
