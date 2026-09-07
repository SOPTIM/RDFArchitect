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
     * A field that holds a list rather than one value: `sh:in`, `sh:ignoredProperties`.
     *
     * Two shapes of list, one editor. `sh:ignoredProperties` holds terms and nothing else, so its
     * rows are term pickers. `sh:in` holds terms *or* plain strings — an enumeration of classes in
     * one profile, a list of literals in the next — so its rows are text, and a value that reads as
     * a term is written as one. That is the writer's own rule, deliberately: the box shows what
     * will end up in the document.
     */
    import { faPlus, faTrash } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import { abbreviate, resolveTerm } from "$lib/shacl/turtleTerms.js";

    import TermPicker from "./TermPicker.svelte";

    let {
        label,
        values = [],
        /** "term" for a list of IRIs, "value" for one that may hold plain strings too. */
        mode = "term",
        kind = "PROPERTY",
        terms = [],
        prefixes = {},
        disabled = false,
        onchange = () => {},
    } = $props();

    /**
     * Rows added here but not yet part of the list, because they say nothing.
     *
     * Sending an empty value would be a request the writer filters out and a row that vanishes the
     * moment it appears — the same way "Add a rule" used to behave. A blank row therefore stays
     * local until something is typed into it.
     */
    let blanks = $state(0);

    const rows = $derived([
        ...values,
        ...Array.from({ length: blanks }, () => ""),
    ]);

    /** Replaces one entry, drops it when cleared, and adopts a blank row once it says something. */
    function set(index, value) {
        const written = value === null || value === undefined ? "" : value;
        if (index >= values.length) {
            blanks = Math.max(0, blanks - 1);
            if (written !== "") {
                onchange([...values, resolved(written)]);
            }
            return;
        }
        const next = [...values];
        if (written === "") {
            next.splice(index, 1);
        } else {
            next[index] = resolved(written);
        }
        onchange(next);
    }

    function remove(index) {
        if (index >= values.length) {
            blanks = Math.max(0, blanks - 1);
            return;
        }
        const next = [...values];
        next.splice(index, 1);
        onchange(next);
    }

    /**
     * What was typed, as the value the document will hold.
     *
     * A prefixed name is resolved so that reading a value and leaving the box alone gives back the
     * same value — the box shows `cim:Kind.a`, and without this, blurring it would turn the IRI
     * into the plain string "cim:Kind.a".
     */
    function resolved(typed) {
        return mode === "term"
            ? typed
            : (resolveTerm(typed, prefixes) ?? typed);
    }

    /** A term is shown through the document's prefixes; a plain string is shown as it is. */
    function shown(value) {
        return value?.startsWith("http") || value?.startsWith("urn:")
            ? abbreviate(value, prefixes)
            : (value ?? "");
    }
</script>

<div>
    <span class="text-default-text text-sm">{label}</span>
    <div class="mt-1 space-y-1">
        {#each rows as value, index (index)}
            <div class="flex items-end gap-1">
                <div class="min-w-0 flex-1">
                    {#if mode === "term"}
                        <TermPicker
                            {kind}
                            {terms}
                            {prefixes}
                            {disabled}
                            value={value || null}
                            onpick={picked => set(index, picked)}
                        />
                    {:else}
                        <TextEditControl
                            value={shown(value)}
                            readonly={disabled}
                            callOnChange={text => set(index, text)}
                        />
                    {/if}
                </div>
                {#if !disabled}
                    <button
                        class="text-text-subtle hover:text-red mb-1 shrink-0 cursor-pointer p-1 text-xs"
                        title="Remove this value"
                        aria-label="Remove this value"
                        onclick={() => remove(index)}
                    >
                        <Fa icon={faTrash} />
                    </button>
                {/if}
            </div>
        {/each}
        {#if !disabled}
            <button
                class="text-text-subtle hover:text-default-text flex cursor-pointer items-center gap-1 text-xs"
                onclick={() => (blanks += 1)}
            >
                <Fa icon={faPlus} />
                {rows.length ? "another value" : "add a value"}
            </button>
        {/if}
    </div>
</div>
