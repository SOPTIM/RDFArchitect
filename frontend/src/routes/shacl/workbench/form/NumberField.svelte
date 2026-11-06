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
     * A number on a rule, typed as text.
     *
     * Not `<input type=number>`: that one hands back `2` for `2.0` and nothing at all for `1.`
     * halfway through typing `1.5`, which cleared the bound and — typed again — wrote a bare
     * decimal where the document had an `xsd:float`. The text typed is what is kept, and it is
     * only passed on once it is a number; until then the field says what is wrong with it.
     */
    import { untrack } from "svelte";

    import InputWithButtonsControl from "$lib/components/InputWithButtonsControl.svelte";
    import { countProblem, numberProblem } from "$lib/shacl/ruleValidation.js";

    let {
        label,
        /** A count as a number, or a bound as the lexical form the document writes. */
        value = null,
        /** "count" for a whole number of things, "number" for a bound of a value range. */
        kind = "number",
        readonly = false,
        /** Something wrong with the value in relation to the rest of the rule. */
        problem = null,
        /** Called with the value, or null for a cleared field, and whether typing goes on. */
        onvalue = () => {},
    } = $props();

    const id = crypto.randomUUID();

    // The effect below keeps it in step with `value` from then on.
    let text = $state(untrack(() => written(value)));

    /** What the typed text says is wrong with it, before anything is passed on. */
    const typo = $derived(
        kind === "count"
            ? countProblem(text.trim())
            : numberProblem(text.trim()),
    );

    const shown = $derived(typo ?? problem);

    /**
     * Takes a value that changed from somewhere else — a re-read, an undone edit.
     *
     * Only when it is not merely what the box already says: `2.50` read back as `2.50` must not
     * be respelled, and a box holding something unfinished keeps it rather than being reset to
     * the value it had before the typing started.
     */
    $effect(() => {
        const next = value;
        untrack(() => {
            if (typo === null && same(parsed(text), next)) {
                return;
            }
            if (typo !== null && document.activeElement?.id === id) {
                return;
            }
            text = written(next);
        });
    });

    function written(value) {
        return value === null || value === undefined ? "" : String(value);
    }

    function parsed(raw) {
        const typed = raw.trim();
        if (typed === "") {
            return null;
        }
        return kind === "count" ? Number(typed) : typed;
    }

    function same(a, b) {
        return (a ?? null) === (b ?? null);
    }

    function send(raw, soon) {
        text = raw ?? "";
        if (typo !== null) {
            return;
        }
        onvalue(parsed(text), soon);
    }
</script>

<div class="text-default-text flex h-full w-full flex-col">
    <label for={id}>{label}</label>
    <InputWithButtonsControl
        {id}
        type="text"
        inputmode={kind === "count" ? "numeric" : "decimal"}
        bind:value={text}
        {readonly}
        warn={shown !== null}
        aria-invalid={shown !== null}
        callOnInput={raw => send(raw, true)}
        callOnChange={raw => send(raw, false)}
    />
    {#if shown}
        <p class="text-red-text mt-0.5 text-xs">{shown}</p>
    {/if}
</div>
