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
    import { faExclamation } from "@fortawesome/free-solid-svg-icons";

    import {
        deleteResources,
        getDeletionImpact,
    } from "$lib/api/generated/index.ts";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import {
        copyState,
        forceReloadTrigger,
        multiSelectState,
    } from "$lib/sharedState.svelte.js";
    import { editorState } from "$lib/sharedState.svelte.js";

    import { getDefaultAction } from "./deleteDependencyDefaults.js";
    import DeleteDependencyNode from "./DeleteDependencyNode.svelte";

    let {
        showDialog = $bindable(),
        onOpen = () => {},
        onClose = () => {},
        datasetName,
        graphUri,
        resourceUuid,
        resourceUuids,
    } = $props();

    /** @type {Array<object>} One affected-resource tree per requested resource. */
    let roots = $state([]);

    /** @type {Map<string, string>} "uuid::reason" -> selected action */
    let selectedActions = $state(new Map());

    /** The resources to delete; supports a single uuid or a list (multiselect). */
    const targetUuids = $derived(
        resourceUuids && resourceUuids.length
            ? resourceUuids
            : resourceUuid
              ? [resourceUuid]
              : [],
    );

    let type = $derived(roots[0]?.type?.toLowerCase());

    /** Ordered list of actions that exist anywhere across all trees */
    let availableActions = $derived(
        roots.length
            ? [
                  "DELETE",
                  "KEEP",
                  "REMOVE_PACKAGE_REFERENCE",
                  "REMOVE_SUBCLASS_REFERENCE",
              ].filter(a => {
                  const all = new Set();
                  roots.forEach(root => collectActions(root, all));
                  return all.has(a);
              })
            : [],
    );

    /**
     * Collects all unique actions across the entire tree.
     * @param {object} node
     * @param {Set<string>} actions
     * @returns {Set<string>}
     */
    function collectActions(node, actions = new Set()) {
        for (const action of node.actions) {
            actions.add(action);
        }
        if (node.children) {
            for (const child of node.children) {
                collectActions(child, actions);
            }
        }
        return actions;
    }

    function onOpenInternal() {
        onOpen();
        fetchDeleteDependencies();
    }

    /**
     * Recursively initializes selectedActions using default rules.
     * @param {object} node
     */
    function initSelectedActions(node) {
        const key = `${node.resourceIdentifier.uuid}::${node.reason}`;
        const defaultAction = getDefaultAction(node);
        selectedActions.set(key, defaultAction);
        if (node.children) {
            for (const child of node.children) {
                initSelectedActions(child);
            }
        }
    }

    async function fetchDeleteDependencies() {
        if (!datasetName || !graphUri || targetUuids.length === 0) {
            console.error(
                "Missing required properties to delete resource:",
                datasetName,
                graphUri,
                targetUuids,
            );
            showDialog = false;
            return;
        }
        let { data } = await getDeletionImpact({
            path: {
                datasetName: datasetName,
                graphURI: graphUri,
                uuid: targetUuids,
            },
        });
        const impactByUuid = data
        roots = Object.values(impactByUuid);

        selectedActions = new Map();
        roots.forEach(root => initSelectedActions(root));

        console.warn(
            "Delete dependencies - check for warnings before confirming deletion:",
            roots,
        );
    }

    /**
     * Builds a flat list of {uuid, action}, excluding children of non-DELETE nodes.
     * @param {object} node
     * @param {boolean} parentActive
     * @param {Array} result
     * @returns {Array<{uuid: string, action: string}>}
     */
    function buildPayload(node, parentActive = true, result = []) {
        if (!parentActive) return result;
        const key = `${node.resourceIdentifier.uuid}::${node.reason}`;
        const action = selectedActions.get(key) ?? node.actions[0];
        result.push({ uuid: node.resourceIdentifier.uuid, action });
        if (node.children) {
            for (const child of node.children) {
                buildPayload(child, action === "DELETE", result);
            }
        }
        return result;
    }

    function dedupePayload(entries) {
        const byUuid = new Map();
        for (const entry of entries) {
            const existing = byUuid.get(entry.uuid);
            if (!existing || existing.action !== "DELETE") {
                byUuid.set(entry.uuid, entry);
            }
        }
        return [...byUuid.values()];
    }

    async function submitDeleteRequest() {
        if (!roots.length) return;
        const payload = dedupePayload(
            roots.flatMap(root => buildPayload(root)),
        );
        pruneCopyState(payload);
        console.log("Submit delete with selections:", payload);
        const isSingle = roots.length === 1;
        const label = isSingle ? roots[0].resourceIdentifier.label : null;

        let { error } = await deleteResources({
            path: { datasetName: datasetName, graphURI: graphUri },
            body: payload,
        });
        if (error) {
            console.error("Failed to delete resources:", error);
            toastStore.error(
                "Delete failed",
                label
                    ? `Could not delete ${type} "${label}".`
                    : "Could not delete the selected resources.",
            );
        } else {
            console.log("Successfully submitted delete request");
            forceReloadTrigger.trigger();
            editorState.selectedClassDataset.updateValue(null);
            editorState.selectedClassGraph.updateValue(null);
            editorState.selectedClass.updateValue({ type: null, id: null });
            multiSelectState.clear();
            const noun = isSingle
                ? (type ?? "resource")
                : pluralize(type ?? "resource");
            toastStore.success(
                `${noun.charAt(0).toUpperCase() + noun.slice(1)} deleted`,
                label
                    ? `"${label}" was removed.`
                    : `${roots.length} resources were removed.`,
            );
        }
    }

    function pruneCopyState(payload) {
        if (copyState.isEmpty) return;
        for (const entry of payload) {
            if (entry.action === "DELETE") {
                copyState.remove(datasetName, graphUri, entry.uuid);
            }
        }
    }

    function onSelectAction(key, action) {
        selectedActions.set(key, action);
        selectedActions = new Map(selectedActions);
    }

    /**
     * Applies the given action to all direct children of parentNode that
     * actually support it. Used by the root's bulk-apply controls.
     * @param {object} parentNode
     * @param {string} action
     */
    function onBulkApplyToChildren(parentNode, action) {
        if (!parentNode.children) return;
        for (const child of parentNode.children) {
            if (child.actions.includes(action)) {
                const key = `${child.resourceIdentifier.uuid}::${child.reason}`;
                selectedActions.set(key, action);
            }
        }
        selectedActions = new Map(selectedActions);
    }

    function pluralize(word) {
        return word.endsWith("s") ? `${word}es` : `${word}s`;
    }

    function getDialogTitle() {
        if (roots.length === 1) {
            return `Delete ${type} "${roots[0].resourceIdentifier.label}"?`;
        }
        if (roots.length > 1) {
            return `Delete ${roots.length} ${pluralize(type ?? "resource")}?`;
        }
        return "Delete resource?";
    }
