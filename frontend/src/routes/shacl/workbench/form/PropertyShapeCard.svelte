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
     *
     * A rule the document writes as a shape of its own appears twice: as a card of its own, and
     * under every shape referencing it. Both are editable and both say how many shapes rely on it,
     * because that is what the user needs before changing it — the card is the same either way, so
     * a rule cannot say one thing in one place and another somewhere else.
     */
    import { faCode, faLock, faTrash } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import { keptClauses, keptFields } from "$lib/shacl/retained.js";
    import { ruleProblems } from "$lib/shacl/ruleValidation.js";
    import { abbreviate } from "$lib/shacl/turtleTerms.js";

    import CardSection from "./CardSection.svelte";
    import KeptClause from "./KeptClause.svelte";
    import KeptClauseList from "./KeptClauseList.svelte";
    import NumberField from "./NumberField.svelte";
    import TermPicker from "./TermPicker.svelte";
    import ValueListEditor from "./ValueListEditor.svelte";

    let {
        property = $bindable(),
        terms = [],
        prefixes = {},
        targetClass = null,
        readOnly = false,
        /** Why the server refused the last change made on this card, if it did. */
        failure = null,
        onchange = () => {},
        /** A field still being typed in: the same edit, to be sent once typing pauses. */
        onedit = () => {},
        /** Left out where there is nothing to remove the rule from, as on its own card. */
        onremove = null,
        /** Shows the line the document writes this rule on, in the Turtle view. */
        onreveal = () => {},
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

    const RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    /**
     * The datatypes offered, most used first.
     *
     * Everything the official ENTSO-E library states is here — `xsd:duration` alone is on some 140
     * rules, and `xsd:anyURI`, `xsd:gMonthDay`, `xsd:dateTimeStamp`, `xsd:time` and
     * `rdf:langString` were missing, so a rule stating one showed an empty box and could not be
     * set to it.
     */
    const DATATYPES = [
        ...[
            "string",
            "boolean",
            "integer",
            "float",
            "double",
            "decimal",
            "dateTime",
            "dateTimeStamp",
            "date",
            "time",
            "duration",
            "gYear",
            "gYearMonth",
            "gMonthDay",
            "gMonth",
            "gDay",
            "anyURI",
            "long",
            "int",
            "short",
            "byte",
            "nonNegativeInteger",
            "positiveInteger",
            "nonPositiveInteger",
            "negativeInteger",
            "unsignedLong",
            "unsignedInt",
            "normalizedString",
            "token",
            "language",
            "base64Binary",
            "hexBinary",
        ].map(name => ({ iri: `${XSD}${name}`, label: name })),
        { iri: `${RDF}langString`, label: "langString (text with a language)" },
        { iri: `${RDF}HTML`, label: "HTML" },
    ];

    /**
     * What a value of `sh:in` or `sh:hasValue` is picked from: an enumeration's values, and the
     * classes the NC profiles list there (`sh:in ( nc:SchedulingArea nc:SubSchedulingArea )`).
     */
    const VALUE_KINDS = ["ENUM_MEMBER", "CLASS"];

    /** Ids for the selects, which are labelled by `<label for>` like every other field. */
    const ids = {
        dataType: crypto.randomUUID(),
        nodeKind: crypto.randomUUID(),
        severity: crypto.randomUUID(),
    };

    /** The fields this card puts on screen. Anything else kept as written is listed instead. */
    const SHOWN = [
        "path",
        "minCount",
        "maxCount",
        "dataType",
        "classIri",
        "nodeKind",
        "hasValue",
        "allowedValues",
        "minInclusive",
        "maxInclusive",
        "minExclusive",
        "maxExclusive",
        "minLength",
        "maxLength",
        "pattern",
        "flags",
        "name",
        "description",
        "severity",
        "message",
        "order",
        "group",
        "deactivated",
    ];

    /** The four bounds a value range can state, in the order a reader expects them. */
    const RANGES = [
        { field: "minInclusive", label: "At least" },
        { field: "maxInclusive", label: "At most" },
        { field: "minExclusive", label: "More than" },
        { field: "maxExclusive", label: "Less than" },
    ];

    /**
     * Bumped whenever the card writes a field.
     *
     * The rule may be a plain object rather than state — as on a draft — and then nothing else
     * would tell `problems` that it changed.
     */
    let written = $state(0);

    /** Which groups already say something, and so open themselves. */
    const filled = $derived({
        range: says([
            "minInclusive",
            "maxInclusive",
            "minExclusive",
            "maxExclusive",
        ]),
        text: says(["minLength", "maxLength", "pattern", "flags"]),
    });

    /** A rule written as its own resource, which any number of shapes may reference. */
    const referenced = $derived(property.iri != null);

    /** How many shapes a change to this rule would reach. */
    const shares = $derived(property.usedBy?.length ?? 0);

    /** Either the workspace forbids changes, or the form cannot place an edit in this rule. */
    const locked = $derived(readOnly || property.editable === false);

    /** Only the rule's own limits are worth explaining; a read-only workspace says so elsewhere. */
    const turtleOnly = $derived(property.editable === false);

    const readOnlyTitle = $derived(
        property.readOnlyReason ??
            "This rule is written in a way the form cannot edit. Edit it in the Turtle view.",
    );

    /** The fields whose value the form shows but will not write. */
    const kept = $derived(keptFields(property.retained));

    const pathLabel = $derived(
        property.path
            ? abbreviate(property.path, prefixes)
            : (kept.get("path")?.[0]?.value ?? "no property chosen"),
    );

    /**
     * The datatypes on offer, with the rule's own among them even when it is none of the above.
     *
     * A select whose value is not one of its options shows the first option instead, so a rule
     * stating a datatype the list lacks read as "any".
     */
    const datatypeOptions = $derived([
        { iri: null, label: "any" },
        ...DATATYPES,
        ...(property.dataType &&
        !DATATYPES.some(option => option.iri === property.dataType)
            ? [
                  {
                      iri: property.dataType,
                      label: abbreviate(property.dataType, prefixes),
                  },
              ]
            : []),
    ]);

    /** What is wrong with the rule as it stands, which keeps it from being sent. */
    const problems = $derived.by(() => {
        // eslint-disable-next-line @typescript-eslint/no-unused-expressions -- a dependency, read to be tracked
        written;
        return ruleProblems(property);
    });

    /**
     * The enumerations whose values belong first in the value pickers.
     *
     * The one the rule names as its `sh:class`, and those of the values it already lists: an enum
     * value is written `cim:WindingConnection.D`, so what it belongs to is its IRI up to the last
     * dot. The schema's terms do not say which enumeration a property ranges over, so this is as
     * close as the list can get without asking.
     */
    const enumerations = $derived.by(() => {
        // eslint-disable-next-line @typescript-eslint/no-unused-expressions -- a dependency, read to be tracked
        written;
        return new Set(
            [
                property.classIri,
                enumerationOf(property.hasValue),
                ...(property.allowedValues ?? []).map(enumerationOf),
            ].filter(Boolean),
        );
    });

    /**
     * What a plain string in a value picker is written as, when the rule says its values are of
     * some other type: a string never equals a typed value, so the rule would never be met.
     */
    const literalNote = $derived(
        property.dataType && property.dataType !== `${XSD}string`
            ? `Written as a plain string, which never equals a ${abbreviate(property.dataType, prefixes)} value.`
            : null,
    );

    /** Whether the rule states any of these fields, or the document does where the form cannot. */
    function says(fields) {
        return fields.some(field => {
            const value = property[field];
            return (
                kept.has(field) ||
                (Array.isArray(value) ? value.length > 0 : Boolean(value))
            );
        });
    }

    function enumerationOf(iri) {
        if (!iri) {
            return null;
        }
        const cut = Math.max(iri.lastIndexOf("#"), iri.lastIndexOf("/"));
        const dot = iri.lastIndexOf(".");
        return dot > cut ? iri.slice(0, dot) : null;
    }

    function inEnumeration(term) {
        return (
            term.kind === "ENUM_MEMBER" &&
            enumerations.has(enumerationOf(term.iri))
        );
    }

    /**
     * Writes one field and asks for the shape to be applied.
     *
     * `soon` is for a field that changes while it is being typed in: the model is updated at once,
     * so the form never shows something the user did not type, but the document is rewritten once
     * the typing stops. A rule that has something wrong with it is held back until it is put
     * right, with the reason on the field.
     */
    function set(field, value, { soon = false } = {}) {
        property[field] = value === "" ? null : value;
        written += 1;
        if (Object.keys(problems).length) {
            return;
        }
        if (soon) {
            onedit();
        } else {
            onchange();
        }
    }
</script>

<div
    class="border-border bg-window-background rounded border p-3"
    data-shape={property.iri}
>
    <div class="mb-2 flex items-center gap-2">
        <span class="text-default-text truncate font-mono text-sm">
            {pathLabel}
        </span>
        {#if referenced}
            <!--
              Named, because two rules about one property are told apart only by their names — and
              in the list of the document's own rules that is the whole of what distinguishes them.
            -->
            <span class="text-text-subtle shrink-0 font-mono text-xs">
                {abbreviate(property.iri, prefixes)}
            </span>
            <span
                class="text-text-subtle ml-auto shrink-0 text-xs"
                title="This rule is written as a shape of its own, so a change to it reaches every shape that uses it."
            >
                shared rule · used by {shares} shape{shares === 1 ? "" : "s"}
            </span>
        {/if}
        {#if property.line}
            <button
                class="text-text-subtle hover:text-default-text shrink-0 cursor-pointer p-1 text-xs"
                class:ml-auto={!referenced}
                title="Show this rule in the Turtle view (line {property.line})"
                aria-label="Show this rule in the Turtle view"
                onclick={() => onreveal(property.line)}
            >
                <Fa icon={faCode} />
            </button>
        {/if}
        {#if turtleOnly}
            <span
                class="text-text-subtle flex shrink-0 items-center gap-1 text-xs"
                class:ml-auto={!referenced && !property.line}
                title={readOnlyTitle}
            >
                <Fa icon={faLock} />
                Turtle only
            </span>
        {:else if !readOnly && onremove}
            <button
                class="text-text-subtle hover:text-red cursor-pointer p-1 text-xs"
                class:ml-auto={!referenced && !property.line}
                title={referenced
                    ? "Remove this rule from the shape"
                    : "Remove this rule"}
                aria-label={referenced
                    ? "Remove this rule from the shape"
                    : "Remove this rule"}
                onclick={onremove}
            >
                <Fa icon={faTrash} />
            </button>
        {/if}
    </div>

    {#if turtleOnly}
        <p
            class="text-text-subtle bg-background-subtle mb-2 rounded p-2 text-sm"
        >
            {readOnlyTitle}
        </p>
    {/if}

    {#if failure}
        <p
            class="bg-red-background border-red-border text-red-text mb-2 rounded border p-2 text-sm"
            role="alert"
        >
            Not applied: {failure}
        </p>
    {/if}

    <div class="space-y-3">
        <div>
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

        <CardSection title="How many" filled={true}>
            {#if kept.has("minCount")}
                <KeptClause
                    label="Minimum values"
                    clauses={kept.get("minCount")}
                />
            {:else}
                <NumberField
                    label="Minimum values"
                    kind="count"
                    value={property.minCount}
                    readonly={locked}
                    problem={problems.minCount}
                    onvalue={(value, soon) => set("minCount", value, { soon })}
                />
            {/if}
            {#if kept.has("maxCount")}
                <KeptClause
                    label="Maximum values"
                    clauses={kept.get("maxCount")}
                />
            {:else}
                <NumberField
                    label="Maximum values"
                    kind="count"
                    value={property.maxCount}
                    readonly={locked}
                    problem={problems.maxCount}
                    onvalue={(value, soon) => set("maxCount", value, { soon })}
                />
            {/if}
        </CardSection>

        <CardSection title="What kind of value" filled={true}>
            {#if kept.has("dataType")}
                <KeptClause label="Value type" clauses={kept.get("dataType")} />
            {:else}
                <div>
                    <label class="text-default-text text-sm" for={ids.dataType}>
                        Value type
                    </label>
                    <SelectEditControl
                        id={ids.dataType}
                        value={property.dataType}
                        options={datatypeOptions}
                        getOptionValue={option => option.iri}
                        getOptionLabel={option => option.label}
                        disabled={locked}
                        onchange={value => set("dataType", value)}
                    />
                </div>
            {/if}
            {#if kept.has("classIri")}
                <KeptClause
                    label="Value class"
                    clauses={kept.get("classIri")}
                />
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
                    <label class="text-default-text text-sm" for={ids.nodeKind}>
                        Value form
                    </label>
                    <SelectEditControl
                        id={ids.nodeKind}
                        value={property.nodeKind}
                        options={NODE_KINDS}
                        getOptionValue={option => option.value}
                        getOptionLabel={option => option.label}
                        disabled={locked}
                        onchange={value => set("nodeKind", value)}
                    />
                </div>
            {/if}
            {#if kept.has("hasValue")}
                <KeptClause
                    label="Must be exactly"
                    clauses={kept.get("hasValue")}
                />
            {:else}
                <div>
                    <TermPicker
                        label="Must be exactly"
                        kind={VALUE_KINDS}
                        literals
                        value={property.hasValue}
                        {terms}
                        {prefixes}
                        prefer={inEnumeration}
                        note={literalNote}
                        disabled={locked}
                        onpick={value => set("hasValue", value)}
                    />
                </div>
            {/if}
            <div class="col-span-2">
                {#if kept.has("allowedValues")}
                    <KeptClause
                        label="One of"
                        clauses={kept.get("allowedValues")}
                    />
                {:else}
                    <ValueListEditor
                        label="One of"
                        mode="value"
                        kind={VALUE_KINDS}
                        values={property.allowedValues ?? []}
                        {terms}
                        {prefixes}
                        prefer={inEnumeration}
                        note={literalNote}
                        disabled={locked}
                        onchange={values => set("allowedValues", values)}
                    />
                {/if}
            </div>
        </CardSection>

        <CardSection title="Between which values" filled={filled.range}>
            {#each RANGES as range (range.field)}
                {#if kept.has(range.field)}
                    <KeptClause
                        label={range.label}
                        clauses={kept.get(range.field)}
                    />
                {:else}
                    <NumberField
                        label={range.label}
                        value={property[range.field]}
                        readonly={locked}
                        problem={problems[range.field]}
                        onvalue={(value, soon) =>
                            set(range.field, value, { soon })}
                    />
                {/if}
            {/each}
        </CardSection>

        <CardSection title="What the text must look like" filled={filled.text}>
            {#if kept.has("minLength")}
                <KeptClause label="Shortest" clauses={kept.get("minLength")} />
            {:else}
                <NumberField
                    label="Shortest"
                    kind="count"
                    value={property.minLength}
                    readonly={locked}
                    problem={problems.minLength}
                    onvalue={(value, soon) => set("minLength", value, { soon })}
                />
            {/if}
            {#if kept.has("maxLength")}
                <KeptClause label="Longest" clauses={kept.get("maxLength")} />
            {:else}
                <NumberField
                    label="Longest"
                    kind="count"
                    value={property.maxLength}
                    readonly={locked}
                    problem={problems.maxLength}
                    onvalue={(value, soon) => set("maxLength", value, { soon })}
                />
            {/if}
            {#if kept.has("pattern")}
                <KeptClause label="Matching" clauses={kept.get("pattern")} />
            {:else}
                <div>
                    <TextEditControl
                        label="Matching"
                        value={property.pattern ?? ""}
                        readonly={locked}
                        warn={problems.pattern !== undefined}
                        callOnInput={text =>
                            set("pattern", text, { soon: true })}
                        callOnChange={text => set("pattern", text)}
                    />
                    {#if problems.pattern}
                        <p class="text-red-text mt-0.5 text-xs">
                            {problems.pattern}
                        </p>
                    {/if}
                </div>
            {/if}
            {#if kept.has("flags")}
                <KeptClause label="Match flags" clauses={kept.get("flags")} />
            {:else}
                <div>
                    <TextEditControl
                        label="Match flags"
                        value={property.flags ?? ""}
                        readonly={locked}
                        warn={problems.flags !== undefined}
                        callOnInput={text => set("flags", text, { soon: true })}
                        callOnChange={text => set("flags", text)}
                    />
                    {#if problems.flags}
                        <p class="text-red-text mt-0.5 text-xs">
                            {problems.flags}
                        </p>
                    {/if}
                </div>
            {/if}
        </CardSection>

        <!--
          Open like the two above it: the message, the severity and the switch have been on this
          card since it shipped, and folding them away to make room for the new fields would be a
          worse card than the flat one this grouping replaces.
        -->
        <CardSection title="What it is called and reports" filled={true}>
            <div class="col-span-2">
                {#if kept.has("name")}
                    <KeptClause label="Name" clauses={kept.get("name")} />
                {:else}
                    <TextEditControl
                        label="Name"
                        value={property.name ?? ""}
                        readonly={locked}
                        callOnInput={text => set("name", text, { soon: true })}
                        callOnChange={text => set("name", text)}
                    />
                {/if}
            </div>
            <div class="col-span-2">
                {#if kept.has("description")}
                    <KeptClause
                        label="Description"
                        clauses={kept.get("description")}
                    />
                {:else}
                    <TextEditControl
                        label="Description"
                        value={property.description ?? ""}
                        readonly={locked}
                        callOnInput={text =>
                            set("description", text, { soon: true })}
                        callOnChange={text => set("description", text)}
                    />
                {/if}
            </div>
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
                        callOnInput={text =>
                            set("message", text, { soon: true })}
                        callOnChange={text => set("message", text)}
                    />
                {/if}
            </div>
            {#if kept.has("severity")}
                <KeptClause label="Severity" clauses={kept.get("severity")} />
            {:else}
                <div>
                    <label class="text-default-text text-sm" for={ids.severity}>
                        Severity
                    </label>
                    <SelectEditControl
                        id={ids.severity}
                        value={property.severity}
                        options={SEVERITIES}
                        getOptionValue={option => option.value}
                        getOptionLabel={option => option.label}
                        disabled={locked}
                        onchange={value => set("severity", value)}
                    />
                </div>
            {/if}
            {#if kept.has("order")}
                <KeptClause label="Shown at" clauses={kept.get("order")} />
            {:else}
                <NumberField
                    label="Shown at"
                    value={property.order}
                    readonly={locked}
                    onvalue={(value, soon) => {
                        if (!soon) {
                            set("order", value);
                        }
                    }}
                />
            {/if}
            <div class="col-span-2">
                {#if kept.has("group")}
                    <KeptClause label="In group" clauses={kept.get("group")} />
                {:else}
                    <TermPicker
                        label="In group"
                        kind="NODE"
                        value={property.group}
                        {terms}
                        {prefixes}
                        disabled={locked}
                        onpick={iri => set("group", iri)}
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
        </CardSection>

        <KeptClauseList
            clauses={keptClauses(property.retained, SHOWN)}
            {prefixes}
        />
    </div>
</div>
