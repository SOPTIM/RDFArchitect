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
    import { faPlus } from "@fortawesome/free-solid-svg-icons";
    import { faRotateLeft, faSave } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import { getNamespaces } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import { DropdownMenu } from "$lib/components/bitsui/dropdown/index.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import SearchableSelect from "$lib/components/SearchableSelect.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import DiscardCancelConfirmDialog from "$lib/dialog/DiscardCancelConfirmDialog.svelte";
    import { ReactiveOntology } from "$lib/models/reactive/models/ontology/reactive-ontology.svelte.js";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";

    import AddKnownFieldsDialog from "./AddKnownFieldsDialog.svelte";
    import OntologyEntryRow from "./OntologyEntryRow.svelte";

    let {
        showDialog = $bindable(),
        dataset,
        graphUri,
        namespaces,
        ontology = $bindable(),
        readonly,
        onSubmit,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let showAddKnownEntriesPopUp = $state(false);
    let showDiscardSaveConfirmDialog = $state(false);

    let tableContainerRef = $state(null);

    let ontologyObject = $state();

    let loadingOntology = $state(false);

    let hasChanges = $derived(ontologyObject?.isModified ?? false);

    let isValid = $derived(ontologyObject?.isValid ?? false);

    let disableSubmit = $derived(!hasChanges || !isValid);

    async function onOpen() {
        if (!namespaces) {
            namespaces = await getNamespaces(dataset);
        }
        if (!ontology) {
            ontologyObject = new ReactiveOntology();
        } else {
            ontologyObject = new ReactiveOntology(
                ontology.uuid,
                ontology.namespace,
                ontology.entries,
            );
        }
    }

    function onClose() {
        ontologyObject = null;
    }

    function closeDialog(triggerConfirmDialog) {
        if (triggerConfirmDialog && hasChanges) {
            showDiscardSaveConfirmDialog = true;
            return false;
        }
        if (hasChanges) {
            discardChanges();
        }
        showDialog = false;
        onClose();
        return true;
    }

    function discardChanges() {
        ontologyObject.reset();
    }

    async function save() {
        loadingOntology = true;
        try {
            await saveOntology(dataset, graphUri, ontologyObject);
            const ontology = await getOntology();
            if (ontology) {
                ontologyObject = new ReactiveOntology(
                    ontology.uuid,
                    ontology.namespace,
                    ontology.entries,
                );
            }
            ontologyObject.save();
            if (onSubmit) {
                onSubmit();
            } else {
                forceReloadTrigger.trigger();
            }
        } finally {
            loadingOntology = false;
        }
    }

    function discard() {
        showDialog = false;
        discardChanges();
    }

    function handleAddEntry() {
        ontologyObject.entries.append();
        scrollEntriesToBottom();
    }

    async function getOntology() {
        if (!graphUri) {
            return null;
        }
        const res = await bec.getOntology(dataset, graphUri);
        let content = await res.text();
        if (!content) {
            return null;
        }
        return JSON.parse(content);
    }

    async function saveOntology(datasetName, graphUri, ontologyObject) {
        const serializable = ontologyObject.getPlainObject();
        if (ontologyObject.uuid.value) {
            await bec.putOntology(datasetName, graphUri, serializable);
        } else {
            await bec.postOntology(datasetName, graphUri, serializable);
        }
    }

    function scrollEntriesToBottom() {
        setTimeout(() => {
            if (tableContainerRef) {
                tableContainerRef.scrollTop = tableContainerRef.scrollHeight;
            }
        }, 0);
    }

    function getSubstitutedNamespace(namespace) {
        const namespaceObj = namespaces?.find(p => p?.prefix === namespace);
        return namespaceObj ? namespaceObj.substitutedPrefix : namespace;
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    onClose={() => closeDialog(true)}
    size="w-2/3 h-3/4"
    secondaryLabel={readonly ? null : "Discard"}
    secondaryIcon={faRotateLeft}
    secondaryVariant={"danger"}
    onSecondary={discardChanges}
    disableSecondary={!hasChanges}
    primaryLabel={readonly ? null : hasChanges ? "Save" : "No Changes"}
    primaryIcon={faSave}
    onPrimary={save}
    closeOnPrimary={false}
    disablePrimary={disableSubmit}
    {readonly}
    title={readonly
        ? "View profile header meta data"
        : "Edit profile header meta data"}
>
    {#if loadingOntology}
        <div
            class="absolute inset-0 z-50 flex items-center justify-center bg-white/50"
        >
            <LoadingSpinner />
        </div>
    {/if}
    <div class="mx-2 flex h-full flex-col">
        {#key ontologyObject}
            {#if ontologyObject}
                <div class="mb-2 w-150">
                    <SearchableSelect
                        label="Namespace:"
                        placeholder="*namespace"
                        value={getSubstitutedNamespace(
                            ontologyObject.namespace.value,
                        )}
                        optionObjectList={namespaces}
                        accessDisplayData={namespace =>
                            getSubstitutedNamespace(
                                namespace.substitutedPrefix,
                            )}
                        accessIdentifier={namespace =>
                            `${namespace.substitutedPrefix} (${namespace?.prefix})`}
                        callOnValidChange={value =>
                            (ontologyObject.namespace.value = value?.prefix)}
                        {readonly}
                    />
                </div>

                <span class="">Entries:</span>
                <div
                    bind:this={tableContainerRef}
                    class="border-border text-default-text mt-1 max-h-full overflow-scroll rounded-lg border-2"
                >
                    <table class="w-full border-collapse text-sm">
                        <thead
                            class="bg-default-background border-border sticky top-0 z-10 border-b"
                        >
                            <tr>
                                <th
                                    class="w-1/3 px-2 py-1 text-left tracking-wide uppercase"
                                >
                                    Entry IRI
                                </th>
                                <th
                                    class="px-2 py-1 text-left tracking-wide uppercase"
                                >
                                    Value
                                </th>
                                <th
                                    class="px-2 py-1 text-left tracking-wide uppercase"
                                >
                                    Data Type
                                </th>
                                <th
                                    class="w-11 px-2 py-1 text-center tracking-wide uppercase"
                                >
                                    IRI
                                </th>
                                <th
                                    class="w-11 px-2 py-1 text-left tracking-wide uppercase"
                                ></th>
                                {#if !readonly}
                                    <th class="w-11 px-2 py-1 text-center">
                                        <DropdownMenu.Root>
                                            <DropdownMenu.Trigger>
                                                <div class="size-9">
                                                    <ButtonControl height={9}>
                                                        <Fa icon={faPlus} />
                                                    </ButtonControl>
                                                </div>
                                            </DropdownMenu.Trigger>
                                            <DropdownMenu.Content>
                                                <DropdownMenu.Item.Button
                                                    onSelect={handleAddEntry}
                                                >
                                                    Add Empty Entry
                                                </DropdownMenu.Item.Button>
                                                <DropdownMenu.Item.Button
                                                    onSelect={() =>
                                                        (showAddKnownEntriesPopUp = true)}
                                                >
                                                    Add CGMES 3.0 entries
                                                </DropdownMenu.Item.Button>
                                            </DropdownMenu.Content>
                                        </DropdownMenu.Root>
                                    </th>
                                {/if}
                            </tr>
                        </thead>

                        <tbody>
                            {#each ontologyObject.entries.values as entry}
                                <OntologyEntryRow
                                    entries={ontologyObject.entries}
                                    {entry}
                                    {readonly}
                                    {namespaces}
                                />
                            {/each}
                        </tbody>
                    </table>
                </div>
            {:else}
                <p>Loading...</p>
            {/if}
        {/key}
    </div>
</ActionDialog>
<DiscardCancelConfirmDialog
    bind:showDialog={showDiscardSaveConfirmDialog}
    onDiscard={discard}
    onSave={save}
    disableSave={disableSubmit}
/>
<AddKnownFieldsDialog
    bind:showDialog={showAddKnownEntriesPopUp}
    existingEntries={ontologyObject.entries}
    scrollToBottom={scrollEntriesToBottom}
    {namespaces}
    {dataset}
    {graphUri}
/>
