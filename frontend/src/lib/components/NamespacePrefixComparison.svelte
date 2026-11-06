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
    import { faTriangleExclamation } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import TextEditControl from "$lib/components/TextEditControl.svelte";
    import ViolationMessages from "$lib/components/ViolationMessages.svelte";
    import { ReactiveValueWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-value-wrapper.svelte.js";
    import { getControlButtonsForReactiveObject } from "$lib/models/reactive/utils/reactive-objects-control-button-utils.js";
    import { isInvalidNamespacePrefix } from "$lib/models/reactive/validity-rules/validityFunctions.js";
    import { withColon, withoutColon } from "$lib/utils/namespace.js";

    let {
        comparison,
        resolutions = $bindable([]),
        isValid = $bindable(true),
    } = $props();

    const Action = { KEEP: "KEEP", RENAME: "RENAME", DROP: "DROP" };
    /** Prefix, then the namespace it is to stand for and where that namespace comes from. */
    const ROW_COLUMNS = "grid-cols-[8rem_1fr_9rem]";

    let model = $state({ bindings: [], groups: [] });

    $effect(() => {
        model = buildModel(comparison);
    });

    $effect(() => {
        resolutions = model.bindings.map(resolutionOf);
        isValid = model.bindings.every(binding => binding.prefix.isValid);
    });

    /**
     * One binding per namespace laying claim to a contested prefix, grouped by that prefix. The
     * prefixes nobody contests are not shown — there is nothing to decide about them — but they
     * are still occupied, so a rename into one of them has to be caught here rather than by the
     * backend.
     */
    function buildModel(comparisonToShow) {
        const entries = comparisonToShow ?? [];
        const reserved = new Map(
            entries
                .filter(entry => !entry.contested)
                .map(entry => [
                    withoutColon(entry.prefix),
                    entry.workspace?.iri ?? entry.imported?.[0]?.iri,
                ]),
        );
        const groups = entries
            .filter(entry => entry.contested)
            .map(entry => ({
                prefix: entry.prefix,
                bindings: bindingsOf(entry),
            }));
        const bindings = groups.flatMap(group => group.bindings);
        for (const binding of bindings) {
            binding.prefix.violationChecks.push(() =>
                prefixMustBeFree(binding, bindings, reserved),
            );
        }
        return { bindings, groups };
    }

    /**
     * The namespaces claiming one prefix, the dataset first so that a prefix it already has stays
     * with it by default. A namespace both sides bring stays one binding, or the two halves would
     * be answered separately and contradict each other.
     */
    function bindingsOf(entry) {
        const workspace = entry.workspace
            ? bindingOf(entry, entry.workspace, true)
            : null;
        const imported = (entry.imported ?? []).flatMap(claim => {
            if (workspace && claim.iri === workspace.iri) {
                workspace.fileNames = claim.fileNames ?? [];
                return [];
            }
            return [bindingOf(entry, claim, false)];
        });
        return workspace ? [workspace, ...imported] : imported;
    }

    /** One claim as an editable binding: `value` is what the prefix is to become, `backup` what
     * the file or the dataset brought. */
    function bindingOf(entry, claim, inWorkspace) {
        return {
            prefix: new ReactiveValueWrapper(
                entry.prefix,
                isInvalidNamespacePrefix,
            ),
            inWorkspace,
            iri: claim.iri,
            fileNames: claim.fileNames ?? [],
        };
    }

    /**
     * The comparison settles one set of prefixes for the dataset, so a prefix has to be free of
     * every other namespace — decided on here or left alone as uncontested. Two bindings of the
     * same namespace may share a prefix; they are one entry of the set, not two.
     */
    function prefixMustBeFree(binding, bindings, reserved) {
        const prefix = withoutColon(binding.prefix.value);
        if (prefix.length === 0) {
            return [];
        }
        const takenByAnother =
            bindings.some(
                other =>
                    other !== binding &&
                    other.iri !== binding.iri &&
                    withoutColon(other.prefix.value) === prefix,
            ) ||
            (reserved.has(prefix) && reserved.get(prefix) !== binding.iri);
        return takenByAnother ? ["must be unique"] : [];
    }

    /** Where the namespace comes from, which is what tells two rows of a group apart. */
    function originOf(binding) {
        const origins = binding.inWorkspace ? ["the workspace"] : [];
        return [...origins, ...binding.fileNames].join(", ");
    }

    function resolutionOf(binding) {
        const prefix = withoutColon(binding.prefix.value);
        const decision = { prefix: binding.prefix.backup, iri: binding.iri };
        if (prefix.length === 0) {
            return { ...decision, action: Action.DROP, newPrefix: null };
        }
        if (prefix === withoutColon(binding.prefix.backup)) {
            return { ...decision, action: Action.KEEP, newPrefix: null };
        }
        return {
            ...decision,
            action: Action.RENAME,
            newPrefix: withColon(prefix),
        };
    }
</script>

<div class="mx-2 mt-2 flex min-h-0 flex-col">
    {#if model.groups.length > 0}
        <p class="flex items-center gap-2 font-semibold">
            <Fa class="text-orange" icon={faTriangleExclamation} />
            The import has conflicting namespaces
        </p>
        <p class="text-text-subtle mt-1 text-xs">
            Nothing has been imported yet. A prefix can only stand for one
            namespace, so the ones below need a different one — or none at all,
            by clearing the field.
        </p>

        <div class="mt-3 max-h-[45vh] min-h-0 overflow-y-auto">
            {#each model.groups as group (group.prefix)}
                <p class="text-text-subtle mt-3 text-xs">
                    {group.bindings.length} namespaces claim "{group.prefix}"
                </p>
                {#each group.bindings as binding (binding.iri)}
                    <div
                        class={`border-border grid ${ROW_COLUMNS} items-center gap-x-3 border-t py-2`}
                    >
                        <TextEditControl
                            bind:value={binding.prefix.value}
                            placeholder="no prefix"
                            warn={!binding.prefix.isValid}
                            highlight={binding.prefix.isModified}
                            buttons={getControlButtonsForReactiveObject(
                                binding.prefix,
                                false,
                            )}
                        />
                        <span class="text-text-subtle text-xs break-all">
                            {binding.iri}
                        </span>
                        <span class="text-text-subtle text-xs break-all">
                            {originOf(binding)}
                        </span>

                        <div class="col-span-2 col-start-1 self-start">
                            <ViolationMessages
                                violations={binding.prefix.violations}
                            />
                        </div>
                    </div>
                {/each}
            {/each}
        </div>
    {/if}
</div>
