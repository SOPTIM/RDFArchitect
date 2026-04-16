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
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import { graphViewState } from "$lib/sharedState.svelte.js";

    let { showDialog = $bindable() } = $props();

    const FILTER_GROUPS = [
        {
            title: "Class Content",
            description:
                "Control which class members are rendered inside the package view.",
            options: [
                {
                    key: "includeAttributes",
                    title: "Attributes",
                    description: "Show attributes inside class boxes.",
                },
                {
                    key: "includeEnumEntries",
                    title: "Enum Entries",
                    description: "Show enum members inside enum classes.",
                },
            ],
        },
        {
            title: "Relationships",
            description:
                "Decide which connections are rendered between classes in the diagram.",
            options: [
                {
                    key: "includeAssociations",
                    title: "Associations",
                    description: "Show association edges between classes.",
                },
                {
                    key: "includeAssociationLabels",
                    title: "Association Role Labels",
                    description:
                        "Show association role labels on edges in the SvelteFlow diagram.",
                },
                {
                    key: "includeInheritance",
                    title: "Inheritance",
                    description: "Show superclass relationships.",
                },
            ],
        },
        {
            title: "Package Scope",
            description:
                "Choose whether links beyond the selected package stay visible.",
            options: [
                {
                    key: "includeRelationsToExternalPackages",
                    title: "External Package Relations",
                    description:
                        "Keep edges to classes outside the selected package visible.",
                },
            ],
        },
    ];

    let options = $state([]);

    function syncOptions() {
        const currentFilter = graphViewState.filter.getValue();

        options = FILTER_GROUPS.map(group => ({
            ...group,
            options: group.options.map(option => ({
                ...option,
                value: currentFilter[option.key],
            })),
        }));
    }

    function submit() {
        const nextFilter = options.reduce((filter, group) => {
            for (const option of group.options) {
                filter[option.key] = option.value;
            }

            return filter;
        }, {});

        graphViewState.filter.updateValue(nextFilter);
        showDialog = false;
    }

    syncOptions();
</script>

<ActionDialog
    bind:showDialog
    primaryLabel="Apply"
    onPrimary={submit}
    onOpen={syncOptions}
    title="Filter Diagram"
    size="w-[34rem] max-w-[calc(100vw-2rem)]"
>
    <div class="flex flex-col gap-4 px-2 py-1">
        <div class="border-border bg-default-background rounded border p-3">
            <p class="text-default-text text-sm font-medium">
                Choose what should stay visible in the current diagram.
            </p>
            <p class="text-default-text mt-1 text-sm opacity-75">
                These filters affect the package view only and can be changed at
                any time.
            </p>
        </div>

        {#each options as group}
            <section class="border-border rounded border">
                <div
                    class="bg-default-background border-border border-b px-4 py-3"
                >
                    <h3 class="text-default-text text-sm font-semibold">
                        {group.title}
                    </h3>
                    <p class="text-default-text mt-1 text-sm opacity-75">
                        {group.description}
                    </p>
                </div>

                <div class="flex flex-col gap-3 p-3">
                    {#each group.options as option}
                        <label
                            class="border-border bg-window-background hover:bg-default-background flex cursor-pointer items-start gap-3 rounded border px-3 py-3 transition-colors"
                        >
                            <input
                                type="checkbox"
                                class="text-button-default-text bg-default-background checked:bg-button-default-background mt-0.5 h-4 w-4 rounded border-none"
                                bind:checked={option.value}
                            />
                            <span class="min-w-0">
                                <span
                                    class="text-default-text block text-sm font-medium"
                                >
                                    {option.title}
                                </span>
                                <span
                                    class="text-default-text mt-1 block text-sm opacity-75"
                                >
                                    {option.description}
                                </span>
                            </span>
                        </label>
                    {/each}
                </div>
            </section>
        {/each}
    </div>
</ActionDialog>
