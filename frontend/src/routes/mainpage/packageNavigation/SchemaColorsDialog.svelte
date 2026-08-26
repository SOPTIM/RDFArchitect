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
    import ModifyDataDialog from "$lib/dialog/ModifyDataDialog.svelte";
    import { graphColors } from "$lib/graphColors.svelte.js";
    import { URI } from "$lib/models/dto/index.ts";
    import { userSettings } from "$lib/userSettings.svelte.js";
    import { normalizeHex } from "$lib/utils/color.js";

    let { showDialog = $bindable(), workspaceName } = $props();

    let colorEntries = $state([]);
    let originalJson = $state("[]");

    const hasChanges = $derived(JSON.stringify(colorEntries) !== originalJson);

    const duplicateColors = $derived(
        new Set(
            colorEntries
                .map(entry => entry.color)
                .filter((color, index, all) => all.indexOf(color) !== index),
        ),
    );

    const colorsShownInMergedView = $derived(
        userSettings.get("useColoredPropertiesInMergedView"),
    );

    async function onOpen() {
        if (!workspaceName) return;
        const loaded = await graphColors.reload(workspaceName);
        colorEntries = Object.entries(loaded)
            .sort(([a], [b]) => a.localeCompare(b))
            .map(([graphURI, color]) => ({ graphURI, color }));
        snapshotOriginal();
    }

    function onClose() {
        colorEntries = [];
        originalJson = "";
    }

    async function saveColors() {
        const graphColorMap = Object.fromEntries(
            colorEntries.map(entry => [entry.graphURI, entry.color]),
        );
        const saved = await graphColors.replaceAll(
            workspaceName,
            graphColorMap,
        );
        if (!saved) {
            return;
        }
        snapshotOriginal();
    }

    function discardColors() {
        colorEntries = JSON.parse(originalJson);
    }

    function snapshotOriginal() {
        originalJson = JSON.stringify(colorEntries);
    }

    function shortName(uri) {
        try {
            return new URI(uri).suffix;
        } catch {
            return uri;
        }
    }

    /** Rejects invalid input by snapping the field back to the current color. */
    function applyHexInput(entry, input) {
        const hex = normalizeHex(input.value);
        if (hex) {
            entry.color = hex;
        }
        input.value = entry.color;
    }
</script>

<ModifyDataDialog
    bind:showDialog
    {onOpen}
    {onClose}
    title="Schema Colors – {workspaceName}"
    saveChanges={saveColors}
    discardChanges={discardColors}
    {hasChanges}
    isValid={true}
>
    <div class="mx-2 flex h-[60vh] max-h-[60vh] flex-col">
        <div class="flex min-h-0 flex-1 flex-col">
            {#if colorEntries.length === 0}
                <p class="text-muted-foreground text-sm italic">
                    No schemas available for this workspace.
                </p>
            {:else}
                <p class="text-muted-foreground mb-2 text-sm">
                    {#if colorsShownInMergedView}
                        Each schema is shown in its color in the merged view and
                        in the navigation.
                    {:else}
                        Colors are shown in the navigation. Enable "colored
                        properties" in the settings to use them in the merged
                        view as well.
                    {/if}
                </p>

                <div class="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto">
                    {#each colorEntries as entry (entry.graphURI)}
                        <div
                            class="flex items-center gap-3 rounded border px-3 py-2"
                        >
                            <input
                                type="color"
                                bind:value={entry.color}
                                class="h-9 w-12 cursor-pointer rounded border-0 bg-transparent p-0"
                                title="Pick color for {entry.graphURI}"
                            />

                            <div class="min-w-0 flex-1">
                                <p
                                    class="truncate font-medium"
                                    title={entry.graphURI}
                                >
                                    {shortName(entry.graphURI)}
                                </p>
                                <p
                                    class="text-muted-foreground truncate text-xs"
                                    title={entry.graphURI}
                                >
                                    {entry.graphURI}
                                </p>
                                {#if duplicateColors.has(entry.color)}
                                    <p class="text-xs italic">
                                        This color is used by another schema.
                                    </p>
                                {/if}
                            </div>

                            <input
                                type="text"
                                value={entry.color}
                                maxlength="7"
                                placeholder="#000000"
                                class="w-24 rounded border px-2 py-1 font-mono text-sm"
                                title="Hex color code"
                                onchange={event =>
                                    applyHexInput(entry, event.currentTarget)}
                            />
                        </div>
                    {/each}
                </div>
            {/if}
        </div>
    </div>
</ModifyDataDialog>
