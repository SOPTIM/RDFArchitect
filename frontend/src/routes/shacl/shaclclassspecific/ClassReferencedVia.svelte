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
    import { getClassesReferencingThisClass } from "$lib/api/generated/index.ts";

    let { workspaceName, schemaName, classUUID, onClickOnClass } = $props();

    /**
     * The relations the backend reports, in the order they are shown in. Super classes are not
     * classes referring to this one, so they get a heading of their own.
     */
    const RELATIONS = [
        { key: "attributes", heading: "Referenced by", label: "Attributes" },
        {
            key: "associations",
            heading: "Referenced by",
            label: "Associations",
        },
        { key: "superClasses", heading: "Inherits from", label: null },
    ];

    let classesReferencingThisClass = $state({});

    /** Only the relations that name a class: an empty one says nothing about this class. */
    const relations = $derived(
        RELATIONS.map(relation => ({
            ...relation,
            classes: [
                ...(classesReferencingThisClass[relation.key] ?? []),
            ].sort((a, b) => (a.label ?? "").localeCompare(b.label ?? "")),
        })).filter(relation => relation.classes.length > 0),
    );

    // Read in the class's own schema: the dialog can be about a class of another schema than the
    // one selected.
    $effect(() => {
        const path = { workspaceName, schemaName, classUUID };
        classesReferencingThisClass = {};
        if (!workspaceName || !schemaName || !classUUID) {
            return;
        }
        let current = true;
        getClassesReferencingThisClass({ path })
            .then(({ data }) => {
                if (current) {
                    classesReferencingThisClass =
                        data?.classesReferencingThisClass ?? {};
                }
            })
            .catch(() => {});
        return () => (current = false);
    });
</script>

<!--
  @component
  The classes of the schema that refer to this one through an attribute or an association, and the
  classes it inherits from.
-->

<section class="space-y-3 text-sm">
    {#if relations.length === 0}
        <p class="text-text-subtle italic">
            No other class of the schema refers to it.
        </p>
    {/if}
    {#each ["Referenced by", "Inherits from"] as heading (heading)}
        {@const shown = relations.filter(
            relation => relation.heading === heading,
        )}
        {#if shown.length > 0}
            <div>
                <h3 class="text-default-text font-semibold">{heading}</h3>
                {#each shown as relation (relation.key)}
                    {#if relation.label}
                        <p class="text-text-subtle mt-1">{relation.label}</p>
                    {/if}
                    <ul class="ml-2">
                        {#each relation.classes as classObject (classObject.uuid)}
                            <li>
                                <button
                                    class="text-blue cursor-pointer text-left hover:underline"
                                    title="Open {classObject.label}"
                                    onclick={() => onClickOnClass(classObject)}
                                >
                                    {classObject.label}
                                </button>
                            </li>
                        {/each}
                    </ul>
                {/each}
            </div>
        {/if}
    {/each}
</section>
