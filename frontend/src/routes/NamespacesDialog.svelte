<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->
<script>
    import { faMinus, faPlus } from "@fortawesome/free-solid-svg-icons";
    import { tick } from "svelte";
    import { Fa } from "svelte-fa";

    import { getNamespaces } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import FaIconButton from "$lib/components/FaIconButton.svelte";
    import List from "$lib/components/List.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ModifyDataDialog from "$lib/dialog/ModifyDataDialog.svelte";
    import { mapNamespaceDtoToReactiveNamespace } from "$lib/models/reactive/mapper/map-dto-to-reactive-object.js";
    import { mapReactiveNamespaceToNamespaceDto } from "$lib/models/reactive/mapper/map-reactive-object-to-dto.js";
    import { ReactiveNamespace } from "$lib/models/reactive/models/reactive-namespace.svelte.js";
    import { ReactiveObjectsArrayWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-objects-array-wrapper.svelte.js";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/utils/reactive-objects-control-button-utils.js";
    import { namespacePrefixesAreUnique } from "$lib/models/reactive/validity-rules/validityFunctions.js";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    let { showDialog = $bindable() } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let datasetName = $state("");
    let readonly = $state(false);
    let namespaces = $state([]);

    let listRef;

    async function onOpen() {
        datasetName = editorState.selectedDataset.getValue();
        if (!datasetName) return;
        readonly = await isReadOnly(datasetName);
        await loadNamespaces(datasetName);
    }

    async function loadNamespaces(datasetNameLocal) {
        const namespaceDTOs = await getNamespaces(datasetNameLocal);
        const objectsForReactiveNamespaces = namespaceDTOs.map(namespaceDto => {
            return mapNamespaceDtoToReactiveNamespace(namespaceDto);
        });
        namespaces = new ReactiveObjectsArrayWrapper(
            objectsForReactiveNamespaces,
            ReactiveNamespace,
            (namespace, namespacesArray) => {
                namespace.prefix.violationChecks.push(prefix =>
                    namespacePrefixesAreUnique(prefix, namespacesArray),
                );
            },
        );
    }

    async function isReadOnly(datasetNameLocal) {
        const res = await bec.isReadOnly(datasetNameLocal);
        return await res.json();
    }

    async function saveNamespaces() {
        namespaces?.save();
        const plainReactiveNamespaces = namespaces.getPlainObject();
        const namespaceDTOs = plainReactiveNamespaces.map(namespace => {
            return mapReactiveNamespaceToNamespaceDto(namespace);
        });
        await bec.replaceNamespaces(datasetName, namespaceDTOs);
        forceReloadTrigger.trigger();
    }

    async function addNamespace() {
        namespaces.prepend();
        await tick();
        listRef?.scrollToTop?.();
    }
</script>

<ModifyDataDialog
    bind:showDialog
    {onOpen}
    saveChanges={saveNamespaces}
    discardChanges={() => namespaces?.reset()}
    hasChanges={namespaces?.isModified}
    isValid={namespaces?.isValid}
    {readonly}
>
    <div class="mx-2 flex h-[60vh] max-h-[60vh] flex-col">
        <h2 class="mb-2 flex-none text-lg font-semibold">
            {#if readonly}
                View namespaces - {datasetName}
            {:else}
                Manage namespaces - {datasetName}
            {/if}
        </h2>

        <div class="min-h-0 flex-1">
            <List bind:this={listRef} legend="Namespaces" isCollapsible={false}>
                {#snippet actions()}
                    {#if !readonly}
                        <div class="w-8">
                            <FaIconButton
                                callOnClick={addNamespace}
                                icon={faPlus}
                            />
                        </div>
                    {/if}
                {/snippet}

                {#snippet contents()}
                    <colgroup>
                        <col class="w-48" />
                        <col class="w-auto" />
                        {#if !readonly}
                            <col class="w-8" />
                        {/if}
                    </colgroup>

                    <tbody>
                        {#if namespaces?.values.length > 0}
                            {#each namespaces.values as namespace}
                                <tr>
                                    <td>
                                        <TextEditControl
                                            placeholder="prefix..."
                                            bind:value={namespace.prefix.value}
                                            {readonly}
                                            highlight={namespace.prefix
                                                .isModified}
                                            warn={!namespace.prefix.isValid}
                                            buttons={getControlButtonsForReactiveObject(
                                                namespace.prefix,
                                                readonly,
                                            )}
                                        />
                                    </td>

                                    <td>
                                        <TextEditControl
                                            placeholder="uri..."
                                            bind:value={namespace.iri.value}
                                            {readonly}
                                            highlight={namespace.iri.isModified}
                                            warn={!namespace.iri.isValid}
                                            buttons={getControlButtonsForReactiveObject(
                                                namespace.iri,
                                                readonly,
                                            )}
                                        />
                                    </td>

                                    {#if !readonly}
                                        <td>
                                            <ButtonControl
                                                callOnClick={() =>
                                                    namespaces.remove(
                                                        namespace,
                                                        true,
                                                    )}
                                            >
                                                <Fa icon={faMinus} />
                                            </ButtonControl>
                                        </td>
                                    {/if}
                                </tr>
                                {#if !namespace.prefix.isValid || !namespace.iri.isValid}
                                    <tr>
                                        <td class="align-top">
                                            <ViolationMessages
                                                violations={namespace.prefix
                                                    .violations}
                                            />
                                        </td>
                                        <td class="align-top">
                                            <ViolationMessages
                                                violations={namespace.iri
                                                    .violations}
                                            />
                                        </td>
                                        <td></td>
                                    </tr>
                                {/if}
                            {/each}
                        {:else}
                            <tr>
                                <td colspan={readonly ? 2 : 3}>
                                    No prefixes available
                                </td>
                            </tr>
                        {/if}
                    </tbody>
                {/snippet}
            </List>
        </div>
    </div>
</ModifyDataDialog>
