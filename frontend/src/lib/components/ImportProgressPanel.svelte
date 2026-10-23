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

    import ImportWarningsDialog from "$lib/components/ImportWarningsDialog.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import ProgressBar from "$lib/components/ProgressBar.svelte";
    import { FileState } from "$lib/utils/importProgress.svelte.js";
    import { countUncarried } from "$lib/utils/importWarnings.js";

    let { progress } = $props();

    const rowStyles = {
        [FileState.IMPORTED]: {
            icon: faCircleCheck,
            color: "text-green-text",
        },
        [FileState.SCANNED]: {
            icon: faCircleCheck,
            color: "text-green-text",
        },
        [FileState.FAILED]: { icon: faCircleXmark, color: "text-red-text" },
        [FileState.SKIPPED]: { icon: faMinus, color: "text-text-subtle" },
    };

    const rowNotes = {
        [FileState.SCANNED]: "read, nothing imported yet",
        [FileState.FAILED]: "could not be imported",
        [FileState.SKIPPED]: "not imported",
    };
    let showWarnings = $state(false);

    let total = $derived(progress.files.length);
    let packages = $derived(progress.packages ?? []);
    let profiles = $derived(progress.profiles ?? []);

    let warnings = $derived(
        progress.warnings.filter(warning => (warning.items ?? []).length > 0),
    );
    let warningCount = $derived(countUncarried(warnings));
</script>

<div class="mx-2 mt-2 flex min-h-0 flex-col">
    <ProgressBar
        label={progress.statusText}
        percent={progress.percent}
        ariaLabel="Import progress"
    />

    <!-- Beside the count of files rather than in a box of its own: what could not be carried
         over belongs to the result, and is read once the import is done. -->
    {#if total || warnings.length > 0}
        <p class="text-text-subtle mt-1 flex items-baseline gap-2 text-xs">
            {#if total}
                <span class="tabular-nums">
                    {progress.finishedCount} of {total} files
                </span>
            {/if}
            {#if warnings.length > 0}
                <button
                    type="button"
                    class="border-orange bg-orange/10 text-orange hover:bg-orange/20 flex cursor-pointer items-center justify-start! gap-1.5 rounded border px-2 py-0.5 font-semibold"
                    onclick={() => (showWarnings = true)}
                >
                    <Fa icon={faTriangleExclamation} />
                    {warningCount}
                    {warningCount === 1 ? "warning" : "warnings"}
                </button>
            {/if}
        </p>
    {/if}

    {#if total}
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
                    {#if rowNotes[file.state]}
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

    {#if packages.length > 0}
        <div
            class="border-border bg-background-subtle text-default-text mt-3 max-h-56 overflow-y-auto rounded border px-3 py-2 text-xs"
        >
            <p class="font-semibold">
                {packages.length} package{packages.length === 1 ? "" : "s"} found,
                {profiles.length} of them a profile
            </p>
            <p class="mt-1">
                Nothing has been imported. Choosing which of them become schemas
                comes next.
            </p>
            <ul class="mt-2 flex flex-col gap-1">
                {#each profiles as profile (profile.fileName + profile.id)}
                    <li>
                        <span class="font-semibold">{profile.name}</span>
                        {#if profile.namespaceCandidates.length > 0}
                            <span class="text-text-subtle">
                                — {profile.namespaceCandidates[0]}
                                {#if profile.namespaceCandidates.length > 1}
                                    (+{profile.namespaceCandidates.length - 1}
                                    more)
                                {/if}
                            </span>
                        {/if}
                    </li>
                {/each}
            </ul>
        </div>
    {/if}
</div>

<ImportWarningsDialog bind:showDialog={showWarnings} {warnings} />
