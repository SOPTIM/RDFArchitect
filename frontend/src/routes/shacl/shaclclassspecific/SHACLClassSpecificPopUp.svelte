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

    import { getShaclRelatedToClass } from "$lib/api/generated/index.ts";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { onOpenClass } from "$lib/monaco/turtleLanguageFeatures.js";
    import {
        emptyRelations,
        firstDocumentOf,
    } from "$lib/shacl/classConstraints.js";
    import { SchemaTermSource } from "$lib/shacl/schemaTermSource.svelte.js";
    import { parsePrefixes } from "$lib/shacl/turtleTerms.js";
    import { selectSchemaOf, workbenchHref } from "$lib/shacl/workbenchLink.js";
    import { editorState } from "$lib/sharedState.svelte.js";
    import { navigateToClass } from "$lib/utils/model-navigation.js";

    import ClassConstraintsView from "./ClassConstraintsView.svelte";
    import ClassReferencedVia from "./ClassReferencedVia.svelte";

    import { goto } from "$app/navigation";

    let {
        workspaceName,
        schemaName,
        reactiveClass,
        showDialog = $bindable(),
    } = $props();

    let customShacl = $state(emptyRelations());
    let generatedShacl = $state(emptyRelations());
    let loading = $state(false);
    let error = $state(null);
    let fetchKey = $state(0);

    /** The schema the shown rules are read against, for hover and Ctrl+click on their terms. */
    const termSource = $derived(
        workspaceName && schemaName
            ? new SchemaTermSource({ workspaceName })
            : null,
    );

    /** The rules are shown one shape at a time, without their documents' prefix declarations. */
    const prefixes = $derived({
        ...parsePrefixes(generatedShacl?.namespaces ?? ""),
        ...parsePrefixes(customShacl?.namespaces ?? ""),
    });

    // While the dialog is open, following a term goes to its class here on the main page.
    $effect(() => {
        if (!showDialog) {
            return;
        }
        return onOpenClass((targetSchema, classUUID, packageUUID) => {
            showDialog = false;
            navigateToClass({
                workspaceName,
                schemaName: targetSchema,
                packageUUID,
                classUUID,
            });
        });
    });

    function onOpen() {
        fetchShacl();
    }

    function onClose() {
        customShacl = emptyRelations();
        generatedShacl = emptyRelations();
        error = null;
        fetchKey = 0;
    }

    /** The shapes that target this class, both halves of the answer. */
    async function fetchShacl() {
        loading = true;
        try {
            const { data, error: failure } = await getShaclRelatedToClass({
                path: {
                    workspaceName: workspaceName,
                    schemaName: schemaName,
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
     * Without the document the workbench opens on whatever it opened last, which for a schema with
     * a dozen constraints files is rarely the one being asked about. The class's schema is
     * selected first, because the workbench opens the selected one.
     */
    function openInWorkbench(documentId = null, line = null) {
        showDialog = false;
        selectSchemaOf(editorState, workspaceName, schemaName);
        goto(workbenchHref(documentId, line));
    }

    /** Opens a class the list beside the rules names, in the same schema. */
    function openRelatedClass(classObject) {
        showDialog = false;
        navigateToClass({
            workspaceName,
            schemaName,
            packageUUID: classObject.belongsToCategory,
            classUUID: classObject.uuid,
        });
    }

    /** The header button: the document the class's own rules are in, when there is one. */
    function openClassInWorkbench() {
        const first = firstDocumentOf({ custom: customShacl });
        openInWorkbench(first?.documentId ?? null, first?.line ?? null);
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
                <ButtonControl callOnClick={openClassInWorkbench}>
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

        <div class="flex min-h-0 flex-1 gap-3">
            {#if loading}
                <div class="flex flex-1 items-center justify-center">
                    <LoadingSpinner />
                </div>
            {:else}
                <div class="min-h-0 flex-1">
                    {#key fetchKey}
                        <ClassConstraintsView
                            custom={customShacl}
                            generated={generatedShacl}
                            {termSource}
                            {prefixes}
                            onopen={openInWorkbench}
                        />
                    {/key}
                </div>
            {/if}
            <aside
                class="border-border w-1/5 min-w-40 shrink-0 overflow-y-auto border-l pl-3"
            >
                <ClassReferencedVia
                    {workspaceName}
                    {schemaName}
                    classUUID={reactiveClass.uuid.value}
                    onClickOnClass={openRelatedClass}
                />
            </aside>
        </div>
    </div>
</ActionDialog>
