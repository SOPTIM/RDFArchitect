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
     * One rule about one property: which property, how many values, of what kind.
     *
     * Fields the form does not show — sh:pattern, sh:order, sh:group — are still carried on the
     * model and written back untouched, so editing here never silently drops part of a shape.
     */
    import { faTrash } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import NumberInputControl from "$lib/components/NumberInputControl.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import { writeTerm } from "$lib/shacl/turtleTerms.js";

    import TermPicker from "./TermPicker.svelte";

    let {
        property,
        terms = [],
        prefixes = {},
        targetClass = null,
        readOnly = false,
        onchange = () => {},
        onremove = () => {},
    } = $props();

    const SHACL = "http://www.w3.org/ns/shacl#";
    const XSD = "http://www.w3.org/2001/XMLSchema#";

    const NODE_KINDS = [
        { value: null, label: "any" },
        { value: `${SHACL}IRI`, label: "IRI" },
        { value: `${SHACL}Literal`, label: "Literal" },
        { value: `${SHACL}BlankNode`, label: "Blank node" },
        { value: `${SHACL}BlankNodeOrIRI`, label: "Blank node or IRI" },
    ];

    const SEVERITIES = [
        { value: null, label: "Violation (default)" },
        { value: `${SHACL}Violation`, label: "Violation" },
        { value: `${SHACL}Warning`, label: "Warning" },
        { value: `${SHACL}Info`, label: "Info" },
    ];

    const DATATYPES = [
        "string",
        "boolean",
        "integer",
        "float",
        "double",
        "decimal",
        "dateTime",
        "date",
    ].map(name => ({ iri: `${XSD}${name}`, label: name }));

    /**
     * A rule written as its own resource is kept as a reference rather than inlined, so editing it
     * here would have nowhere to go. It is shown, and changed in the Turtle view.
     */
    const referenced = $derived(property.iri != null);

    const locked = $derived(readOnly || referenced);

    const pathLabel = $derived(
        property.path
            ? writeTerm(
                  {
                      iri: property.path,
                      namespace: property.path.slice(
                          0,
                          Math.max(
                              property.path.lastIndexOf("#"),
                              property.path.lastIndexOf("/"),
                          ) + 1,
                      ),
                      localName: property.path.slice(
                          Math.max(
                              property.path.lastIndexOf("#"),
                              property.path.lastIndexOf("/"),
                          ) + 1,
                      ),
                  },
                  prefixes,
              )
            : "no property chosen",
    );

    /** A cleared number field means "no bound stated", which is not the same as zero. */
    function numberOf(event) {
        const raw = event?.target?.value;
        return raw === "" || raw === undefined || raw === null
            ? null
            : Number(raw);
    }

    function set(field, value) {
        property[field] = value === "" ? null : value;
        onchange();
    }
</script>

<div class="border-border bg-window-background rounded border p-3">
    <div class="mb-2 flex items-center gap-2">
        <span class="text-default-text truncate font-mono text-sm">
            {pathLabel}
        </span>
        {#if referenced}
            <span class="text-text-subtle ml-auto shrink-0 text-xs">
                shared rule — edit in Turtle
            </span>
        {/if}
        {#if !locked}
            <button
                class="text-text-subtle hover:text-red ml-auto cursor-pointer p-1 text-xs"
                title="Remove this rule"
                aria-label="Remove this rule"
                onclick={onremove}
            >
                <Fa icon={faTrash} />
            </button>
        {/if}
    </div>

    <div class="grid grid-cols-2 gap-3">
        <div class="col-span-2">
            <TermPicker
                label="Property"
                kind="PROPERTY"
                value={property.path}
                {terms}
                {prefixes}
                preferredDomain={targetClass}
                disabled={locked}
                onpick={iri => set("path", iri)}
            />
        </div>

        <NumberInputControl
            label="Minimum values"
            value={property.minCount}
            readonly={locked}
            callOnInput={event => set("minCount", numberOf(event))}
        />
        <NumberInputControl
            label="Maximum values"
            value={property.maxCount}
            readonly={locked}
            callOnInput={event => set("maxCount", numberOf(event))}
        />

        <div>
            <span class="text-default-text text-sm">Value type</span>
            <SelectEditControl
                value={property.dataType}
                options={[{ iri: null, label: "any" }, ...DATATYPES]}
                getOptionValue={option => option.iri}
                getOptionLabel={option => option.label}
                disabled={locked}
                onchange={() => onchange()}
            />
        </div>
        <div>
            <TermPicker
                label="Value class"
                kind="CLASS"
                value={property.classIri}
                {terms}
                {prefixes}
                disabled={locked}
                onpick={iri => set("classIri", iri)}
            />
        </div>

        <div>
            <span class="text-default-text text-sm">Value form</span>
            <SelectEditControl
                value={property.nodeKind}
                options={NODE_KINDS}
                getOptionValue={option => option.value}
                getOptionLabel={option => option.label}
                disabled={locked}
                onchange={() => onchange()}
            />
        </div>
        <div>
            <span class="text-default-text text-sm">Severity</span>
            <SelectEditControl
                value={property.severity}
                options={SEVERITIES}
                getOptionValue={option => option.value}
                getOptionLabel={option => option.label}
                disabled={locked}
                onchange={() => onchange()}
            />
        </div>

        <div class="col-span-2">
            <TextEditControl
                label="Message shown when the rule is broken"
                value={property.message ?? ""}
                readonly={locked}
                callOnInput={event => set("message", event.target.value)}
            />
        </div>

        <div class="col-span-2">
            <CheckBoxEditControl
                label="Switched off"
                value={property.deactivated === true}
                readonly={locked}
                callOnInputTrue={() => set("deactivated", true)}
                callOnInputFalse={() => set("deactivated", null)}
            />
        </div>
    </div>
</div>
