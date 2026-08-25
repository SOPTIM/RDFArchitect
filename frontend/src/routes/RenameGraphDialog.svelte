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
    import { v4 as uuidv4 } from "uuid";

    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { URI } from "$lib/models/dto/index.ts";
    import {
        editorState,
        forceReloadTrigger
    } from "$lib/sharedState.svelte.js";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";

    import { getUri } from "./mainpage/packageNavigation/packageNavigationUtils.svelte.js";

    let { showDialog = $bindable(), workspaceName, graphUri } = $props();

    const uniqueId = uuidv4();
    const defaultNamespace = "http://graph#";
    const uriSchemePattern = /^[a-zA-Z][a-zA-Z\d+.-]*:/;
    const namespaceInputId = `renameGraphNamespace-${uniqueId}`;
    const namespaceListId = `renameGraphNamespaces-${uniqueId}`;
    const labelInputId = `renameGraphLabel-${uniqueId}`;

    let namespaceUserInput = $state("");
    let labelUserInput = $state("");
    let initialLabel = $state("");
    let namespaceOptions = $state([]);
    let otherGraphUris = $state([]);

    const trimmedNamespace = $derived(namespaceUserInput.trim());
    const trimmedLabel = $derived(labelUserInput.trim());
    const resolvedGraphUri = $derived(
        trimmedNamespace && trimmedLabel ? trimmedNamespace + trimmedLabel : ""
    );
    const namespaceIsInvalid = $derived(
        !!trimmedNamespace && !uriSchemePattern.test(trimmedNamespace)
    );
    const graphExists = $derived(
        !!resolvedGraphUri && otherGraphUris.includes(resolvedGraphUri)
    );
    const uriChanged = $derived(resolvedGraphUri !== graphUri);
    // The tree labels a schema by its dcat:keyword and only falls back to the
    // URI suffix, so a new label has to reach the keyword as well.
    const labelChanged = $derived(trimmedLabel !== initialLabel);
    const disableSubmit = $derived(
        !resolvedGraphUri || namespaceIsInvalid || graphExists || !uriChanged
    );

    async function onOpen() {
        const uri = graphUri ? new URI(graphUri) : null;
        namespaceUserInput = uri?.prefix || defaultNamespace;
        initialLabel = uri?.suffix ?? "";
        labelUserInput = initialLabel;

        namespaceOptions = await loadNamespaceOptions();
        otherGraphUris = await loadOtherGraphUris();
    }

    function onClose() {
        namespaceUserInput = "";
        labelUserInput = "";
        initialLabel = "";
        namespaceOptions = [];
        otherGraphUris = [];
    }

    async function loadNamespaceOptions() {
        const namespaces = await workspaceStore.getNamespaces(workspaceName) ?? [];
        const options = namespaces
            .map(namespace => namespace?.prefix)
            .filter(prefix => !!prefix);
        return [
            ...new Set([defaultNamespace, ...options, namespaceUserInput])
        ].sort((a, b) => a.localeCompare(b));
    }

    async function loadOtherGraphUris() {
        if (!workspaceName) {
            return [];
        }

        const graphs = await graphStore.getGraphs(workspaceName) ?? [];
        return graphs.map(getUri).filter(uri => uri !== graphUri);
    }

    async function renameGraph() {
        const oldGraphUri = graphUri;
        const newGraphUri = resolvedGraphUri;
        const newKeyword = labelChanged ? trimmedLabel : null;

        const { error } = await graphStore.renameGraph(
            workspaceName,
            oldGraphUri,
            newGraphUri,
            newKeyword
        );
        if (error) return;

        editorState.renameGraph(workspaceName, oldGraphUri, newGraphUri);
        forceReloadTrigger.trigger();
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    {onClose}
    primaryLabel="Rename Schema"
    onPrimary={renameGraph}
    title="Rename Schema"
    disablePrimary={disableSubmit}
>
    <div class="mx-2 flex h-full flex-col">
        <label for={namespaceInputId} class="mb-1">Namespace</label>
        <input
            class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
            type="text"
            id={namespaceInputId}
            list={namespaceListId}
            placeholder={defaultNamespace}
            autocomplete="off"
            bind:value={namespaceUserInput}
        />
        <datalist id={namespaceListId}>
            {#each namespaceOptions as namespaceOption}
                <option value={namespaceOption}>{namespaceOption}</option>
            {/each}
        </datalist>

        <label for={labelInputId} class="mt-2 mb-1">Label</label>
        <input
            class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
            type="text"
            id={labelInputId}
            placeholder="Schema label"
            autocomplete="off"
            bind:value={labelUserInput}
        />

        {#if namespaceIsInvalid}
            <div class="mt-1 mb-1 h-6 text-sm">
                Namespace must start with a scheme, e.g. http://
            </div>
        {:else if graphExists}
            <div class="mt-1 mb-1 h-6 text-sm">Schema already exists</div>
        {:else if resolvedGraphUri}
            <div class="text-nav-text mt-1 mb-1 h-6 truncate text-sm">
                {resolvedGraphUri}
            </div>
        {/if}
    </div>
</ActionDialog>
