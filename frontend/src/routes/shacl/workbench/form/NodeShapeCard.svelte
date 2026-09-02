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
     * One shape: the classes it applies to, and the rules written under it.
     *
     * Anything the shape says that the form cannot write is shown rather than hidden, and does not
     * stop the rest of the shape from being edited: an edit changes the clause it was made on and
     * leaves the others as their author wrote them.
     */

    import {
        faChevronDown,
        faChevronRight,
        faLock,
        faPlus,
        faTrash,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import { keptClauses, keptFields } from "$lib/shacl/retained.js";
    import { abbreviate } from "$lib/shacl/turtleTerms.js";

    import KeptClause from "./KeptClause.svelte";
    import KeptClauseList from "./KeptClauseList.svelte";
    import PropertyShapeCard from "./PropertyShapeCard.svelte";
    import TermPicker from "./TermPicker.svelte";

    let {
        shape,
        terms = [],
        prefixes = {},
        readOnly = false,
        expanded = false,
        ontoggle = () => {},
        onchange = () => {},
        /** A field still being typed in: the same edit, to be sent once typing pauses. */
        onedit = () => {},
        onremove = () => {},
    } = $props();

    /** The fields this card puts on screen. Anything else kept as written is listed instead. */
    const SHOWN = ["targetClasses"];

    /**
     * Rules added here but not yet written to the document, because they name no property.
     *
     * A rule with no `sh:path` says nothing, so there is nothing to write: it used to be posted
     * the instant the button was pressed, dropped by the writer, and gone from the card on the
     * next read — the button looked broken. A draft therefore stays on this card until a property
     * is picked, and only then joins the shape and is applied.
     *
     * Held raw rather than deeply reactive: the rule card writes the fields of the draft it was
     * given, and a proxied draft would make that a child mutating this component's state, which
     * Svelte reports as an ownership violation. Nothing needs to re-render while a draft is being
     * filled in — the inputs hold what was typed — and adding or dropping one is a reassignment,
     * which is reactive either way.
     */
    let drafts = $state.raw([]);

    /** Whether a picker for one more target class is on screen. */
    let addingClass = $state(false);

    /** Either the workspace forbids changes, or the shape is not one the form can write back. */
    const locked = $derived(readOnly || shape.editable === false);

    /** Only the shape's own limits are worth explaining; a read-only workspace says so elsewhere. */
    const turtleOnly = $derived(shape.editable === false);

    /**
     * Why the form will not write this shape.
     *
     * The server says it, because it is the side that knows: after clause-preserving edits the
     * remaining reasons are all about the text — a subject written as two statements, or two rules
     * so alike that an edit cannot be placed.
     */
    const readOnlyTitle = $derived(
        shape.readOnlyReason ??
            "This shape is written in a way the form cannot edit. Edit it in the Turtle view.",
    );

    const kept = $derived(keptFields(shape.retained));

    const classes = $derived(shape.targetClasses ?? []);

    const title = $derived(abbreviate(shape.iri, prefixes));

    const target = $derived(
        classes.length
            ? classes.map(iri => abbreviate(iri, prefixes)).join(", ")
            : "no class chosen",
    );

    /** Replaces one target class, or drops it when the picker was cleared. */
    function setClass(index, iri) {
        const next = [...classes];
        if (iri) {
            next[index] = iri;
        } else {
            next.splice(index, 1);
        }
        shape.targetClasses = next;
        onchange();
    }

    function addClass(iri) {
        addingClass = false;
        if (!iri) {
            return;
        }
        shape.targetClasses = [...classes, iri];
        onchange();
    }

    function addRule() {
        drafts = [...drafts, { path: null }];
    }

    function removeRule(index) {
        shape.properties = shape.properties.filter((_, at) => at !== index);
        onchange();
    }

    function removeDraft(index) {
        drafts = drafts.filter((_, at) => at !== index);
    }

    /** Moves a draft into the shape once it names a property. Until then it stays a draft. */
    function promoteDraft(index) {
        const draft = drafts[index];
        if (!draft?.path) {
            return;
        }
        drafts = drafts.filter((_, at) => at !== index);
        shape.properties = [...(shape.properties ?? []), { ...draft }];
        onchange();
    }
</script>

<div class="border-border rounded border">
    <div class="flex items-center gap-2 px-3 py-2">
        <button
            class="text-default-text flex min-w-0 flex-1 cursor-pointer items-center gap-2 text-left"
            onclick={ontoggle}
        >
            <Fa
                icon={expanded ? faChevronDown : faChevronRight}
                class="text-text-subtle"
            />
            <span class="min-w-0">
                <span class="block truncate font-mono text-sm">{title}</span>
                <span class="text-text-subtle block truncate text-xs">
                    applies to {target} · {shape.properties?.length ?? 0} rule{(shape
                        .properties?.length ?? 0) === 1
                        ? ""
                        : "s"}
                </span>
            </span>
        </button>

        {#if turtleOnly}
            <span
                class="text-text-subtle flex shrink-0 items-center gap-1 text-xs"
                title={readOnlyTitle}
            >
                <Fa icon={faLock} />
                Turtle only
            </span>
        {:else if !locked}
            <button
                class="text-text-subtle hover:text-red shrink-0 cursor-pointer p-1 text-xs"
                title="Delete this shape"
                aria-label="Delete this shape"
                onclick={onremove}
            >
                <Fa icon={faTrash} />
            </button>
        {/if}
    </div>

    {#if expanded}
        <div class="border-border space-y-3 border-t px-3 py-3">
            {#if turtleOnly}
                <p
                    class="text-text-subtle bg-background-subtle rounded p-2 text-sm"
                >
                    {readOnlyTitle}
                </p>
            {/if}

            {#if kept.has("targetClasses")}
                <KeptClause
                    label="Applies to class"
                    clauses={kept.get("targetClasses")}
                />
            {:else}
                {#each classes.length ? classes : [null] as iri, index (index)}
                    <TermPicker
                        label={index === 0 ? "Applies to class" : "and also to"}
                        kind="CLASS"
                        value={iri}
                        {terms}
                        {prefixes}
                        disabled={locked}
                        onpick={picked => setClass(index, picked)}
                    />
                {/each}
                {#if addingClass}
                    <TermPicker
                        label="and also to"
                        kind="CLASS"
                        value={null}
                        {terms}
                        {prefixes}
                        onpick={addClass}
                    />
                {:else if !locked && classes.length > 0}
                    <button
                        class="text-text-subtle hover:text-default-text cursor-pointer text-xs"
                        onclick={() => (addingClass = true)}
                    >
                        + another class
                    </button>
                {/if}
            {/if}

            {#each shape.properties ?? [] as property, index (index)}
                <PropertyShapeCard
                    {property}
                    {terms}
                    {prefixes}
                    targetClass={classes[0] ?? null}
                    readOnly={locked}
                    {onchange}
                    {onedit}
                    onremove={() => removeRule(index)}
                />
            {/each}

            {#each drafts as draft, index (index)}
                <PropertyShapeCard
                    property={draft}
                    {terms}
                    {prefixes}
                    targetClass={classes[0] ?? null}
                    readOnly={locked}
                    onchange={() => promoteDraft(index)}
                    onedit={() => promoteDraft(index)}
                    onremove={() => removeDraft(index)}
                />
            {/each}

            <KeptClauseList
                clauses={keptClauses(shape.retained, SHOWN)}
                {prefixes}
            />

            {#if !locked}
                <div class="h-8 w-40">
                    <ButtonControl variant="inline" callOnClick={addRule}>
                        <span class="flex items-center gap-2 text-sm">
                            <Fa icon={faPlus} />
                            Add a rule
                        </span>
                    </ButtonControl>
                </div>
            {/if}
        </div>
    {/if}
</div>
