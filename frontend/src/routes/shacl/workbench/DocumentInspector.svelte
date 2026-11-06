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
    import { faLocationCrosshairs } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import Badge from "$lib/components/Badge.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { extractOutline } from "$lib/shacl/outline.js";
    import { uriSuffix } from "$lib/utils/iri.js";

    import SchemaChecklist from "./SchemaChecklist.svelte";

    let { workbench, onreveal = () => {} } = $props();

    /**
     * How many shapes are listed at once. An official profile runs to thousands, and this list is
     * rebuilt as the text changes; past this many a search is the way to find one anyway.
     */
    const SHAPES_SHOWN = 200;

    let search = $state("");

    /** A comparison is about a schema, and the tree shows it so: this must not say otherwise. */
    const document = $derived(
        workbench.comparedSchema ? null : workbench.selected,
    );
    const shapes = $derived(extractOutline(workbench.text));
    const matches = $derived.by(() => {
        const wanted = search.trim().toLowerCase();
        return wanted === ""
            ? shapes
            : shapes.filter(
                  shape =>
                      shape.name.toLowerCase().includes(wanted) ||
                      shape.targetClass?.toLowerCase().includes(wanted),
              );
    });

    /** URIs the document names that no schema of this workspace has. */
    const absentSchemas = $derived(
        (document?.schemas ?? []).filter(
            uri => !workbench.schemas.some(schema => schema.uri === uri),
        ),
    );

    async function setSchemas(schemas) {
        if (!(await workbench.setSchemas(document.id, schemas))) {
            toastStore.error(
                "Not changed",
                workbench.lastError ??
                    "Which schemas the document applies to could not be changed.",
            );
        }
    }
</script>

<!--
  @component
  What the open document is and what is in it.

  The shape list is read off the editor's text, so it keeps working while the document does not
  parse — which is when navigating a long file matters most. Clicking a shape jumps to it.
-->

