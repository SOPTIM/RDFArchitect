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
    import { setContext, untrack } from "svelte";

    import { getClassSchemas } from "$lib/actions/schemaExtensionActions.js";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import {
        FocusField,
        propertyEditorRequest,
        PropertyKind,
    } from "$lib/propertyEditorRequest.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";
    import { classStore } from "$lib/stores/classStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import {
        createClassEditorContextValue,
        createReactiveClass,
        loadClassEditorContextData,
        openClassEditor,
    } from "./class-editor-context.js";
    import AssociationEditorDialog from "./components/associations/associationEditorDialog/AssociationEditorDialog.svelte";
    import AttributeEditorDialog from "./components/attributes/AttributeEditorDialog.svelte";
    import EnumEntryEditorDialog from "./components/enum-entries/EnumEntryEditorDialog.svelte";

    let source = $state(null);
    let kind = $state(null);
    let focusField = $state(null);
    let focusInverse = $state(false);
    let property = $state(null);
    let showDialog = $state(false);

    let requestToken = 0;

    $effect(() => {
        const request = propertyEditorRequest.current;
        untrack(() => openRequestedProperty(request));
    });

    $effect(() => {
        if (!showDialog && propertyEditorRequest.current) {
            untrack(() => propertyEditorRequest.close());
        }
    });

    async function openRequestedProperty(request) {
        if (!request) {
            showDialog = false;
            return;
        }

        const token = ++requestToken;
        const workspaceName = editorState.selectedWorkspace.getValue();
        const owner = await resolveOwner(workspaceName, request);
        const nextSource =
            openClassEditor.match(
                workspaceName,
                owner.graphUri,
                owner.classUuid,
            ) ?? (await loadSource(workspaceName, owner));
        if (token !== requestToken) {
            return;
        }

        const found = nextSource && findProperty(nextSource, request);
        if (!found) {
            toastStore.error(
                "Could not open the property",
                "It is no longer part of the class it was opened from.",
            );
            propertyEditorRequest.close();
            return;
        }

        source = nextSource;
        kind = request.kind;
        property = found.property;
        focusField = request.focus ?? FocusField.LABEL;
        focusInverse = found.inverse;
        showDialog = true;
    }

    async function loadSource(workspaceName, owner) {
        if (!owner.classUuid) {
            return null;
        }

        const [classDto, contextData, readOnly] = await Promise.all([
            classStore.getClassInfo(
                workspaceName,
                owner.graphUri,
                owner.classUuid,
            ),
            loadClassEditorContextData(workspaceName, owner.graphUri),
            workspaceStore.isReadOnly(workspaceName),
        ]);
        if (!classDto || classDto.external) {
            return null;
        }

        const data = { ...contextData, targetClassInfos: [], superClass: null };
        return {
            workspaceName,
            graphUri: owner.graphUri,
            classUuid: owner.classUuid,
            readOnly,
            data,
            reactiveClass: createReactiveClass(classDto, data),
        };
    }

    // Resolved before comparing with the open class editor, which holds the schema's own uuid
    // while a merged diagram carries the merged one.
    async function resolveOwner(workspaceName, request) {
        const diagramGraphUri = editorState.selectedGraph.getValue();
        const graphUri = request.graphUri ?? diagramGraphUri ?? null;
        if (
            graphUri &&
            diagramGraphUri &&
            String(graphUri) === String(diagramGraphUri)
        ) {
            return { graphUri, classUuid: request.classUuid };
        }

        const occurrences = await getClassSchemas(
            workspaceName,
            request.classUuid,
        );
        const present = occurrences.filter(occurrence => occurrence.present);
        const match = graphUri
            ? present.find(
                  occurrence =>
                      String(occurrence.graphUri) === String(graphUri),
              )
            : present[0];
        if (!match) {
            return { graphUri, classUuid: graphUri ? null : request.classUuid };
        }
        return { graphUri: match.graphUri, classUuid: match.classUUID };
    }

    function findProperty(nextSource, request) {
        const reactiveClass = nextSource.reactiveClass;
        if (!reactiveClass) {
            return null;
        }
        if (request.kind === PropertyKind.ATTRIBUTE) {
            return matchByUuid(reactiveClass.attributes, request.propertyUuid);
        }
        if (request.kind === PropertyKind.ENUM_ENTRY) {
            return matchByUuid(reactiveClass.enumEntries, request.propertyUuid);
        }

        const direct = matchByUuid(
            reactiveClass.associations,
            request.propertyUuid,
        );
        if (direct) {
            return direct;
        }
        const inverse = reactiveClass.associations.values.find(
            association =>
                association.inverse.uuid.value === request.propertyUuid,
        );
        return inverse ? { property: inverse, inverse: true } : null;
    }

    function matchByUuid(collection, propertyUuid) {
        const property = collection.values.find(
            entry => entry.uuid.value === propertyUuid,
        );
        return property ? { property, inverse: false } : null;
    }

    setContext(
        "classEditor",
        createClassEditorContextValue(() => source),
    );
</script>

{#if source && property}
    {#if kind === PropertyKind.ATTRIBUTE}
        <AttributeEditorDialog
            bind:showDialog
            bind:attribute={property}
            attributes={source.reactiveClass.attributes}
            {focusField}
        />
    {:else if kind === PropertyKind.ASSOCIATION}
        <AssociationEditorDialog
            bind:showDialog
            bind:association={property}
            associations={source.reactiveClass.associations}
            {focusField}
            {focusInverse}
        />
    {:else if kind === PropertyKind.ENUM_ENTRY}
        <EnumEntryEditorDialog
            bind:showDialog
            bind:enumEntry={property}
            enumEntries={source.reactiveClass.enumEntries}
            {focusField}
        />
    {/if}
{/if}
