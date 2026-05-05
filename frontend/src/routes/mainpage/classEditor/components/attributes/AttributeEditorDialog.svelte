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

    import NumberInputControl from "$lib/components/NumberInputControl.svelte";
    import SearchableSelect from "$lib/components/SearchableSelect.svelte";
    import TextAreaControl from "$lib/components/TextAreaControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import ModifyDataDialog from "$lib/dialog/ModifyDataDialog.svelte";
    import { mapReactiveAttributeToAttributeDto } from "$lib/models/reactive/mapper/map-reactive-object-to-dto.js";
    import { ReactiveAttribute } from "$lib/models/reactive/models/reactive-attribute.svelte.js";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/utils/reactive-objects-control-button-utils.js";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { getNsPrefixNsUriString } from "$lib/utils/namespace.js";

    import { saveApiAttributeToBackend } from "./save-attribute-to-backend.js";

    let {
        showDialog = $bindable(),
        attribute = $bindable(),
        attributes,
    } = $props();

    let classEditorContext = $state();

    let isNewAttribute = $state(true);
    let readonly = $derived(classEditorContext?.readonly);
    let datatypes = $derived(classEditorContext?.datatypes);

    function onOpen() {
        classEditorContext = getContext("classEditor");
        if (!attributes.contains(attribute)) {
            isNewAttribute = true;
            attribute = new ReactiveAttribute({
                namespace: classEditorContext.reactiveClass.namespace.value,
            });
            attributes.appendClass(attribute);
        } else {
            isNewAttribute = false;
        }
    }

    function onClose() {
        if (isNewAttribute) {
            attributes.remove(attribute);
        }
        isNewAttribute = true;
        attribute = null;
    }

    function getDatatypeLabelByUri(uri) {
        const datatype = classEditorContext.getDatatypeByUri(uri);
        if (!datatype) {
            return uri;
        }
        return datatype.label;
    }

    async function saveAttribute() {
        const apiAttribute = mapReactiveAttributeToAttributeDto(
            attribute,
            classEditorContext.getDatatypeByUri,
            classEditorContext.reactiveClass.namespace.backup +
                classEditorContext.reactiveClass.label.backup,
        );
        const result = await saveApiAttributeToBackend(
            classEditorContext.datasetName,
            classEditorContext.graphUri,
            classEditorContext.reactiveClass.uuid.value,
            apiAttribute,
            isNewAttribute,
        );
        if (!result.ok) {
            return;
        }

        attribute.uuid.value = result.attributeUUID;
        attribute.save();
        if (isNewAttribute) {
            isNewAttribute = false;
        }
        forceReloadTrigger.trigger();
    }
</script>

<ModifyDataDialog
    bind:showDialog
    {onOpen}
    {onClose}
    saveChanges={saveAttribute}
    discardChanges={() => attribute.reset()}
    hasChanges={attribute?.isModified}
    isValid={attribute?.isValid}
    title={isNewAttribute
        ? "Create new attribute"
        : `Edit attribute '${attribute.label.backup}'`}
    {readonly}
