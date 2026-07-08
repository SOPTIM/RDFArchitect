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
        faCaretRight,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import TextAreaControl from "$lib/components/TextAreaControl.svelte";
    import { URI } from "$lib/models/dto/index.ts";

    let {
        resource = $bindable(),
        title = "",
        ignorePrefixes = false,
    } = $props();

    const RESOURCE_CHANGE_LABELS = {
        ADD: "Added",
        DELETE: "Deleted",
        CHANGE: "Changed",
        RENAME: "Renamed",
    };

    const FIELD_CHANGE_LABELS = {
        LABEL_CHANGE: "Label",
        COMMENT_CHANGE: "Comment",
        SUPERCLASS_CHANGE: "Superclass",
        BELONGS_TO_CATEGORY_CHANGE: "Package",
        DATATYPE_CHANGE: "Data type",
        DATATYPE_RENAME: "Data type (renamed)",
        MADE_OPTIONAL: "Made optional",
        MADE_REQUIRED: "Made required",
        MULTIPLICITY_CHANGE: "Multiplicity",
        STEREOTYPE_ADDED: "Stereotype added",
        STEREOTYPE_REMOVED: "Stereotype removed",
        MADE_ABSTRACT: "Made abstract",
        DOMAIN_CHANGE: "Domain",
        DOMAIN_RENAME: "Domain (renamed)",
        TARGET_CHANGE: "Target",
        ASSOCIATION_USED_CHANGE: "Association used",
        DEFAULT_VALUE_CHANGE: "Default value",
        FIXED_VALUE_CHANGE: "Fixed value",
    };

    let expanded = $state(false);

    let oldLabel = $derived(
        resource.oldIRI ? new URI(resource.oldIRI).suffix : null,
    );
    let newLabel = $derived(new URI(resource.iri).suffix);

    function getChangeLabel(changeType) {
        if (
            changeType === "RENAME" &&
            ignorePrefixes &&
            oldLabel === newLabel
        ) {
            return "Changed";
        } else {
            return FIELD_CHANGE_LABELS[changeType] ?? changeType;
        }
    }

    function badgeClass(type) {
        switch (type) {
            case "ADD":
            case "ADDED_FROM_INHERITANCE":
                return "bg-green-background text-green-text border-green-border";
            case "DELETE":
            case "DELETED_FROM_INHERITANCE":
                return "bg-red-background text-red-text border-red-border";
            case "RENAME":
                if (ignorePrefixes && oldLabel === newLabel) {
                    return "bg-lightgray text-default-text border-border";
                } else {
                    return "bg-lightblue text-blue border-blue";
                }
            case "CHANGE":
            default:
                return "bg-lightgray text-default-text border-border";
        }
    }

    function toggleExpanded() {
        expanded = !expanded;
    }
</script>

<div
    class="border-border bg-window-background flex flex-col space-y-4 rounded-lg border p-4 shadow-sm"
>
    <div class="flex items-center justify-between">
        <div class="flex items-center space-x-2">
            {#if title}
                <span class="text-text-subtle text-xs uppercase">{title}</span>
            {/if}
            <h3 class="text-default-text text-base font-semibold">
                {resource.label ?? resource.iri}
            </h3>
        </div>
        <span
            class={`rounded border px-2 py-0.5 text-xs font-medium ${badgeClass(resource.semanticResourceChangeType)}`}
        >
            {RESOURCE_CHANGE_LABELS[resource.semanticResourceChangeType] ??
                resource.semanticResourceChangeType ??
                "—"}
        </span>
    </div>

    {#if ignorePrefixes && resource.oldIRI && newLabel !== oldLabel}
        <div class="text-text-subtle text-xs">
            <span class="font-mono">
                {ignorePrefixes ? oldLabel : resource.oldIRI}
            </span>
            <span class="mx-1">→</span>
            <span class="font-mono">
                {ignorePrefixes ? newLabel : resource.iri}
            </span>
        </div>
    {/if}

    {#if resource.changes && resource.changes.length > 0 && resource.semanticResourceChangeType !== "DELETE"}
        <div
            class="flex text-default-text mb-1 text-sm font-medium"
            onclick={toggleExpanded}
        >
            Changes
            {#if expanded}
                <Fa icon={faCaretDown} />
            {:else}
                <Fa icon={faCaretRight} />
            {/if}
        </div>
        {#if expanded}
            <ul class="space-y-1 text-sm">
                {#each resource.changes as change}
                    <li
                        class="border-border bg-default-background flex flex-col rounded border px-2 py-1"
                    >
                        <span class="text-default-text text-xs font-semibold">
                            {getChangeLabel()}
                        </span>
                        <div
                            class="text-text-subtle font-mono text-xs break-all"
                        >
                            <span class="text-red-text">
                                {change.from ?? "—"}
                            </span>
                            <span class="mx-1">→</span>
                            <span class="text-green-text">
                                {change.to ?? "—"}
                            </span>
                        </div>
                    </li>
                {/each}
            </ul>
        {/if}
    {/if}

    <div>
        <div class="text-default-text mb-1 text-sm font-medium">Comment</div>
        <TextAreaControl
            rowcount={3}
            placeholder="Add a comment for this change…"
            bind:value={resource.comment}
        />
    </div>
</div>
