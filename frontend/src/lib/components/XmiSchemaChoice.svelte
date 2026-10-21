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
    import PackageTreeSelect from "$lib/components/PackageTreeSelect.svelte";
    import XmiSchemaSummary from "$lib/components/XmiSchemaSummary.svelte";
    import { withoutWrapper } from "$lib/utils/packageTree.js";

    let {
        /** The progress of the read, which is what there is to choose from. */
        read,
        /** The selection worked out, as {@link importChoiceOf} reports it. */
        choice,
        /** The rows ticked in the tree. */
        selectedIds = $bindable(new Set()),
        /** The available diagrams turned down, rather than the ones taken. */
        deselectedDiagramIds = $bindable(new Set()),
        /** Which packages are opened, and which rows of the summary; held by the caller so that
         * stepping away and back leaves the tree as it was. */
        expandedPackages = $bindable(new Set()),
        openedSummaryRows = $bindable(new Set()),
    } = $props();

    /** What the four words on this screen mean here, which is not what they mean everywhere. */
    const TERMS_EXPLAINED =
        "Profile: A package that declares a namespace of its own, published as a single RDFS " +
        "file.\n\n" +
        "Class: A class of the model, with its attributes, associations and enum entries.\n\n" +
        "Dependency: A class the selected profile depends on. This could be a datatype or a " +
        "class referenced via an association or inheritance. A copy is written into the created " +
        "profile.\n\n" +
        "Diagram: A class diagram drawn in Enterprise Architect, imported as a custom diagram " +
        "with the same content and layout.";
    /** The tree as it is shown: the export's outermost package becomes its title. */
    const tree = $derived(withoutWrapper(read?.packages ?? []));
</script>

<div class="mx-2 flex h-full min-h-0 flex-col">
    <div class="flex min-h-0 flex-1 gap-3">
        <!-- Titled like the summary beside it, which is also what gives the terms somewhere to be
             explained now that the screen says nothing of its own. -->
        <div class="border-border flex min-h-0 flex-1 flex-col rounded border">
            <p
                class="border-border flex items-center gap-2 border-b px-3 py-2 text-xs font-semibold"
            >
                {tree.title ?? "Packages"}
                <InfoTooltip text={TERMS_EXPLAINED} side="bottom" />
            </p>
            <div class="min-h-0 flex-1 overflow-y-auto p-2">
                <PackageTreeSelect
                    packages={read.packages}
                    nodes={tree.nodes}
                    bind:selectedIds
                    diagramsByPackage={choice.diagramsByOwner}
                    diagramGroups={choice.groups}
                    orderedRowIds={choice.orderedRowIds}
                    bind:deselectedDiagramIds
                    bind:expanded={expandedPackages}
                />
            </div>
        </div>

        <div
            class="border-border bg-background-subtle min-h-0 w-1/4 rounded border"
        >
            <XmiSchemaSummary
                {choice}
                classNames={read.classNames}
                bind:openedRows={openedSummaryRows}
            />
        </div>
    </div>
</div>
