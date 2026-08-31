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
        expanded = false,
        ontoggle = () => {},
        onchange = () => {},
        onremove = () => {},
    } = $props();

    const readOnly = $derived(shape.editable === false);

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
        shape.properties = [...(shape.properties ?? []), { path: null }];
        onchange();
    }

    function removeRule(index) {
        shape.properties = shape.properties.filter((_, at) => at !== index);
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

        {#if readOnly}
            <span
                class="text-text-subtle flex shrink-0 items-center gap-1 text-xs"
                title={`This shape uses ${shape.unsupported?.join(", ")}, which the form does not edit. Use the Turtle view.`}
            >
                <Fa icon={faLock} />
                Turtle only
            </span>
        {:else}
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
            {#if readOnly}
                <p
                    class="text-text-subtle bg-background-subtle rounded p-2 text-sm"
                >
                    This shape uses
                    <span class="font-mono">
                        {shape.unsupported?.join(", ")}
                    </span>
                    , which the form does not represent. Edit it in the Turtle view
                    so nothing is lost.
                </p>
            {/if}

            <TermPicker
                label="Applies to class"
                kind="CLASS"
                value={shape.targetClass}
                {terms}
                {prefixes}
                disabled={readOnly}
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
                    {readOnly}
                    {onchange}
                    onremove={() => removeRule(index)}
                />
            {/each}

            {#if !readOnly}
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
