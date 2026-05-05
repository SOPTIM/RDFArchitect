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
    import { editorState } from "$lib/sharedState.svelte.js";

    import ClassReferencedVia from "./ClassReferencedVia.svelte";
    import SHACLShapeTtlRenderer from "./SHACLShapeTtlRenderer.svelte";

    let {
        datasetName,
        graphUri,
        reactiveClass,
        showDialog = $bindable(),
    } = $props();

    let customShacl = $state({
        namespaces: "",
        nodeShapes: [],
        propertyShapes: [],
        derivedPropertyShapes: [],
    });
    let generatedShacl = $state({
        namespaces: "",
        nodeShapes: [],
        propertyShapes: [],
        derivedPropertyShapes: [],
    });
    let showGeneratedShacl = $state(false);

    function onOpen() {
        fetchShacl();
    }

    /**
     * fetches the SHACL rules for the selected class.
     */
    function fetchShacl() {
        fetch(
            PUBLIC_BACKEND_URL +
                "/datasets/" +
                encodeURIComponent(datasetName) +
                "/graphs/" +
                encodeURIComponent(graphUri) +
                "/classes/" +
                encodeURIComponent(reactiveClass.uuid.value) +
                "/shacl",
            {
                method: "GET",
                credentials: "include",
            },
        )
            .then(res => res.text())
            .then(res => {
                let resultObj = JSON.parse(res);
                customShacl = resultObj.custom;
                generatedShacl = resultObj.generated;
            });
    }

    function goToClass(classUUID) {
        editorState.selectedClassDataset.updateValue(datasetName);
        editorState.selectedClassGraph.updateValue(graphUri);
        editorState.selectedClassUUID.updateValue(classUUID);
        showDialog = false;
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    size="w-3/5 h-4/5"
    title={`Constraints (SHACL) for: "${reactiveClass.label.value}"`}
    primaryLabel={null}
>
    <div class="flex h-full flex-col space-y-2">
        <!-- main content area -->
        <div class="flex min-h-0 flex-1 space-x-2">
            <!-- main content/display of shacl shapes-->
            <div class="flex w-3/4 flex-col">
                <!-- button controls -->
                <div class="flex h-9 w-full shrink-0 space-x-2">
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

                <!-- scrollable content area -->
                <div class="min-h-0 w-full flex-1 overflow-y-auto">
                    {#key generatedShacl || customShacl}
                        {#if showGeneratedShacl}
                            <SHACLShapeTtlRenderer
                                namespaces={generatedShacl.namespaces}
                                nodeShapesList={generatedShacl.nodeShapes}
                                propertyShapesWrapperList={generatedShacl.propertyShapes}
                                derivedPropertyShapesWrapperList={generatedShacl.derivedPropertyShapes}
                                readOnly={true}
                            />
                        {:else}
                            <SHACLShapeTtlRenderer
                                namespaces={customShacl.namespaces}
                                nodeShapesList={customShacl.nodeShapes}
                                propertyShapesWrapperList={customShacl.propertyShapes}
                                derivedPropertyShapesWrapperList={customShacl.derivedPropertyShapes}
                                readOnly={false}
                            />
                        {/if}
                    {/key}
                </div>
            </div>

            <!-- sidebar -->
            <div class="w-1/4">
                <ClassReferencedVia
                    classUUID={editorState.selectedClassUUID.getValue()}
                    onClickOnClass={goToClass}
                />
            </div>
        </div>
    </div>
</ActionDialog>