>
    {#if attribute && classEditorContext && datatypes && readonly !== undefined}
        <div class="mx-2 flex h-full flex-col space-y-1 pl-2">
            <!-- NAMESPACE -->
            <div>
                <span class="mb-1">Namespace:</span>
                <SearchableSelect
                    placeholder="namespace..."
                    value={classEditorContext.getSubstitutedNamespace(
                        attribute.namespace.value,
                    )}
                    optionObjectList={classEditorContext.namespaces}
                    accessDisplayData={namespace => namespace.substitutedPrefix}
                    accessIdentifier={getNsPrefixNsUriString}
                    callOnValidChange={newNamespace =>
                        (attribute.namespace.value = newNamespace?.prefix)}
                    highlight={attribute.namespace.isModified}
                    warn={!attribute.namespace.isValid}
                    {readonly}
                    buttons={getControlButtonsForReactiveObject(
                        attribute.namespace,
                        readonly,
                    )}
                    tooltip={attribute.namespace.value}
                />
                <ViolationMessages
                    violations={attribute.namespace.violations}
                />
            </div>

            <!-- LABEL -->
            <div>
                <TextEditControl
                    label="Label:"
                    placeholder="attribute label..."
                    bind:value={attribute.label.value}
                    highlight={attribute.label.isModified}
                    warn={!attribute.label.isValid}
                    {readonly}
                    buttons={getControlButtonsForReactiveObject(
                        attribute.label,
                        readonly,
                    )}
                />
                <ViolationMessages violations={attribute.label.violations} />
            </div>

            <!-- DATATYPE -->
            <div>
                <SearchableSelect
                    label="Type:"
                    placeholder="type label..."
                    value={getDatatypeLabelByUri(attribute.datatype.value)}
                    optionObjectList={classEditorContext.datatypes}
                    accessDisplayData={datatype => datatype.label}
                    accessIdentifier={datatype =>
                        classEditorContext.getSubstitutedNamespace(
                            datatype.prefix,
                        ) +
                        ":" +
                        datatype.label}
                    callOnValidChange={newDatatype =>
                        (attribute.datatype.value = newDatatype
                            ? newDatatype.prefix + newDatatype.label
                            : null)}
                    highlight={attribute.datatype.isModified}
                    warn={!attribute.datatype.isValid}
                    {readonly}
                    buttons={getControlButtonsForReactiveObject(
                        attribute.datatype,
                        readonly,
                    )}
                    tooltip={attribute.datatype.value}
                />
                <ViolationMessages violations={attribute.datatype.violations} />
            </div>

            <!-- MULTIPLICITY LOWERBOUND -->
            <div>
                <NumberInputControl
                    label="Multiplicity LowerBound:"
                    placeholder="multiplicity LowerBound..."
                    bind:value={attribute.multiplicityLowerBound.value}
                    highlight={attribute.multiplicityLowerBound.isModified}
                    warn={!attribute.multiplicityLowerBound.isValid}
                    {readonly}
                    buttons={getControlButtonsForReactiveObject(
                        attribute.multiplicityLowerBound,
                        readonly,
                    )}
                />
                <ViolationMessages
                    violations={attribute.multiplicityLowerBound.violations}
                />
            </div>

            <!-- MULTIPLICITY UPPERBOUND -->
            <div>
                <NumberInputControl
                    label="Multiplicity UpperBound:"
                    placeholder="multiplicity UpperBound..."
                    bind:value={attribute.multiplicityUpperBound.value}
                    highlight={attribute.multiplicityUpperBound.isModified}
                    warn={!attribute.multiplicityUpperBound.isValid}
                    {readonly}
                    buttons={getControlButtonsForReactiveObject(
                        attribute.multiplicityUpperBound,
                        readonly,
                    )}
                />
                <ViolationMessages
                    violations={attribute.multiplicityUpperBound.violations}
                />
            </div>

            <!-- FIXED VALUE -->
            <div>
                <TextEditControl
                    label="Fixed Value:"
                    placeholder="fixed value..."
                    bind:value={attribute.fixedValue.value}
                    highlight={attribute.fixedValue.isModified}
                    warn={!attribute.fixedValue.isValid}
                    {readonly}
                    buttons={getControlButtonsForReactiveObject(
                        attribute.fixedValue,
                        readonly,
                    )}
                />
                <ViolationMessages
                    violations={attribute.fixedValue.violations}
                />
            </div>

            <!-- DEFAULT VALUE -->
            <div>
                <TextEditControl
                    label="Default Value:"
                    placeholder="default value..."
                    bind:value={attribute.defaultValue.value}
                    highlight={attribute.defaultValue.isModified}
                    warn={!attribute.defaultValue.isValid}
                    {readonly}
                    buttons={getControlButtonsForReactiveObject(
                        attribute.defaultValue,
                        readonly,
                    )}
                />
                <ViolationMessages
                    violations={attribute.defaultValue.violations}
                />
            </div>

            <!-- COMMENT -->
            <div>
                <label for="attribute-edit-dialog-comment-text-area">
                    Comment:
                </label>
                <TextAreaControl
                    id="attribute-edit-dialog-comment-text-area"
                    placeholder="comment..."
                    bind:value={attribute.comment.value}
                    highlight={attribute.comment.isModified}
                    warn={!attribute.comment.isValid}
                    {readonly}
                    buttons={getControlButtonsForReactiveObject(
                        attribute.comment,
                        readonly,
                    )}
                />
                <ViolationMessages violations={attribute.comment.violations} />
            </div>
        </div>
    {/if}
</ModifyDataDialog>
