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
        faLock,
        faPlus,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import CollapseToggle from "$lib/components/CollapseToggle.svelte";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import {
        entryAtLine,
        matchingRules,
        matchingShapes,
    } from "$lib/shacl/formNavigation.js";
    import { newShape, shapeNamespaceOf } from "$lib/shacl/formState.svelte.js";
    import { parsePrefixes } from "$lib/shacl/turtleTerms.js";

    import NodeShapeCard from "./form/NodeShapeCard.svelte";
    import PropertyShapeCard from "./form/PropertyShapeCard.svelte";
    import SharedRuleDialog from "./form/SharedRuleDialog.svelte";

    let {
        form,
        turtle = "",
        terms = [],
        readOnly = false,
        onturtle = () => {},
        onvalidate = () => {},
        /** Shows a line of the document in the Turtle view. */
        onreveal = () => {},
    } = $props();

    /** A change to a rule several shapes use, waiting for the user to say how far it should go. */
    let sharedEdit = $state(null);
    let askingAboutSharedRule = $state(false);

    /**
     * Whether the document's own rules are on screen. Closed to begin with, deliberately.
     *
     * A rule card is a dozen controls and an official `-Con-Simple-` profile holds some five
     * hundred of them, so rendering the lot on open would cost more than the section is worth to
     * someone who came to look at a shape. Each rule is also shown under every shape referencing
     * it, which is the way most people will reach one.
     */
    let showingSharedRules = $state(false);

    let list = $state(null);

    const prefixes = $derived(parsePrefixes(turtle));

    const sharedRules = $derived(
        matchingRules(form.propertyShapes, filters, prefixes),
    );

    const filters = $derived({
        filter: form.filter,
        lockedOnly: form.lockedOnly,
    });

    /** The shapes the filter leaves, in the order the document writes them. */
    const shapes = $derived(matchingShapes(form.shapes, filters, prefixes));

    const filtering = $derived(form.filter.trim() !== "" || form.lockedOnly);

    /**
     * Opens the card holding a line somebody asked for from outside the form.
     *
     * The line arrives before the shapes do — the Turtle view knows one the moment the form is
     * switched to, and reading the document is a round trip — so it waits here until there is
     * something to match it against.
     */
    $effect(() => {
        if (form.focusLine === null || form.shapes.length === 0) {
            return;
        }
        const entry = entryAtLine(
            form.shapes,
            form.propertyShapes,
            form.focusLine,
        );
        form.focusLine = null;
        if (!entry) {
            return;
        }
        // A line inside a shared rule is in neither shape above it; the section holding it opens
        // instead, which is where that rule's card is.
        if (entry.kind === "rule") {
            showingSharedRules = true;
        } else {
            form.expanded.add(entry.iri);
        }
        scrollTo(entry.iri);
    });

    $effect(() => {
        form?.read(turtle);
    });

    /** Brings a card into view once it has been rendered. */
    function scrollTo(iri) {
        requestAnimationFrame(() => {
            list?.querySelector(
                `[data-shape="${CSS.escape(iri)}"]`,
            )?.scrollIntoView({ block: "nearest" });
        });
    }

    /** Sends one shape back and puts the resulting document into the buffer. */
    async function apply(shape) {
        const result = await form.applyShape(turtle, shape);
        handle(result);
    }

    /**
     * The same edit, once typing pauses.
     *
     * The wait lives in the form view rather than here so that leaving the tab, or saving, still
     * sends what was typed: this component is unmounted when the Turtle view is shown.
     */
    function applySoon(shape) {
        form.schedule(turtle, shape, handle);
    }

    async function remove(shape) {
        const result = await form.removeShape(turtle, shape.iri);
        handle(result);
    }

    /**
     * Writes back a rule the document holds as a shape of its own.
     *
     * A rule more than one shape uses is not written until the user has said what the change is
     * meant to reach, because both answers are reasonable and only one of them is undoable by
     * looking at it: changing a shared cardinality quietly retunes every class that relies on it.
     *
     * @param shapeIri the shape the change was made under, or null on the rule's own card
     */
    async function applyRule(rule, shapeIri = null) {
        if ((rule.usedBy?.length ?? 0) > 1) {
            sharedEdit = { rule, shapeIri };
            askingAboutSharedRule = true;
            return;
        }
        handle(await form.applyRule(turtle, rule));
    }

    /**
     * The same, once typing pauses — but never for a shared rule.
     *
     * A dialog per keystroke would be unusable, so a shared rule's typed fields are held until the
     * field is left, which is when the card asks for the change rather than merely noting it.
     */
    function applyRuleSoon(rule) {
        if ((rule.usedBy?.length ?? 0) > 1) {
            return;
        }
        form.scheduleRule(turtle, rule, handle);
    }

    async function splitSharedRule(newIri) {
        const { rule, shapeIri } = sharedEdit;
        handle(
            await form.applyRule(turtle, rule, {
                newIri,
                nodeShapeIri: shapeIri,
                sourceIndex: rule.sourceIndex,
            }),
        );
    }

    async function changeSharedRuleForAll() {
        handle(await form.applyRule(turtle, sharedEdit.rule));
    }

    /**
     * Puts the card back to what the document says.
     *
     * The card writes the field as it is typed, so by the time the question is asked the change is
     * already on screen. Answering "neither" has to take it off again, and the document is the
     * only thing that knows what was there before.
     */
    function forgetSharedRuleEdit() {
        form.reload(turtle);
    }

    function handle(result) {
        if (!result) {
            toastStore.error(
                "Not applied",
                form.error ?? "The change could not be applied.",
            );
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
        form.expanded.add(shape.iri);
        await apply(shape);
    }
</script>

<div class="flex h-full min-h-0 flex-col">
    <div
        class="border-border flex shrink-0 items-center gap-2 border-b px-3 py-2"
    >
        <h2 class="text-default-text shrink-0 text-sm font-semibold">Shapes</h2>
        <div class="min-w-0 grow">
            <TextEditControl
                value={form.filter}
                placeholder="filter by class, property, name or message"
                callOnInput={text => (form.filter = text)}
            />
        </div>
        <button
            class="flex shrink-0 cursor-pointer items-center gap-1 rounded px-2 py-1 text-xs {form.lockedOnly
                ? 'bg-background-select text-nav-active-text'
                : 'text-text-subtle hover:text-default-text'}"
            title="Show only what the form will not write"
            aria-pressed={form.lockedOnly}
            onclick={() => (form.lockedOnly = !form.lockedOnly)}
        >
            <Fa icon={faLock} />
            Locked only
        </button>
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

    <div class="min-h-0 flex-1 overflow-y-auto p-3" bind:this={list}>
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
        {:else if form.shapes.length === 0 && form.propertyShapes.length === 0}
            <div class="flex h-full items-center justify-center">
                <EmptyStateCard
                    title="No shapes yet"
                    description="Add a shape to say which class it applies to and what its values must look like."
                />
            </div>
        {:else if filtering && shapes.length === 0 && sharedRules.length === 0}
            <div class="flex h-full items-center justify-center">
                <EmptyStateCard
                    title="Nothing matches"
                    description="No shape or rule in this document matches what you are looking for."
                />
            </div>
        {:else}
            <div class="flex flex-col gap-2">
                {#each shapes as shape (shape.iri)}
                    <NodeShapeCard
                        {shape}
                        {terms}
                        {prefixes}
                        sharedRules={form.propertyShapes ?? []}
                        readOnly={readOnly || shape.editable === false}
                        expanded={form.expanded.has(shape.iri)}
                        ontoggle={() => form.toggle(shape.iri)}
                        onchange={() => apply(shape)}
                        onedit={() => applySoon(shape)}
                        onremove={() => remove(shape)}
                        onrulechange={rule => applyRule(rule, shape.iri)}
                        onruleedit={applyRuleSoon}
                        {onreveal}
                    />
                {/each}
            </div>

            {#if sharedRules.length}
                <!--
                  The rules the document writes on their own, listed once. In an official
                  -Con-Simple- profile this is where every constraint in the file lives, and the
                  shapes above are little more than lists of references to it.
                -->
                <div class="mt-4 mb-2">
                    <CollapseToggle
                        expanded={showingSharedRules}
                        label="Shared rules"
                        onclick={() =>
                            (showingSharedRules = !showingSharedRules)}
                    >
                        <span class="text-sm font-semibold">
                            Shared rules ({sharedRules.length})
                        </span>
                    </CollapseToggle>
                </div>
                {#if showingSharedRules}
                    <div class="flex flex-col gap-2">
                        {#each sharedRules as rule (rule.iri)}
                            <PropertyShapeCard
                                property={rule}
                                {terms}
                                {prefixes}
                                {readOnly}
                                onchange={() => applyRule(rule)}
                                onedit={() => applyRuleSoon(rule)}
                                {onreveal}
                            />
                        {/each}
                    </div>
                {/if}
            {/if}
        {/if}
    </div>
</div>

<SharedRuleDialog
    bind:showDialog={askingAboutSharedRule}
    rule={sharedEdit?.rule}
    shapeIri={sharedEdit?.shapeIri}
    {prefixes}
    onsplit={splitSharedRule}
    onall={changeSharedRuleForAll}
    oncancel={forgetSharedRuleEdit}
/>
