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
    import XmiNamespaceEditor from "$lib/components/XmiNamespaceEditor.svelte";

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

    const statedModel = $derived(read?.packages?.[0]?.modelNamespace ?? "");
    const statedExtension = $derived(
        read?.packages?.[0]?.extensionNamespace ?? "",
    );
    const extensionNeeded = $derived(
        read?.packages?.[0]?.hasExtensionElements ?? false,
    );
    /** The prefixes the import carries over beside the two that are decided here. */
    const carriedOver = $derived(
        (read?.prefixes ?? [])
            .filter(
                one =>
                    one.namespace !== statedModel &&
                    one.namespace !== statedExtension,
            )
            .map(one => one.prefix),
    );

    function plural(count, one, many = `${one}s`) {
        return `${count} ${count === 1 ? one : many}`;
    }
</script>

<div class="mx-2 flex h-full min-h-0 flex-col gap-4">
    <p class="text-sm">
        <span class="font-semibold">Importing:</span>
        {plural(choice.schemas.length, "profile")},
        {plural(choice.classes, "class", "classes")},
        {plural(choice.diagramIds.length, "diagram")}
    </p>

    <XmiNamespaceEditor
        stated={{ model: statedModel, extension: statedExtension }}
        {extensionNeeded}
        bind:model={modelNamespace}
        bind:extension={extensionNamespace}
    />

    {#if carriedOver.length > 0}
        <p class="text-font-secondary flex items-baseline gap-2 text-xs">
            <span>
                {plural(
                    carriedOver.length,
                    "further prefix is",
                    "further prefixes are",
                )} carried over ({carriedOver
                    .slice(0, 4)
                    .join(", ")}{carriedOver.length > 4 ? ", …" : ""}).
            </span>
            <InfoTooltip text={CARRIED_OVER_EXPLAINED} side="top" />
        </p>
    {/if}
</div>
