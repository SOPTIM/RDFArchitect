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
    import {
        graphLabeller,
        graphUri as uriOf,
    } from "$lib/utils/graph-label.js";
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
    /**
     * Where the name the profile gives itself is edited, or null when the profile offers nowhere.
     * A CGMES 3.0 profile keeps it on an ontology object; a CGMES 2.4.15 profile has no such
     * object and fixes it on a class instead. A profile with neither must be left alone: an
     * ontology object written into a CIM16 graph is a header that profile never reads back.
     */
    let headerTarget = $state(null);

    const trimmedName = $derived(nameUserInput.trim());
    const localName = $derived(toLocalName(trimmedName));
    const resolvedGraphUri = $derived(localName ? namespace + localName : "");
    const graphExists = $derived(
        !!resolvedGraphUri && otherGraphUris.includes(resolvedGraphUri),
    );
    /**
     * A rename is a change of the graph URI, and the URI is built from the name rather than being
     * it: "My Notes" and "MyNotes" are the same graph, and renaming one to the other would report
     * success for a request the backend turns into nothing.
     */
    const uriChanged = $derived(
        !!resolvedGraphUri && resolvedGraphUri !== graphUri,
    );
    const disableSubmit = $derived(
        !resolvedGraphUri || graphExists || !uriChanged,
    );
    /** Shown where the typed name cannot stand in a URI as written. */
    const spelledAs = $derived(localName !== trimmedName ? localName : "");

    async function onOpen() {
        const uri = graphUri ? new URI(graphUri) : null;
        namespace = uri?.prefix || defaultNamespace;
        initialName = uri?.suffix ?? "";
        nameUserInput = initialName;

        const graphs = (await graphStore.getGraphs(workspaceName)) ?? [];
        const nameOf = graphLabeller(graphs);
        const current = graphs.find(graph => uriOf(graph) === graphUri);
        // Named among the other schemas, not on its own: the schemas this dialog has to tell
        // apart are exactly the ones whose profiles name themselves alike.
        profileName = current?.label || current?.keyword ? nameOf(current) : "";
        headerTarget = headerTargetOf(current);
        otherGraphUris = graphs.map(uriOf).filter(uri => uri !== graphUri);
    }

    function headerTargetOf(graph) {
        if (graph?.ontologyHeader) {
            return { kind: "ontology" };
        }
        if (graph?.profileClassUuid) {
            return {
                kind: "class",
                uuid: graph.profileClassUuid,
                label: uriSuffix(graph.profileClassIri),
            };
        }
        return null;
    }

    function onClose() {
        nameUserInput = "";
        initialName = "";
        namespace = defaultNamespace;
        otherGraphUris = [];
        profileName = "";
        headerTarget = null;
    }

    function editHeader() {
        // Read before closing: onClose resets the state this hands over.
        const target = headerTarget;
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
        {:else if spelledAs}
            <!--
              The graph URI is built from the name, so a name an IRI cannot carry as written is
              spelled differently in it - and without saying so, Rename simply stays greyed out.
            -->
            <div class="mt-1 mb-1 h-6 text-sm">
                Stored as {spelledAs}
            </div>
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
                {#if headerTarget}
                    <button
                        type="button"
                        class="text-blue cursor-pointer underline"
                        onclick={editHeader}
                    >
                        {headerTarget.kind === "class"
                            ? `Edit on ${headerTarget.label}`
                            : "Edit profile header"}
                    </button>
                {/if}
            </div>
        {/if}
    </div>
</ActionDialog>
