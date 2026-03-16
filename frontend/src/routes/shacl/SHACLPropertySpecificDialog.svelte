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
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import DialogBase from "$lib/dialog/DialogBase.svelte";
    import { ReactiveAssociation } from "$lib/models/reactive/reactive-association.svelte.js";
    import { ReactiveAttribute } from "$lib/models/reactive/reactive-attribute.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";
    import TtlCodeEditor from "$lib/ttl/TtlCodeEditor.svelte";

    let { showDialog = $bindable(), property } = $props();

    let customShacl = $state({
        namespaces: "",
        propertyShapes: [],
    });
    let customShaclBackUp = $state("");
    let generatedShacl = $state({
        namespaces: "",
        propertyShapes: [],
    });
    let showGeneratedShacl = $state(false);
    let showNamespaces = $state(false);
    let classDatasetName = $derived(
        editorState.selectedClassDataset.getValue() ??
            editorState.selectedDataset.getValue(),
    );
    let classGraphUri = $derived(
        editorState.selectedClassGraph.getValue() ??
            editorState.selectedGraph.getValue(),
    );

    function onOpen() {
        if (!editorState.selectedClassUUID.getValue() || !property) {
            return;
        }
        fetchShacl(
            editorState.selectedClassUUID.getValue(),
            property.uuid.value,
        );
    }

    function getType() {
        if (property instanceof ReactiveAttribute) {
            return "attributes";
        } else if (property instanceof ReactiveAssociation) {
            return "associations";
        }
        return "";
    }

    /**
     * fetches the SHACL rules for the selected class.
     */
    function fetchShacl(newViewedClassUUID, viewedPropertyUUID) {
        let type = getType();
        fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(classDatasetName) +
                "/graphs/" +
                encodeURIComponent(classGraphUri) +
                "/classes/" +
                encodeURIComponent(newViewedClassUUID) +
                "/" +
                type +
                "/" +
                encodeURIComponent(viewedPropertyUUID) +
                "/shacl",
            {
                method: "GET",
                credentials: "include",
            },
        )
            .then(res => res.json())
            .then(res => {
                customShacl.propertyShapes = res.custom;
                generatedShacl.propertyShapes = res.generated;
                console.log(res);
            })
            .then(() => {
                fetchFormattedNamespaces();
            });
    }

    function fetchFormattedNamespaces() {
        fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(classDatasetName) +
                "/graphs/" +
                encodeURIComponent(classGraphUri) +
                "/shacl/generated/namespaces/ttl",
            {
                method: "GET",
                credentials: "include",
            },
        )
            .then(res => res.text())
            .then(res => {
                generatedShacl.prefixes = res;
            });
        fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(classDatasetName) +
                "/graphs/" +
                encodeURIComponent(classGraphUri) +
                "/shacl/custom/namespaces/ttl",
            {
                method: "GET",
                credentials: "include",
            },
        )
            .then(res => res.text())
            .then(res => {
                customShacl.prefixes = res;
            })
            .then(() => (customShaclBackUp = buildTtlString(customShacl)));
    }

    function saveChanges() {
        let ttlString = buildTtlString(customShacl);
        let type = getType();
        fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(classDatasetName) +
                "/graphs/" +
                encodeURIComponent(classGraphUri) +
                "/classes/" +
                encodeURIComponent(editorState.selectedClassUUID.getValue()) +
                "/" +
                type +
                "/" +
                encodeURIComponent(property.uuid.value) +
                "/shacl",
            {
                method: "PUT",
                body: ttlString,
                credentials: "include",
            },
        )
            .then(res => {
                if (res.ok) {
                    console.log("successfully updated custom SHACL rules.");
                } else {
                    console.log("failed to update custom SHACL rules.");
                }
            })
            .finally(() => {
                fetchShacl(
                    editorState.selectedClassUUID.getValue(),
                    property.uuid.value,
                );
            });
    }
    function buildTtlString(shacl) {
        let ttlString = "";
        if (shacl.namespaces) {
            ttlString += shacl.namespaces;
        }
        for (const propertyShape of shacl.propertyShapes) {
            ttlString += propertyShape.triples + "\n";
        }
        return ttlString;
    }
</script>

<DialogBase bind:showDialog {onOpen} size="w-2/5 h-3/5">
    <div class="flex h-full flex-col space-y-2">
        {#if property}
            <div class="flex-shrink-0">
                <div class="flex">
                    <div class="mx-2 grow">
                        <p class="m-1 font-bold">
                            SHACL Rules validating "{property.label.value}"
                        </p>
                    </div>
                    <div class="m-1 w-fit">
                        <ButtonControl
                            callOnClick={() => {
                                showDialog = false;
                            }}
                        >
                            close dialog
                        </ButtonControl>
                    </div>
                </div>
                <div class="flex h-9 w-full space-x-2">
                    <div class="text-nowrap">
                        <ButtonControl
                            callOnClick={() => (showGeneratedShacl = true)}
                        >
                            Generated SHACL
                        </ButtonControl>
                    </div>
                    <div class="text-nowrap">
                        <ButtonControl
                            callOnClick={() => (showGeneratedShacl = false)}
                        >
                            Custom SHACL
                        </ButtonControl>
                    </div>
                </div>
            </div>
            <div class="min-h-0 flex-1 overflow-y-auto rounded">
                {#if showGeneratedShacl}
                    <div class="flex flex-col">
                        {#if generatedShacl.namespaces.trim().length === 0}
                            <p class="">No namespaces found.</p>
                        {:else}
                            <button
                                class="w-fit font-bold hover:cursor-pointer hover:underline"
                                onclick={() => {
                                    showNamespaces = !showNamespaces;
                                }}
                            >
                                namespaces:
                            </button>
                        {/if}
                        {#if showNamespaces}
                            <TtlCodeEditor
                                value={generatedShacl.namespaces}
                                readOnly={true}
                            />
                        {/if}
                        <div class="my-2 space-y-2">
                            {#if generatedShacl.propertyShapes.length === 0}
                                <p class="">No SHACL rules found.</p>
                            {/if}
                            {#each generatedShacl.propertyShapes as propertyShape}
                                <div>
                                    <TtlCodeEditor
                                        value={propertyShape.triples.trim()}
                                        readOnly={true}
                                    />
                                </div>
                            {/each}
                        </div>
                    </div>
                {:else}
                    <div class="flex h-full flex-col">
                        {#if buildTtlString(customShacl) !== customShaclBackUp}
                            <div class="w-fit">
                                <ButtonControl callOnClick={saveChanges}>
                                    Save Changes
                                </ButtonControl>
                            </div>
                        {/if}
                        {#if customShacl.namespaces.trim().length === 0}
                            <p class="">No namespaces found.</p>
                        {:else}
                            <button
                                class="w-fit font-bold hover:underline"
                                onclick={() => {
                                    showNamespaces = !showNamespaces;
                                }}
                            >
                                namespaces:
                            </button>
                        {/if}
                        {#if showNamespaces}
                            <TtlCodeEditor
                                bind:value={customShacl.namespaces}
                                readOnly={false}
                            />
                        {/if}
                        <div class="my-2 space-y-2">
                            {#if customShacl.propertyShapes.length === 0}
                                <p class="">No SHACL rules found.</p>
                            {/if}
                            {#each customShacl.propertyShapes as propertyShape}
                                <div>
                                    <TtlCodeEditor
                                        bind:value={propertyShape.triples}
                                        readOnly={false}
                                    />
                                </div>
                            {/each}
                        </div>
                    </div>
                {/if}
            </div>
        {/if}
    </div>
</DialogBase>
