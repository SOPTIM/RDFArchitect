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
        faCircleCheck,
        faCircleXmark,
        faMinus,
        faTriangleExclamation,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import ProgressBar from "$lib/components/ProgressBar.svelte";
    import { FileState } from "$lib/utils/importProgress.svelte.js";

    let { progress } = $props();

    const rowStyles = {
        [FileState.IMPORTED]: {
            icon: faCircleCheck,
            color: "text-green-text",
        },
        [FileState.FAILED]: { icon: faCircleXmark, color: "text-red-text" },
        [FileState.SKIPPED]: { icon: faMinus, color: "text-text-subtle" },
    };

    const rowNotes = {
        [FileState.FAILED]: "could not be imported",
        [FileState.SKIPPED]: "not imported",
    };

    const stageNotes = {
        PARSING: "reading",
        ANALYZING: "checking",
        STORING: "storing",
    };

    let total = $derived(progress.files.length);
    let warnings = $derived(
        progress.warnings.filter(
            warning => (warning.undisplayableProperties ?? []).length > 0,
        ),
    );
</script>

<div class="mx-2 mt-2 flex min-h-0 flex-col">
    <ProgressBar
        label={progress.statusText}
        percent={progress.percent}
        ariaLabel="Import progress"
    />

    {#if total}
        <p class="text-text-subtle mt-1 text-xs tabular-nums">
            {progress.finishedCount} of {total} files
        </p>

        <div
            class="border-border bg-background-subtle mt-3 max-h-56 min-h-0 overflow-y-auto rounded border p-1"
        >
            {#each progress.files as file (file.index)}
                <div class="flex items-center gap-2 px-2 py-1 text-sm">
                    <span
                        class="flex size-4 shrink-0 items-center justify-center"
                    >
                        {#if file.state === FileState.RUNNING}
                            <LoadingSpinner
                                size={14}
                                stroke={2}
                                ariaLabel="Importing"
                            />
                        {:else if rowStyles[file.state]}
                            <Fa
                                class={rowStyles[file.state].color}
                                icon={rowStyles[file.state].icon}
                            />
                        {/if}
                    </span>
                    <span
                        class="min-w-0 flex-1 truncate {file.state ===
                        FileState.PENDING
                            ? 'text-text-subtle'
                            : 'text-default-text'}"
                    >
                        {file.fileName}
                    </span>
                    {#if file.state === FileState.RUNNING && stageNotes[file.stage]}
                        <span class="text-text-subtle shrink-0 text-xs">
                            {stageNotes[file.stage]}
                        </span>
                    {:else if rowNotes[file.state]}
                        <span class="text-text-subtle shrink-0 text-xs">
                            {rowNotes[file.state]}
                        </span>
                    {/if}
                </div>
            {/each}
        </div>
    {/if}

    {#if progress.errorMessage}
        <div
            class="bg-red-background text-red-text border-red-border mt-3 rounded border px-3 py-2 text-sm"
        >
            <p class="font-semibold">The import stopped</p>
            <p class="mt-1 text-xs">{progress.errorMessage}</p>
        </div>
    {/if}

    {#if warnings.length > 0}
        <div
            class="border-orange bg-background-subtle text-default-text mt-3 max-h-40 overflow-y-auto rounded border px-3 py-2 text-xs"
        >
            <p class="flex items-center gap-2 font-semibold">
                <Fa class="text-orange" icon={faTriangleExclamation} />
                Some properties will not be displayed
            </p>
            <p class="mt-1">
                They are stored, but they are missing the CIM stereotype or
                association metadata RDFArchitect needs to show them.
            </p>
            <ul class="mt-1 list-disc pl-5">
                {#each warnings as warning (warning.fileName)}
                    <li>
                        {warning.fileName}: {warning.undisplayableProperties.join(
                            ", ",
                        )}
                    </li>
                {/each}
            </ul>
        </div>
    {/if}
</div>
