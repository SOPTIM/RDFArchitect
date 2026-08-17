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
    import { normalizeHex } from "$lib/utils/color.js";

    let {
        showDialog = $bindable(),
        workspaceName,
        graphUri,
        graphLabel,
    } = $props();

    let color = $state("#000000");
    let originalColor = $state("#000000");

    const hasChanges = $derived(color !== originalColor);

    function onOpen() {
        color = graphColors.get(workspaceName, graphUri) ?? "#000000";
        originalColor = color;
    }

    async function saveColor() {
        const saved = await graphColors.set(workspaceName, graphUri, color);
        if (saved) {
            originalColor = color;
        }
    }

    function discardColor() {
        color = originalColor;
    }

    /** Rejects invalid input by snapping the field back to the current color. */
    function applyHexInput(input) {
        const hex = normalizeHex(input.value);
        if (hex) {
            color = hex;
        }
        input.value = color;
    }
</script>

<ModifyDataDialog
    bind:showDialog
    {onOpen}
    size="w-96 max-w-96"
    title="Schema Color"
    saveChanges={saveColor}
    discardChanges={discardColor}
    {hasChanges}
    isValid={true}
>
    <div class="mx-2 flex flex-col gap-3 py-2">
        <p class="text-muted-foreground truncate text-xs" title={graphUri}>
            {graphLabel ?? graphUri}
        </p>

        <div class="flex items-center gap-3">
            <input
                type="color"
                bind:value={color}
                class="h-12 w-16 cursor-pointer rounded border-0 bg-transparent p-0"
                title="Pick a color for this schema"
            />
            <input
                type="text"
                value={color}
                maxlength="7"
                placeholder="#000000"
                class="w-28 rounded border px-2 py-1 font-mono text-sm"
                title="Hex color code"
                onchange={event => applyHexInput(event.currentTarget)}
            />
        </div>
    </div>
</ModifyDataDialog>
