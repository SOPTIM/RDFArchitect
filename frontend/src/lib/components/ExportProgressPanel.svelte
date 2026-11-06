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
        faMinus,
        faTriangleExclamation,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import ProgressBar from "$lib/components/ProgressBar.svelte";
    import { PackageStatus } from "$lib/utils/exportProgress.svelte.js";

    let { progress } = $props();

    const rowStyles = {
        [PackageStatus.DONE]: { icon: faCircleCheck, color: "text-green-text" },
        [PackageStatus.EMPTY]: { icon: faMinus, color: "text-text-subtle" },
        [PackageStatus.FAILED]: {
            icon: faTriangleExclamation,
            color: "text-red-text",
        },
    };

    const rowNotes = {
        [PackageStatus.EMPTY]: "no classes",
        [PackageStatus.FAILED]: "failed",
    };

    let total = $derived(progress.packages.length);
</script>

<div class="mx-2 mt-2 flex min-h-0 flex-col">
    <ProgressBar
        label={progress.statusText}
        percent={progress.percent}
        ariaLabel="Export progress"
    />

    {#if total}
        <p class="text-text-subtle mt-1 text-xs tabular-nums">
            {progress.finishedCount} of {total} packages
        </p>

        <div
            class="border-border bg-background-subtle mt-3 max-h-56 min-h-0 overflow-y-auto rounded border p-1"
        >
            {#each progress.packages as pkg (pkg.uuid)}
                <div class="flex items-center gap-2 px-2 py-1 text-sm">
                    <span
                        class="flex size-4 shrink-0 items-center justify-center"
                    >
                        {#if pkg.status === PackageStatus.RENDERING}
                            <LoadingSpinner
                                size={14}
                                stroke={2}
                                ariaLabel="Rendering"
                            />
                        {:else if rowStyles[pkg.status]}
                            <Fa
                                class={rowStyles[pkg.status].color}
                                icon={rowStyles[pkg.status].icon}
                            />
                        {/if}
                    </span>
                    <span
                        class="min-w-0 flex-1 truncate {pkg.status ===
                        PackageStatus.PENDING
                            ? 'text-text-subtle'
                            : 'text-default-text'}"
                    >
                        {pkg.label}
                    </span>
                    {#if rowNotes[pkg.status]}
                        <span class="text-text-subtle shrink-0 text-xs">
                            {rowNotes[pkg.status]}
                        </span>
                    {/if}
                </div>
            {/each}
        </div>
    {/if}
</div>
