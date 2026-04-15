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

    import { BackendConnection } from "$lib/api/backend.js";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime.js";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
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
    } = $props();

    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    let deleteDependencies = $state(null);

    /** @type {Map<string, string>} "uuid::reason" -> selected action */
    let selectedActions = $state(new Map());

    let type = $derived(deleteDependencies?.type.toLowerCase());

    /** Ordered list of actions that exist anywhere in the tree */
    let availableActions = $derived(
        deleteDependencies
            ? [
                  "DELETE",
                  "KEEP",
                  "REMOVE_PACKAGE_REFERENCE",
                  "REMOVE_SUBCLASS_REFERENCE",
              ].filter(a => collectActions(deleteDependencies).has(a))
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
        if (!datasetName || !graphUri || !resourceUuid) {
            console.error(
                "Missing required properties to delete resource:",
                datasetName,
                graphUri,
                resourceUuid,
            );
            showDialog = false;
        }
        let res = await bec.getDeleteRelation(
            datasetName,
            graphUri,
            resourceUuid,
        );
        deleteDependencies = await res.json();

        selectedActions = new Map();
        initSelectedActions(deleteDependencies);

        console.warn(
            "Delete dependencies - check for warnings before confirming deletion:",
            deleteDependencies,
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

    async function submitDeleteRequest() {
        if (!deleteDependencies) return;
        const payload = buildPayload(deleteDependencies);
        console.log("Submit delete with selections:", payload);
        let res = await bec.deleteResources(datasetName, graphUri, payload);
        if (!res.ok) {
            console.error("Failed to delete resources:", await res.text());
        } else {
            console.log("Successfully submitted delete request");
            forceReloadTrigger.trigger();
            editorState.selectedClassDataset.updateValue(null);
            editorState.selectedClassGraph.updateValue(null);
            editorState.selectedClassUUID.updateValue(null);
        }
    }

    function onSelectAction(key, action) {
        selectedActions.set(key, action);
        selectedActions = new Map(selectedActions);
    }

    function getDialogTitle() {
        if (deleteDependencies) {
            return `Delete ${type} "${deleteDependencies.resourceIdentifier.label}"?`;
        }
        return `Delete resource "${resourceUuid}"?`;
    }
</script>

<ActionDialog
    bind:showDialog
    onOpen={onOpenInternal}
    {onClose}
    size="w-full max-w-1/3 max-h-3/4"
    primaryLabel="Delete"
    onPrimary={submitDeleteRequest}
    title={getDialogTitle()}
    primaryVariant="danger"
    titleIcon={faExclamation}
    titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
>
    <div class="px-3 py-3">
        <p class="text-default-text mb-3 w-3/4 text-sm leading-relaxed">
            Select how affected resources should be handled when deleting this
            {type}.
        </p>

        {#if deleteDependencies}
            <div
                class="border-border overflow-y-auto rounded-md border"
                style="max-height: calc(75vh - 10rem);"
            >
                <DeleteDependencyNode
                    node={deleteDependencies}
                    {selectedActions}
                    {onSelectAction}
                    {availableActions}
                    depth={0}
                    isRoot={true}
                />
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
