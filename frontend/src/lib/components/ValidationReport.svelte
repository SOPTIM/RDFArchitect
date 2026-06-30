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
        faCircleExclamation,
        faCircleInfo,
        faTriangleExclamation,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    let { result } = $props();

    const SEVERITY = {
        ERROR: {
            label: "Error",
            order: 0,
            icon: faCircleExclamation,
            card: "bg-red-background border-red-border",
            text: "text-red-text",
            iconClass: "text-red-text",
        },
        WARNING: {
            label: "Warning",
            order: 1,
            icon: faTriangleExclamation,
            card: "bg-orange/10 border-orange",
            text: "text-orange",
            iconClass: "text-orange",
        },
        INFO: {
            label: "Info",
            order: 2,
            icon: faCircleInfo,
            card: "bg-lightblue border-blue",
            text: "text-blue",
            iconClass: "text-blue",
        },
    };

    const UNKNOWN_SEVERITY = {
        label: "Unknown",
        order: 3,
        icon: faCircleInfo,
        card: "bg-background-subtle border-border",
        text: "text-default-text",
        iconClass: "text-default-text",
    };

    const issues = $derived(result?.issues ?? []);

    const sortedIssues = $derived(
        [...issues].sort(
            (a, b) =>
                severityMeta(a.severity).order - severityMeta(b.severity).order,
        ),
    );

    const counts = $derived.by(() => {
        const acc = { ERROR: 0, WARNING: 0, INFO: 0 };
        for (const issue of issues) {
            if (issue.severity in acc) {
                acc[issue.severity] += 1;
            }
        }
        return acc;
    });

    function severityMeta(severity) {
        return SEVERITY[severity] ?? UNKNOWN_SEVERITY;
    }
</script>

<!-- Status summary -->
<div
    class={`mb-6 flex flex-wrap items-center gap-x-6 gap-y-2 rounded border p-4 ${
        result.valid
            ? "bg-green-background border-green-border"
            : "bg-red-background border-red-border"
    }`}
>
    <div class="flex items-center gap-2">
        <Fa
            icon={result.valid ? faCircleCheck : faCircleExclamation}
            class={result.valid ? "text-green-text" : "text-red-text"}
        />
        <span
            class={`text-base font-semibold ${
                result.valid ? "text-green-text" : "text-red-text"
            }`}
        >
            {result.valid ? "Schema is valid" : "Schema is invalid"}
        </span>
    </div>

    <div class="text-default-text flex flex-wrap items-center gap-4 text-sm">
        <span class="flex items-center gap-1.5">
            <Fa icon={SEVERITY.ERROR.icon} class={SEVERITY.ERROR.iconClass} />
            {counts.ERROR}
            {counts.ERROR === 1 ? "Error" : "Errors"}
        </span>
        <span class="flex items-center gap-1.5">
            <Fa
                icon={SEVERITY.WARNING.icon}
                class={SEVERITY.WARNING.iconClass}
            />
            {counts.WARNING}
            {counts.WARNING === 1 ? "Warning" : "Warnings"}
        </span>
        <span class="flex items-center gap-1.5">
            <Fa icon={SEVERITY.INFO.icon} class={SEVERITY.INFO.iconClass} />
            {counts.INFO}
            {counts.INFO === 1 ? "Info" : "Infos"}
        </span>
    </div>
</div>

<!-- Issue list -->
{#if sortedIssues.length > 0}
    <div class="flex flex-col gap-3">
        {#each sortedIssues as issue}
            {@const meta = severityMeta(issue.severity)}
            <div
                class={`flex items-start gap-3 rounded border p-4 ${meta.card}`}
            >
                <Fa icon={meta.icon} class={`mt-0.5 ${meta.iconClass}`} />
                <div class="min-w-0 flex-1">
                    <div class="mb-1 flex items-center gap-2">
                        <span
                            class={`text-xs font-semibold tracking-wide uppercase ${meta.text}`}
                        >
                            {meta.label}
                        </span>
                    </div>
                    <p class="text-default-text text-sm break-words">
                        {issue.message}
                    </p>
                    {#if issue.resourceUri}
                        <p
                            class="text-text-subtle mt-1 font-mono text-xs break-all"
                        >
                            {issue.resourceUri}
                        </p>
                    {/if}
                </div>
            </div>
        {/each}
    </div>
{:else}
    <div class="bg-green-background border-green-border rounded border p-6">
        <p class="text-green-text italic">No issues found.</p>
    </div>
{/if}
