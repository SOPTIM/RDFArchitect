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
    import {
        faArrowDown,
        faArrowUp,
        faExclamation,
        faFileCirclePlus,
        faFileImport,
        faPen,
        faTrash,
        faWandMagicSparkles,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { uniqueDocumentName } from "$lib/shacl/documentNames.js";
    import {
        severityMeta,
        summarise,
        VALID_ICON,
    } from "$lib/shacl/severity.js";

    let { workbench, onbeforeswitch = async () => true } = $props();

    let renamingId = $state(null);
    let renameValue = $state("");
    let pendingDelete = $state(null);
    let showDeleteDialog = $state(false);
    let fileInput;

    /** Read-only is a property of the workspace; nothing here may write when it is set. */
    const readOnly = $derived(workbench.readOnly === true);

    const results = $derived(
        new Map(workbench.results.map(result => [result.documentId, result])),
    );

    async function open(documentId) {
        if (documentId === workbench.selectedId) {
            return;
        }
        if (await onbeforeswitch()) {
            await workbench.select(documentId);
        }
    }

    async function addDocument() {
        const name = uniqueName("constraints.ttl");
        if ((await workbench.create(name)) === null) {
            toastStore.error(
                "Not created",
                "The document could not be created.",
            );
        }
    }

    async function importSelected(event) {
        const file = event.target.files?.[0];
        // Clearing the input means picking the same file twice in a row still fires a change.
        event.target.value = "";
        if (!file) {
            return;
        }
        if (
            (await workbench.importFile(file, uniqueName(file.name))) === null
        ) {
            toastStore.error(
                "Not imported",
                `"${file.name}" could not be imported.`,
            );
        }
    }

    /** The backend rejects a duplicate name, so a second "eq.ttl" becomes "eq.ttl (2)". */
    function uniqueName(name) {
        return uniqueDocumentName(
            workbench.documents.map(document => document.name),
            name,
        );
    }

    function startRename(document) {
        renamingId = document.id;
        renameValue = document.name;
    }

    async function commitRename() {
        const documentId = renamingId;
        const name = renameValue.trim();
        renamingId = null;
        const current = workbench.documents.find(
            document => document.id === documentId,
        );
        if (!documentId || name === "" || name === current?.name) {
            return;
        }
        if (!(await workbench.rename(documentId, name))) {
            toastStore.error("Not renamed", `"${name}" is already taken.`);
        }
    }

    async function confirmDelete() {
        const document = pendingDelete;
        pendingDelete = null;
        if (!(await workbench.remove(document.id))) {
            toastStore.error(
                "Not deleted",
                `"${document.name}" could not be deleted.`,
            );
        }
    }
</script>

<!--
  @component
  The graph's constraints documents: which one is open, which take part in validation, and what
  validation found in each.

  Every enabled document applies and none overrides another, so the checkbox is about
  participation and the arrows are about reading order — neither changes which constraints win.

  The first entry is not a document at all: it is what RDFArchitect derives from the schema. It is
  in the list because this is where someone looks for "what constrains this schema", and reading
  the generated rules beside an imported file is what makes a conformance report legible.
-->

<div class="flex h-full min-h-0 flex-col">
    <div class="border-border flex items-center gap-2 border-b px-3 py-2">
        <h2 class="text-default-text grow text-sm font-semibold">Documents</h2>
        {#if !readOnly}
            <button
                class="text-text-subtle hover:text-blue cursor-pointer p-1"
                title="New document"
                aria-label="New document"
                onclick={addDocument}
            >
                <Fa icon={faFileCirclePlus} />
            </button>
            <button
                class="text-text-subtle hover:text-blue cursor-pointer p-1"
                title="Import a constraints file"
                aria-label="Import a constraints file"
                onclick={() => fileInput.click()}
            >
                <Fa icon={faFileImport} />
            </button>
        {/if}
        <input
            bind:this={fileInput}
            class="hidden"
            type="file"
            accept=".ttl,.shacl,.n3,text/turtle"
            onchange={importSelected}
        />
    </div>

    <ul class="min-h-0 flex-1 overflow-y-auto">
        {#each workbench.entries as document (document.id)}
            {@const result = results.get(document.id)}
            {@const worst = result?.errorCount
                ? "ERROR"
                : result?.warningCount
                  ? "WARNING"
                  : null}
            {@const summary = summarise(result)}
            <li
                class="border-border group border-b {document.id ===
                workbench.selectedId
                    ? 'bg-background-select border-l-border-select border-l-2'
                    : 'hover:bg-nav-hover-background border-l-2 border-l-transparent'}"
            >
                <div class="flex items-center gap-2 px-2 py-1.5">
                    {#if document.generated}
                        <div
                            class="text-text-subtle shrink-0"
                            title="Derived from the schema itself — not a stored document"
                        >
                            <Fa icon={faWandMagicSparkles} />
                        </div>
                    {:else}
                        <div
                            class="shrink-0"
                            title="Take part in validation and export"
                        >
                            <CheckBoxEditControl
                                value={document.enabled}
                                readonly={readOnly}
                                callOnInputTrue={() =>
                                    workbench.setEnabled(document.id, true)}
                                callOnInputFalse={() =>
                                    workbench.setEnabled(document.id, false)}
                            />
                        </div>
                    {/if}

                    {#if renamingId === document.id}
                        <!-- svelte-ignore a11y_autofocus -->
                        <input
                            class="border-border-select min-w-0 flex-1 rounded border px-1 text-sm"
                            type="text"
                            aria-label="Document name"
                            autofocus
                            bind:value={renameValue}
                            onblur={commitRename}
                            onkeydown={event => {
                                if (event.key === "Enter") commitRename();
                                if (event.key === "Escape") renamingId = null;
                            }}
                        />
                    {:else}
                        <button
                            class="min-w-0 flex-1 cursor-pointer text-left"
                            ondblclick={() => {
                                if (!readOnly && !document.generated) {
                                    startRename(document);
                                }
                            }}
                            onclick={() => open(document.id)}
                        >
                            <span
                                class="block truncate text-sm {document.enabled
                                    ? 'text-default-text'
                                    : 'text-text-subtle italic'}"
                                title={document.sourceFileName ?? document.name}
                            >
                                {document.name}
                            </span>
                            <span
                                class="text-text-subtle block truncate text-xs"
                            >
                                {#if document.generated}
                                    from the schema · read-only
                                {:else}
                                    {document.tripleCount ?? 0} triples{summary
                                        ? ` · ${summary}`
                                        : ""}
                                {/if}
                            </span>
                        </button>
                    {/if}

                    {#if document.generated}
                        <!-- Nothing validates the generated shapes against their own schema. -->
                    {:else if worst}
                        <Fa
                            icon={severityMeta(worst).icon}
                            class="shrink-0 {severityMeta(worst).text}"
                        />
                    {:else if result}
                        <Fa
                            icon={VALID_ICON}
                            class="text-green-text shrink-0"
                        />
                    {/if}
                </div>

                {#if !readOnly && !document.generated}
                    <div
                        class="flex justify-end gap-1 px-2 pb-1 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100"
                    >
                        <button
                            class="text-text-subtle hover:text-blue cursor-pointer p-1 text-xs"
                            title="Move up"
                            aria-label="Move up"
                            onclick={() => workbench.move(document.id, -1)}
                        >
                            <Fa icon={faArrowUp} />
                        </button>
                        <button
                            class="text-text-subtle hover:text-blue cursor-pointer p-1 text-xs"
                            title="Move down"
                            aria-label="Move down"
                            onclick={() => workbench.move(document.id, 1)}
                        >
                            <Fa icon={faArrowDown} />
                        </button>
                        <button
                            class="text-text-subtle hover:text-blue cursor-pointer p-1 text-xs"
                            title="Rename"
                            aria-label="Rename"
                            onclick={() => startRename(document)}
                        >
                            <Fa icon={faPen} />
                        </button>
                        {#if !document.default}
                            <button
                                class="text-text-subtle hover:text-red cursor-pointer p-1 text-xs"
                                title="Delete"
                                aria-label="Delete"
                                onclick={() => {
                                    pendingDelete = document;
                                    showDeleteDialog = true;
                                }}
                            >
                                <Fa icon={faTrash} />
                            </button>
                        {/if}
                    </div>
                {/if}
            </li>
        {/each}
    </ul>
</div>

<ActionDialog
    bind:showDialog={showDeleteDialog}
    onClose={() => (pendingDelete = null)}
    size="w-full max-w-lg"
    primaryLabel="Delete Document"
    onPrimary={confirmDelete}
    title={pendingDelete
        ? `Delete "${pendingDelete.name}"?`
        : "Delete document?"}
    titleIcon={faExclamation}
    titleIconStyle="text-white text-xl bg-red w-8 min-h-8 p-1.5 rounded-md flex items-center justify-center"
>
    <div class="space-y-4 px-3 py-3">
        <p class="text-default-text text-sm leading-relaxed">
            Every constraint the document holds is removed from this schema.
            <br />
            This action is not reversible.
        </p>
    </div>
</ActionDialog>
