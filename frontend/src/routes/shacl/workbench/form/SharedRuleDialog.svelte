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
        /** Called with the name for the copy, to split. */
        onsplit = () => {},
        /** Called to change the rule where it stands, for every shape using it. */
        onall = () => {},
        /** Called when neither was chosen, so the typed change can be put back. */
        oncancel = () => {},
    } = $props();

    let newIri = $state("");
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
     * the copy does not end up called `…ShapeNameCardinality`.
     */
    function suggest() {
        const namespace = localPart(rule?.iri ?? "").namespace;
        const owner = localPart(shapeIri ?? "").local.replace(/Shape$/, "");
        const original = localPart(rule?.iri ?? "").local;
        return `${namespace}${owner}${original}`;
    }

    function localPart(iri) {
        const cut = Math.max(iri.lastIndexOf("#"), iri.lastIndexOf("/"));
        return cut < 0
            ? { namespace: "", local: iri }
            : { namespace: iri.slice(0, cut + 1), local: iri.slice(cut + 1) };
    }

    function open() {
        decided = false;
        newIri = splittable ? suggest() : "";
    }

    function split() {
        decided = true;
        onsplit(newIri.trim());
    }

    function all() {
        decided = true;
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
    disablePrimary={splittable && newIri.trim() === ""}
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
            <TextEditControl
                label="Name for this shape's own copy"
                bind:value={newIri}
                placeholder="a name the document does not use yet"
            />
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