</script>

<ActionDialog
    bind:showDialog
    onOpen={onOpenInternal}
    {onClose}
    size="w-1/3 max-w-1/2 max-h-3/4"
    primaryLabel="Delete"
    onPrimary={submitDeleteRequest}
    title={getDialogTitle()}
    primaryVariant="danger"
    titleIcon={faExclamation}
    titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
>
    <div class="px-3 py-3">
        <p class="text-default-text mb-3 w-3/4 text-sm leading-relaxed">
            Select how affected resources should be handled when deleting {roots.length >
            1
                ? "the selected resources"
                : `this ${type ?? "resource"}`}.
        </p>

        {#if roots.length}
            <div
                class="border-border overflow-y-auto rounded-md border"
                style="max-height: calc(75vh - 10rem);"
            >
                {#each roots as root, i (root.resourceIdentifier.uuid)}
                    {#if i > 0}
                        <div class="bg-border h-px" role="presentation"></div>
                    {/if}
                    <DeleteDependencyNode
                        node={root}
                        {selectedActions}
                        {onSelectAction}
                        {onBulkApplyToChildren}
                        {availableActions}
                        depth={0}
                        isRoot={true}
                        expandedByDefault={roots.length === 1}
                    />
                {/each}
            </div>
        {:else}
            <div
                class="text-text-subtle flex items-center justify-center py-8 text-sm"
            >
                Loading dependencies...
            </div>
        {/if}
    </div>
</ActionDialog>
