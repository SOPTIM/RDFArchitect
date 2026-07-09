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
    import SearchableSelect from "$lib/components/SearchableSelect.svelte";
    import TextAreaControl from "$lib/components/TextAreaControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import ModifyDataDialog from "$lib/dialog/ModifyDataDialog.svelte";
    import { mapReactivePackageToPackageDto } from "$lib/models/reactive/mapper/map-reactive-object-to-dto.js";
    import { ReactivePackage } from "$lib/models/reactive/models/reactive-package.svelte.js";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/utils/reactive-objects-control-button-utils.js";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { packageStore } from "$lib/stores/PackageStore.ts";
    import {
        getPackageDisplayLabel,
        restorePackageLabelPrefix,
    } from "$lib/utils/package-label.js";
    import { datasetStore } from "$lib/stores/DatasetStore.ts";

    let {
        showDialog = $bindable(),
        pack,
        readonly = false,
        datasetName = null,
        graphUri = null,
    } = $props();

    let pkg = $state(null);
    let isNewPackage = $state(true);
    let namespaces = $state([]);
    let packages = $state([]);

    function getSubstitutedNamespace(namespace) {
        const namespaceObj = namespaces.find(n => n.prefix === namespace);
        return namespaceObj ? namespaceObj.substitutedPrefix : namespace;
    }

    async function onOpen() {
        await datasetStore.load();
        namespaces = datasetStore.getNamespaces(datasetName);

        if (!datasetName || !graphUri) {
            packages = [];
            return;
        }

        await packageStore.load(datasetName, graphUri);
        const result = packageStore.getPackages(datasetName, graphUri);
        packages = [
            ...result.internal,
            ...result.external,
        ]

        if (pack) {
            isNewPackage = false;
            pkg = new ReactivePackage({
                uuid: pack.uuid,
                label: getPackageDisplayLabel(pack.label),
                namespace: pack.prefix,
                comment: pack.comment,
                compareNamespaces: namespaces,
            });
        } else {
            isNewPackage = true;
            pkg = new ReactivePackage();
        }
        pkg.label.violationChecks.push(value => {
            if (
                packages.some(
                    p =>
                        getPackageDisplayLabel(p.label) === value &&
                        p.uuid !== pkg.uuid.value,
                )
            ) {
                return ["must be unique"];
            }
            return [];
        });
    }

    async function savePackage() {
        if (!datasetName || !graphUri) {
            return;
        }

        const apiPackage = mapReactivePackageToPackageDto(pkg);
        apiPackage.label = restorePackageLabelPrefix(
            apiPackage.label,
            pack?.label,
        );
        const res = await packageStore.savePackage(
            datasetName,
            graphUri,
            apiPackage,
        );

        if (!res.error) {
            pkg.save();
            forceReloadTrigger.trigger();
        }

        return res;
    }
</script>

<ModifyDataDialog
    bind:showDialog
    {onOpen}
    saveChanges={savePackage}
    discardChanges={() => pkg.reset()}
    hasChanges={isNewPackage || pkg?.isModified}
    isValid={pkg?.isValid}
    {readonly}
    title={isNewPackage
        ? "Create Package"
        : readonly
          ? `View Package "${pkg.label.value}"`
          : `Edit Package "${pkg.label.backup}"`}
>
    {#if pkg}
        <div class="mx-2 flex h-full flex-col">
            <!-- LABEL -->
            <TextEditControl
                label="Label:"
                placeholder="package label..."
                bind:value={pkg.label.value}
                highlight={pkg.label.isModified}
                warn={!pkg.label.isValid}
                {readonly}
                buttons={getControlButtonsForReactiveObject(
                    pkg.label,
                    readonly,
                )}
            />
            <ViolationMessages violations={pkg.label.violations} />

            <!-- NAMESPACE -->
            <SearchableSelect
                label="Namespace:"
                value={getSubstitutedNamespace(pkg.namespace.value)}
                optionObjectList={namespaces}
                accessDisplayData={namespace =>
                    getSubstitutedNamespace(namespace.prefix)}
                accessIdentifier={namespace =>
                    namespace.substitutedPrefix +
                    " (" +
                    namespace.prefix +
                    ") "}
                callOnChange={newNamespace =>
                    (pkg.namespace.value =
                        newNamespace?.prefix !== undefined
                            ? newNamespace.prefix
                            : newNamespace)}
                highlight={pkg.namespace.isModified}
                warn={!pkg.namespace.isValid}
                {readonly}
                buttons={getControlButtonsForReactiveObject(
                    pkg.namespace,
                    readonly,
                )}
                tooltip={pkg.namespace.value}
            />
            <ViolationMessages violations={pkg.namespace.violations} />

            <!-- COMMENT -->
            <label for="package-edit-dialog-comment-text-area">Comment:</label>
            <TextAreaControl
                id="package-edit-dialog-comment-text-area"
                placeholder="comment..."
                bind:value={pkg.comment.value}
                highlight={pkg.comment.isModified}
                warn={!pkg.comment.isValid}
                {readonly}
                buttons={getControlButtonsForReactiveObject(
                    pkg.comment,
                    readonly,
                )}
            />
            <ViolationMessages violations={pkg.comment.violations} />
        </div>
    {/if}
</ModifyDataDialog>
