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
    import { faDiagramProject } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import {
        groupCandidatesByStub,
        schemaLabel,
    } from "$lib/actions/schemaExtensionActions.js";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { graphColors } from "$lib/graphColors.svelte.js";
    import { uriSuffix } from "$lib/utils/iri.js";

    let {
        showDialog = $bindable(),
        workspaceName,
        candidates = [],
        targetLabel = "",
        onPick = () => {},
    } = $props();

    const properties = [
        { label: "Label", read: stub => stub?.label ?? "—" },
        {
            label: "Derived from",
            read: stub => suffixOf(stub?.superClassUri),
        },
        { label: "Package", read: stub => stub?.packageLabel ?? "—" },
        {
            label: "Stereotypes",
            read: stub =>
                stub?.stereotypes?.length
                    ? stub.stereotypes.map(suffixOf).join(", ")
                    : "—",
        },
        { label: "Comment", read: stub => stub?.comment ?? "—" },
    ];

    let pickedKey = $state(null);

    let groups = $derived(groupCandidatesByStub(candidates));

    let picked = $derived(
        (groups.find(group => group.key === pickedKey) ?? groups[0])
            ?.occurrences[0],
    );

    /** The properties the candidates do not agree on. */
    let differing = $derived(
        new Set(
            properties
                .filter(property => {
                    const first = property.read(
                        groups[0]?.occurrences[0]?.stub,
                    );
                    return groups.some(
                        group =>
                            property.read(group.occurrences[0].stub) !== first,
                    );
                })
                .map(property => property.label),
        ),
    );

    function onOpen() {
        pickedKey = groups[0]?.key ?? null;
    }

    function confirm() {
        if (picked) {
            onPick(picked);
        }
    }

    function suffixOf(uri) {
        return uriSuffix(uri) || "—";
    }
</script>

<ActionDialog
    bind:showDialog
    {onOpen}
    title="Select Source Schema"
    primaryLabel="Create Class"
    disablePrimary={!picked}
    onPrimary={confirm}
>
    <div class="space-y-3 px-3 py-3">
        <p class="text-default-text text-sm leading-relaxed">
            The class is maintained differently in the schemas below. Please
            select the one to copy into{targetLabel ? ` "${targetLabel}"` : ""}.
        </p>
        <div class="flex flex-col gap-2">
            {#each groups as group (group.key)}
                <label
                    class="border-border hover:bg-nav-hover-background flex cursor-pointer gap-2 rounded border p-2"
                >
                    <input
                        type="radio"
                        class="mt-1"
                        value={group.key}
                        checked={pickedKey === group.key}
                        onchange={() => (pickedKey = group.key)}
                    />
                    <span class="flex min-w-0 flex-1 flex-col gap-1">
                        <span
                            class="text-default-text flex flex-wrap items-center gap-x-3 gap-y-1 text-sm font-bold"
                        >
                            {#each group.occurrences as occurrence (occurrence.graphUri)}
                                <span
                                    class="flex items-center gap-1"
                                    title={occurrence.graphUri}
                                >
                                    <span
                                        class="shrink-0"
                                        style={graphColors.get(
                                            workspaceName,
                                            occurrence.graphUri,
                                        )
                                            ? `color: ${graphColors.get(workspaceName, occurrence.graphUri)};`
                                            : ""}
                                    >
                                        <Fa icon={faDiagramProject} />
                                    </span>
                                    {schemaLabel(occurrence)}
                                </span>
                            {/each}
                        </span>
                        <span
                            class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-xs"
                        >
                            {#each properties as property (property.label)}
                                <span
                                    class="text-text-subtle whitespace-nowrap"
                                >
                                    {property.label}
                                </span>
                                <span
                                    class="min-w-0 break-words {differing.has(
                                        property.label,
                                    )
                                        ? 'text-default-text font-bold'
                                        : 'text-text-subtle'}"
                                >
                                    {property.read(group.occurrences[0].stub)}
                                </span>
                            {/each}
                        </span>
                    </span>
                </label>
            {/each}
        </div>
    </div>
</ActionDialog>
