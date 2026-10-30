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
    import InfoTooltip from "$lib/components/InfoTooltip.svelte";
    import TreeChevron from "$lib/components/TreeChevron.svelte";
    import XmiNamespaceEditor from "$lib/components/XmiNamespaceEditor.svelte";
    import { plural } from "$lib/utils/plural.js";

    let {
        /** The progress of the read, which is what the export states. */
        read,
        /** The selection worked out, as {@link importChoiceOf} reports it. */
        choice,
        /** What to name nearly every resource with, empty to leave the export its own. */
        modelNamespace = $bindable(""),
        /** What to name the extension-stereotyped elements with, the same way. */
        extensionNamespace = $bindable(""),
    } = $props();

    const CARRIED_OVER_EXPLAINED =
        "Declarations the export brings along, which the workspace takes over as they are. " +
        "None of them decides what a resource is called, so they can be changed at any time " +
        "afterwards under Workspace → Namespaces.";

    let showCarriedOver = $state(false);

    const statedModel = $derived(read?.packages?.[0]?.modelNamespace ?? "");
    const statedExtension = $derived(
        read?.packages?.[0]?.extensionNamespace ?? "",
    );
    const extensionNeeded = $derived(
        read?.packages?.[0]?.hasExtensionElements ?? false,
    );
    /**
     * The prefixes the import carries over beside the two that are decided here. Measured against
     * what the two are going to be rather than against what the export states: overruling the
     * model namespace does not take its prefix out of the table, it leaves it as one more
     * declaration the workspace takes over.
     */
    const carriedOver = $derived.by(() => {
        const decided = [
            modelNamespace.trim() || statedModel,
            extensionNamespace.trim() || statedExtension,
        ].filter(Boolean);
        return (read?.prefixes ?? [])
            .filter(one => !decided.includes(one.namespace))
            .toSorted((one, other) => one.prefix.localeCompare(other.prefix));
    });
</script>

<div class="mx-2 flex h-full min-h-0 flex-col gap-4">
    <p class="text-sm">
        <span class="font-semibold">Importing:</span>
        {plural(choice.schemas.length, "profile")},
        {plural(choice.classes, "class definition")},
        {plural(choice.diagramIds.length, "diagram")}
    </p>

    <XmiNamespaceEditor
        stated={{ model: statedModel, extension: statedExtension }}
        {extensionNeeded}
        bind:model={modelNamespace}
        bind:extension={extensionNamespace}
    />

    {#if carriedOver.length > 0}
        <!-- Listed rather than counted with a few names: naming four of twenty raises the
             question which the other sixteen are without answering it. -->
        <div class="text-font-secondary min-h-0 text-xs">
            <div class="flex items-center gap-2">
                <button
                    type="button"
                    class="hover:text-default-text -m-1 flex cursor-pointer items-center gap-1 p-1"
                    aria-expanded={showCarriedOver}
                    onclick={() => (showCarriedOver = !showCarriedOver)}
                >
                    <TreeChevron expanded={showCarriedOver} />
                    <span>
                        {plural(
                            carriedOver.length,
                            "further prefix is",
                            "further prefixes are",
                        )} carried over
                    </span>
                </button>
                <InfoTooltip text={CARRIED_OVER_EXPLAINED} side="top" />
            </div>

            {#if showCarriedOver}
                <ul
                    class="border-border mt-1 flex max-h-40 flex-col overflow-y-auto rounded border px-2 py-1"
                >
                    {#each carriedOver as one (one.prefix)}
                        <li class="flex gap-2 py-0.5">
                            <span class="w-24 shrink-0 font-mono">
                                {one.prefix}
                            </span>
                            <span class="min-w-0 break-all">
                                {one.namespace}
                            </span>
                        </li>
                    {/each}
                </ul>
            {/if}
        </div>
    {/if}
</div>
