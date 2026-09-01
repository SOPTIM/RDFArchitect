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
    import { Pane, Splitpanes } from "svelte-splitpanes";

    import { getShaclRelatedToClass } from "$lib/api/generated/index.ts";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { workbenchHref } from "$lib/shacl/workbenchLink.js";
    import { ClassType, editorState } from "$lib/sharedState.svelte.js";

    import ClassConstraintsView from "./ClassConstraintsView.svelte";
    import ClassReferencedVia from "./ClassReferencedVia.svelte";

    import { goto } from "$app/navigation";

    let {
        workspaceName,
        graphUri,
        reactiveClass,
        showDialog = $bindable(),
    } = $props();

    const emptyShacl = () => ({
        namespaces: "",
        nodeShapes: [],
        propertyShapes: [],
        derivedPropertyShapes: [],
    });

    let customShacl = $state(emptyShacl());
    let generatedShacl = $state(emptyShacl());
    let loading = $state(false);
    let error = $state(null);
    let fetchKey = $state(0);

    function onOpen() {
        fetchShacl();
    }

    function onClose() {
        customShacl = emptyShacl();
        generatedShacl = emptyShacl();
        error = null;
        fetchKey = 0;
    }

    /** The shapes that target this class, both halves of the answer. */
    async function fetchShacl() {
        loading = true;
        try {
            const { data, error: failure } = await getShaclRelatedToClass({
                path: {
                    datasetName: workspaceName,
                    graphURI: graphUri,
                    classUUID: reactiveClass.uuid.value,
                },
            });
            if (failure) {
                error = "The constraints for this class could not be read.";
                return;
            }
            customShacl = data.custom;
            generatedShacl = data.generated;
            error = null;
            fetchKey++;
        } catch (failure) {
            console.warn("Failed to fetch SHACL:", failure);
            error = "The constraints for this class could not be read.";
        } finally {
            loading = false;
        }
    }

    /**
     * Follows a provenance chip into the workbench, at the document and line the rule lives in.
     *
     * Without the document the workbench opens on whatever it opened last, which for a graph with
     * a dozen constraints files is rarely the one being asked about.
     */
    function openInWorkbench(documentId = null, line = null) {
        showDialog = false;
        goto(workbenchHref(documentId, line));
    }

    function goToClass(classUUID) {
        editorState.selectedClassWorkspace.updateValue(workspaceName);
        editorState.selectedClassGraph.updateValue(graphUri);
        editorState.selectedClass.updateValue({
            type: ClassType.SINGLE_CLASS,
            id: classUUID,
        });
        showDialog = false;
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    size="w-3/5 h-4/5"
    title={`Constraints (SHACL) for: "${reactiveClass.label.value}"`}
    primaryLabel={null}
>
    <div class="flex h-full min-h-0 flex-col gap-2">
        <div class="flex shrink-0 items-center gap-2">
            <div class="ml-auto w-48 text-nowrap">
                <ButtonControl callOnClick={() => openInWorkbench()}>
                    <span class="flex items-center gap-2">
                        <Fa icon={faFileShield} />
                        Edit in workbench
                    </span>
                </ButtonControl>
            </div>
        </div>

        {#if error}
            <p
                class="bg-red-background border-red-border text-red-text shrink-0 rounded border px-3 py-2 text-sm"
            >
                {error}
            </p>
        {/if}

        {#if loading}
            <div class="flex flex-1 items-center justify-center">
                <LoadingSpinner />
            </div>
        {:else}
            <Splitpanes theme="opencgmes-theme" class="flex min-h-0 flex-1">
                <Pane size={75} minSize={45}>
                    {#key fetchKey}
                        <ClassConstraintsView
                            custom={customShacl}
                            generated={generatedShacl}
                            onopen={openInWorkbench}
                        />
                    {/key}
                </Pane>
                <Pane size={25} minSize={15} maxSize={45}>
                    <div class="h-full min-h-0 pl-2">
                        <ClassReferencedVia
                            classUUID={reactiveClass.uuid.value}
                            onClickOnClass={goToClass}
                        />
                    </div>
                </Pane>
            </Splitpanes>
        {/if}
    </div>
</ActionDialog>
