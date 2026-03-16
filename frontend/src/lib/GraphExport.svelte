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
    import { faCaretDown } from "@fortawesome/free-solid-svg-icons";
    import { onMount } from "svelte";
    import { Fa } from "svelte-fa";

    import { getNamespaces } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import { DropdownMenu } from "$lib/components/bitsui/dropdown/index";
    import DatasetAndGraphSelection from "$lib/components/DatasetAndGraphSelection.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { ReactiveOntology } from "$lib/models/reactive/ontology/reactive-ontology.svelte.js";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { saveFile, supportedRDFMediaTypes } from "$lib/utils/fileUtils.ts";

    import { editorState } from "../lib/sharedState.svelte.js";

    let {
        getAPIRoute,
        showDialog = $bindable(),
        disablePrimary = $bindable(),
        onSubmit = $bindable(),
        lockedDatasetName,
        lockedGraphUri,
        generateOntologyEntries = false,
        supportedMediaTypes = supportedRDFMediaTypes,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let selectedDatasetName = $state(null);
    let graphURI = $state(null);
    let selectedMediaType = $state();

    let ontology = $state();
    let generatedOntologyEntries = $state([]);

    let namespaces = $state([]);
    let hasOntology = $derived(!!ontology);

    // Derived state for checkbox
    let allSelected = $derived(
        generatedOntologyEntries.length > 0 &&
            generatedOntologyEntries.every(entry => entry.generate),
    );
    let someSelected = $derived(
        generatedOntologyEntries.some(entry => entry.generate),
    );

    $effect(
        () =>
            (disablePrimary =
                !selectedDatasetName || !graphURI || !selectedMediaType),
    );

    $effect(async () => {
        if (selectedDatasetName) {
            namespaces = await getNamespaces(selectedDatasetName);
        } else {
            namespaces = [];
        }
    });

    $effect(async () => {
        if (selectedDatasetName && graphURI) {
            let ontologyJSON = await getOntology(selectedDatasetName, graphURI);
            if (!ontologyJSON) {
                ontology = null;
                return;
            }
            ontology = new ReactiveOntology(
                ontologyJSON.uuid,
                ontologyJSON.namespace,
                ontologyJSON.entries,
            );
        }
    });

    $effect(async () => {
        if (selectedDatasetName && graphURI && hasOntology) {
            const res = await bec.generateOntologyEntries(
                selectedDatasetName,
                graphURI,
            );
            generatedOntologyEntries = await res.json();
            generatedOntologyEntries.forEach(entry => (entry.generate = true));
            return;
        }
        generatedOntologyEntries = [];
    });

    onMount(() => (onSubmit = handleExport));

    onMount(async () => {
        selectedDatasetName =
            lockedDatasetName ?? editorState.selectedDataset.getValue();
        graphURI = lockedGraphUri ?? editorState.selectedGraph.getValue();
        if (selectedDatasetName) {
            namespaces = await getNamespaces(selectedDatasetName);
        }
    });

    function toggleAllEntries() {
        const newValue = !allSelected;
        generatedOntologyEntries.forEach(entry => (entry.generate = newValue));
    }

    async function fetchGraphFile() {
        return fetch(getAPIRoute(selectedDatasetName, graphURI), {
            method: "GET",
            headers: new Headers({ Accept: selectedMediaType.mimeType }),
            credentials: "include",
        });
    }

    async function getOntology(datasetName) {
        const res = await bec.getOntology(datasetName, graphURI);
        let content = await res.text();
        if (!content) {
            return content;
        }
        return JSON.parse(content);
    }

    async function handleExport() {
        if (
            !getAPIRoute ||
            !selectedDatasetName ||
            !graphURI ||
            !selectedMediaType
        ) {
            return;
        }
        if (generateOntologyEntries && someSelected) {
            for (const entry of generatedOntologyEntries) {
                if (entry.generate) {
                    ontology.entries.append(entry);
                }
            }
            await bec.putOntology(
                selectedDatasetName,
                graphURI,
                ontology.getPlainObject(),
            );
            forceReloadTrigger.trigger();
        }
        try {
            const response = await fetchGraphFile();
            const blob = await response.blob();
            const suggestedFilename = response.headers.get(
                "content-disposition",
            );
            saveFile(blob, suggestedFilename, selectedMediaType);
        } catch (e) {
            console.error("Failed to download graph:", e);
        } finally {
            showDialog = false;
        }
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

<div class="mx-2 mt-2 flex h-full flex-col">
    <DatasetAndGraphSelection
        bind:dataset={selectedDatasetName}
        bind:graph={graphURI}
        {lockedDatasetName}
        {lockedGraphUri}
        displayAsCard={false}
    />
    {#if generateOntologyEntries}
        {#if hasOntology}
            {#if generatedOntologyEntries.length > 0}
                <div>
                    <p class="text-default-text mt-2 mb-1 block">
                        Generate ontology entries:
                    </p>
                    <div class="flex gap-1.5">
                        <DropdownMenu.Root>
                            <DropdownMenu.Trigger>
                                <button
                                    type="button"
                                    class="border-border bg-button-default-background text-button-default-text hover:bg-button-hover-background flex h-9 items-center gap-2 rounded px-4 py-2 font-normal hover:cursor-pointer"
                                >
                                    <input
                                        type="checkbox"
                                        checked={someSelected}
                                        onclick={() => {
                                            toggleAllEntries();
                                        }}
                                        class="cursor-pointer"
                                    />
                                    <Fa icon={faCaretDown} />
                                </button>
                            </DropdownMenu.Trigger>
                            <DropdownMenu.Content>
                                {#each generatedOntologyEntries as entry}
                                    <DropdownMenu.Item.CheckBox
                                        bind:value={entry.generate}
                                    >
                                        {getShortenedIRI(namespaces, entry.iri)}
                                        ({entry.value})
                                    </DropdownMenu.Item.CheckBox>
                                {/each}
                            </DropdownMenu.Content>
                        </DropdownMenu.Root>
                    </div>
                </div>
            {:else}
                <p class="mt-2 text-sm italic">
                    No ontology entries can be generated for the selected graph.
                </p>
            {/if}
        {:else if graphURI}
            <p class="mt-2 text-sm italic">This graph has no ontology</p>
        {/if}
    {/if}
    <label for="media-types-Download" class="mt-2 mb-1">Media type</label>
    <select
        class=" border-border bg-window-background focus:border-orange h-9 w-fit rounded border-2 p-2"
        id="media-types-Download"
        bind:value={selectedMediaType}
    >
        {#each supportedMediaTypes as mediaType}
            <option value={mediaType}>{mediaType.name}</option>
        {/each}
    </select>
</div>
