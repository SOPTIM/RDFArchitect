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
     * One shape: the class it applies to, and the rules written under it.
     *
     * A shape the form cannot fully represent is shown read-only rather than hidden. Offering to
     * edit it would mean writing it back without the part the form does not model.
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
    import { writeTerm } from "$lib/shacl/turtleTerms.js";

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

    /** Either the workspace forbids changes, or the shape is not one the form can write back. */
    const locked = $derived(readOnly || shape.editable === false);

    /** Only the shape's own limits are worth explaining; a read-only workspace says so elsewhere. */
    const turtleOnly = $derived(shape.editable === false);

    /**
     * Why the form will not write this shape.
     *
     * The server says it, because it is the side that knows: a shape can be read-only for
     * something no predicate list shows, such as a second `sh:targetClass` or a message with a
     * language tag. The predicate list stays as the detail underneath.
     */
    const readOnlyTitle = $derived(
        shape.readOnlyReason ??
            "This shape uses something the form does not write back. Edit it in the Turtle view.",
    );

    const title = $derived(shortForm(shape.iri));

    const target = $derived(
        shape.targetClass ? shortForm(shape.targetClass) : "no class chosen",
    );

    function shortForm(iri) {
        if (!iri) {
            return "";
        }
        const cut = Math.max(iri.lastIndexOf("#"), iri.lastIndexOf("/"));
        return writeTerm(
            {
                iri,
                namespace: iri.slice(0, cut + 1),
                localName: iri.slice(cut + 1),
            },
            prefixes,
        );
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
                    {#if shape.unsupported?.length}
                        <br />
                        <span class="font-mono text-xs">
                            {shape.unsupported.join(", ")}
                        </span>
                    {/if}
                </p>
            {/if}

            <TermPicker
                label="Applies to class"
                kind="CLASS"
                value={shape.targetClass}
                {terms}
                {prefixes}
                disabled={locked}
                onpick={iri => {
                    shape.targetClass = iri;
                    onchange();
                }}
            />

            {#each shape.properties ?? [] as property, index (index)}
                <PropertyShapeCard
                    {property}
                    {terms}
                    {prefixes}
                    targetClass={shape.targetClass}
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
                    targetClass={shape.targetClass}
                    readOnly={locked}
                    onchange={() => promoteDraft(index)}
                    onedit={() => promoteDraft(index)}
                    onremove={() => removeDraft(index)}
                />
            {/each}

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
