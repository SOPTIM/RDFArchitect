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
    import {
        faDiagramProject,
        faFileExport,
    } from "@fortawesome/free-solid-svg-icons";

    import {
        extendToSchemaAndReveal,
        loadClassSchemas,
        resolveClassSources,
        schemaLabel,
        sourceCandidates,
        sourceOfOccurrence,
        stubsDiffer,
    } from "$lib/actions/schemaExtensionActions.js";
    import Badge from "$lib/components/Badge.svelte";
    import { ContextMenu } from "$lib/components/bitsui/contextmenu";
    import { askForExtendSource } from "$lib/extendSourceRequest.svelte.js";
    import { graphColors } from "$lib/graphColors.svelte.js";

    let {
        label,
        withInheritance = false,
        workspaceName,
        classes = [],
        currentGraphUri = null,
        selectedClassUuid = null,
        readOnly = false,
        disabled = false,
        showBlockedReason = true,
        onDone = () => {},
    } = $props();

    let schemaOccurrences = $state([]);
    let loadingSchemas = $state(false);
    let extending = $state(false);

    /** Every schema but the one the classes are extended from. */
    const extendSchemas = $derived(
        schemaOccurrences.filter(
            occurrence =>
                !currentGraphUri ||
                occurrence.graphUri !== String(currentGraphUri),
        ),
    );

    const nothingToExtend = $derived(
        schemaOccurrences.length > 0 &&
            extendSchemas.every(occurrence => occurrence.present),
    );

    async function loadSchemaOccurrences() {
        if (loadingSchemas || classes.length === 0) {
            return;
        }
        loadingSchemas = true;
        try {
            schemaOccurrences = await loadClassSchemas(workspaceName, classes);
        } finally {
            loadingSchemas = false;
        }
    }

    /** Why the classes cannot be extended into that schema, empty when they can. */
    function blockedReason(occurrence) {
        if (readOnly) {
            return "read-only";
        }
        return occurrence.present ? "already exists" : "";
    }

    async function extendInto(occurrence) {
        if (extending) {
            return;
        }
        // Only a selection without a schema of its own, as in the cross-profile
        // diagram, leaves the schema to copy from open.
        const candidates = currentGraphUri
            ? []
            : sourceCandidates(schemaOccurrences, occurrence.graphUri);
        if (classes.length === 1 && stubsDiffer(candidates)) {
            askForExtendSource({
                workspaceName,
                candidates,
                targetLabel: schemaLabel(occurrence),
                onPick: picked =>
                    extendToSchemaAndReveal({
                        workspaceName,
                        sources: sourceOfOccurrence(picked),
                        targetGraphUri: occurrence.graphUri,
                        targetLabel: schemaLabel(occurrence),
                        selectedClassUuid,
                        withInheritance,
                    }),
            });
            onDone();
            return;
        }
        const sources = await resolveClassSources(workspaceName, classes);
        await extend(sources, occurrence);
        onDone();
    }

    async function extend(sources, occurrence) {
        if (!occurrence || sources.length === 0) {
            return;
        }
        extending = true;
        try {
            await extendToSchemaAndReveal({
                workspaceName,
                sources,
                targetGraphUri: occurrence.graphUri,
                targetLabel: schemaLabel(occurrence),
                selectedClassUuid,
                withInheritance,
            });
        } finally {
            extending = false;
        }
    }
</script>

<ContextMenu.SubMenu.Root
    onOpenChange={nextOpen => nextOpen && loadSchemaOccurrences()}
>
    <ContextMenu.SubMenu.Trigger
        faIcon={faFileExport}
        disabled={disabled ||
            extending ||
            classes.length === 0 ||
            nothingToExtend}
    >
        {label}
    </ContextMenu.SubMenu.Trigger>
    <ContextMenu.SubMenu.Content>
        {#if extendSchemas.length === 0}
            <ContextMenu.Item.Button faIcon={faDiagramProject} disabled>
                {loadingSchemas ? "Loading schemas" : "No other schema"}
            </ContextMenu.Item.Button>
        {:else}
            {#each extendSchemas as occurrence (occurrence.graphUri)}
                <ContextMenu.Item.Button
                    onSelect={() => extendInto(occurrence)}
                    faIcon={faDiagramProject}
                    iconColor={graphColors.get(
                        workspaceName,
                        occurrence.graphUri,
                    )}
                    disabled={!!blockedReason(occurrence)}
                >
                    <span class="flex w-full min-w-0 items-center gap-2">
                        <span class="min-w-0 flex-1 truncate">
                            {schemaLabel(occurrence)}
                        </span>
                        {#if showBlockedReason && blockedReason(occurrence)}
                            <Badge
                                text={blockedReason(occurrence)}
                                variant="muted"
                            />
                        {/if}
                    </span>
                </ContextMenu.Item.Button>
            {/each}
        {/if}
    </ContextMenu.SubMenu.Content>
</ContextMenu.SubMenu.Root>
