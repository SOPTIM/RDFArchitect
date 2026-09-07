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
    import { extractOutline } from "$lib/shacl/outline.js";

    let { workbench, onreveal = () => {} } = $props();

    const document = $derived(workbench.selected);
    const shapes = $derived(extractOutline(workbench.text));
    const nodeShapes = $derived(
        shapes.filter(shape => shape.kind !== "PropertyShape"),
    );
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
            <p class="text-text-subtle italic">No document selected.</p>
        {:else}
            <section>
                <h3
                    class="text-text-subtle mb-1 text-xs font-semibold uppercase"
                >
                    Document
                </h3>
                <dl class="text-default-text space-y-1">
                    <div class="flex gap-2">
                        <dt class="text-text-subtle w-24 shrink-0">Name</dt>
                        <dd class="min-w-0 break-words">{document.name}</dd>
                    </div>
                    <div class="flex items-center gap-2">
                        <dt class="text-text-subtle w-24 shrink-0">Origin</dt>
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
                            <dt class="text-text-subtle w-24 shrink-0">File</dt>
                            <dd class="min-w-0 break-all">
                                {document.sourceFileName}
                            </dd>
                        </div>
                    {/if}
                    <div class="flex gap-2">
                        <dt class="text-text-subtle w-24 shrink-0">Triples</dt>
                        <dd>{document.tripleCount ?? 0}</dd>
                    </div>
                    <div class="flex gap-2">
                        <dt class="text-text-subtle w-24 shrink-0">
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
                >
                    Shapes ({nodeShapes.length})
                </h3>
                {#if nodeShapes.length === 0}
                    <p class="text-text-subtle italic">
                        No shapes found in this document.
                    </p>
                {:else}
                    <ul class="flex flex-col">
                        {#each nodeShapes as shape (shape.line)}
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
                                        >
                                            {shape.name}
                                        </span>
                                        {#if shape.targetClass}
                                            <span
                                                class="text-text-subtle block truncate text-xs"
                                            >
                                                targets {shape.targetClass}
                                            </span>
                                        {/if}
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
                {/if}
            </section>

            <section>
                <h3
                    class="text-text-subtle mb-1 text-xs font-semibold uppercase"
                >
                    Checked against ({workbench.profiles.length})
                </h3>
                <!--
                  Constraints are checked against every profile in the workspace, not only the
                  schema they are attached to: the official cross-profile constraints files
                  reference terms from their neighbours on purpose.
                -->
                {#if workbench.profiles.length === 0}
                    <p class="text-text-subtle italic">Not validated yet.</p>
                {:else}
                    <ul class="text-text-subtle space-y-0.5 font-mono text-xs">
                        {#each workbench.profiles as profile (profile)}
                            <li class="break-all">{profile}</li>
                        {/each}
                    </ul>
                {/if}
            </section>
        {/if}
    </div>
</div>
