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
        faBan,
        faCheck,
        faExclamation,
        faFileCirclePlus,
        faFileImport,
        faFolderOpen,
        faPen,
        faTrash,
        faWandMagicSparkles,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
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

    function askDelete(document) {
        pendingDelete = document;
        showDeleteDialog = true;
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

    /** Where a document sits among the documents, ignoring the generated entry above them. */
    function positionOf(document) {
        return workbench.documents.findIndex(
            candidate => candidate.id === document.id,
        );
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

  Each row carries its actions twice, on purpose: on the row itself, where hovering shows them
  without hiding the name behind a menu, and in the right-click menu, which is where someone
  coming from a file explorer looks for them first.
-->

<div class="flex h-full min-h-0 flex-col">
    <div class="border-border flex items-center gap-1 border-b px-3 py-2">
        <h2 class="text-default-text grow text-sm font-semibold">Documents</h2>
        {#if !readOnly}
            <button
                class="text-text-subtle hover:bg-nav-hover-background hover:text-blue cursor-pointer rounded p-1.5"
                title="New document"
                aria-label="New document"
                onclick={addDocument}
            >
                <Fa icon={faFileCirclePlus} />
            </button>
            <button
                class="text-text-subtle hover:bg-nav-hover-background hover:text-blue cursor-pointer rounded p-1.5"
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

    <ul class="min-h-0 flex-1 overflow-y-auto py-1">
        {#each workbench.entries as document (document.id)}
            {@const result = results.get(document.id)}
            {@const worst = result?.errorCount
                ? "ERROR"
                : result?.warningCount
                  ? "WARNING"
                  : null}
            {@const summary = summarise(result)}
            {@const selected = document.id === workbench.selectedId}
            {@const editable = !readOnly && !document.generated}
            {@const position = positionOf(document)}
            <li class="px-1.5 py-px">
                <ContextMenu.Root>
                    <ContextMenu.TriggerArea class="block w-full">
                        <div
                            class="group relative flex items-center gap-2 rounded-md border px-2 py-1.5 {selected
                                ? 'bg-nav-active-background text-nav-active-text border-border-select'
                                : 'hover:bg-nav-hover-background focus-within:bg-nav-hover-background border-transparent'}"
                        >
                            {#if document.generated}
                                <div
                                    class="text-text-subtle w-4 shrink-0 text-center"
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
                                            workbench.setEnabled(
                                                document.id,
                                                true,
                                            )}
                                        callOnInputFalse={() =>
                                            workbench.setEnabled(
                                                document.id,
                                                false,
                                            )}
                                    />
                                </div>
                            {/if}

                            {#if renamingId === document.id}
                                <!-- svelte-ignore a11y_autofocus -->
                                <input
                                    class="border-border-select bg-input-default-background text-default-text min-w-0 flex-1 rounded border px-1 text-sm"
                                    type="text"
                                    aria-label="Document name"
                                    autofocus
                                    bind:value={renameValue}
                                    onblur={commitRename}
                                    onkeydown={event => {
                                        if (event.key === "Enter")
                                            commitRename();
                                        if (event.key === "Escape")
                                            renamingId = null;
                                    }}
                                />
                            {:else}
                                <button
                                    class="flex min-w-0 flex-1 cursor-pointer flex-col items-start text-left"
                                    ondblclick={() => {
                                        if (editable) {
                                            startRename(document);
                                        }
                                    }}
                                    onclick={() => open(document.id)}
                                >
                                    <span
                                        class="w-full truncate text-sm leading-tight {document.enabled
                                            ? ''
                                            : 'text-text-subtle italic'}"
                                        title={document.sourceFileName ??
                                            document.name}
                                    >
                                        {document.name}
                                    </span>
                                    <span
                                        class="text-text-subtle w-full truncate text-xs leading-tight"
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

                            <!-- Nothing validates the generated shapes against their own schema. -->
                            {#if !document.generated}
                                <span class="shrink-0">
                                    {#if worst}
                                        <Fa
                                            icon={severityMeta(worst).icon}
                                            class={severityMeta(worst).text}
                                        />
                                    {:else if result}
                                        <Fa
                                            icon={VALID_ICON}
                                            class="text-green-text"
                                        />
                                    {/if}
                                </span>
                            {/if}

                            <!--
                              The actions sit over the right end of the row rather than beside the
                              name: a pane this narrow has no room for both, and a row that grows a
                              second line on hover moves every row below it.
                            -->
                            {#if editable && renamingId !== document.id}
                                <div
                                    class="absolute inset-y-0 right-1 flex items-center gap-0.5 rounded bg-inherit pl-2 opacity-0 group-hover:opacity-100 focus-within:opacity-100"
                                >
                                    <button
                                        class="text-text-subtle hover:text-blue cursor-pointer p-1 text-xs disabled:cursor-default disabled:opacity-40"
                                        title="Move up"
                                        aria-label="Move up"
                                        disabled={position <= 0}
                                        onclick={() =>
                                            workbench.move(document.id, -1)}
                                    >
                                        <Fa icon={faArrowUp} />
                                    </button>
                                    <button
                                        class="text-text-subtle hover:text-blue cursor-pointer p-1 text-xs disabled:cursor-default disabled:opacity-40"
                                        title="Move down"
                                        aria-label="Move down"
                                        disabled={position ===
                                            workbench.documents.length - 1}
                                        onclick={() =>
                                            workbench.move(document.id, 1)}
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
                                            onclick={() => askDelete(document)}
                                        >
                                            <Fa icon={faTrash} />
                                        </button>
                                    {/if}
                                </div>
                            {/if}
                        </div>
                    </ContextMenu.TriggerArea>

                    <ContextMenu.Content>
                        <ContextMenu.Item.Button
                            onSelect={() => open(document.id)}
                            disabled={selected}
                            faIcon={faFolderOpen}
                        >
                            Open
                        </ContextMenu.Item.Button>
                        {#if editable}
                            <ContextMenu.Item.Button
                                onSelect={() =>
                                    workbench.setEnabled(
                                        document.id,
                                        !document.enabled,
                                    )}
                                faIcon={document.enabled ? faBan : faCheck}
                            >
                                {document.enabled
                                    ? "Exclude from validation"
                                    : "Include in validation"}
                            </ContextMenu.Item.Button>
                            <ContextMenu.Separator />
                            <ContextMenu.Item.Button
                                onSelect={() => startRename(document)}
                                faIcon={faPen}
                            >
                                Rename
                            </ContextMenu.Item.Button>
                            <ContextMenu.Item.Button
                                onSelect={() => workbench.move(document.id, -1)}
                                disabled={position <= 0}
                                faIcon={faArrowUp}
                            >
                                Move up
                            </ContextMenu.Item.Button>
                            <ContextMenu.Item.Button
                                onSelect={() => workbench.move(document.id, 1)}
                                disabled={position ===
                                    workbench.documents.length - 1}
                                faIcon={faArrowDown}
                            >
                                Move down
                            </ContextMenu.Item.Button>
                            {#if !document.default}
                                <ContextMenu.Separator />
                                <ContextMenu.Item.Button
                                    onSelect={() => askDelete(document)}
                                    faIcon={faTrash}
                                    variant="danger"
                                >
                                    Delete
                                </ContextMenu.Item.Button>
                            {/if}
                        {/if}
                    </ContextMenu.Content>
                </ContextMenu.Root>
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
