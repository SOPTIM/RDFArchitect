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
        kind = "CLASS",
        terms = [],
        prefixes = {},
        /** Properties of this class are offered first. */
        preferredDomain = null,
        disabled = false,
        onpick = () => {},
    } = $props();

    /** Bumped to put the box back to the term it holds after something unusable was typed in. */
    let reverts = $state(0);

    const options = $derived.by(() => {
        const matching = terms.filter(term => term.kind === kind);
        const preferred = [];
        const rest = [];
        for (const term of matching) {
            (preferredDomain && term.domain === preferredDomain
                ? preferred
                : rest
            ).push(term);
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
        const known = options.find(option => option.iri === value);
        return known ? known.written : abbreviate(value, prefixes);
    });

    /**
     * What the box was left holding, as an IRI.
     *
     * The box is a text field with a list of suggestions, so it can be left holding anything.
     * Picked from the list it hands back the term itself; typed by hand it hands back the text,
     * and that text used to go straight into `sh:path` — where a phrase with a space in it was
     * written as `<a phrase>` and the document stopped parsing. So a typed term is accepted only
     * when the document could actually write it: a prefixed name whose prefix the document binds,
     * or an absolute IRI. Anything else puts the previous term back.
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
        const iri = resolveTerm(typed, prefixes) ?? absoluteIri(typed);
        if (!iri) {
            reverts += 1;
            toastStore.warning(
                "Not a term",
                `"${typed}" is not a term this document can write. Pick one from the list, or write it as cim:Name or <http://…>.`,
            );
            return;
        }
        onpick(iri);
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
        accessDisplayData={option => option.written}
        accessIdentifier={option => option.written}
        placeholder={kind === "CLASS" ? "pick a class" : "pick a property"}
        callOnChange={picked}
    />
{/key}
