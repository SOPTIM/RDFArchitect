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
     * rows only take terms. `sh:in` holds terms *or* plain strings — an enumeration's values in one
     * profile, a list of literals in the next — so its rows offer the schema's terms and take
     * anything else as a string. That is the writer's own rule, deliberately: the box shows what
     * will end up in the document.
     */
    import { faPlus, faTrash } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import TermPicker from "./TermPicker.svelte";

    let {
        label,
        values = [],
        /** "term" for a list of IRIs, "value" for one that may hold plain strings too. */
        mode = "term",
        /** The kind of term each row offers, or a list of kinds. */
        kind = "PROPERTY",
        terms = [],
        prefixes = {},
        /** Which terms each row offers first. */
        prefer = null,
        /** Said under a row holding a plain string. */
        note = null,
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
                onchange([...values, written]);
            }
            return;
        }
        const next = [...values];
        if (written === "") {
            next.splice(index, 1);
        } else {
            next[index] = written;
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
</script>

<div>
    <span class="text-default-text text-sm">{label}</span>
    <div class="mt-1 space-y-1">
        {#each rows as value, index (index)}
            <div class="flex items-start gap-1">
                <div class="min-w-0 flex-1">
                    <TermPicker
                        {kind}
                        {terms}
                        {prefixes}
                        {disabled}
                        {prefer}
                        {note}
                        literals={mode === "value"}
                        value={value || null}
                        onpick={picked => set(index, picked)}
                    />
                </div>
                {#if !disabled}
                    <button
                        class="text-text-subtle hover:text-red mt-1 shrink-0 cursor-pointer p-1 text-xs"
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
