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
    import { faKeyboard } from "@fortawesome/free-solid-svg-icons";

    import ActionDialog from "$lib/dialog/ActionDialog.svelte";

    let { showDialog = $bindable() } = $props();

    const sections = [
        {
            title: "File",
            shortcuts: [
                { description: "Import Schema (RDFS)", keys: ["Ctrl", "I"] },
                { description: "Export Schema (RDFS)", keys: ["Ctrl", "E"] },
                {
                    description: "Import Constraints (SHACL)",
                    keys: ["Ctrl", "Shift", "I"],
                },
                {
                    description: "Export Constraints (SHACL)",
                    keys: ["Ctrl", "Shift", "E"],
                },
                { description: "Share Snapshot", keys: ["Ctrl", "Shift", "S"] },
            ],
        },
        {
            title: "Edit",
            shortcuts: [
                { description: "Undo", keys: ["Ctrl", "Z"] },
                { description: "Redo", keys: ["Ctrl", "Y"] },
                { description: "New Class", keys: ["Ctrl", "Shift", "N"] },
                { description: "New Package", keys: ["Ctrl", "Alt", "N"] },
                { description: "Copy Class", keys: ["Ctrl", "C"] },
                { description: "Paste", keys: ["Ctrl", "V"] },
                {
                    description: "Paste without Attributes/Enum Entries",
                    keys: ["Ctrl", "Shift", "V"],
                },
                {
                    description: "Paste without Associations",
                    keys: ["Ctrl", "Alt", "V"],
                },
                {
                    description: "Paste bare",
                    keys: ["Ctrl", "Shift", "Alt", "V"],
                },
                {
                    description: "Enable / Disable Editing",
                    keys: ["Ctrl", "Alt", "E"],
                },
                {
                    description: "Manage Namespaces",
                    keys: ["Ctrl", "Shift", "A"],
                },
                {
                    description: "Create / Edit Profile Header",
                    keys: ["Ctrl", "Shift", "P"],
                },
                { description: "Edit Package", keys: ["Ctrl", "Shift", "K"] },
            ],
        },
        {
            title: "View",
            shortcuts: [
                {
                    description: "Compare Schemas",
                    keys: ["Ctrl", "Shift", "C"],
                },
                { description: "Changelog", keys: ["Ctrl", "Shift", "H"] },
                { description: "Migrate Schema", keys: ["Ctrl", "Shift", "M"] },
                {
                    description: "Constraints Full View (SHACL)",
                    keys: ["Ctrl", "Shift", "L"],
                },
            ],
        },
        {
            title: "General",
            shortcuts: [
                { description: "Focus Search", keys: ["Ctrl", "F"] },
                { description: "Keyboard Shortcuts", keys: ["?"] },
                { description: "Confirm Dialog", keys: ["Enter"] },
                { description: "Close Dialog", keys: ["Escape"] },
                { description: "Settings", keys: ["Ctrl", "Alt", "S"] },
            ],
        },
    ];
</script>

<ActionDialog
    bind:showDialog
    title="Keyboard Shortcuts"
    titleIcon={faKeyboard}
    readonly
    size="w-1/3"
>
    <div class="mx-2 flex flex-col gap-6 overflow-y-auto py-2 max-h-96">
        {#each sections as section}
            <div>
                <h3
                    class="text-default-text mb-2 text-sm font-semibold uppercase tracking-wide opacity-60"
                >
                    {section.title}
                </h3>
                <div class="flex flex-col gap-1">
                    {#each section.shortcuts as shortcut}
                        <div
                            class="flex items-center justify-between rounded px-2 py-1 hover:bg-black/5"
                        >
                            <span class="text-default-text text-sm">
                                {shortcut.description}
                            </span>
                            <div class="flex items-center gap-1">
                                {#each shortcut.keys as key, i}
                                    {#if i > 0}
                                        <span
                                            class="text-default-text text-xs opacity-50"
                                        >
                                            +
                                        </span>
                                    {/if}
                                    <kbd
                                        class="border-border bg-window-background text-default-text rounded border px-2 py-0.5 font-mono text-xs shadow-sm"
                                    >
                                        {key}
                                    </kbd>
                                {/each}
                            </div>
                        </div>
                    {/each}
                </div>
            </div>
        {/each}
    </div>
</ActionDialog>
