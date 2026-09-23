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
    import { graphLabel, graphUri as uriOf } from "$lib/utils/graph-label.js";
    import { toLocalName, uriSuffix } from "$lib/utils/iri.js";

    let {
        showDialog = $bindable(),
        workspaceName,
        graphUri,
        onEditHeader,
    } = $props();

    const uniqueId = uuidv4();
    /**
     * Every graph the app makes lives here — see `RDFA.GRAPH_URI`, `NewGraphDialog` and
     * `ImportDialog`. A graph URI names the graph and nothing else, so it is not something to
     * choose: only its tail is editable, and an older graph keeps whatever namespace it has.
     */
    const defaultNamespace = "http://graph#";
    const nameInputId = `renameGraphName-${uniqueId}`;

    let nameUserInput = $state("");
    let initialName = $state("");
    let namespace = $state(defaultNamespace);
    let otherGraphUris = $state([]);
    /** What the profile calls itself, if it does. Empty for a graph with no header to read. */
    let profileName = $state("");
    /** For a CGMES 2.4.15 profile, the class its keyword and version IRIs are fixed on. */
    let profileClass = $state(null);

    const trimmedName = $derived(nameUserInput.trim());
    const localName = $derived(toLocalName(trimmedName));
    const resolvedGraphUri = $derived(localName ? namespace + localName : "");
    const graphExists = $derived(
        !!resolvedGraphUri && otherGraphUris.includes(resolvedGraphUri),
    );
    const nameChanged = $derived(trimmedName !== initialName);
    const disableSubmit = $derived(
        !resolvedGraphUri || graphExists || !nameChanged,
    );

    async function onOpen() {
        const uri = graphUri ? new URI(graphUri) : null;
        namespace = uri?.prefix || defaultNamespace;
        initialName = uri?.suffix ?? "";
        nameUserInput = initialName;

        const graphs = (await graphStore.getGraphs(workspaceName)) ?? [];
        const current = graphs.find(graph => uriOf(graph) === graphUri);
        profileName =
            current?.label || current?.keyword ? graphLabel(current) : "";
        profileClass = current?.profileClassUuid
            ? {
                  uuid: current.profileClassUuid,
                  label: uriSuffix(current.profileClassIri),
              }
            : null;
        otherGraphUris = graphs.map(uriOf).filter(uri => uri !== graphUri);
    }

    function onClose() {
        nameUserInput = "";
        initialName = "";
        namespace = defaultNamespace;
        otherGraphUris = [];
        profileName = "";
        profileClass = null;
    }

    function editHeader() {
        // Read before closing: onClose resets the state this hands over.
        const target = profileClass;
        showDialog = false;
        onClose();
        onEditHeader?.(target);
    }

    async function renameGraph() {
        const oldGraphUri = graphUri;
        const newGraphUri = resolvedGraphUri;

        const { error } = await graphStore.renameGraph(
            workspaceName,
            oldGraphUri,
            newGraphUri,
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

        {#if graphExists}
            <div class="mt-1 mb-1 h-6 text-sm">Schema already exists</div>
        {/if}

        {#if profileName}
            <!--
              A profile names itself, and that name is what the tree shows - so renaming the
              graph here changes nothing the reader can see unless they are told where the
              name actually comes from.
            -->
            <!-- prettier-ignore -->
            <div class="text-nav-text mt-2 text-sm">
                Shown as <span class="font-semibold">{profileName}</span>, from the profile header.
                <button
                    type="button"
                    class="text-blue cursor-pointer underline"
                    onclick={editHeader}
                >
                    {profileClass
                        ? `Edit on ${profileClass.label}`
                        : "Edit profile header"}
                </button>
            </div>
        {/if}
    </div>
</ActionDialog>
