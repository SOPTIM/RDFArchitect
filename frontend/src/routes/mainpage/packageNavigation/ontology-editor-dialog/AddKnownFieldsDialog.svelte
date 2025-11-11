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
    import { getKnownFields } from "$lib/api/apiGlobalUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import Dialog from "$lib/dialog/Dialog.svelte";
    import DialogLeaveButtons from "$lib/dialog/DialogLeaveButtons.svelte";

    let {
        showDialog = $bindable(),
        existingEntries,
        scrollToBottom,
        namespaces,
        dataset,
        graphUri,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    let knownFields = $state([]);
    let generatedEntries = $state([]);

    let addAll = $derived(knownFields.every(entry => entry.toAdd));
    let overrideAll = $derived(
        knownFields.every(
            entry => entry.override === undefined || entry.override,
        ),
    );
    let generateAll = $derived(
        knownFields.every(
            entry => entry.generate === undefined || entry.generate,
        ),
    );

    let disableSubmit = $derived(!knownFields.some(entry => entry.toAdd));

    async function onOpen() {
        knownFields = await getKnownFields();
        generatedEntries = await (
            await bec.generateOntologyEntries(dataset, graphUri)
        ).json();
        setToAdd();
        resetOverride();
        resetGenerate();
    }

    function setToAdd(includeExistingFields = false) {
        for (const knownField of knownFields) {
            knownField.toAdd =
                includeExistingFields || !entryWithIriExists(knownField.iri);
        }
    }

    function resetToAdd() {
        for (const knownField of knownFields) {
            knownField.toAdd = false;
        }
    }

    function setOverride() {
        for (const knownField of knownFields) {
            if (entryWithIriExists(knownField.iri)) {
                knownField.override = true;
            }
        }
    }

    function resetOverride() {
        for (const knownField of knownFields) {
            if (entryWithIriExists(knownField.iri)) {
                knownField.override = false;
            }
        }
    }

    function setGenerate() {
        for (const knownField of knownFields) {
            if (isAutoGeneratableField(knownField.iri)) {
                knownField.generate = true;
            }
        }
    }

    function resetGenerate() {
        for (const knownField of knownFields) {
            if (isAutoGeneratableField(knownField.iri)) {
                knownField.generate = false;
            }
        }
    }

    function addSelectedFieldsToOntologyEntries() {
        for (const knownField of knownFields) {
            if (knownField.toAdd) {
                //remove existing entry if override is set
                if (knownField.override) {
                    for (const entry of existingEntries.values) {
                        if (entry.iri.value === knownField.iri) {
                            existingEntries.remove(entry);
                        }
                    }
                }
                //add generated value if applicable
                let value;
                if (knownField.generate) {
                    let generatedEntryWithSameIri = generatedEntries.find(
                        entry => entry.iri === knownField.iri,
                    );
                    value = generatedEntryWithSameIri?.value;
                }
                //add new entry
                existingEntries.append({
                    ...knownField,
                    value: value,
                });
            }
        }
    }

    function isAutoGeneratableField(fieldIri) {
        return generatedEntries.some(entry => entry.iri === fieldIri);
    }

    function entryWithIriExists(Iri) {
        return existingEntries.values.find(entry => entry.iri.value === Iri);
    }

    function getShortenedIRI(namespaces, iri) {
        if (!iri) return null;
        for (let namespace of namespaces) {
            if (iri.startsWith(namespace.prefix)) {
                return (
                    namespace.substitutedPrefix +
                    iri.substring(namespace.prefix.length)
                );
            }
        }
        return iri;
    }
</script>

<Dialog bind:showDialog {onOpen} onClose={scrollToBottom}>
    <div class="flex flex-col pb-1">
        <div
            class="border-border text-default-text mt-1 overflow-y-auto rounded-lg border-2"
        >
            <table class="w-full border-collapse text-sm">
                <thead
                    class="bg-default-background border-border sticky top-0 z-10 border-b"
                >
                    <tr>
                        <th
                            class="px-2 py-1 text-center tracking-wide uppercase"
                        >
                            <CheckBoxEditControl
                                value={addAll}
                                callOnInputTrue={() => setToAdd(true)}
                                callOnInputFalse={() => {
                                    resetToAdd();
                                }}
                            />
                        </th>
                        <th
                            class="w-50 px-2 py-1 text-left tracking-wide uppercase"
                        >
                            Field iri
                        </th>
                        <th
                            class="py-1 pl-2 text-right tracking-wide uppercase"
                        >
                            Override
                            <CheckBoxEditControl
                                value={overrideAll}
                                callOnInputTrue={() => setOverride()}
                                callOnInputFalse={() => {
                                    resetOverride();
                                }}
                            />
                        </th>
                        <th
                            class="py-1 pr-10 pl-2 text-right tracking-wide uppercase"
                        >
                            autogenerate
                            <CheckBoxEditControl
                                value={generateAll}
                                callOnInputTrue={() => setGenerate()}
                                callOnInputFalse={() => {
                                    resetGenerate();
                                }}
                            />
                        </th>
                    </tr>
                </thead>
                <tbody>
                    {#each knownFields as field}
                        <tr
                            class="border-border hover:bg-button-hover-background not-last:border-b"
                        >
                            <td class="text-center">
                                <div
                                    class="flex h-full items-center justify-center"
                                >
                                    <CheckBoxEditControl
                                        bind:value={field.toAdd}
                                    />
                                </div>
                            </td>
                            <td class="px-2 py-1 text-left">
                                {getShortenedIRI(namespaces, field.iri)}
                            </td>

                            <!-- Override checkbox -->
                            <td class="text-center">
                                {#if entryWithIriExists(field.iri)}
                                    <div
                                        class="flex h-full items-center justify-end"
                                    >
                                        <CheckBoxEditControl
                                            bind:value={field.override}
                                            callOnInputTrue={() =>
                                                (field.toAdd = true)}
                                        />
                                    </div>
                                {/if}
                            </td>
                            <!-- Autogenerate checkbox -->
                            <td class="">
                                {#if isAutoGeneratableField(field.iri)}
                                    <div
                                        class="flex h-full items-center justify-end pr-10"
                                    >
                                        <CheckBoxEditControl
                                            bind:value={field.generate}
                                            callOnInputTrue={() =>
                                                (field.toAdd = true)}
                                        />
                                    </div>
                                {/if}
                            </td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        </div>
    </div>
    <DialogLeaveButtons
        bind:showDialog
        submitLabel="Add Selected Fields"
        onSubmit={() => {
            addSelectedFieldsToOntologyEntries();
            showDialog = false;
        }}
        onCancel={() => {
            showDialog = false;
        }}
        {disableSubmit}
    />
</Dialog>
