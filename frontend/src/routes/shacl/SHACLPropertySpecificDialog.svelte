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
    import { faFileShield } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import {
        getAssociationShacl,
        getAttributeShacl,
        getCustomShaclNamespacesAsString,
        getGeneratedShaclNamespacesAsString,
    } from "$lib/api/generated/index.ts";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { ReactiveAssociation } from "$lib/models/reactive/models/reactive-association.svelte.js";
    import { ReactiveAttribute } from "$lib/models/reactive/models/reactive-attribute.svelte.js";
    import TurtleEditor from "$lib/monaco/TurtleEditor.svelte";
    import { editorState } from "$lib/sharedState.svelte.js";

    import { goto } from "$app/navigation";

    let {
        showDialog = $bindable(),
        property,
        classUuidOverride = null,
    } = $props();

    let defaultShacl = () => ({
        namespaces: "",
        propertyShapes: [],
    });

    let customShacl = $state(defaultShacl());
    let generatedShacl = $state(defaultShacl());
    let showGeneratedShacl = $state(false);
    let showGeneratedNamespaces = $state(false);
    let showCustomNamespaces = $state(false);
    let classWorkspaceName = $derived(
        editorState.selectedClassWorkspace.getValue() ??
            editorState.selectedWorkspace.getValue(),
    );
    let classGraphUri = $derived(
        editorState.selectedClassGraph.getValue() ??
            editorState.selectedGraph.getValue(),
    );

    function getViewedClassUuid() {
        return classUuidOverride ?? editorState.selectedClass.getProperty("id");
    }

    function onOpen() {
        const viewedClassUuid = getViewedClassUuid();
        if (!viewedClassUuid || !property) {
            return;
        }
        fetchShacl(viewedClassUuid, property.uuid.value);
    }

    function onClose() {
        customShacl = defaultShacl();
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

    /**
     * fetches the SHACL rules for the selected class.
     */
    async function fetchShacl(newViewedClassUUID, viewedPropertyUUID) {
        try {
            const type = getType();
            let res;
            if (type === "attributes") {
                res = await getAttributeShacl({
                    path: {
                        datasetName: classWorkspaceName,
                        graphURI: classGraphUri,
                        classUUID: newViewedClassUUID,
                        attributeUUID: viewedPropertyUUID,
                    },
                });
            } else if (type === "associations") {
                res = await getAssociationShacl({
                    path: {
                        datasetName: classWorkspaceName,
                        graphURI: classGraphUri,
                        classUUID: newViewedClassUUID,
                        associationUUID: viewedPropertyUUID,
                    },
                });
            } else {
                console.warn("Failed to fetch SHACL: property type unknown");
                return;
            }

            if (res.error) {
                console.warn(
                    "Failed to fetch SHACL:",
                    res.status,
                    res.statusText,
                );
                return;
            }
            const data = res.data;
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
                getGeneratedShaclNamespacesAsString({
                    path: {
                        datasetName: classWorkspaceName,
                        graphURI: classGraphUri,
                    },
                }),
                getCustomShaclNamespacesAsString({
                    path: {
                        datasetName: classWorkspaceName,
                        graphURI: classGraphUri,
                    },
                }),
            ]);

            if (generatedRes.error) {
                console.warn(
                    "Failed to fetch generated namespaces:",
                    generatedRes.error,
                );
            } else {
                generatedShacl.namespaces = await generatedRes.data;
            }

            if (customRes.error) {
                console.warn(
                    "Failed to fetch custom namespaces:",
                    customRes.error,
                );
            } else {
                customShacl.namespaces = await customRes.data;
            }
        } catch (error) {
            console.warn("Failed to fetch namespaces:", error);
        }
    }

    function openWorkbench() {
        showDialog = false;
        goto("/shacl");
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
                    <!--
                      Reads only. The endpoint this used to save through wrote every edit into the
                      graph's default document, whichever document the rule actually came from.
                    -->
                    <div class="ml-auto w-48 text-nowrap">
                        <ButtonControl callOnClick={openWorkbench}>
                            <span class="flex items-center gap-2">
                                <Fa icon={faFileShield} />
                                Edit in workbench
                            </span>
                        </ButtonControl>
                    </div>
                </div>
            </div>
            <div class="min-h-0 flex-1 overflow-y-auto rounded">
                {#if showGeneratedShacl}
                    <div class="flex flex-col">
                        {#if generatedShacl.namespaces.trim().length === 0}
                            <p class="text-text-subtle text-sm italic">
                                No prefixes.
                            </p>
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
                            <TurtleEditor
                                autoGrow
                                value={generatedShacl.namespaces}
                                readOnly={true}
                            />
                        {/if}
                        <div class="my-2 space-y-2">
                            {#if generatedShacl.propertyShapes.length === 0}
                                <p class="text-text-subtle text-sm italic">
                                    No constraints on this property.
                                </p>
                            {/if}
                            {#each generatedShacl.propertyShapes as propertyShape}
                                <div>
                                    <TurtleEditor
                                        autoGrow
                                        value={propertyShape.triples.trim()}
                                        readOnly={true}
                                    />
                                </div>
                            {/each}
                        </div>
                    </div>
                {:else}
                    <div class="flex h-full flex-col">
                        {#if customShacl.namespaces.trim().length === 0}
                            <p class="text-text-subtle text-sm italic">
                                No prefixes.
                            </p>
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
                            <TurtleEditor
                                autoGrow
                                value={customShacl.namespaces}
                                readOnly={true}
                            />
                        {/if}
                        <div class="my-2 space-y-2">
                            {#if customShacl.propertyShapes.length === 0}
                                <p class="text-text-subtle text-sm italic">
                                    No constraints on this property.
                                </p>
                            {/if}
                            {#each customShacl.propertyShapes as propertyShape}
                                <div>
                                    <TurtleEditor
                                        autoGrow
                                        value={propertyShape.triples.trim()}
                                        readOnly={true}
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
