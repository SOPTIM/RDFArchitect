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
    import { faPaste } from "@fortawesome/free-solid-svg-icons";

    import CheckBoxEditControl from "$lib/components/CheckBoxEditControl.svelte";
    import CollapseToggle from "$lib/components/CollapseToggle.svelte";
    import InfoTooltip from "$lib/components/InfoTooltip.svelte";
    import ActionDialog from "$lib/dialog/ActionDialog.svelte";
    import {
        DEFAULT_PASTE_OPTIONS,
        PASTE_REFERENCE_KINDS,
    } from "$lib/pasteOptions.js";
    import { copyState } from "$lib/sharedState.svelte.js";

    import { getUri } from "./mainpage/packageNavigation/packageNavigationUtils.svelte.js";
    import {
        confirmPaste,
        pasteFlow,
    } from "./mainpage/packageNavigation/paste-flow.svelte.js";

    const DISPLAY_BY_KIND = {
        DATA_TYPE: {
            label: "Data Types of Attributes",
            describeUsage: usage => memberPath(usage),
        },
        ASSOCIATION_TARGET: {
            label: "Target Classes of Associations",
            tooltip: "Copied along as stubs.",
            describeUsage: (usage, target) =>
                `${usage.className} → ${target.label}`,
        },
        SUPER_CLASS: {
            label: "Super Classes",
            tooltip: "Copied along as stubs.",
            describeUsage: (usage, target) =>
                `${usage.className} → ${target.label}`,
        },
    };

    let selected = $state(new Set());
    let expanded = $state(new Set());

    let singleClassCopied = $derived(copyState.getEntries().length === 1);
    let options = $derived(pasteFlow.options ?? DEFAULT_PASTE_OPTIONS);

    let groups = $derived(
        PASTE_REFERENCE_KINDS.map(({ kind, option }) => ({
            key: kind,
            ...DISPLAY_BY_KIND[kind],
            references: options[option]
                ? (pasteFlow.preview?.missing?.[kind] ?? [])
                : [],
        })).filter(group => group.references.length > 0),
    );

    let allReferences = $derived(groups.flatMap(group => group.references));

    let requiredBy = $derived(
        allReferences.reduce((map, entry) => {
            if (!selected.has(getUri(entry.uri))) return map;
            for (const uri of entry.requires ?? []) {
                const key = getUri(uri);
                map.set(key, [...(map.get(key) ?? []), entry.label]);
            }
            return map;
        }, new Map()),
    );

    let effectiveSelection = $derived(
        new Set([...selected, ...requiredBy.keys()]),
    );

    function memberPath(usage) {
        return usage.memberName
            ? `${usage.className}.${usage.memberName}`
            : usage.className;
    }

    function describeUsages(group, entry) {
        return [
            ...new Set(
                (entry.usedBy ?? []).map(usage =>
                    group.describeUsage(usage, entry),
                ),
            ),
        ];
    }

    function checkBoxLabel(entry) {
        const forcedBy = requiredBy.get(getUri(entry.uri));
        return forcedBy
            ? `${entry.label} (required by ${forcedBy.join(", ")})`
            : entry.label;
    }

    function onOpen() {
        selected = new Set(allReferences.map(({ uri }) => getUri(uri)));
        expanded = new Set();
    }

    function paste() {
        confirmPaste({
            ...options,
            referencesToCopy: allReferences
                .map(({ uri }) => uri)
                .filter(uri => effectiveSelection.has(getUri(uri))),
        });
    }

    function toggle(set, key, on) {
        const next = new Set(set);
        if (on) {
            next.add(key);
        } else {
            next.delete(key);
        }
        return next;
    }

    function toggleGroup(references) {
        const keys = references.map(reference => getUri(reference.uri));
        const on = !keys.every(key => selected.has(key));
        const next = new Set(selected);
        keys.forEach(key => (on ? next.add(key) : next.delete(key)));
        selected = next;
    }

    function toggleExpanded(key, on) {
        expanded = toggle(expanded, key, on);
    }
</script>

{#snippet reference(group, entry)}
    {@const uri = getUri(entry.uri)}
    {@const key = `${group.key}|${uri}`}
    {@const isExpanded = expanded.has(key)}
    {@const usedBy = describeUsages(group, entry)}
    <div class="flex flex-col gap-1">
        <div class="flex items-center gap-2">
            {#if usedBy.length > 0}
                <CollapseToggle
                    expanded={isExpanded}
                    label="{isExpanded
                        ? 'Hide'
                        : 'Show'} what uses {entry.label}"
                    onclick={() => toggleExpanded(key, !isExpanded)}
                />
            {:else}
                <span class="w-3"></span>
            {/if}
            <CheckBoxEditControl
                label={checkBoxLabel(entry)}
                value={effectiveSelection.has(uri)}
                disabled={requiredBy.has(uri)}
                callOnInputTrue={() => (selected = toggle(selected, uri, true))}
                callOnInputFalse={() =>
                    (selected = toggle(selected, uri, false))}
                labelFirst={false}
            />
        </div>
        {#if isExpanded}
            <ul class="text-text-subtle ml-6 flex flex-col text-sm">
                {#each usedBy as usage (usage)}
                    <li>{usage}</li>
                {/each}
            </ul>
        {/if}
    </div>
{/snippet}

{#snippet referenceGroup(group)}
    {@const isExpanded = expanded.has(group.key)}
    {@const allSelected = group.references.every(({ uri }) =>
        effectiveSelection.has(getUri(uri)),
    )}
    {@const someSelected = group.references.some(({ uri }) =>
        effectiveSelection.has(getUri(uri)),
    )}
    <div class="flex flex-col gap-1">
        <div class="flex items-center gap-2">
            <CollapseToggle
                expanded={isExpanded}
                label="{isExpanded ? 'Hide' : 'Show'} {group.label}"
                onclick={() => toggleExpanded(group.key, !isExpanded)}
            />
            <CheckBoxEditControl
                label="{group.label} ({group.references.length} missing)"
                value={allSelected}
                indeterminate={someSelected && !allSelected}
                callOnInputTrue={() => toggleGroup(group.references)}
                callOnInputFalse={() => toggleGroup(group.references)}
                labelFirst={false}
            />
            {#if group.tooltip}
                <InfoTooltip text={group.tooltip} />
            {/if}
        </div>
        {#if isExpanded}
            <div class="ml-6 flex flex-col gap-1">
                {#each group.references as entry (getUri(entry.uri))}
                    {@render reference(group, entry)}
                {/each}
            </div>
        {/if}
    </div>
{/snippet}

<ActionDialog
    bind:showDialog={pasteFlow.showDialog}
    {onOpen}
    size="w-1/3 max-w-2xl"
    title="Paste"
    titleIcon={faPaste}
    primaryLabel="Paste"
    primaryIcon={faPaste}
    onPrimary={paste}
>
    <div class="mx-2 flex h-full flex-col gap-4 overflow-y-auto py-2">
        <p class="text-text-subtle text-sm">
            {singleClassCopied
                ? "The pasted class references"
                : "The pasted classes reference"}
            classes that are not defined in the schema. Select the ones to copy along.
        </p>

        <div class="border-border flex flex-col gap-3 rounded border p-3">
            {#each groups as entry (entry.key)}
                {@render referenceGroup(entry)}
            {/each}
        </div>
    </div>
</ActionDialog>
