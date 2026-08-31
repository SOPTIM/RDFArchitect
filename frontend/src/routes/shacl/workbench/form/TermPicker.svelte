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
    import { writeTerm } from "$lib/shacl/turtleTerms.js";

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

    const shown = $derived(
        value
            ? (options.find(option => option.iri === value)?.written ?? value)
            : "",
    );
</script>

<SearchableSelect
    {label}
    {disabled}
    value={shown}
    optionObjectList={options}
    accessDisplayData={option => option.written}
    accessIdentifier={option => option.written}
    placeholder={kind === "CLASS" ? "pick a class" : "pick a property"}
    callOnChange={picked => onpick(picked?.iri ?? picked ?? null)}
/>
