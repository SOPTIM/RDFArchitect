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
    import {
        newShape,
        newShapeIri,
        shapeNamespaceOf,
    } from "$lib/shacl/formState.svelte.js";
    import {
        abbreviate,
        parsePrefixes,
        resolveTerm,
    } from "$lib/shacl/turtleTerms.js";

    import NodeShapeCard from "./form/NodeShapeCard.svelte";
    import PropertyShapeCard from "./form/PropertyShapeCard.svelte";
    import SharedRuleDialog from "./form/SharedRuleDialog.svelte";
    import TermPicker from "./form/TermPicker.svelte";

    let {
        form,
        turtle = "",
        terms = [],
        readOnly = false,
        /** Which document the buffer holds, so the form can tell another one from an edit. */
        documentId = undefined,
        /**
         * Called with the new text and the text the edit was made to. May answer `false` when
         * the buffer no longer holds that text and the result was dropped; the form then reads
         * the buffer again rather than go on describing text that is not there.
         */
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

    /** The row asking what a new shape is for, while it is open. */
    let adding = $state(null);

    const prefixes = $derived(parsePrefixes(turtle));

    const sharedRules = $derived(
        matchingRules(form.propertyShapes, filters, prefixes),
    );

    const filters = $derived({
        filter: form.filter,
        lockedOnly: form.lockedOnly,
        pinned: form.added,
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
        if (documentId !== undefined) {
            form?.showDocument(documentId);
        }
    });

    $effect(() => {
        form?.read(turtle);
    });

    /**
     * Sends what is still waiting for a pause when the form goes, rather than a moment later.
     *
     * The form is unmounted whenever the Turtle view is shown, and an edit held back for a pause
     * would otherwise arrive after the user had started typing there.
     */
    $effect(() => {
        const current = form;
        return () => current?.flush();
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

    /**
     * Gives one shape its own copy of a shared rule. Answers why, when the copy was refused.
     *
     * A refusal is almost always the name — one the document already uses — so it is left to the
     * dialog, which stays open to take another, instead of being reported and put back here.
     */
    async function splitSharedRule(newIri) {
        const { rule, shapeIri } = sharedEdit;
        const result = await form.applyRule(turtle, rule, {
            newIri,
            nodeShapeIri: shapeIri,
            sourceIndex: rule.sourceIndex,
        });
        if (!result) {
            const reason = form.error ?? "The copy could not be made.";
            form.failure = null;
            return reason;
        }
        handle(result);
        return null;
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

    /**
     * Puts an edit's text into the buffer, or the card back to what the document says.
     *
     * A refused edit leaves the card holding the value the server would not write; showing it as
     * though it had been written is how the form came to disagree with its own document. So the
     * document is read again, and the reason stays on the card.
     */
    function handle(result) {
        if (!result) {
            toastStore.error(
                "Not applied",
                form.error ?? "The change could not be applied.",
            );
            form.reload(turtle);
            return;
        }
        if (onturtle(result.turtle, result.base) === false) {
            form.reload(turtle);
            return;
        }
        onvalidate();
        result.warnings.forEach(warning =>
            toastStore.warning("Shape rewritten", warning),
        );
    }

    /** Every IRI the document names a shape or rule with, which a new one must not reuse. */
    function takenIris() {
        return new Set(
            [...form.shapes, ...form.propertyShapes].map(entry => entry.iri),
        );
    }

    function suggestedName(targetClass) {
        const schemaNamespaces = [
            ...new Set(terms.map(term => term.namespace)),
        ];
        const namespace = shapeNamespaceOf(
            form.shapes,
            prefixes,
            schemaNamespaces,
        );
        return abbreviate(
            newShapeIri(namespace, targetClass, takenIris()),
            prefixes,
        );
    }

    function startAdding() {
        adding = {
            targetClass: null,
            name: suggestedName(null),
            named: false,
            problem: null,
        };
    }

    /** A class picked for the new shape names it too, unless a name was typed already. */
    function pickClass(iri) {
        adding.targetClass = iri;
        if (!adding.named) {
            adding.name = suggestedName(iri);
        }
    }

    function nameShape(text) {
        adding.name = text;
        adding.named = true;
        adding.problem = null;
    }

    async function addShape() {
        const typed = adding.name.trim();
        const iri =
            resolveTerm(typed, prefixes) ??
            (/^(?:[A-Za-z][A-Za-z0-9+.-]*:\/\/|urn:)\S+$/.test(typed)
                ? typed
                : null);
        if (!iri) {
            adding.problem =
                "Write the name as a prefixed name the document binds, such as ex:BreakerShape, or as a full IRI.";
            return;
        }
        if (takenIris().has(iri)) {
            adding.problem =
                "The document already has a shape or rule of that name.";
            return;
        }
        const shape = newShape(iri, adding.targetClass);
        adding = null;
        form.added.add(iri);
        form.expanded.add(iri);
        await apply(shape);
        scrollTo(iri);
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
                    callOnClick={startAdding}
                    disabled={form.applying ||
                        form.parseError !== null ||
                        adding !== null ||
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

    {#if adding && !readOnly}
        <!--
          A shape is asked what it is for before it is written, because that is also what it is
          called: `<Class>Shape`, in the namespace the document's shapes already use.
        -->
        <div
            class="border-border bg-background-subtle flex shrink-0 flex-wrap items-start gap-2 border-b px-3 py-2"
            data-adding-shape
        >
            <div class="min-w-48 flex-1">
                <TermPicker
                    label="For every instance of"
                    kind="CLASS"
                    value={adding.targetClass}
                    {terms}
                    {prefixes}
                    onpick={pickClass}
                />
            </div>
            <div class="min-w-48 flex-1">
                <TextEditControl
                    label="Called"
                    value={adding.name}
                    warn={adding.problem !== null}
                    callOnInput={nameShape}
                />
                {#if adding.problem}
                    <p class="text-red-text mt-0.5 text-xs" role="alert">
                        {adding.problem}
                    </p>
                {/if}
            </div>
            <div class="mt-6 flex shrink-0 gap-2">
                <div class="h-8 w-20">
                    <ButtonControl
                        variant="inline"
                        callOnClick={addShape}
                        disabled={form.applying || adding.name.trim() === ""}
                    >
                        <span class="text-sm">Add</span>
                    </ButtonControl>
                </div>
                <div class="h-8 w-20">
                    <ButtonControl
                        variant="inline"
                        callOnClick={() => (adding = null)}
                    >
                        <span class="text-sm">Cancel</span>
                    </ButtonControl>
                </div>
            </div>
        </div>
    {/if}

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
                {#each shapes as shape, index (shape.iri)}
                    <NodeShapeCard
                        bind:shape={shapes[index]}
                        {terms}
                        {prefixes}
                        sharedRules={form.propertyShapes ?? []}
                        readOnly={readOnly || shape.editable === false}
                        expanded={form.expanded.has(shape.iri)}
                        failureOf={key => form.failureOf(key)}
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
                        {#each sharedRules as rule, index (rule.iri)}
                            <PropertyShapeCard
                                bind:property={sharedRules[index]}
                                {terms}
                                {prefixes}
                                {readOnly}
                                failure={form.failureOf(`rule:${rule.iri}`)}
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