<div class="flex h-full min-h-0 flex-col">
    <div class="border-border flex items-center gap-2 border-b px-3 py-2">
        <h2 class="text-default-text grow text-sm font-semibold">Inspector</h2>
    </div>

    <div class="min-h-0 flex-1 space-y-4 overflow-y-auto px-3 py-3 text-sm">
        {#if !document}
            <p class="text-text-subtle italic">No document open.</p>
        {:else}
            <section>
                <h3
                    class="text-text-subtle mb-1 text-xs font-semibold uppercase"
                >
                    Document
                </h3>
                <dl class="text-default-text space-y-1">
                    <div class="flex gap-2">
                        <dt class="text-text-subtle w-32 shrink-0">Name</dt>
                        <dd class="min-w-0 break-words">{document.name}</dd>
                    </div>
                    <div class="flex items-center gap-2">
                        <dt class="text-text-subtle w-32 shrink-0">Origin</dt>
                        <dd>
                            <Badge
                                text={document.origin === "IMPORTED"
                                    ? "Imported"
                                    : "Authored"}
                                variant={document.origin === "IMPORTED"
                                    ? "external"
                                    : "default"}
                            />
                        </dd>
                    </div>
                    {#if document.sourceFileName}
                        <div class="flex gap-2">
                            <dt class="text-text-subtle w-32 shrink-0">File</dt>
                            <dd class="min-w-0 break-all">
                                {document.sourceFileName}
                            </dd>
                        </div>
                    {/if}
                    <!--
                      What the document holds, not only how big it is: ten thousand triples says
                      little until it reads as 145 node shapes and 572 property shapes.
                    -->
                    <div class="flex gap-2">
                        <dt class="text-text-subtle w-32 shrink-0">
                            Node shapes
                        </dt>
                        <dd>{document.nodeShapeCount ?? 0}</dd>
                    </div>
                    <div class="flex gap-2">
                        <dt class="text-text-subtle w-32 shrink-0">
                            Property shapes
                        </dt>
                        <dd>{document.propertyShapeCount ?? 0}</dd>
                    </div>
                    {#if document.sparqlConstraintCount}
                        <div class="flex gap-2">
                            <dt class="text-text-subtle w-32 shrink-0">
                                SPARQL constraints
                            </dt>
                            <dd>{document.sparqlConstraintCount}</dd>
                        </div>
                    {/if}
                    <div class="flex gap-2">
                        <dt class="text-text-subtle w-32 shrink-0">Triples</dt>
                        <dd>{document.tripleCount ?? 0}</dd>
                    </div>
                    <div class="flex gap-2">
                        <dt class="text-text-subtle w-32 shrink-0">
                            Validation
                        </dt>
                        <dd>
                            {document.enabled
                                ? "Takes part"
                                : "Excluded while disabled"}
                        </dd>
                    </div>
                </dl>
            </section>

            <section>
                <h3
                    class="text-text-subtle mb-1 text-xs font-semibold uppercase"
                    title="Decides where the document is listed and which schema's comparison reads it. It is validated against every schema of the workspace either way."
                >
                    Applies to
                </h3>
                <SchemaChecklist
                    schemas={workbench.schemas}
                    selected={document.schemas ?? []}
                    readonly={workbench.readOnly}
                    onchange={setSchemas}
                />
                {#if absentSchemas.length > 0}
                    <ul class="mt-1 flex flex-col gap-1">
                        {#each absentSchemas as uri (uri)}
                            <li class="text-text-subtle text-xs" title={uri}>
                                {uriSuffix(uri)} — not in this workspace; importing
                                it again brings it back
                                {#if !workbench.readOnly}
                                    <button
                                        class="text-blue cursor-pointer underline underline-offset-2"
                                        onclick={() =>
                                            setSchemas(
                                                document.schemas.filter(
                                                    candidate =>
                                                        candidate !== uri,
                                                ),
                                            )}
                                    >
                                        Forget it
                                    </button>
                                {/if}
                            </li>
                        {/each}
                    </ul>
                {/if}
            </section>

            <section>
                <h3
                    class="text-text-subtle mb-1 text-xs font-semibold uppercase"
                >
                    Shapes ({shapes.length})
                </h3>
                {#if shapes.length === 0}
                    <p class="text-text-subtle italic">
                        No shapes in this document.
                    </p>
                {:else}
                    <input
                        class="border-border bg-input-default-background text-default-text mb-1 h-7 w-full rounded border px-2 text-xs"
                        type="search"
                        placeholder="Search by name or target class"
                        aria-label="Search the shapes"
                        bind:value={search}
                    />
                    <ul class="flex flex-col">
                        {#each matches.slice(0, SHAPES_SHOWN) as shape (shape.line)}
                            <li>
                                <button
                                    class="hover:bg-nav-hover-background flex w-full cursor-pointer items-baseline gap-2 rounded px-1 py-0.5 text-left"
                                    onclick={() => onreveal(shape.line, 1)}
                                >
                                    <Fa
                                        icon={faLocationCrosshairs}
                                        class="text-text-subtle shrink-0 text-xs"
                                    />
                                    <span class="min-w-0 flex-1">
                                        <span
                                            class="text-default-text block truncate font-mono text-xs"
                                            title={shape.name}
                                        >
                                            {shape.name}
                                        </span>
                                        <span
                                            class="text-text-subtle block truncate text-xs"
                                        >
                                            {shape.kind === "PropertyShape"
                                                ? "property shape"
                                                : "node shape"}{shape.targetClass
                                                ? ` · targets ${shape.targetClass}`
                                                : ""}
                                        </span>
                                    </span>
                                    <span
                                        class="text-text-subtle shrink-0 text-xs"
                                    >
                                        {shape.line}
                                    </span>
                                </button>
                            </li>
                        {/each}
                    </ul>
                    {#if matches.length === 0}
                        <p class="text-text-subtle text-xs italic">
                            No shape matches.
                        </p>
                    {:else if matches.length > SHAPES_SHOWN}
                        <p class="text-text-subtle mt-1 text-xs">
                            Showing {SHAPES_SHOWN} of {matches.length}. Search
                            to find the others.
                        </p>
                    {/if}
                {/if}
            </section>
        {/if}
    </div>
</div>
