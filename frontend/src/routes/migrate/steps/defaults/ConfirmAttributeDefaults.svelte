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

    $effect(() => {
        let allRequirementsMet = classes.every(
            cls =>
                cls.attributes
                    ?.filter(attr => requiresInput(attr))
                    .every(
                        attr =>
                            attr.defaultValue &&
                            attr.defaultValue.trim().length > 0,
                    ) ?? true,
        );
        disableNext = !allRequirementsMet;
    });

    function getChangeType(attribute) {
        if (attribute.semanticResourceChangeType === "ADD") {
            if (attribute.optional === true) {
                return "Attribute added (optional)";
            } else {
                return "Attribute added (required)";
            }
        }
        if (attribute.semanticResourceChangeType === "ADDED_FROM_INHERITANCE") {
            return "Attribute newly inherited";
        }
        if (
            attribute.changes?.some(
                c => c.semanticFieldChangeType === "DATATYPE_CHANGE",
            )
        ) {
            return "Data type changed";
        }
        if (
            attribute.changes?.some(
                c => c.semanticFieldChangeType === "MADE_REQUIRED",
            )
        ) {
            return "Made required";
        }
        return "other";
    }

    function allowsInput(attribute) {
        return (
            attribute.semanticResourceChangeType === "ADD" ||
            attribute.semanticResourceChangeType === "ADDED_FROM_INHERITANCE" ||
            (attribute.semanticResourceChangeType !== "DELETE" &&
                (attribute.changes?.some(
                    c => c.semanticFieldChangeType === "MADE_REQUIRED",
                ) ||
                    attribute.changes?.some(
                        c => c.semanticFieldChangeType === "DATATYPE_CHANGE",
                    )))
        );
    }

    function requiresInput(attribute) {
        return (
            (attribute.semanticResourceChangeType === "ADD" &&
                !attribute.optional) ||
            (attribute.semanticResourceChangeType ===
                "ADDED_FROM_INHERITANCE" &&
                !attribute.optional) ||
            (attribute.semanticResourceChangeType === "CHANGE" &&
                (attribute.changes?.some(
                    c => c.semanticFieldChangeType === "MADE_REQUIRED",
                ) ||
                    attribute.changes?.some(
                        c => c.semanticFieldChangeType === "DATATYPE_CHANGE",
                    )))
        );
    }

    function isDisabled(attribute) {
        return !(requiresInput(attribute) || attribute.forceDefaultValue);
    }

    function hasAttributes(cls) {
        return cls.attributes && cls.attributes.some(attr => allowsInput(attr));
    }
</script>

<div class="text-default-text flex flex-col space-y-10">
    <InfoBox>
        This step lets you add <span class="font-semibold">
            default values for attributes
        </span>
        .
        <br />
        Attributes that require a default value are marked with an
        <span class="text-red font-semibold">*</span>
        . New Attributes and their set default values will be instantiated on all
        existing instances of the class and its deriving classes.
    </InfoBox>

    {#if !isLoading && (classes.length === 0 || !classes.some(hasAttributes))}
        <EmptyStateCard
            title="No Attribute Defaults Required"
            description="There are no attributes that require default values in this migration."
        />
    {:else}
        {#each classes as cls}
            {#if hasAttributes(cls)}
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
                                                    Attribute
                                                </th>
                                                <th
                                                    class="w-1/6 px-4 py-2 font-medium"
                                                >
                                                    Change
                                                </th>
                                                <th
                                                    class="w-1/6 px-4 py-2 font-medium"
                                                >
                                                    Data Type
                                                </th>
                                                <th
                                                    class="w-1/3 px-4 py-2 font-medium"
                                                >
                                                    Default Value
                                                </th>
                                                <th
                                                    class="w-1/6 px-4 py-2 text-center font-medium"
                                                >
                                                    Init Optional
                                                </th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {#each cls.attributes as attribute}
                                                {#if allowsInput(attribute)}
                                                    <tr>
                                                        <td class="px-3 py-2">
                                                            {attribute.label}
                                                            {#if requiresInput(attribute)}
                                                                <span
                                                                    class="text-red"
                                                                >
                                                                    *
                                                                </span>
                                                            {/if}
                                                        </td>
                                                        <td class="px-3 py-2">
                                                            {getChangeType(
                                                                attribute,
                                                            )}
                                                        </td>
                                                        <td class="px-3 py-2">
                                                            {attribute.dataType}
                                                        </td>
                                                        <td class="px-3 py-2">
                                                            {#if attribute.allowedValues && attribute.allowedValues.length > 0}
                                                                <select
                                                                    class="border-border text-default-text focus:border-orange w-full rounded-md border bg-white px-2 py-1 text-sm placeholder-gray-400 outline-none focus:border-2 disabled:cursor-not-allowed"
                                                                    disabled={isDisabled(
                                                                        attribute,
                                                                    )}
                                                                    bind:value={
                                                                        attribute.defaultValue
                                                                    }
                                                                >
                                                                    <option
                                                                        value=""
                                                                        disabled
                                                                        selected
                                                                        hidden
                                                                    >
                                                                        ...
                                                                    </option>
                                                                    {#each attribute.allowedValues as val}
                                                                        <option
                                                                            value={val}
                                                                        >
                                                                            {val}
                                                                        </option>
                                                                    {/each}
                                                                </select>
                                                            {:else}
                                                                <input
                                                                    type="text"
                                                                    class="border-border text-default-text focus:border-orange w-full rounded-md border bg-white px-2 py-1 text-sm placeholder-gray-400 outline-none focus:border-2 disabled:cursor-not-allowed"
                                                                    placeholder="..."
                                                                    disabled={isDisabled(
                                                                        attribute,
                                                                    )}
                                                                    bind:value={
                                                                        attribute.defaultValue
                                                                    }
                                                                />
                                                            {/if}
                                                        </td>
                                                        <td class="text-center">
                                                            {#if attribute.optional}
                                                                <input
                                                                    type="checkbox"
                                                                    class="text-button-default-text bg-default-background checked:bg-button-default-background disabled:bg-button-disabled-background mx-2 h-4 w-4 rounded border-none disabled:cursor-not-allowed"
                                                                    bind:checked={
                                                                        attribute.forceDefaultValue
                                                                    }
                                                                />
                                                            {/if}
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
