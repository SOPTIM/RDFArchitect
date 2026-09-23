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
        forceReloadTrigger,
    } from "$lib/sharedState.svelte.js";
    import { graphStore } from "$lib/stores/graphStore.ts";
    import { workspaceStore } from "$lib/stores/workspaceStore.ts";
    import { graphLabel, graphUri as uriOf } from "$lib/utils/graph-label.js";
    import { toLocalName } from "$lib/utils/iri.js";

    let { showDialog = $bindable(), workspaceName, graphUri } = $props();

    const uniqueId = uuidv4();
    const defaultNamespace = "http://graph#";
    const uriSchemePattern = /^[a-zA-Z][a-zA-Z\d+.-]*:/;
    const nameInputId = `renameGraphName-${uniqueId}`;
    const namespaceInputId = `renameGraphNamespace-${uniqueId}`;
    const namespaceListId = `renameGraphNamespaces-${uniqueId}`;
    const localNameInputId = `renameGraphLocalName-${uniqueId}`;

    let nameUserInput = $state("");
    let initialName = $state("");
    let namespaceUserInput = $state("");
    let localNameUserInput = $state("");
    let initialLocalName = $state("");
    let localNameTouched = $state(false);
    let showAdvanced = $state(false);
    let namespaceOptions = $state([]);
    let otherGraphUris = $state([]);
    /**
     * Whether the schema carries a name of its own. A profile does, in its header, and that is
     * what the tree shows it under — so renaming it rewrites the header and leaves the graph URI
     * alone. A graph that is no profile has nowhere to keep a name, and the tail of its URI is
     * the name, so there the rename has to move the URI.
     */
    let namesItself = $state(false);

    const trimmedName = $derived(nameUserInput.trim());
    const trimmedNamespace = $derived(namespaceUserInput.trim());
    const resolvedLocalName = $derived(
        localNameTouched || namesItself
            ? localNameUserInput.trim()
            : toLocalName(trimmedName),
    );
    const resolvedGraphUri = $derived(
        trimmedNamespace && resolvedLocalName
            ? trimmedNamespace + resolvedLocalName
            : "",
    );
    const namespaceIsInvalid = $derived(
        !!trimmedNamespace && !uriSchemePattern.test(trimmedNamespace),
    );
    const graphExists = $derived(
        !!resolvedGraphUri && otherGraphUris.includes(resolvedGraphUri),
    );
    const uriChanged = $derived(resolvedGraphUri !== graphUri);
    const nameChanged = $derived(trimmedName !== initialName);
    const disableSubmit = $derived(
        !trimmedName ||
            !resolvedGraphUri ||
            namespaceIsInvalid ||
            graphExists ||
            (!uriChanged && !nameChanged),
    );

    async function onOpen() {
        const uri = graphUri ? new URI(graphUri) : null;
        namespaceUserInput = uri?.prefix || defaultNamespace;
        initialLocalName = uri?.suffix ?? "";
        localNameUserInput = initialLocalName;
        localNameTouched = false;
        showAdvanced = false;

        const graphs = (await graphStore.getGraphs(workspaceName)) ?? [];
        const current = graphs.find(graph => uriOf(graph) === graphUri);
        namesItself = !!(current?.label || current?.keyword);
        initialName = current ? graphLabel(current) : initialLocalName;
        nameUserInput = initialName;

        namespaceOptions = await loadNamespaceOptions();
        otherGraphUris = graphs.map(uriOf).filter(uri => uri !== graphUri);
    }

    function onClose() {
        nameUserInput = "";
        initialName = "";
        namespaceUserInput = "";
        localNameUserInput = "";
        initialLocalName = "";
        localNameTouched = false;
        showAdvanced = false;
        namespaceOptions = [];
        otherGraphUris = [];
        namesItself = false;
    }

    async function loadNamespaceOptions() {
        const namespaces =
            (await workspaceStore.getNamespaces(workspaceName)) ?? [];
        const options = namespaces
            .map(namespace => namespace?.prefix)
            .filter(prefix => !!prefix);
        return [
            ...new Set([defaultNamespace, ...options, namespaceUserInput]),
        ].sort((a, b) => a.localeCompare(b));
    }

    async function renameGraph() {
        const oldGraphUri = graphUri;
        const newGraphUri = resolvedGraphUri;
        const newName = nameChanged ? trimmedName : null;

        const { error } = await graphStore.renameGraph(
            workspaceName,
            oldGraphUri,
            newGraphUri,
            newName,
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
        <label for={nameInputId} class="mb-1">Name</label>
        <input
            class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
            type="text"
            id={nameInputId}
            placeholder="Schema name"
            autocomplete="off"
            bind:value={nameUserInput}
        />

        <button
            type="button"
            class="text-nav-text mt-3 mb-1 self-start text-sm underline"
            onclick={() => (showAdvanced = !showAdvanced)}
        >
            {showAdvanced ? "Hide" : "Show"} graph URI
        </button>

        {#if showAdvanced}
            <label for={namespaceInputId} class="mt-1 mb-1">Namespace</label>
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

            <label for={localNameInputId} class="mt-2 mb-1">Local name</label>
            <input
                class="border-border bg-window-background focus:border-blue ring-none h-9 w-full rounded border-2 p-2 outline-none"
                type="text"
                id={localNameInputId}
                placeholder="Local name"
                autocomplete="off"
                value={resolvedLocalName}
                oninput={event => {
                    localNameTouched = true;
                    localNameUserInput = event.currentTarget.value;
                }}
            />
        {/if}

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
