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
    import { faMinus, faRotateLeft } from "@fortawesome/free-solid-svg-icons";
    import { onMount } from "svelte";
    import { Fa } from "svelte-fa";

    import { getXSDPrimitives } from "$lib/api/apiGlobalUtils.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import ComboBoxEditControl from "$lib/components/ComboBoxEditControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";

    let { entries, entry, readonly, namespaces } = $props();

    let datatypes = $state([]);
    onMount(async () => {
        const xsdPrimitives = await getXSDPrimitives();
        const xsdDatatypes = [];
        xsdPrimitives.forEach(xsdDatatype => {
            if (xsdDatatype) {
                xsdDatatypes.push(xsdDatatype.prefix + xsdDatatype.suffix);
            }
        });
        datatypes = xsdDatatypes;
    });

    function getSubstitutedNamespace(iri) {
        const namespaceObj = namespaces.find(p => iri?.startsWith(p.prefix));
        return namespaceObj
            ? namespaceObj.substitutedPrefix +
                  iri.slice(namespaceObj.prefix.length)
            : iri;
    }
</script>

<tr
    class="border-border hover:bg-highlight transition-colors not-last:border-b"
>
    <!-- IRI -->
    <td class="px-2 py-1">
        <TextEditControl
            {readonly}
            placeholder="*iri"
            value={getSubstitutedNamespace(entry.iri.value)}
            callOnInput={newIri => {
                entry.iri.value = getSubstitutedNamespace(newIri);
            }}
            warn={!entry.iri.isValid}
        />
    </td>

    {#if entry.isIriEntry.value}
        <!-- IRI Value -->
        <td colspan="2" class="px-2 py-1">
            <TextEditControl
                {readonly}
                placeholder="*iri value"
                value={getSubstitutedNamespace(entry.value.value)}
                callOnInput={newLabel => {
                    entry.value.value = getSubstitutedNamespace(newLabel);
                }}
                warn={!entry.value.isValid}
            />
        </td>
    {:else}
        <!-- Literal Value -->
        <td class="px-2 py-1">
            <TextEditControl
                {readonly}
                placeholder="*literal value"
                bind:value={entry.value.value}
                warn={!entry.value.isValid}
            />
        </td>

        <!-- Data Type -->
        <td class="px-2 py-1">
            <ComboBoxEditControl
                {readonly}
                placeholder="type"
                value={getSubstitutedNamespace(entry.datatypeIri.value)}
                callOnInput={newDatatype => {
                    entry.datatypeIri.value =
                        getSubstitutedNamespace(newDatatype);
                }}
                optionValues={datatypes}
            />
        </td>
    {/if}
    <!-- is IRI Entry -->
    <td class="flex h-11 w-11 items-center justify-center">
        <CheckBoxEditControl
            {readonly}
            bind:value={entry.isIriEntry.value}
            callOnInputFalse={() => {
                entry.datatypeIri.value = null;
            }}
        />
    </td>
    <!-- Reset Changes -->
    <td>
        <div class="size-9">
            {#if entry.isModified}
                <ButtonControl height={9} callOnClick={() => entry.reset()}>
                    <Fa icon={faRotateLeft} />
                </ButtonControl>
            {/if}
        </div>
    </td>
    <!-- Delete Entry -->
    {#if !readonly}
        <td class="px-2 py-1 text-center">
            <div class="size-9">
                <ButtonControl
                    height={9}
                    callOnClick={() => entries.remove(entry, true)}
                >
                    <Fa icon={faMinus} />
                </ButtonControl>
            </div>
        </td>
    {/if}
</tr>
