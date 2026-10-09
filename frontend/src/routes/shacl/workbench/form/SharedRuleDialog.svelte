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
     * Asks what a change to a shared rule is meant to reach.
     *
     * A rule written as a shape of its own may carry the cardinality of forty classes at once, so
     * changing it from under one of them is not a change to that class — it is a change to all of
     * them. The offer made first is therefore the one people almost always mean: give this shape a
     * rule of its own, copied from the shared one, and change that. Editing it for everybody stays
     * available, as a deliberate second choice.
     *
     * The copy's name is shown and can be corrected, because it ends up in the document and the
     * suggestion is only a guess at what the user would have called it.
     */
    import { faCodeBranch } from "@fortawesome/free-solid-svg-icons";

    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { abbreviate } from "$lib/shacl/turtleTerms.js";

    let {
        showDialog = $bindable(),
        /** The rule being changed, with the shapes that use it. */
        rule = null,
        /**
         * The shape the change was made under, whose reference a split would move.
         *
         * Absent when the change was made on the rule's own card, where there is no one shape to
         * give a copy to — the card is the shared rule, so the only question left is whether the
         * user meant to reach all of them.
         */
        shapeIri = null,
        prefixes = {},
        /**
         * Called with the name for the copy, to split. Resolves to why the copy was refused, or
         * to nothing once it is made — a name the document already uses is refused, and the
         * dialog stays open for another one rather than dropping what was typed.
         */
        onsplit = async () => null,
        /** Called to change the rule where it stands, for every shape using it. */
        onall = () => {},
        /** Called when neither was chosen, so the typed change can be put back. */
        oncancel = () => {},
    } = $props();

    let newIri = $state("");
    /** Why the last name for a copy was refused. */
    let refusal = $state(null);
    let splitting = $state(false);
    /** Whether a choice was made, so closing the dialog any other way counts as a cancel. */
    let decided = false;

    const shares = $derived(rule?.usedBy?.length ?? 0);

    const others = $derived(
        (rule?.usedBy ?? []).filter(iri => iri !== shapeIri),
    );

    /** Whether a copy is on offer at all, which needs a shape to give it to. */
    const splittable = $derived(shapeIri != null);

    /**
     * A name for the copy: this shape's name in front of the rule's.
     *
     * `ex:ACLineSegmentShape` + `ex:NameCardinality` reads as `ex:ACLineSegmentNameCardinality`,
     * which says both what it constrains and what it came from. The trailing "Shape" is dropped so
     * the copy does not end up called `…ShapeNameCardinality`. Offered the way the document writes
     * names — the server expands a prefixed name — so it can be read and corrected.
     */
    function suggest() {
        const namespace = localPart(rule?.iri ?? "").namespace;
        const owner = localPart(shapeIri ?? "").local.replace(/Shape$/, "");
        const original = localPart(rule?.iri ?? "").local;
        return abbreviate(`${namespace}${owner}${original}`, prefixes);
    }

    function localPart(iri) {
        const cut = Math.max(iri.lastIndexOf("#"), iri.lastIndexOf("/"));
        return cut < 0
            ? { namespace: "", local: iri }
            : { namespace: iri.slice(0, cut + 1), local: iri.slice(cut + 1) };
    }

    function open() {
        decided = false;
        refusal = null;
        splitting = false;
        newIri = splittable ? suggest() : "";
    }

    /** The name as typed, without the angle brackets someone pasting an IRI may bring along. */
    function typedName() {
        const typed = newIri.trim();
        return typed.startsWith("<") && typed.endsWith(">")
            ? typed.slice(1, -1)
            : typed;
    }

    async function split() {
        if (splitting) {
            return;
        }
        splitting = true;
        refusal = null;
        try {
            refusal = (await onsplit(typedName())) ?? null;
        } finally {
            splitting = false;
        }
        if (refusal === null) {
            decided = true;
            showDialog = false;
        }
    }

    function all() {
        decided = true;
        showDialog = false;
        onall();
    }

    function close() {
        if (!decided) {
            oncancel();
        }
        return true;
    }
</script>

<ActionDialog
    bind:showDialog
    title="This rule is shared"
    titleIcon={faCodeBranch}
    primaryLabel={splittable
        ? "Give this shape its own copy"
        : `Change it for all ${shares} shapes`}
    onPrimary={splittable ? split : all}
    closeOnPrimary={false}
    disablePrimary={splitting || (splittable && newIri.trim() === "")}
    disableSecondary={splitting}
    secondaryLabel={splittable ? `Change it for all ${shares} shapes` : null}
    onSecondary={all}
    onOpen={open}
    onClose={close}
    size="w-[34rem] max-w-[90vw]"
>
    <div class="space-y-3 px-2 text-sm">
        <p class="text-default-text">
            <span class="font-mono">
                {abbreviate(rule?.iri ?? "", prefixes)}
            </span>
            is written as a shape of its own, and
            {shares === 1 ? "one shape uses" : `${shares} shapes use`} it. Changing
            it here changes it for all of them.
        </p>

        {#if others.length}
            <div class="text-text-subtle">
                <p>Also used by:</p>
                <ul class="mt-1 max-h-32 space-y-0.5 overflow-y-auto font-mono">
                    {#each others as iri (iri)}
                        <li class="truncate">{abbreviate(iri, prefixes)}</li>
                    {/each}
                </ul>
            </div>
        {/if}

        {#if splittable}
            <div>
                <TextEditControl
                    label="Name for this shape's own copy"
                    bind:value={newIri}
                    warn={refusal !== null}
                    placeholder="a name the document does not use yet"
                    callOnInput={() => (refusal = null)}
                />
                {#if refusal}
                    <p class="text-red-text mt-1 text-xs" role="alert">
                        {refusal}
                    </p>
                {/if}
            </div>
            <p class="text-text-subtle text-xs">
                The copy is taken from the rule as the document writes it, so it
                starts out saying exactly the same thing. Only
                <span class="font-mono">
                    {abbreviate(shapeIri ?? "", prefixes)}
                </span>
                is moved to it.
            </p>
        {/if}
    </div>
</ActionDialog>
