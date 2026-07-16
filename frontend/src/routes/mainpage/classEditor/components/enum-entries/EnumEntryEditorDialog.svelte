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

    import SearchableSelect from "$lib/components/SearchableSelect.svelte";
    import TextAreaControl from "$lib/components/TextAreaControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import ModifyDataDialog from "$lib/dialog/ModifyDataDialog.svelte";
    import { mapReactiveEnumEntryToEnumEntryDto } from "$lib/models/reactive/mapper/map-reactive-object-to-dto.js";
    import { ReactiveEnumEntry } from "$lib/models/reactive/models/reactive-enum-entry.svelte.js";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/utils/reactive-objects-control-button-utils.js";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";
    import { classStore } from "$lib/stores/ClassStore.ts";
    import { getNsPrefixNsUriString } from "$lib/utils/namespace.js";

    let {
        showDialog = $bindable(),
        enumEntry = $bindable(),
        enumEntries,
    } = $props();

    let classEditorContext = $state();

    let isNewEnumEntry = $state(true);
    let readonly = $derived(classEditorContext?.readOnly);

    function onOpen() {
        classEditorContext = getContext("classEditor");
        if (!enumEntries.contains(enumEntry)) {
            isNewEnumEntry = true;
            enumEntry = new ReactiveEnumEntry({
                namespace: classEditorContext.reactiveClass.namespace.value,
            });
        } else {
            isNewEnumEntry = false;
        }
    }

    function onClose() {
        enumEntry = null;
        isNewEnumEntry = true;
    }

    async function saveEnumEntry() {
        const apiEnumEntry = mapReactiveEnumEntryToEnumEntryDto(
            enumEntry,
            classEditorContext.reactiveClass.namespace.backup +
                classEditorContext.reactiveClass.label.backup,
        );

        const { error, data } = isNewEnumEntry
            ? await classStore.addEnumEntry(
                  classEditorContext.datasetName,
                  classEditorContext.graphUri,
                  classEditorContext.reactiveClass.uuid.value,
                  apiEnumEntry,
              )
            : await classStore.replaceEnumEntry(
                  classEditorContext.datasetName,
                  classEditorContext.graphUri,
                  classEditorContext.reactiveClass.uuid.value,
                  apiEnumEntry,
              );

        if (error) return;

        editorState.selectedClassUUID.trigger();
        editorState.selectedDiagram.trigger();

        enumEntry.uuid.value = data;
        enumEntry.save();
        if (isNewEnumEntry) {
            enumEntries.append(enumEntry);
            isNewEnumEntry = false;
        }
        enumEntry.save();
        forceReloadTrigger.trigger();
    }
</script>

<ModifyDataDialog
    bind:showDialog
    {onOpen}
    {onClose}
    saveChanges={saveEnumEntry}
    discardChanges={() => enumEntry.reset()}
    hasChanges={enumEntry?.isModified}
    isValid={enumEntry?.isValid}
    {readonly}
    title={isNewEnumEntry
        ? "Create new Enum entry"
        : `Edit Enum entry: ${enumEntry.label.backup}`}
>
    {#if enumEntry && classEditorContext && readonly !== undefined}
        <div class="mx-2 flex h-full flex-col space-y-1 pl-2">
            <!-- NAMESPACE -->
            <div>
                <span class="mb-1">Namespace:</span>
                <SearchableSelect
                    placeholder="namespace..."
                    value={classEditorContext.getSubstitutedNamespace(
                        enumEntry.namespace.value,
                    )}
                    optionObjectList={classEditorContext.namespaces}
                    accessDisplayData={namespace => {
                        let prefix = namespace.substitutedPrefix;
                        return prefix?.endsWith(":")
                            ? prefix.slice(0, -1)
                            : prefix;
                    }}
                    accessIdentifier={getNsPrefixNsUriString}
                    callOnChange={newNamespace =>
                        (enumEntry.namespace.value =
                            newNamespace?.prefix !== undefined
                                ? newNamespace.prefix
                                : newNamespace)}
                    highlight={enumEntry.namespace.isModified}
                    warn={!enumEntry.namespace.isValid}
                    {readonly}
                    buttons={getControlButtonsForReactiveObject(
                        enumEntry.namespace,
                        readonly,
                    )}
                    tooltip={enumEntry.namespace.value}
                />
                <ViolationMessages
                    violations={enumEntry.namespace.violations}
                />
            </div>

            <!-- LABEL -->
            <div>
                <TextEditControl
                    label="Label:"
                    placeholder="enum entry label..."
                    bind:value={enumEntry.label.value}
                    highlight={enumEntry.label.isModified}
                    warn={!enumEntry.label.isValid}
                    {readonly}
                    buttons={getControlButtonsForReactiveObject(
                        enumEntry.label,
                        readonly,
                    )}
                />
                <ViolationMessages violations={enumEntry.label.violations} />
            </div>

            <!-- COMMENT -->
            <div>
                <label for="enum-entry-edit-dialog-comment-text-area">
                    Comment:
                </label>
                <TextAreaControl
                    id="enum-entry-edit-dialog-comment-text-area"
                    placeholder="comment..."
                    bind:value={enumEntry.comment.value}
                    highlight={enumEntry.comment.isModified}
                    warn={!enumEntry.comment.isValid}
                    {readonly}
                    buttons={getControlButtonsForReactiveObject(
                        enumEntry.comment,
                        readonly,
                    )}
                />
                <ViolationMessages violations={enumEntry.comment.violations} />
            </div>
        </div>
    {/if}
</ModifyDataDialog>
