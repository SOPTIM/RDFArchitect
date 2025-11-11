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
    import { BackendConnection } from "$lib/api/backend.js";
    import SearchableSelect from "$lib/components/SearchableSelect.svelte";
    import TextAreaControl from "$lib/components/TextAreaControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ModifyDataDialog from "$lib/dialog/ModifyDataDialog.svelte";
    import { Package } from "$lib/models/dto";
    import {
        editorState,
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";

    import { getNamespaces } from "./classEditor/fetch-class-editor-context.js";

    let {
        showDialog = $bindable(),
        pack,
        readonly = false,
        datasetName = null,
        graphUri = null,
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);
    // modeled after the PackageObject, because Classes are not reactive
    let modifiedPackage = $state({
        uuid: "",
        prefix: "",
        label: "",
        comment: "",
        external: false,
    });

    let namespaces = $state([]);
    let resolvedPack = $state(null);

    const originalPackage = $derived(
        resolvedPack || pack ? new Package(resolvedPack ?? pack) : null,
    );

    let hasUnsavedChanges = $derived(
        originalPackage
            ? !new Package({
                  ...originalPackage,
                  comment: normalizeOptionalComment(originalPackage.comment),
              }).equals(
                  new Package({
                      ...modifiedPackage,
                      comment: normalizeOptionalComment(
                          modifiedPackage.comment,
                      ),
                  }),
              )
            : false,
    );
    //disable if required properties are not set
    let disableSubmit = $derived(
        !modifiedPackage.label || !modifiedPackage.prefix,
    );

    function normalizeOptionalComment(value) {
        return value === "" ? null : value;
    }

    async function onOpen() {
        if (!pack || !datasetName || !graphUri) {
            return;
        }
        resolvedPack = null;
        resolvedPack = await resolveCurrentPackage();
        const packageToEdit = new Package(resolvedPack ?? pack);
        modifiedPackage = {
            uuid: packageToEdit.uuid,
            prefix: packageToEdit.prefix,
            label: packageToEdit.label,
            comment: packageToEdit.comment,
            external: packageToEdit.external,
        };
        if (!readonly) {
            namespaces = await getNamespaces(datasetName);
        }
    }

    async function resolveCurrentPackage() {
        if (!pack?.uuid) {
            return pack;
        }

        if (!datasetName || !graphUri) {
            return pack;
        }

        try {
            const response = await bec.getPackages(datasetName, graphUri);
            if (!response.ok) {
                return pack;
            }
            const packageJson = await response.json();
            const allPackages = [
                ...(packageJson.internalPackageList ?? []),
                ...(packageJson.externalPackageList ?? []),
            ];
            return allPackages.find(p => p?.uuid === pack.uuid) ?? pack;
        } catch {
            return pack;
        }
    }

    async function savePackage() {
        if (!datasetName || !graphUri) {
            return;
        }
        const response = await bec.putPackage(datasetName, graphUri, {
            ...modifiedPackage,
            comment: normalizeOptionalComment(modifiedPackage.comment),
        });
        if (!response.ok) {
            return;
        }
        editorState.selectedPackageUUID.trigger();
        editorState.selectedClassUUID.trigger();
        forceReloadTrigger.trigger();
    }

    function onClose() {
        resolvedPack = null;
    }

    function getSubstitutedNamespace(namespace) {
        const namespaceObj = namespaces.find(p => p.prefix === namespace);
        return namespaceObj ? namespaceObj.substitutedPrefix : namespace;
    }
</script>

<ModifyDataDialog
    bind:showDialog
    {onOpen}
    {onClose}
    saveChanges={savePackage}
    hasChanges={hasUnsavedChanges}
    isValid={!disableSubmit}
    {readonly}
>
    {#if pack}
        <div class="mx-2 flex h-full flex-col">
            <span class="mb-2 text-lg">
                {#if readonly}
                    Viewing package <b>{originalPackage.label}</b>
                {:else}
                    Editing package <b>{originalPackage.label}</b>
                {/if}
            </span>

            <span class="mb-1 font-semibold">UUID:</span>
            <p class="mb-2 w-full">{originalPackage.uuid}</p>

            <!--LABEL-->
            <TextEditControl
                label="Label:"
                bind:value={modifiedPackage.label}
                placeholder="package label..."
                {readonly}
            />

            <!--URI PREFIX-->
            <SearchableSelect
                label="URI Prefix:"
                value={getSubstitutedNamespace(modifiedPackage.prefix)}
                optionObjectList={namespaces}
                accessDisplayData={namespace =>
                    getSubstitutedNamespace(namespace.prefix)}
                accessIdentifier={namespace =>
                    namespace.substitutedPrefix +
                    " (" +
                    namespace.prefix +
                    ") "}
                callOnValidChange={newNamespace =>
                    (modifiedPackage.prefix = newNamespace
                        ? newNamespace.prefix
                        : null)}
                {readonly}
            />
            <!--COMMENT-->
            <label for="package-edit-dialog-comment-text-area">Comment:</label>
            <TextAreaControl
                id="package-edit-dialog-comment-text-area"
                bind:value={modifiedPackage.comment}
                placeholder="package comment..."
                {readonly}
            />
        </div>
    {/if}
</ModifyDataDialog>
