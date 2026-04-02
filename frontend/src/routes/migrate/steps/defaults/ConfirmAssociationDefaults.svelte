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
    import { faCaretDown } from "@fortawesome/free-solid-svg-icons";
    import { CollapsibleCard } from "svelte-collapsible";
    import { Fa } from "svelte-fa";

    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";

    let { classes, disableNext = $bindable(), isLoading } = $props();

    function getChangeType(association) {
        if (association.semanticResourceChangeType === "ADD") {
            if (association.optional === true) {
                return "Association added (optional)";
            } else {
                return "Association added (required)";
            }
        } else if (
            association.semanticResourceChangeType === "ADDED_FROM_INHERITANCE"
        ) {
            return "Association newly inherited";
        } else if (
            association.semanticResourceChangeType !== "DELETE" &&
            association.changes?.some(
                a => a.semanticFieldChangeType === "RANGE_CHANGE",
            )
        ) {
            return "Target class changed";
        }
        return "other";
    }

    function allowsInput(association) {
        return (
            association.associationUsed &&
            (association.semanticResourceChangeType === "ADD" ||
                association.semanticResourceChangeType ===
                    "ADDED_FROM_INHERITANCE" ||
                (association.semanticResourceChangeType !== "DELETE" &&
                    (association.changes?.some(
                        c => c.semanticFieldChangeType === "RANGE_CHANGE",
                    ) ||
                        association.changes?.some(
                            c =>
                                c.semanticFieldChangeType ===
                                "ASSOCIATION_USED_CHANGE",
                        ))))
        );
    }

    function hasAssociations(cls) {
        return (
            cls.associations &&
            cls.associations.some(assoc => allowsInput(assoc))
        );
    }
</script>

<div class="text-default-text flex flex-col space-y-10">
    <InfoBox>
        This step lets you add <span class="font-semibold">
            default values for associations
        </span>
        .
        <br />
        You can either provide a SPARQL-Mapping for association targets or leave the
        field empty to not set a default value. The provided mapping must represent
        specify a ?target variable and will be inserted in the WHERE clause of the
        update.
    </InfoBox>

    {#if !isLoading && (classes.length === 0 || !classes.some(hasAssociations))}
        <EmptyStateCard
            title="No Association Defaults Required"
            description="There are no associations that require default values in this migration."
        />
    {:else}
        {#each classes as cls}
            {#if hasAssociations(cls)}
                <div class="space-y-4">
                    <CollapsibleCard>
                        <h2 slot="header" class="mb-3 text-lg font-semibold">
                            {cls.classLabel}
                            <Fa icon={faCaretDown} />
                        </h2>
                        <div
                            class="border-l-blue flex-col space-y-4 border-l-4 pl-4"
                            slot="body"
                        >
                            <div>
                                <div
                                    class="border-border bg-window-background overflow-x-auto rounded-xl border shadow-sm"
                                >
                                    <table
                                        class="w-full border-collapse text-left text-sm"
                                    >
                                        <thead class="bg-lightgray">
                                            <tr>
                                                <th
                                                    class="w-1/4 px-4 py-2 font-medium"
                                                >
                                                    Association
                                                </th>
                                                <th
                                                    class="w-1/6 px-4 py-2 font-medium"
                                                >
                                                    Change
                                                </th>
                                                <th
                                                    class="w-1/6 px-4 py-2 font-medium"
                                                >
                                                    Target Class
                                                </th>
                                                <th
                                                    class="w-1/3 px-4 py-2 font-medium"
                                                >
                                                    SPARQL Mapping
                                                </th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {#each cls.associations as association}
                                                {#if allowsInput(association)}
                                                    <tr>
                                                        <td class="px-3 py-2">
                                                            {association.label}
                                                        </td>
                                                        <td class="px-3 py-2">
                                                            {getChangeType(
                                                                association,
                                                            )}
                                                        </td>
                                                        <td class="px-3 py-2">
                                                            {association.range ??
                                                                "Unknown"}
                                                        </td>
                                                        <td class="px-3 py-2">
                                                            <textarea
                                                                rows="3"
                                                                class="border-border text-default-text focus:border-orange w-full resize-y rounded-md border bg-white px-2 py-1 text-sm placeholder-gray-400 outline-none focus:border-2 disabled:cursor-not-allowed"
                                                                placeholder="SPARQL Mapping for default value"
                                                                bind:value={
                                                                    association.mapping
                                                                }
                                                            ></textarea>
                                                        </td>
                                                    </tr>
                                                {/if}
                                            {/each}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>
                    </CollapsibleCard>
                </div>
            {/if}
        {/each}
    {/if}
</div>
