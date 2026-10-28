<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script>
    import { faXmark } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import { Checkbox } from "$lib/components/bitsui/checkbox/index.js";
    import { URI } from "$lib/models/dto/index.ts";
    import { computeMappingType } from "$lib/utils/migrationUtils.js";

    let {
        mappings = $bindable([]),
        candidates = [],
        invalidMappingIds = new Set(),
        getCandidateSubtitle = () => null,
        resourceType = "Item",
        currentClassLabel = null,
    } = $props();

    const BADGE_LABELS = {
        RENAME: "Renamed",
        MOVE: "Moved",
        MERGE: "Merged",
        SPLIT: "Split",
        UNMAPPED: "Unmapped",
        INVALID: "Conflicting",
    };

    const BADGE_CLASSES = {
        RENAME: "bg-lightblue text-blue border-blue",
        MOVE: "bg-purple/10 text-purple border-purple/40",
        MERGE: "bg-lightgray text-default-text border-border",
        SPLIT: "bg-lightgray text-default-text border-border",
        UNMAPPED: "bg-default-background text-text-subtle border-border",
        INVALID: "bg-red-background text-red-text border-red-border",
    };

    let expandedIds = $state(new Set());

    function statusOf(mapping) {
        if (invalidMappingIds.has(mapping.id)) return "INVALID";
        return computeMappingType(mapping.oldResources, mapping.newResources);
    }

    function isLinked(mapping, candidate) {
        return mapping.newResources.some(r => r.iri === candidate.iri);
    }

    function toggleLink(mapping, candidate) {
        if (isLinked(mapping, candidate)) {
            mapping.newResources = mapping.newResources.filter(
                r => r.iri !== candidate.iri,
            );
        } else {
            mapping.newResources = [...mapping.newResources, candidate];
        }
    }

    function unlink(mapping, candidate) {
        mapping.newResources = mapping.newResources.filter(
            r => r.iri !== candidate.iri,
        );
    }

    function toggleExpanded(mapping) {
        const next = new Set(expandedIds);
        if (next.has(mapping.id)) {
            next.delete(mapping.id);
        } else {
            next.add(mapping.id);
        }
        expandedIds = next;
    }

    function otherMappingsLinking(candidate, currentMapping) {
        return mappings.filter(
            m =>
                m.id !== currentMapping.id &&
                m.newResources.some(r => r.iri === candidate.iri),
        );
    }

    function oldLabel(mapping) {
        if (currentClassLabel) {
            const ownResource = mapping.oldResources.find(
                r =>
                    !r.domain || new URI(r.domain).suffix === currentClassLabel,
            );
            if (ownResource) return ownResource.label;
        }
        return mapping.oldResources.map(r => r.label).join(", ");
    }
</script>

<div>
    <h3 class="mb-3 font-semibold">
        Deleted and Possibly Remapped {resourceType}s
    </h3>
    <div
        class="border-border bg-window-background overflow-x-auto rounded-xl border shadow-sm"
    >
        <table class="w-full border-collapse text-left text-sm">
            <thead class="bg-lightgray">
                <tr>
                    <th class="w-3/11 px-3 py-2 font-medium">Old Name</th>
                    <th class="w-2/11 px-3 py-2 font-medium">Status</th>
                    <th class="w-5/11 px-3 py-2 font-medium">Mapped To</th>
                    <th class="w-1/11 px-3 py-2 font-medium">Confidence</th>
                </tr>
            </thead>
            <tbody>
                {#each mappings as mapping (mapping.id)}
                    <tr class="align-top">
                        <td class="px-3 py-2">{oldLabel(mapping)}</td>
                        <td class="px-3 py-2">
                            <span
                                class={`rounded border px-2 py-0.5 text-xs font-medium ${BADGE_CLASSES[statusOf(mapping)]}`}
                            >
                                {BADGE_LABELS[statusOf(mapping)]}
                            </span>
                        </td>
                        <td class="px-3 py-2">
                            <div class="flex flex-wrap items-center gap-1">
                                {#each mapping.newResources as candidate (candidate.iri)}
                                    <span
                                        class="bg-lightgray text-default-text flex items-center gap-1 rounded px-2 py-0.5 text-xs"
                                    >
                                        {candidate.label}
                                        {#if getCandidateSubtitle(candidate)}
                                            <span class="text-text-subtle">
                                                ({getCandidateSubtitle(
                                                    candidate,
                                                )})
                                            </span>
                                        {/if}
                                        <button
                                            type="button"
                                            class="hover:text-red-text"
                                            aria-label={`Unlink ${candidate.label}`}
                                            onclick={() =>
                                                unlink(mapping, candidate)}
                                        >
                                            <Fa icon={faXmark} size="xs" />
                                        </button>
                                    </span>
                                {/each}
                                <button
                                    type="button"
                                    class="border-border text-text-subtle hover:text-default-text rounded border border-dashed px-2 py-0.5 text-xs"
                                    onclick={() => toggleExpanded(mapping)}
                                >
                                    {expandedIds.has(mapping.id)
                                        ? "Close"
                                        : "+ Link"}
                                </button>
                            </div>

                            {#if expandedIds.has(mapping.id)}
                                <div
                                    class="border-border mt-2 max-h-48 space-y-1 overflow-y-auto rounded-md border p-2"
                                >
                                    {#each candidates as candidate (candidate.iri)}
                                        {@const others = otherMappingsLinking(
                                            candidate,
                                            mapping,
                                        )}
                                        <label
                                            class="hover:bg-lightgray flex items-center gap-2 rounded px-1 py-0.5 text-xs"
                                        >
                                            <Checkbox
                                                checked={isLinked(
                                                    mapping,
                                                    candidate,
                                                )}
                                                onCheckedChange={() =>
                                                    toggleLink(
                                                        mapping,
                                                        candidate,
                                                    )}
                                            />
                                            <span>
                                                {candidate.label}
                                                {#if getCandidateSubtitle(candidate)}
                                                    <span
                                                        class="text-text-subtle"
                                                    >
                                                        ({getCandidateSubtitle(
                                                            candidate,
                                                        )})
                                                    </span>
                                                {/if}
                                                {#if others.length > 0}
                                                    <span
                                                        class="text-text-subtle italic"
                                                    >
                                                        - also linked from {others
                                                            .map(oldLabel)
                                                            .join(", ")}
                                                    </span>
                                                {/if}
                                            </span>
                                        </label>
                                    {/each}
                                </div>
                            {/if}
                        </td>
                        <td class="px-3 py-2">
                            {#if mapping.confidenceScore == null || mapping.newResources.length === 0}
                                —
                            {:else}
                                {Math.round(mapping.confidenceScore * 10000) /
                                    100}%
                            {/if}
                        </td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </div>
</div>
