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
     * Picks a class or a property from the workspace's live schema.
     *
     * The whole point of the form view is that nobody has to type an IRI, so the options are the
     * terms the schema actually declares, shown the way the open document would write them.
     *
     * With `literals` the box holds a value rather than a term — `sh:in`, `sh:hasValue` — and text
     * that is not a term is taken as a plain string instead of being refused. It still says so
     * when that text looks like a term the document cannot resolve: `cim:Kind.b` with `cim:`
     * unbound is written as the string "cim:Kind.b", which never equals the enum value it was
     * meant to be.
     */
    import SearchableSelect from "$lib/components/SearchableSelect.svelte";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import {
        abbreviate,
        resolveTerm,
        writeTerm,
    } from "$lib/shacl/turtleTerms.js";

    let {
        label,
        value = null,
        /** The kind of term offered, or a list of kinds. */
        kind = "CLASS",
        terms = [],
        prefixes = {},
        /** Properties of this class are offered first. */
        preferredDomain = null,
        /** Which terms are offered first, where a domain cannot say. */
        prefer = null,
        /** Whether a plain string is something this box may hold. */
        literals = false,
        /** Said under the box about a plain string in it, such as how it will be written. */
        note = null,
        disabled = false,
        onpick = () => {},
    } = $props();

    /**
     * What each kind of box invites.
     *
     * A kind the schema has no list for — a target node, a property group — says so rather than
     * offering to "pick a property" it cannot pick from. The box still takes a prefixed name or an
     * absolute IRI, which is the only way to name one of those.
     */
    const PLACEHOLDERS = {
        CLASS: "pick a class",
        PROPERTY: "pick a property",
        ENUM_MEMBER: "pick a value or type one",
    };

    /** Bumped to put the box back to the term it holds after something unusable was typed in. */
    let reverts = $state(0);

    const kinds = $derived(new Set(Array.isArray(kind) ? kind : [kind]));

    const placeholder = $derived(
        PLACEHOLDERS[Array.isArray(kind) ? kind[0] : kind] ?? "write a term",
    );

    /**
     * The suggestions, in the order they are offered.
     *
     * Only worked out once the list is rendered, which is while the box has focus: every box on
     * an open card used to write out every term of the schema — some five thousand for CGMES —
     * the moment it was drawn.
     */
    const options = $derived.by(() => {
        const preferred = [];
        const rest = [];
        for (const term of terms) {
            if (!kinds.has(term.kind)) {
                continue;
            }
            const first =
                (preferredDomain && term.domain === preferredDomain) ||
                prefer?.(term);
            (first ? preferred : rest).push(term);
        }
        const written = term => ({
            ...term,
            written: writeTerm(term, prefixes),
        });
        return [...preferred.map(written), ...rest.map(written)];
    });

    /**
     * The term in the box, written the way the open document writes terms.
     *
     * A term the schema does not declare — one typed by hand, or from a profile this workspace has
     * not loaded — is not among the options, so it is abbreviated from the document's prefixes
     * instead of shown as a bare IRI. What the box holds then reads like the rest of the form.
     */
    const shown = $derived.by(() => {
        if (!value) {
            return "";
        }
        if (literals && !absoluteIri(value)) {
            return value;
        }
        const known = terms.find(
            term => term.iri === value && kinds.has(term.kind),
        );
        return known ? writeTerm(known, prefixes) : abbreviate(value, prefixes);
    });

    /** What is worth saying about the value in the box, and whether it is a warning. */
    const remark = $derived(literals ? remarkOn(value) : null);

    /**
     * What the box was left holding, as an IRI.
     *
     * The box is a text field with a list of suggestions, so it can be left holding anything.
     * Picked from the list it hands back the term itself; typed by hand it hands back the text,
     * and that text used to go straight into `sh:path` — where a phrase with a space in it was
     * written as `<a phrase>` and the document stopped parsing. So a typed term is accepted only
     * when the document could actually write it: a prefixed name whose prefix the document binds,
     * or an absolute IRI. Anything else puts the previous term back — unless the box takes plain
     * strings, where that is what it is.
     */
    function picked(option) {
        if (option && typeof option === "object") {
            onpick(option.iri ?? null);
            return;
        }
        const typed = (option ?? "").trim();
        if (typed === "") {
            onpick(null);
            return;
        }
        // A bare name is how people type a class — `ACLineSegment`, not `cim:ACLineSegment` — and
        // refusing it when exactly one offered term is called that only sent them to the list.
        const iri =
            resolveTerm(typed, prefixes) ??
            absoluteIri(typed) ??
            onlyTermNamed(typed);
        if (iri) {
            onpick(iri);
            return;
        }
        if (literals) {
            onpick(typed);
            return;
        }
        reverts += 1;
        toastStore.warning(
            "Not a term",
            `"${typed}" is not a term this document can write. Pick one from the list, or write it as cim:Name or <http://…>.`,
        );
    }

    /**
     * The one offered term with this local name, for a term typed without its prefix.
     *
     * `WindingConnection.D` is how people write an enum value, and the document needs the term.
     * Only taken when exactly one term is called that, so a string that merely resembles one is
     * left a string.
     */
    function onlyTermNamed(typed) {
        const named = terms.filter(
            term => kinds.has(term.kind) && term.localName === typed,
        );
        return named.length === 1 ? named[0].iri : null;
    }

    function remarkOn(current) {
        if (!current || absoluteIri(current)) {
            return null;
        }
        const prefix = /^([A-Za-z][\w.-]*):\S/.exec(current)?.[1];
        if (prefix !== undefined && prefixes[prefix] === undefined) {
            return {
                warning: true,
                text: `"${prefix}:" is not a prefix this document binds, so this is written as the text "${current}" rather than as a term.`,
            };
        }
        return note ? { warning: false, text: note } : null;
    }

    /**
     * An absolute IRI typed without its angle brackets, which is how people write them.
     *
     * Deliberately only the schemes that actually turn up here, matching the backend writer's own
     * test. A looser rule would read `cim:Foo` with `cim:` unbound as an absolute IRI with the
     * scheme `cim`, write it as `<cim:Foo>`, and mean something other than what was typed.
     */
    function absoluteIri(typed) {
        return /^(?:[A-Za-z][A-Za-z0-9+.-]*:\/\/|urn:)[^\s<>"{}|^`\\]+$/.test(
            typed,
        )
            ? typed
            : null;
    }
</script>

<!--
  The key discards the box and its typed-in text, which is the only way to put the term it held
  back on screen: the box keeps that text in state of its own, so an unchanged `value` changes
  nothing.
-->
{#key reverts}
    <SearchableSelect
        {label}
        {disabled}
        value={shown}
        optionObjectList={options}
        optionsOnFocus
        accessDisplayData={option => option.written}
        accessIdentifier={option => option.written}
        {placeholder}
        warn={remark?.warning === true}
        callOnChange={picked}
    />
{/key}
{#if remark}
    <p
        class="mt-0.5 text-xs {remark.warning
            ? 'text-red-text'
            : 'text-text-subtle'}"
    >
        {remark.text}
    </p>
{/if}
