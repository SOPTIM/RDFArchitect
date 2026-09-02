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
     * Fields the form does not show — sh:pattern, sh:group — are carried on the model and left
     * where the document wrote them. A field whose value the form cannot spell again is shown as
     * that value instead of as an empty box, and one clause it cannot write no longer costs the
     * rule its other fields.
     */
    import { faTrash } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import NumberInputControl from "$lib/components/NumberInputControl.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import { keptClauses, keptFields } from "$lib/shacl/retained.js";
    import { abbreviate } from "$lib/shacl/turtleTerms.js";

    import KeptClause from "./KeptClause.svelte";
    import KeptClauseList from "./KeptClauseList.svelte";
    import TermPicker from "./TermPicker.svelte";

    let {
        property,
        terms = [],
        prefixes = {},
        targetClass = null,
        readOnly = false,
        onchange = () => {},
        /** A field still being typed in: the same edit, to be sent once typing pauses. */
        onedit = () => {},
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

    /** The fields this card puts on screen. Anything else kept as written is listed instead. */
    const SHOWN = [
        "path",
        "minCount",
        "maxCount",
        "dataType",
        "classIri",
        "nodeKind",
        "severity",
        "message",
        "deactivated",
    ];

    /**
     * A rule written as its own resource is shared: changing it here would change every shape that
     * references it, so it is shown with what it says and edited in the Turtle view for now.
     */
    const referenced = $derived(property.iri != null);

    const locked = $derived(readOnly || referenced);

    /** The fields whose value the form shows but will not write. */
    const kept = $derived(keptFields(property.retained));

    const pathLabel = $derived(
        property.path
            ? abbreviate(property.path, prefixes)
            : (kept.get("path")?.[0]?.value ?? "no property chosen"),
    );

    /**
     * A cleared number field means "no bound stated", which is not the same as zero.
     *
     * The controls hand their callbacks the input's **value**, not the event — reading
     * `event.target.value` here is what made the number fields write `null` for everything typed
     * into them, and the message field throw on every keystroke.
     */
    function numberOf(raw) {
        if (raw === "" || raw === undefined || raw === null) {
            return null;
        }
        const value = Number(raw);
        return Number.isFinite(value) ? value : null;
    }

    /**
     * Writes one field and asks for the shape to be applied.
     *
     * `soon` is for a field that changes while it is being typed in: the model is updated at once,
     * so the form never shows something the user did not type, but the document is rewritten once
     * the typing stops.
     */
    function set(field, value, { soon = false } = {}) {
        property[field] = value === "" ? null : value;
        if (soon) {
            onedit();
        } else {
            onchange();
        }
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
            {#if kept.has("path")}
                <KeptClause label="Property" clauses={kept.get("path")} />
            {:else}
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
            {/if}
        </div>

        {#if kept.has("minCount")}
            <KeptClause label="Minimum values" clauses={kept.get("minCount")} />
        {:else}
            <NumberInputControl
                label="Minimum values"
                value={property.minCount}
                readonly={locked}
                callOnInput={raw =>
                    set("minCount", numberOf(raw), { soon: true })}
                callOnChange={raw => set("minCount", numberOf(raw))}
            />
        {/if}
        {#if kept.has("maxCount")}
            <KeptClause label="Maximum values" clauses={kept.get("maxCount")} />
        {:else}
            <NumberInputControl
                label="Maximum values"
                value={property.maxCount}
                readonly={locked}
                callOnInput={raw =>
                    set("maxCount", numberOf(raw), { soon: true })}
                callOnChange={raw => set("maxCount", numberOf(raw))}
            />
        {/if}

        {#if kept.has("dataType")}
            <KeptClause label="Value type" clauses={kept.get("dataType")} />
        {:else}
            <div>
                <span class="text-default-text text-sm">Value type</span>
                <SelectEditControl
                    value={property.dataType}
                    options={[{ iri: null, label: "any" }, ...DATATYPES]}
                    getOptionValue={option => option.iri}
                    getOptionLabel={option => option.label}
                    disabled={locked}
                    onchange={value => set("dataType", value)}
                />
            </div>
        {/if}
        {#if kept.has("classIri")}
            <KeptClause label="Value class" clauses={kept.get("classIri")} />
        {:else}
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
        {/if}

        {#if kept.has("nodeKind")}
            <KeptClause label="Value form" clauses={kept.get("nodeKind")} />
        {:else}
            <div>
                <span class="text-default-text text-sm">Value form</span>
                <SelectEditControl
                    value={property.nodeKind}
                    options={NODE_KINDS}
                    getOptionValue={option => option.value}
                    getOptionLabel={option => option.label}
                    disabled={locked}
                    onchange={value => set("nodeKind", value)}
                />
            </div>
        {/if}
        {#if kept.has("severity")}
            <KeptClause label="Severity" clauses={kept.get("severity")} />
        {:else}
            <div>
                <span class="text-default-text text-sm">Severity</span>
                <SelectEditControl
                    value={property.severity}
                    options={SEVERITIES}
                    getOptionValue={option => option.value}
                    getOptionLabel={option => option.label}
                    disabled={locked}
                    onchange={value => set("severity", value)}
                />
            </div>
        {/if}

        <div class="col-span-2">
            {#if kept.has("message")}
                <KeptClause
                    label="Message shown when the rule is broken"
                    clauses={kept.get("message")}
                />
            {:else}
                <TextEditControl
                    label="Message shown when the rule is broken"
                    value={property.message ?? ""}
                    readonly={locked}
                    callOnInput={text => set("message", text, { soon: true })}
                    callOnChange={text => set("message", text)}
                />
            {/if}
        </div>

        <div class="col-span-2">
            {#if kept.has("deactivated")}
                <KeptClause
                    label="Switched off"
                    clauses={kept.get("deactivated")}
                />
            {:else}
                <CheckBoxEditControl
                    label="Switched off"
                    value={property.deactivated === true}
                    readonly={locked}
                    callOnInputTrue={() => set("deactivated", true)}
                    callOnInputFalse={() => set("deactivated", null)}
                />
            {/if}
        </div>

        <div class="col-span-2">
            <KeptClauseList
                clauses={keptClauses(property.retained, SHOWN)}
                {prefixes}
            />
        </div>
    </div>
</div>
