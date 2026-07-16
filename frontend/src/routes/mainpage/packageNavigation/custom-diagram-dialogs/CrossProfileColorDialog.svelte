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
    import { URI } from "$lib/models/dto/index.ts";
    import { forceReloadTrigger } from "$lib/sharedState.svelte.js";
    import { crossProfileStore } from "$lib/stores/CrossProfileStore.ts";
    import { userSettings } from "$lib/userSettings.svelte.js";

    let { showDialog = $bindable(), datasetName } = $props();

    let colorEntries = $state([]);

    let originalJson = $state("");

    let hasChanges = $derived(JSON.stringify(colorEntries) !== originalJson);

    async function onOpen() {
        if (!datasetName) return;
        await loadColors();
    }

    function onClose() {
        colorEntries = [];
        originalJson = "";
    }

    async function loadColors() {
        const { error, data } =
            await crossProfileStore.fetchColors(datasetName);
        if (error) {
            return;
        }

        const map = data.graphColors ?? {};
        colorEntries = Object.entries(map).map(([graphURI, color]) => ({
            graphURI,
            color,
        }));
        snapshotOriginal();
    }

    async function saveColors() {
        const graphColors = Object.fromEntries(
            colorEntries.map(e => [e.graphURI, e.color]),
        );

        const { error } = await crossProfileStore.saveColors(datasetName, {
            graphColors,
        });
        if (error) {
            return;
        }
        snapshotOriginal();
        forceReloadTrigger.trigger();
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
</script>

<ModifyDataDialog
    bind:showDialog
    {onOpen}
    {onClose}
    title="Merged View Colors – {datasetName} {userSettings.get(
        'useColoredPropertiesInMergedView',
    )
        ? ''
        : '(disabled in settings)'}"
    saveChanges={saveColors}
    discardChanges={discardColors}
    {hasChanges}
    isValid={true}
>
    <div class="mx-2 flex h-[60vh] max-h-[60vh] flex-col">
        <div class="min-h-0 flex-1 overflow-y-auto">
            {#if colorEntries.length === 0}
                <p class="text-muted-foreground text-sm italic">
                    No graphs available for this dataset.
                </p>
            {:else}
                <p class="mb-3 text-sm">
                    Assign a color to each schema. The color is used in the
                    merged view.
                </p>

                <div class="flex flex-col gap-2 pr-1">
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
                            </div>

                            <input
                                type="text"
                                bind:value={entry.color}
                                maxlength="7"
                                pattern="^#[0-9a-fA-F]{6}$"
                                placeholder="#000000"
                                class="w-24 rounded border px-2 py-1 font-mono text-sm"
                                title="Hex color code"
                            />
                        </div>
                    {/each}
                </div>
            {/if}
        </div>
    </div>
</ModifyDataDialog>
