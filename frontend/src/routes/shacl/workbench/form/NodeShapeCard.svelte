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
     * One shape: the classes it applies to, and the rules written under it.
     *
     * Anything the shape says that the form cannot write is shown rather than hidden, and does not
     * stop the rest of the shape from being edited: an edit changes the clause it was made on and
     * leaves the others as their author wrote them.
     */

    import {
        faChevronDown,
        faChevronRight,
        faCode,
        faLock,
        faPlus,
        faTrash,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import SelectEditControl from "$lib/components/SelectEditControl.svelte";
    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import { keptClauses, keptFields } from "$lib/shacl/retained.js";
    import { abbreviate } from "$lib/shacl/turtleTerms.js";

    import CardSection from "./CardSection.svelte";
    import KeptClause from "./KeptClause.svelte";
    import KeptClauseList from "./KeptClauseList.svelte";
    import PropertyShapeCard from "./PropertyShapeCard.svelte";
    import TermPicker from "./TermPicker.svelte";
    import ValueListEditor from "./ValueListEditor.svelte";

    let {
        shape,
        terms = [],
        prefixes = {},
        /** The rules the document writes as shapes of their own, to reference one of them. */
        sharedRules = [],
        readOnly = false,
        expanded = false,
        ontoggle = () => {},
        onchange = () => {},
        /** A field still being typed in: the same edit, to be sent once typing pauses. */
        onedit = () => {},
        onremove = () => {},
        /** Shows the line the document writes this shape on, in the Turtle view. */
        onreveal = () => {},
        /**
         * A change to a rule the document writes as a shape of its own.
         *
         * Not part of this shape's edit: the rule's clauses live in its own statement, and a
         * change to them reaches every shape referencing it — which is a question to put to the
         * user rather than an answer to assume.
         */
        onrulechange = () => {},
        /** The same, still being typed in. */
        onruleedit = () => {},
    } = $props();

    /** The fields this card puts on screen. Anything else kept as written is listed instead. */
    const SHOWN = [
        "targetClasses",
        "targetSubjectsOf",
        "targetObjectsOf",
        "targetNodes",
        "name",
        "description",
        "message",
        "severity",
        "closed",
        "ignoredProperties",
        "deactivated",
    ];

    /**
     * The four ways SHACL says what a shape applies to.
     *
     * Shown as rows of kind plus value rather than as four separate lists, because that is the
     * question being answered — "what does this apply to?" — and a shape may answer it more than
     * once and in more than one way. `sh:targetNode` names a resource rather than a schema term,
     * so it has no list to pick from and its box is typed into.
     */
    const TARGET_KINDS = [
        { field: "targetClasses", label: "every instance of", pick: "CLASS" },
        {
            field: "targetSubjectsOf",
            label: "whatever states",
            pick: "PROPERTY",
        },
        {
            field: "targetObjectsOf",
            label: "whatever is the object of",
            pick: "PROPERTY",
        },
        { field: "targetNodes", label: "the resource", pick: "NODE" },
    ];

    const SEVERITIES = [
        { value: null, label: "Violation (default)" },
        { value: "http://www.w3.org/ns/shacl#Violation", label: "Violation" },
        { value: "http://www.w3.org/ns/shacl#Warning", label: "Warning" },
        { value: "http://www.w3.org/ns/shacl#Info", label: "Info" },
    ];

    /**
     * Rules added here but not yet written to the document, because they name no property.
     *
     * A rule with no `sh:path` says nothing, so there is nothing to write: it used to be posted
     * the instant the button was pressed, dropped by the writer, and gone from the card on the
     * next read — the button looked broken. A draft therefore stays on this card until a property
     * is picked, and only then joins the shape and is applied.
     *
     * Held raw rather than deeply reactive: the rule card writes the fields of the draft it was
     * given, and a proxied draft would make that a child mutating this component's state, which
     * Svelte reports as an ownership violation. Nothing needs to re-render while a draft is being
     * filled in — the inputs hold what was typed — and adding or dropping one is a reassignment,
     * which is reactive either way.
     */
    let drafts = $state.raw([]);

    /** Whether a picker for one more target is on screen, and which kind it would be. */
    let addingTarget = $state(null);

    /** The picker is an action rather than a field, so it goes back to its placeholder. */
    let picked = $state(null);

    /** Either the workspace forbids changes, or the shape is not one the form can write back. */
    const locked = $derived(readOnly || shape.editable === false);

    /** Only the shape's own limits are worth explaining; a read-only workspace says so elsewhere. */
    const turtleOnly = $derived(shape.editable === false);

    /**
     * Why the form will not write this shape.
     *
     * The server says it, because it is the side that knows: after clause-preserving edits the
     * remaining reasons are all about the text — a subject written as two statements, or two rules
     * so alike that an edit cannot be placed.
     */
    const readOnlyTitle = $derived(
        shape.readOnlyReason ??
            "This shape is written in a way the form cannot edit. Edit it in the Turtle view.",
    );

    const kept = $derived(keptFields(shape.retained));

    /** Whether the shape says anything about itself, which decides if that group starts open. */
    const saysSomething = $derived(
        Boolean(
            shape.name ||
            shape.description ||
            shape.message ||
            shape.severity ||
            shape.closed ||
            shape.deactivated ||
            (shape.ignoredProperties ?? []).length,
        ),
    );

    const classes = $derived(shape.targetClasses ?? []);

    const title = $derived(abbreviate(shape.iri, prefixes));

    /** Every target the shape states, in kind order, each knowing where it lives. */
    const targets = $derived(
        TARGET_KINDS.filter(entry => !kept.has(entry.field)).flatMap(entry =>
            (shape[entry.field] ?? []).map((iri, at) => ({
                ...entry,
                iri,
                at,
            })),
        ),
    );

    const target = $derived(
        targets.length
            ? targets.map(entry => abbreviate(entry.iri, prefixes)).join(", ")
            : "nothing chosen",
    );

    /** The document's own rules this shape does not reference yet. */
    const available = $derived.by(() => {
        const used = new Set(
            (shape.properties ?? []).map(rule => rule.iri).filter(Boolean),
        );
        return sharedRules.filter(rule => !used.has(rule.iri));
    });

    /** Replaces one target's value, or drops the target when the picker was cleared. */
    function setTarget(entry, iri) {
        const next = [...(shape[entry.field] ?? [])];
        if (iri) {
            next[entry.at] = iri;
        } else {
            next.splice(entry.at, 1);
        }
        shape[entry.field] = next;
        onchange();
    }

    /** Moves a target to another kind, which is a different predicate rather than a new value. */
    function setTargetKind(entry, field) {
        if (field === entry.field) {
            return;
        }
        const from = [...(shape[entry.field] ?? [])];
        from.splice(entry.at, 1);
        shape[entry.field] = from;
        shape[field] = [...(shape[field] ?? []), entry.iri];
        onchange();
    }

    function addTarget(field, iri) {
        addingTarget = null;
        if (!iri) {
            return;
        }
        shape[field] = [...(shape[field] ?? []), iri];
        onchange();
    }

    /** Writes one of the shape's own fields and applies it. */
    function set(field, value, { soon = false } = {}) {
        shape[field] = value === "" ? null : value;
        if (soon) {
            onedit();
        } else {
            onchange();
        }
    }

    function setList(field, values) {
        shape[field] = values;
        onchange();
    }

    function addRule() {
        drafts = [...drafts, { path: null }];
    }

    /**
     * References a rule the document already writes, rather than writing a new one here.
     *
     * How the official profiles are composed, and until now something the form could read but not
     * do: every constraint in a `-Con-Simple-` file is a named rule that dozens of shapes point at.
     */
    function useRule(iri) {
        picked = null;
        if (!iri) {
            return;
        }
        shape.properties = [...(shape.properties ?? []), { iri }];
        onchange();
    }

    /** A shared rule is written back through itself; an inline one through this shape. */
    function changeRule(rule) {
        if (rule.iri) {
            onrulechange(rule);
        } else {
            onchange();
        }
    }

    function editRule(rule) {
        if (rule.iri) {
            onruleedit(rule);
        } else {
            onedit();
        }
    }

    function removeRule(index) {
        shape.properties = shape.properties.filter((_, at) => at !== index);
        onchange();
    }

    function removeDraft(index) {
        drafts = drafts.filter((_, at) => at !== index);
    }

    /** Moves a draft into the shape once it names a property. Until then it stays a draft. */
    function promoteDraft(index) {
        const draft = drafts[index];
        if (!draft?.path) {
            return;
        }
        drafts = drafts.filter((_, at) => at !== index);
        shape.properties = [...(shape.properties ?? []), { ...draft }];
        onchange();
    }
</script>

<div class="border-border rounded border" data-shape={shape.iri}>
    <div class="flex items-center gap-2 px-3 py-2">
        <button
            class="text-default-text flex min-w-0 flex-1 cursor-pointer items-center gap-2 text-left"
            onclick={ontoggle}
        >
            <Fa
                icon={expanded ? faChevronDown : faChevronRight}
                class="text-text-subtle"
            />
            <span class="min-w-0">
                <span class="block truncate font-mono text-sm">{title}</span>
                <span class="text-text-subtle block truncate text-xs">
                    applies to {target} · {shape.properties?.length ?? 0} rule{(shape
                        .properties?.length ?? 0) === 1
                        ? ""
                        : "s"}
                </span>
            </span>
        </button>

        {#if shape.line}
            <button
                class="text-text-subtle hover:text-default-text shrink-0 cursor-pointer p-1 text-xs"
                title="Show this shape in the Turtle view (line {shape.line})"
                aria-label="Show this shape in the Turtle view"
                onclick={() => onreveal(shape.line)}
            >
                <Fa icon={faCode} />
            </button>
        {/if}
        {#if turtleOnly}
            <span
                class="text-text-subtle flex shrink-0 items-center gap-1 text-xs"
                title={readOnlyTitle}
            >
                <Fa icon={faLock} />
                Turtle only
            </span>
        {:else if !locked}
            <button
                class="text-text-subtle hover:text-red shrink-0 cursor-pointer p-1 text-xs"
                title="Delete this shape"
                aria-label="Delete this shape"
                onclick={onremove}
            >
                <Fa icon={faTrash} />
            </button>
        {/if}
    </div>

    {#if expanded}
        <div class="border-border space-y-3 border-t px-3 py-3">
            {#if turtleOnly}
                <p
                    class="text-text-subtle bg-background-subtle rounded p-2 text-sm"
                >
                    {readOnlyTitle}
                </p>
            {/if}

            <div class="space-y-2">
                <span class="text-default-text text-sm">Applies to</span>
                {#each TARGET_KINDS as entry (entry.field)}
                    {#if kept.has(entry.field)}
                        <KeptClause
                            label={entry.label}
                            clauses={kept.get(entry.field)}
                        />
                    {/if}
                {/each}
                {#each targets as entry (`${entry.field}-${entry.at}`)}
                    <div class="flex items-end gap-2">
                        <div class="w-56 shrink-0">
                            <SelectEditControl
                                value={entry.field}
                                options={TARGET_KINDS}
                                getOptionValue={kind => kind.field}
                                getOptionLabel={kind => kind.label}
                                disabled={locked}
                                onchange={field => setTargetKind(entry, field)}
                            />
                        </div>
                        <div class="min-w-0 flex-1">
                            <TermPicker
                                kind={entry.pick}
                                value={entry.iri}
                                {terms}
                                {prefixes}
                                disabled={locked}
                                onpick={picked => setTarget(entry, picked)}
                            />
                        </div>
                    </div>
                {/each}
                {#if addingTarget}
                    <div class="flex items-end gap-2">
                        <div class="w-56 shrink-0">
                            <SelectEditControl
                                value={addingTarget}
                                options={TARGET_KINDS}
                                getOptionValue={kind => kind.field}
                                getOptionLabel={kind => kind.label}
                                onchange={field => (addingTarget = field)}
                            />
                        </div>
                        <div class="min-w-0 flex-1">
                            <TermPicker
                                kind={TARGET_KINDS.find(
                                    kind => kind.field === addingTarget,
                                )?.pick}
                                value={null}
                                {terms}
                                {prefixes}
                                onpick={iri => addTarget(addingTarget, iri)}
                            />
                        </div>
                    </div>
                {:else if !locked}
                    <button
                        class="text-text-subtle hover:text-default-text cursor-pointer text-xs"
                        onclick={() => (addingTarget = "targetClasses")}
                    >
                        + {targets.length ? "another target" : "a target"}
                    </button>
                {/if}
            </div>

            {#each shape.properties ?? [] as property, index (index)}
                <PropertyShapeCard
                    {property}
                    {terms}
                    {prefixes}
                    targetClass={classes[0] ?? null}
                    readOnly={locked}
                    onchange={() => changeRule(property)}
                    onedit={() => editRule(property)}
                    onremove={() => removeRule(index)}
                    {onreveal}
                />
            {/each}

            {#each drafts as draft, index (index)}
                <PropertyShapeCard
                    property={draft}
                    {terms}
                    {prefixes}
                    targetClass={classes[0] ?? null}
                    readOnly={locked}
                    onchange={() => promoteDraft(index)}
                    onedit={() => promoteDraft(index)}
                    onremove={() => removeDraft(index)}
                />
            {/each}

            <CardSection title="This shape's own words" filled={saysSomething}>
                <div class="col-span-2">
                    {#if kept.has("name")}
                        <KeptClause label="Name" clauses={kept.get("name")} />
                    {:else}
                        <TextEditControl
                            label="Name"
                            value={shape.name ?? ""}
                            readonly={locked}
                            callOnInput={text =>
                                set("name", text, { soon: true })}
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
                            value={shape.description ?? ""}
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
                            label="Message shown when the shape is broken"
                            clauses={kept.get("message")}
                        />
                    {:else}
                        <TextEditControl
                            label="Message shown when the shape is broken"
                            value={shape.message ?? ""}
                            readonly={locked}
                            callOnInput={text =>
                                set("message", text, { soon: true })}
                            callOnChange={text => set("message", text)}
                        />
                    {/if}
                </div>
                {#if kept.has("severity")}
                    <KeptClause
                        label="Severity"
                        clauses={kept.get("severity")}
                    />
                {:else}
                    <div>
                        <span class="text-default-text text-sm">Severity</span>
                        <SelectEditControl
                            value={shape.severity}
                            options={SEVERITIES}
                            getOptionValue={option => option.value}
                            getOptionLabel={option => option.label}
                            disabled={locked}
                            onchange={value => set("severity", value)}
                        />
                    </div>
                {/if}
                <div class="flex items-end">
                    {#if kept.has("deactivated")}
                        <KeptClause
                            label="Switched off"
                            clauses={kept.get("deactivated")}
                        />
                    {:else}
                        <CheckBoxEditControl
                            label="Switched off"
                            value={shape.deactivated === true}
                            readonly={locked}
                            callOnInputTrue={() => set("deactivated", true)}
                            callOnInputFalse={() => set("deactivated", null)}
                        />
                    {/if}
                </div>
                <div class="col-span-2">
                    {#if kept.has("closed")}
                        <KeptClause
                            label="No other properties allowed"
                            clauses={kept.get("closed")}
                        />
                    {:else}
                        <CheckBoxEditControl
                            label="No other properties allowed"
                            value={shape.closed === true}
                            readonly={locked}
                            callOnInputTrue={() => set("closed", true)}
                            callOnInputFalse={() => set("closed", null)}
                        />
                    {/if}
                </div>
                {#if shape.closed === true || (shape.ignoredProperties ?? []).length}
                    <div class="col-span-2">
                        {#if kept.has("ignoredProperties")}
                            <KeptClause
                                label="Except these properties"
                                clauses={kept.get("ignoredProperties")}
                            />
                        {:else}
                            <ValueListEditor
                                label="Except these properties"
                                values={shape.ignoredProperties ?? []}
                                kind="PROPERTY"
                                {terms}
                                {prefixes}
                                disabled={locked}
                                onchange={values =>
                                    setList("ignoredProperties", values)}
                            />
                        {/if}
                    </div>
                {/if}
            </CardSection>

            <KeptClauseList
                clauses={keptClauses(shape.retained, SHOWN)}
                {prefixes}
            />

            {#if !locked}
                <div class="flex items-end gap-3">
                    <div class="h-8 w-40 shrink-0">
                        <ButtonControl variant="inline" callOnClick={addRule}>
                            <span class="flex items-center gap-2 text-sm">
                                <Fa icon={faPlus} />
                                Add a rule
                            </span>
                        </ButtonControl>
                    </div>
                    {#if available.length}
                        <div class="min-w-0 flex-1">
                            <span class="text-default-text text-sm">
                                or use a rule the document already has
                            </span>
                            <SelectEditControl
                                bind:value={picked}
                                options={available}
                                getOptionValue={rule => rule.iri}
                                getOptionLabel={rule =>
                                    abbreviate(rule.iri, prefixes)}
                                placeholder="pick a shared rule"
                                onchange={useRule}
                            />
                        </div>
                    {/if}
                </div>
            {/if}
        </div>
    {/if}
</div>
