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
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { ReactiveAssociation } from "$lib/models/reactive/models/reactive-association.svelte.js";
    import { ReactiveAttribute } from "$lib/models/reactive/models/reactive-attribute.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";
    import TtlCodeEditor from "$lib/ttl/TtlCodeEditor.svelte";

    let { showDialog = $bindable(), property } = $props();

    let defaultShacl = () => ({
        namespaces: "",
        propertyShapes: [],
    });

    let customShacl = $state(defaultShacl());
    let customShaclBackUp = $state("");
    let generatedShacl = $state(defaultShacl());
    let showGeneratedShacl = $state(false);
    let showGeneratedNamespaces = $state(false);
    let showCustomNamespaces = $state(false);
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

    function onClose() {
        customShacl = defaultShacl();
        customShaclBackUp = "";
        generatedShacl = defaultShacl();
        showGeneratedShacl = false;
        showGeneratedNamespaces = false;
        showCustomNamespaces = false;
    }

    function getType() {
        if (property instanceof ReactiveAttribute) {
            return "attributes";
        } else if (property instanceof ReactiveAssociation) {
            return "associations";
        }
        return "";
    }

    function buildBaseUrl() {
        return (
            PUBLIC_BACKEND_URL +
            "/datasets/" +
            encodeURIComponent(classDatasetName) +
            "/graphs/" +
            encodeURIComponent(classGraphUri)
        );
    }

    /**
     * fetches the SHACL rules for the selected class.
     */
    async function fetchShacl(newViewedClassUUID, viewedPropertyUUID) {
        try {
            const type = getType();
            const res = await fetch(
                buildBaseUrl() +
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
            );
            if (!res.ok) {
                console.warn(
                    "Failed to fetch SHACL:",
                    res.status,
                    res.statusText,
                );
                return;
            }
            const data = await res.json();
            customShacl.propertyShapes = data.custom;
            generatedShacl.propertyShapes = data.generated;

            await fetchFormattedNamespaces();
        } catch (error) {
            console.warn("Failed to fetch SHACL:", error);
        }
    }

    async function fetchFormattedNamespaces() {
        try {
            const [generatedRes, customRes] = await Promise.all([
                fetch(buildBaseUrl() + "/shacl/generated/namespaces/ttl", {
                    method: "GET",
                    credentials: "include",
                }),
                fetch(buildBaseUrl() + "/shacl/custom/namespaces/ttl", {
                    method: "GET",
                    credentials: "include",
                }),
            ]);

            if (!generatedRes.ok) {
                console.warn(
                    "Failed to fetch generated namespaces:",
                    generatedRes.status,
                    generatedRes.statusText,
                );
            } else {
                generatedShacl.namespaces = await generatedRes.text();
            }

            if (!customRes.ok) {
                console.warn(
                    "Failed to fetch custom namespaces:",
                    customRes.status,
                    customRes.statusText,
                );
            } else {
                customShacl.namespaces = await customRes.text();
            }

            customShaclBackUp = buildTtlString(customShacl);
        } catch (error) {
            console.warn("Failed to fetch namespaces:", error);
        }
    }

    async function saveChanges() {
        const ttlString = buildTtlString(customShacl);
        const type = getType();
        try {
            const res = await fetch(
                buildBaseUrl() +
                    "/classes/" +
                    encodeURIComponent(
                        editorState.selectedClassUUID.getValue(),
                    ) +
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
            );
            if (!res.ok) {
                console.warn(
                    "Failed to save custom SHACL:",
                    res.status,
                    res.statusText,
                );
            }
        } catch (error) {
            console.warn("Failed to save custom SHACL:", error);
        } finally {
            await fetchShacl(
                editorState.selectedClassUUID.getValue(),
                property.uuid.value,
            );
        }
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

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    size="w-2/5 h-3/5"
    title={`Constraints (SHACL) for: "${property?.label?.value}"`}
    primaryLabel={null}
>
    <div class="flex h-full flex-col space-y-2">
        {#if property}
            <div class="shrink-0">
                <div class="flex h-9 w-full space-x-2">
                    <div class="text-nowrap">
                        <ButtonControl
                            callOnClick={() => (showGeneratedShacl = true)}
                            variant={showGeneratedShacl ? "" : "inline"}
                        >
                            Generated Constraints
                        </ButtonControl>
                    </div>
                    <div class="text-nowrap">
                        <ButtonControl
                            callOnClick={() => (showGeneratedShacl = false)}
                            variant={showGeneratedShacl ? "inline" : ""}
                        >
                            Custom Constraints
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
                                    showGeneratedNamespaces =
                                        !showGeneratedNamespaces;
                                }}
                            >
                                namespaces:
                            </button>
                        {/if}
                        {#if showGeneratedNamespaces}
                            <TtlCodeEditor
                                value={generatedShacl.namespaces}
                                readOnly={true}
                            />
                        {/if}
                        <div class="my-2 space-y-2">
                            {#if generatedShacl.propertyShapes.length === 0}
                                <p class="">No Constraints (SHACL) found.</p>
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
                                    showCustomNamespaces =
                                        !showCustomNamespaces;
                                }}
                            >
                                namespaces:
                            </button>
                        {/if}
                        {#if showCustomNamespaces}
                            <TtlCodeEditor
                                bind:value={customShacl.namespaces}
                                readOnly={false}
                            />
                        {/if}
                        <div class="my-2 space-y-2">
                            {#if customShacl.propertyShapes.length === 0}
                                <p class="">No Constraints (SHACL) found.</p>
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
</ActionDialog>
