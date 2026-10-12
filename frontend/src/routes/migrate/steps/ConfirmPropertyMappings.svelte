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
    import { onMount } from "svelte";
    import { get } from "svelte/store";

    import {
        confirmPropertyMappings,
        migrationPropertiesOverview,
    } from "$lib/api/generated/index.ts";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import { migrationState } from "$lib/sharedState.svelte.js";
    import {
        filterRenameCandidates,
        groupMappingsForSubmission,
        isNonInstantiableAssociationRename,
    } from "$lib/utils/migrationUtils.js";

    let { substeps = [], currentSubstepIndex = 0 } = $props();

    const PROPERTY_KINDS = [
        { overviewKey: "attributes", bodyField: "attributeMappings" },
        { overviewKey: "associations", bodyField: "associationMappings" },
        { overviewKey: "enumEntries", bodyField: "enumEntryMappings" },
    ];

    let classes = $state([]);
    let isLoading = $state(true);
    let ignorePrefixes = $state(true);

    let currentSubstep = $derived(substeps[currentSubstepIndex]);

    let filteredClasses = $derived(
        classes.map(cls => ({
            ...cls,
            attributes: filterRenameCandidates(cls.attributes, {
                ignorePrefixes,
            }),
            associations: filterRenameCandidates(cls.associations, {
                ignorePrefixes,
                alsoHide: isNonInstantiableAssociationRename,
            }),
            enumEntries: filterRenameCandidates(cls.enumEntries, {
                ignorePrefixes,
            }),
        })),
    );

    let invalidMappingIdsByKind = $derived.by(() => {
        const result = {};
        for (const { overviewKey } of PROPERTY_KINDS) {
            const allRows = filteredClasses.flatMap(
                cls => cls[overviewKey]?.deletedAndRemapped ?? [],
            );
            result[overviewKey] =
                groupMappingsForSubmission(allRows).invalidMappingIds;
        }
        return result;
    });

    let currentInvalidMappingIds = $derived(
        invalidMappingIdsByKind[
            PROPERTY_KINDS[currentSubstepIndex]?.overviewKey
        ] ?? new Set(),
    );

    onMount(async () => {
        const storedState = get(migrationState);
        ignorePrefixes = storedState.ignorePrefixes;

        const { data, error } = await migrationPropertiesOverview();
        if (error) {
            console.log("Failed to fetch property overview:", error);
        } else {
            classes = data;
        }

        isLoading = false;
    });

    export async function onNext() {
        const { body, anyInvalid } = buildPropertyMappingsBody();

        if (anyInvalid) {
            toastStore.warning(
                "Some mappings were skipped",
                "A property can't be linked as both a merge target and a split source at once. Fix those rows before continuing.",
            );
        }

        try {
            const { error } = await confirmPropertyMappings({
                body: body,
            });

            if (error) {
                toastStore.error(
                    "Save failed",
                    "Could not save property mappings for the migration.",
                );
            }
        } catch (e) {
            console.log("Failed to save property mappings:", e);
            toastStore.error(
                "Save failed",
                "Could not save property mappings for the migration.",
            );
        }
    }

    /**
     * Groups every class's linked rows into the final `ResourceMapping`s (per property kind,
     * across the whole class list at once - a merge/split can link rows that live in two
     * different classes' own tables), then buckets each resulting mapping back under every class
     * one of its member rows originally belonged to. The backend resolves which class a mapping's
     * result actually attaches to from the mapping's own data, not from this bucketing - it only
     * needs each mapping to appear in the submitted list at least once, and safely ignores a
     * mapping id it has already materialized if the same group is reachable from more than one
     * class's bucket here.
     */
    function buildPropertyMappingsBody() {
        const bodyByClassLabel = new Map();
        let anyInvalid = false;

        function bucketFor(classLabel) {
            if (!bodyByClassLabel.has(classLabel)) {
                bodyByClassLabel.set(classLabel, {
                    classLabel,
                    attributeMappings: [],
                    associationMappings: [],
                    enumEntryMappings: [],
                });
            }
            return bodyByClassLabel.get(classLabel);
        }

        for (const { overviewKey, bodyField } of PROPERTY_KINDS) {
            const allRows = classes.flatMap(
                cls => cls[overviewKey]?.deletedAndRemapped ?? [],
            );
            const { invalidMappingIds, mappingByRowId } =
                groupMappingsForSubmission(allRows);
            if (invalidMappingIds.size > 0) {
                anyInvalid = true;
            }

            for (const cls of classes) {
                const rows = cls[overviewKey]?.deletedAndRemapped ?? [];
                const seenGroupIds = new Set();
                for (const row of rows) {
                    const merged = mappingByRowId.get(row.id);
                    if (!merged || seenGroupIds.has(merged.id)) continue;
                    seenGroupIds.add(merged.id);
                    bucketFor(cls.classLabel)[bodyField].push(merged);
                }
            }
        }

        const body = [...bodyByClassLabel.values()].filter(
            cls =>
                cls.attributeMappings.length +
                    cls.associationMappings.length +
                    cls.enumEntryMappings.length >
                0,
        );
        return { body, anyInvalid };
    }
</script>

<div class="flex h-full flex-col space-y-4">
    <div class="flex justify-center">
        <div class="flex justify-center space-x-4">
            {#each substeps as substep, i}
                <span
                    class={`px-2 text-sm ${i === currentSubstepIndex ? "border-b-2 font-bold text-blue-700" : "text-button-disabled-background"}`}
                >
                    {substep.title}
                </span>
            {/each}
        </div>
    </div>

    <div class="flex-1 overflow-y-auto p-2">
        <currentSubstep.component
            classes={filteredClasses}
            {isLoading}
            invalidMappingIds={currentInvalidMappingIds}
        />
    </div>
</div>
