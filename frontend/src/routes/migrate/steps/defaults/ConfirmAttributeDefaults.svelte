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
    import {
        faCaretDown,
        faTriangleExclamation,
    } from "@fortawesome/free-solid-svg-icons";
    import { CollapsibleCard } from "svelte-collapsible";
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import InfoBox from "$lib/components/InfoBox.svelte";
    import { URI } from "$lib/models/dto/index.ts";
    import {
        allowsDefaultValueInput,
        canKeepExistingValues,
        describeAttributeChange,
        isDefaultValueMissing,
        keepsExistingValues,
        requiresDefaultValue,
        skipsInitialization,
    } from "$lib/utils/migrationUtils.js";

    let { classes, disableNext = $bindable(), isLoading } = $props();

    /** Every attribute this step asks a default value for, across all classes. */
    let requiredAttributes = $derived(
        classes.flatMap(cls =>
            (cls.attributes ?? []).filter(requiresDefaultValue),
        ),
    );

    /** Those of them that still block the step, so they can be pointed out and counted. */
    let openAttributes = $derived(
        requiredAttributes.filter(isDefaultValueMissing),
    );

    let openClassLabels = $derived(
        classes
            .filter(cls => openAttributeCount(cls) > 0)
            .map(cls => cls.classLabel),
    );

    let skippedCount = $derived(
        requiredAttributes.filter(
            attribute => attribute.noDefaultValue === true,
        ).length,
    );

    $effect(() => {
        disableNext = openAttributes.length > 0;
    });

    function shorten(iri) {
        return iri ? new URI(iri).suffix : "";
    }

    function isDisabled(attribute) {
        if (keepsExistingValues(attribute) || skipsInitialization(attribute)) {
            return true;
        }
        return !(
            requiresDefaultValue(attribute) || attribute.forceDefaultValue
        );
    }

    function hasAttributes(cls) {
        return (
            cls.attributes &&
            cls.attributes.some(attr => allowsDefaultValueInput(attr))
        );
    }

    function openAttributeCount(cls) {
        return (cls.attributes ?? []).filter(isDefaultValueMissing).length;
    }

    /**
     * Waiving the default value also drops the one that was filled in - the backend only knows the
     * choice by the missing value and would otherwise initialize the attribute after all.
     */
    function setSkipInitialization(attribute, skip) {
        attribute.noDefaultValue = skip;
        if (skip) {
            attribute.defaultValue = "";
        }
    }

    /** Waives the default value for every attribute at once, instead of row by row. */
    function skipAllInitializations() {
        for (const attribute of requiredAttributes) {
            setSkipInitialization(attribute, true);
        }
    }

    function clearAllSkippedInitializations() {
        for (const cls of classes) {
            for (const attribute of cls.attributes ?? []) {
                attribute.noDefaultValue = false;
            }
        }
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
        <br />
        Where a data type changed, both the old and the new one are shown. Tick
        <span class="font-semibold">Equivalent</span>
        if the two are interchangeable for that attribute - all existing values are
        then kept as they are and no default value is needed.
        <br />
        Tick
        <span class="font-semibold">Don't Init</span>
        where no default value can be given - the attribute is then left uninitialized
        and the migration continues without it.
    </InfoBox>

    {#if openAttributes.length > 0}
        <div
            class="border-red-border bg-red-background text-red-text flex items-start space-x-2 rounded-lg border p-4"
            role="alert"
        >
            <Fa icon={faTriangleExclamation} class="mt-0.5" />
            <div class="text-sm leading-relaxed">
                <span class="font-semibold">
                    {openAttributes.length}
                    {openAttributes.length === 1 ? "attribute" : "attributes"} still
                    {openAttributes.length === 1 ? "needs" : "need"} a default value
                </span>
                <br />
                Fill in a default value or tick
                <span class="font-semibold">Don't Init</span>
                for every attribute marked in red to continue:
                {openClassLabels.join(", ")}.
            </div>
        </div>
    {/if}

    {#if !isLoading && (classes.length === 0 || !classes.some(hasAttributes))}
        <EmptyStateCard
            title="No Attribute Defaults Required"
            description="There are no attributes that require default values in this migration."
        />
    {:else}
        {#if requiredAttributes.length > 0}
            <div class="flex items-center justify-end space-x-2">
                <div class="h-8 w-44">
                    <ButtonControl
                        title="Leave every attribute that requires a default value uninitialized"
                        disabled={skippedCount === requiredAttributes.length}
                        callOnClick={skipAllInitializations}
                    >
                        Don't Init All
                    </ButtonControl>
                </div>
                <div class="h-8 w-44">
                    <ButtonControl
                        variant="contrast"
                        title="Clear Don't Init for every attribute"
                        disabled={skippedCount === 0}
                        callOnClick={clearAllSkippedInitializations}
                    >
                        Clear Don't Init
                    </ButtonControl>
                </div>
            </div>
        {/if}

        {#each classes as cls}
            {#if hasAttributes(cls)}
                <div class="space-y-4">
                    <CollapsibleCard>
                        <h2
                            slot="header"
                            class="mb-3 flex items-center space-x-2 text-lg font-semibold"
                        >
                            <span>{cls.classLabel}</span>
                            {#if openAttributeCount(cls) > 0}
                                <span
                                    class="border-red-border bg-red-background text-red-text rounded-full border px-2 py-0.5 text-xs font-medium"
                                >
                                    {openAttributeCount(cls)} open
                                </span>
                            {/if}
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
                                                    class="w-1/6 px-4 py-2 text-center font-medium"
                                                >
                                                    Equivalent
                                                </th>
                                                <th
                                                    class="w-1/3 px-4 py-2 font-medium"
                                                >
                                                    Default Value
                                                </th>
                                                <th
                                                    class="w-1/6 px-4 py-2 text-center font-medium"
                                                >
                                                    Don't Init
                                                </th>
                                                <th
                                                    class="w-1/6 px-4 py-2 text-center font-medium"
                                                >
                                                    Init Optional
                                                </th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {#each cls.attributes as attribute (attribute.iri)}
                                                {#if allowsDefaultValueInput(attribute)}
                                                    <tr
                                                        class={isDefaultValueMissing(
                                                            attribute,
                                                        )
                                                            ? "bg-red-background"
                                                            : ""}
                                                    >
                                                        <td class="px-3 py-2">
                                                            {attribute.label}
                                                            {#if requiresDefaultValue(attribute)}
                                                                <span
                                                                    class="text-red"
                                                                >
                                                                    *
                                                                </span>
                                                            {/if}
                                                        </td>
                                                        <td class="px-3 py-2">
                                                            {describeAttributeChange(
                                                                attribute,
                                                            )}
                                                        </td>
                                                        <td
                                                            class="px-3 py-2"
                                                            title={attribute.oldDataType
                                                                ? `${attribute.oldDataType} \u2192 ${attribute.dataType}`
                                                                : attribute.dataType}
                                                        >
                                                            {#if attribute.oldDataType}
                                                                <span
                                                                    class="line-through"
                                                                >
                                                                    {shorten(
                                                                        attribute.oldDataType,
                                                                    )}
                                                                </span>
                                                                &rarr;
                                                                {shorten(
                                                                    attribute.dataType,
                                                                )}
                                                            {:else}
                                                                {shorten(
                                                                    attribute.dataType,
                                                                )}
                                                            {/if}
                                                        </td>
                                                        <td class="text-center">
                                                            {#if canKeepExistingValues(attribute)}
                                                                <input
                                                                    type="checkbox"
                                                                    class="text-button-default-text bg-default-background checked:bg-button-default-background disabled:bg-button-disabled-background mx-2 h-4 w-4 rounded border-none disabled:cursor-not-allowed"
                                                                    title="The old and the new data type are interchangeable for this attribute - keep all existing values"
                                                                    bind:checked={
                                                                        attribute.dataTypesEquivalent
                                                                    }
                                                                />
                                                            {/if}
                                                        </td>
                                                        <td class="px-3 py-2">
                                                            {#if attribute.allowedValues && attribute.allowedValues.length > 0}
                                                                <select
                                                                    class={`${isDefaultValueMissing(attribute) ? "border-red border-2" : "border-border"} text-default-text focus:border-blue w-full rounded-md border bg-white px-2 py-1 text-sm placeholder-gray-400 outline-none focus:border-2 disabled:cursor-not-allowed`}
                                                                    aria-invalid={isDefaultValueMissing(
                                                                        attribute,
                                                                    )}
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
                                                                    class={`${isDefaultValueMissing(attribute) ? "border-red border-2" : "border-border"} text-default-text focus:border-blue w-full rounded-md border bg-white px-2 py-1 text-sm placeholder-gray-400 outline-none focus:border-2 disabled:cursor-not-allowed`}
                                                                    placeholder="..."
                                                                    aria-invalid={isDefaultValueMissing(
                                                                        attribute,
                                                                    )}
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
                                                            {#if requiresDefaultValue(attribute)}
                                                                <input
                                                                    type="checkbox"
                                                                    class="text-button-default-text bg-default-background checked:bg-button-default-background disabled:bg-button-disabled-background mx-2 h-4 w-4 rounded border-none disabled:cursor-not-allowed"
                                                                    title="Continue without a default value - this attribute is left uninitialized"
                                                                    checked={skipsInitialization(
                                                                        attribute,
                                                                    )}
                                                                    onchange={event =>
                                                                        setSkipInitialization(
                                                                            attribute,
                                                                            event
                                                                                .currentTarget
                                                                                .checked,
                                                                        )}
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
