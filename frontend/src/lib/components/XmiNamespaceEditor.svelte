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
    import { v4 as uuidv4 } from "uuid";

    import ComboBoxEditControl from "$lib/components/ComboBoxEditControl.svelte";
    import InfoTooltip from "$lib/components/InfoTooltip.svelte";

    let {
        /** What the export states, which is what the fields start at. */
        stated = { model: "", extension: "" },
        /**
         * Whether anything the selection holds needs the extension namespace. Where nothing does,
         * the field is not shown: there would be nothing for it to name.
         */
        extensionNeeded = true,
        /** What nearly every resource of a schema is named with. */
        model = $bindable(""),
        /** What the few elements stereotyped Entsoe or European are named with. */
        extension = $bindable(""),
    } = $props();

    /**
     * The namespaces the CGMES releases are published in, by the release they belong to.
     *
     * <p>What the export states is offered beside these rather than among them: the CGMES 3.0
     * model states the ucaiug.io pair of its own while the RDFS released from it uses the iec.ch
     * one, so naming both "CGMES 3.0" would tell them apart by nothing. The export's own comes in
     * under where it is from, which is the export.
     */
    const KNOWN = {
        model: [
            {
                namespace: "http://iec.ch/TC57/CIM100#",
                origin: "CGMES 3.0",
            },
            {
                namespace: "http://iec.ch/TC57/2013/CIM-schema-cim16#",
                origin: "CGMES 2.4.15",
            },
        ],
        extension: [
            {
                namespace: "http://iec.ch/TC57/CIM100-European#",
                origin: "CGMES 3.0",
            },
            {
                namespace: "http://entsoe.eu/CIM/SchemaExtension/3/1#",
                origin: "CGMES 2.4.15",
            },
        ],
    };

    const uniqueId = uuidv4();
    const rows = [
        {
            what: "model",
            label: "Model",
            explains:
                "Nearly every resource of a schema: its packages, classes, attributes, " +
                "associations and enum entries.",
        },
        {
            what: "extension",
            label: "Extension",
            explains: "Only the elements stereotyped Entsoe or European.",
        },
    ];
    const NAMESPACES_EXPLAINED =
        "What a resource's URI starts with: the namespace followed by Terminal is the full " +
        "name other schemas and instance data refer to that class by.";
    const shown = $derived(
        rows.filter(row => row.what !== "extension" || extensionNeeded),
    );

    /** The export's own first, and nothing twice over. */
    const offered = $derived(
        Object.fromEntries(
            rows.map(row => [
                row.what,
                [
                    ...(stated[row.what]
                        ? [
                              {
                                  namespace: stated[row.what],
                                  origin: "stated by this export",
                              },
                          ]
                        : []),
                    ...KNOWN[row.what],
                ].filter(
                    (one, at, all) =>
                        all.findIndex(
                            other => other.namespace === one.namespace,
                        ) === at,
                ),
            ]),
        ),
    );

    const originOf = $derived(
        new Map(
            Object.values(offered)
                .flat()
                .map(one => [one.namespace, one.origin]),
        ),
    );
</script>

<!--
  - One offered namespace: what it is on the left, where it is from on the right. The size is set
  - here rather than inherited, the list being portalled to the body and so outside this component.
  -->
{#snippet item(namespace)}
    <span
        class="flex min-w-0 flex-1 items-baseline justify-between gap-4 text-xs"
    >
        <span class="truncate">{namespace}</span>
        <span class="text-font-secondary shrink-0 text-right">
            {originOf.get(namespace)}
        </span>
    </span>
{/snippet}

<div class="text-xs">
    <p class="flex items-center gap-2 font-semibold">
        Namespaces
        <InfoTooltip text={NAMESPACES_EXPLAINED} side="top" />
    </p>

    <div class="mt-2 flex flex-col gap-2">
        {#each shown as row (row.what)}
            {@const id = `${row.what}-namespace-${uniqueId}`}
            <div class="flex items-center gap-3">
                <div class="flex w-28 shrink-0 items-center gap-2">
                    <label class="font-medium" for={id}>{row.label}</label>
                    <InfoTooltip text={row.explains} side="top" />
                </div>
                <!-- Wide enough for the longest namespace offered and no wider; a field the
                     width of the dialog would read as room for more than a URI. -->
                <div class="max-w-sm min-w-0 flex-1">
                    <ComboBoxEditControl
                        {id}
                        bind:value={
                            () => (row.what === "model" ? model : extension),
                            value =>
                                row.what === "model"
                                    ? (model = value)
                                    : (extension = value)
                        }
                        optionValues={offered[row.what].map(
                            one => one.namespace,
                        )}
                        placeholder={stated[row.what] ||
                            "This export states none, one is needed"}
                        {item}
                    />
                </div>
            </div>
        {/each}
    </div>
</div>
