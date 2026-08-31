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
    /**
     * Exports a chosen set of a graph's constraints as one file.
     *
     * A graph holds several constraints documents, so which of them belongs in an export is a
     * question only the user can answer. The dialog asks it directly instead of offering three
     * fixed combinations whose names — "generate", "custom", "combined" — said nothing about which
     * documents they covered.
     */

    import { listShapesDocuments } from "$lib/api/generated/index.ts";
    import Badge from "$lib/components/Badge.svelte";
    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import GraphExport from "$lib/GraphExport.svelte";

    let {
        showDialog = $bindable(),
        lockedWorkspaceName,
        lockedGraphUri,
    } = $props();

    let disablePrimary = $state(false);
    let shaclExportDialog = $state(null);

    let documents = $state([]);
    /** Ids of the documents to include, kept as a set so the checkboxes stay independent. */
    let selected = $state(new Set());
    let includeGenerated = $state(true);
    let loadFailed = $state(false);

    let nothingChosen = $derived(!includeGenerated && selected.size === 0);

    let onPrimary = $derived(
        shaclExportDialog && !nothingChosen
            ? () => shaclExportDialog.handleExport(exportUrl)
            : null,
    );

    /**
     * Loads the graph's documents whenever the selection changes.
     *
     * Everything is ticked to begin with, so exporting without reading the list gives the whole
     * set rather than an empty file.
     */
    async function loadDocuments(workspaceName, graphUri) {
        if (!workspaceName || !graphUri) {
            documents = [];
            selected = new Set();
            return;
        }
        const { data, error } = await listShapesDocuments({
            path: { datasetName: workspaceName, graphURI: graphUri },
        });
        loadFailed = !!error;
        documents = error
            ? []
            : [...(data ?? [])].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
        selected = new Set(documents.map(document => document.id));
    }

    function toggle(documentId, include) {
        const next = new Set(selected);
        if (include) {
            next.add(documentId);
        } else {
            next.delete(documentId);
        }
        selected = next;
    }

    function exportUrl(workspaceName, graphURI) {
        const query = [...selected].map(
            id => `documentId=${encodeURIComponent(id)}`,
        );
        query.push(`includeGenerated=${includeGenerated}`);
        return (
            PUBLIC_BACKEND_URL +
            "/api/datasets/" +
            encodeURIComponent(workspaceName) +
            "/graphs/" +
            encodeURIComponent(graphURI) +
            "/shacl/export/file?" +
            query.join("&")
        );
    }
</script>

<ActionDialog
    bind:showDialog
    primaryLabel="Export"
    disablePrimary={disablePrimary || nothingChosen}
    {onPrimary}
    title="Export Constraints (SHACL)"
>
    {#key showDialog}
        <GraphExport
            bind:this={shaclExportDialog}
            bind:showDialog
            bind:disablePrimary
            {lockedWorkspaceName}
            {lockedGraphUri}
            onselection={loadDocuments}
        />
    {/key}

    <div class="mt-3">
        <p class="text-default-text mb-1 block">Include:</p>
        <div class="border-border max-h-64 overflow-y-auto rounded border p-2">
            <CheckBoxEditControl
                label="Generated shapes (derived from the schema)"
                value={includeGenerated}
                labelFirst={false}
                callOnInputTrue={() => (includeGenerated = true)}
                callOnInputFalse={() => (includeGenerated = false)}
            />

            {#if loadFailed}
                <p class="text-red-text mt-2 text-sm">
                    The constraints documents could not be listed.
                </p>
            {:else if documents.length === 0}
                <p class="text-text-subtle mt-2 text-sm italic">
                    This schema has no constraints documents.
                </p>
            {:else}
                {#each documents as document (document.id)}
                    <div class="mt-1 flex items-center gap-2">
                        <CheckBoxEditControl
                            label={document.name}
                            value={selected.has(document.id)}
                            labelFirst={false}
                            callOnInputTrue={() => toggle(document.id, true)}
                            callOnInputFalse={() => toggle(document.id, false)}
                        />
                        <span class="text-text-subtle text-xs">
                            {document.tripleCount ?? 0} triples
                        </span>
                        {#if !document.enabled}
                            <!--
                              Disabled means "takes no part in validation", not "cannot be
                              exported" — so it can still be ticked, and the badge says why it
                              looks different from the others.
                            -->
                            <Badge text="Disabled" variant="muted" />
                        {/if}
                        {#if document.origin === "IMPORTED"}
                            <Badge text="Imported" variant="external" />
                        {/if}
                    </div>
                {/each}
            {/if}
        </div>
        {#if nothingChosen}
            <p class="text-text-subtle mt-1 text-sm">
                Pick at least one thing to export.
            </p>
        {/if}
    </div>
</ActionDialog>
