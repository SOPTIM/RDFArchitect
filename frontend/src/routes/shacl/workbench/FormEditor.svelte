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
     * The document's shapes as a form, for people who do not read Turtle.
     *
     * Edits go through the backend and come back as new document text, which is put straight into
     * the same buffer the Turtle view shows. Only the edited shape's statement is rewritten, so a
     * form edit on an imported ENTSO-E file leaves every other byte of it alone.
     */

    import {
        faCircleExclamation,
        faPlus,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { newShape, shapeNamespaceOf } from "$lib/shacl/formState.svelte.js";
    import { parsePrefixes } from "$lib/shacl/turtleTerms.js";

    import NodeShapeCard from "./form/NodeShapeCard.svelte";

    let {
        form,
        turtle = "",
        terms = [],
        readOnly = false,
        onturtle = () => {},
        onvalidate = () => {},
    } = $props();

    let expandedIri = $state(null);

    const prefixes = $derived(parsePrefixes(turtle));

    $effect(() => {
        form?.read(turtle);
    });

    /** Sends one shape back and puts the resulting document into the buffer. */
    async function apply(shape) {
        const result = await form.applyShape(turtle, shape);
        handle(result);
    }

    async function remove(shape) {
        const result = await form.removeShape(turtle, shape.iri);
        handle(result);
    }

    function handle(result) {
        if (!result) {
            toastStore.error("Not applied", "The change could not be applied.");
            return;
        }
        onturtle(result.turtle);
        onvalidate();
        result.warnings.forEach(warning =>
            toastStore.warning("Shape rewritten", warning),
        );
    }

    async function addShape() {
        const namespace = shapeNamespaceOf(form.shapes, prefixes);
        const existing = new Set(form.shapes.map(shape => shape.iri));
        let name = "New";
        let suffix = 1;
        while (existing.has(`${namespace}${name}Shape`)) {
            suffix += 1;
            name = `New${suffix}`;
        }
        const shape = newShape(namespace, null, name);
        expandedIri = shape.iri;
        await apply(shape);
    }
</script>

<div class="flex h-full min-h-0 flex-col">
    <div
        class="border-border flex shrink-0 items-center gap-2 border-b px-3 py-2"
    >
        <h2 class="text-default-text grow text-sm font-semibold">Shapes</h2>
        {#if !readOnly}
            <div class="h-7 w-32">
                <ButtonControl
                    height={7}
                    variant="inline"
                    callOnClick={addShape}
                    disabled={form.applying ||
                        form.parseError !== null ||
                        readOnly}
                >
                    <span class="flex items-center gap-2 text-sm">
                        <Fa icon={faPlus} />
                        Add shape
                    </span>
                </ButtonControl>
            </div>
        {/if}
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto p-3">
        {#if form.loading && form.shapes.length === 0}
            <div class="flex h-full items-center justify-center">
                <LoadingSpinner />
            </div>
        {:else if form.parseError}
            <div
                class="bg-red-background border-red-border text-red-text flex items-start gap-3 rounded border p-4"
            >
                <Fa icon={faCircleExclamation} class="mt-0.5" />
                <div>
                    <p class="text-sm font-semibold">
                        This document cannot be shown as a form yet.
                    </p>
                    <p class="mt-1 text-sm">
                        {form.parseError.message}
                        {#if form.parseError.line}
                            (line {form.parseError.line}, column {form
                                .parseError.column})
                        {/if}
                    </p>
                    <p class="mt-1 text-sm">
                        Fix it in the Turtle view and come back.
                    </p>
                </div>
            </div>
        {:else if form.shapes.length === 0}
            <div class="flex h-full items-center justify-center">
                <EmptyStateCard
                    title="No shapes yet"
                    description="Add a shape to say which class it applies to and what its values must look like."
                />
            </div>
        {:else}
            <div class="flex flex-col gap-2">
                {#each form.shapes as shape (shape.iri)}
                    <NodeShapeCard
                        {shape}
                        {terms}
                        {prefixes}
                        readOnly={readOnly || shape.editable === false}
                        expanded={expandedIri === shape.iri}
                        ontoggle={() =>
                            (expandedIri =
                                expandedIri === shape.iri ? null : shape.iri)}
                        onchange={() => apply(shape)}
                        onremove={() => remove(shape)}
                    />
                {/each}
            </div>
        {/if}
    </div>
</div>
