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
    import { onMount } from "svelte";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { editorState } from "$lib/sharedState.svelte.js";
    import TtlCodeEditor from "$lib/ttl/TtlCodeEditor.svelte";

    import SHACLNodeShapeEditor from "./editors/SHACLNodeShapeEditor.svelte";
    import ShaclPropertyShapeWrapperListEditor from "./editors/SHACLPropertyShapeWrapperListEditor.svelte";

    let {
        namespaces,
        nodeShapesList,
        propertyShapesWrapperList,
        derivedPropertyShapesWrapperList,
        readOnly = false,
        expanded = true,
    } = $props();

    let showNamespaces = $state(false);
    let showNodeShapes = $state(expanded);
    let showPropertyShapes = $state(expanded);
    let showDerivedPropertyShapes = $state(expanded);
    let showUserInput = $state(false);

    let localNamespaces = $state(namespaces);
    let localNodeShapesList = $state();
    let localPropertyShapesWrapperList = $state();
    let localDerivedPropertyShapesList = $state();

    let userInput = $state("");

    let classDatasetName = $derived(
        editorState.selectedClassDataset.getValue() ??
            editorState.selectedDataset.getValue(),
    );
    let classGraphUri = $derived(
        editorState.selectedClassGraph.getValue() ??
            editorState.selectedGraph.getValue(),
    );

    onMount(() => setUpLocalLists());

    function setUpLocalLists() {
        localNodeShapesList = JSON.parse(JSON.stringify(nodeShapesList));
        localPropertyShapesWrapperList = JSON.parse(
            JSON.stringify(propertyShapesWrapperList),
        );
        localDerivedPropertyShapesList = JSON.parse(
            JSON.stringify(derivedPropertyShapesWrapperList),
        );
    }

    function putChanges() {
        const ttlString = buildTtlString(
            localNamespaces,
            localNodeShapesList,
            localPropertyShapesWrapperList,
        );
        fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(classDatasetName) +
                "/graphs/" +
                encodeURIComponent(classGraphUri) +
                "/classes/" +
                encodeURIComponent(editorState.selectedClassUUID.getValue()) +
                "/shacl/custom",
            {
                method: "PUT",
                body: ttlString,
                credentials: "include",
            },
        )
            .then(res => {
                if (res.ok) {
                    console.log("successfully updated custom shacl of class.");
                } else {
                    console.log("failed to update custom shacl of class.");
                }
            })
            .finally(() => {
                editorState.selectedClassUUID.trigger();
            });
    }

    function buildTtlString(
        namespaces,
        nodeShapesList,
        propertyShapesWrapperList,
    ) {
        let ttlString = "";
        if (namespaces) {
            ttlString += namespaces;
        }
        if (nodeShapesList) {
            for (const nodeShape of nodeShapesList) {
                ttlString += nodeShape.triples;
            }
        }
        if (propertyShapesWrapperList) {
            for (const propertyShapeWrapper of propertyShapesWrapperList) {
                for (const propertyShape of propertyShapeWrapper.propertyShapes) {
                    ttlString += propertyShape.triples;
                }
            }
        }
        if (userInput) {
            ttlString += userInput;
        }
        return ttlString;
    }

    function checkForChanges() {
        return (
            userInput ||
            buildTtlString(
                localNamespaces,
                localNodeShapesList,
                localPropertyShapesWrapperList,
            ) !==
                buildTtlString(
                    namespaces,
                    nodeShapesList,
                    propertyShapesWrapperList,
                )
        );
    }
</script>

<div class="flex h-fit flex-col">
    <div>
        {#if !readOnly && checkForChanges()}
            <div class="w-fit">
                <ButtonControl
                    class="w-fit font-semibold italic hover:underline"
                    callOnClick={putChanges}
                >
                    save changes
                </ButtonControl>
            </div>
        {/if}
    </div>
    <div>
        {#if namespaces && namespaces.length > 0}
            <button
                class="w-fit font-bold hover:cursor-pointer hover:underline"
                onclick={() => {
                    showNamespaces = !showNamespaces;
                }}
            >
                namespaces:
            </button>
            {#if showNamespaces}
                <TtlCodeEditor bind:value={localNamespaces} {readOnly} />
            {/if}
        {:else}
            <p class="text-default-text font-semibold">no namespaces found</p>
        {/if}
    </div>
    <div>
        {#if localNodeShapesList && localNodeShapesList.length > 0}
            <button
                class="w-fit font-semibold hover:cursor-pointer hover:underline"
                onclick={() => (showNodeShapes = !showNodeShapes)}
            >
                NodeShapes:
            </button>
            {#if showNodeShapes && localNodeShapesList}
                <SHACLNodeShapeEditor
                    bind:nodeShapesList={localNodeShapesList}
                    {readOnly}
                />
            {/if}
        {:else}
            <p class="text-default-text font-semibold">no nodeShapes found</p>
        {/if}
    </div>
    <div>
        {#if localPropertyShapesWrapperList && localPropertyShapesWrapperList.length > 0}
            <button
                class="w-fit font-semibold hover:cursor-pointer hover:underline"
                onclick={() => (showPropertyShapes = !showPropertyShapes)}
            >
                PropertyShapes:
            </button>
            {#if showPropertyShapes && localPropertyShapesWrapperList}
                <ShaclPropertyShapeWrapperListEditor
                    bind:propertyShapesWrapperList={
                        localPropertyShapesWrapperList
                    }
                    {readOnly}
                />
            {/if}
        {:else}
            <p class="text-default-text font-semibold">
                no propertyShapes found
            </p>
        {/if}
    </div>
    <div>
        {#if localDerivedPropertyShapesList && localDerivedPropertyShapesList.length > 0}
            <button
                class="w-fit font-semibold hover:cursor-pointer hover:underline"
                onclick={() =>
                    (showDerivedPropertyShapes = !showDerivedPropertyShapes)}
            >
                derived PropertyShapes:
            </button>
            {#if showDerivedPropertyShapes && localDerivedPropertyShapesList}
                <ShaclPropertyShapeWrapperListEditor
                    bind:propertyShapesWrapperList={
                        localDerivedPropertyShapesList
                    }
                    readOnly={true}
                />
            {/if}
        {:else}
            <p class="text-default-text font-semibold">
                no derived propertyShapes found
            </p>
        {/if}
    </div>
    {#if !readOnly}
        <div class="mt-4">
            {#if showUserInput}
                <TtlCodeEditor bind:value={userInput} {readOnly} />
            {:else}
                <button
                    class="w-fit font-semibold hover:cursor-pointer hover:underline"
                    onclick={() => (showUserInput = true)}
                >
                    add new custom shapes:
                </button>
            {/if}
        </div>
    {/if}
</div>
