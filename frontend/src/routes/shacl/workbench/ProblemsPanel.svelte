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
        faChevronDown,
        faChevronUp,
        faRotate,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import {
        bySeverityThenPosition,
        FINDING_SEVERITY,
        severityMeta,
        SOURCE_LABEL,
        VALID_ICON,
    } from "$lib/shacl/severity.js";

    let {
        workbench,
        onselect = () => {},
        expanded = $bindable(true),
    } = $props();

    const problems = $derived(
        workbench.results
            .flatMap(result =>
                (result.findings ?? []).map(finding => ({
                    ...finding,
                    documentId: result.documentId,
                    documentName: result.documentName,
                })),
            )
            .sort(bySeverityThenPosition),
    );

    const totals = $derived(workbench.totals);
</script>

<!--
  @component
  Everything validation found, across all of the graph's constraints documents.

  Clicking a finding opens the document it belongs to and moves the cursor to it. Findings with no
  position — a contradiction between two documents, say — still appear here, which is the reason
  this panel exists alongside the editor's squiggles rather than duplicating them.
-->

<div class="border-border bg-window-background flex min-h-0 flex-col border-t">
    <div class="flex shrink-0 items-center gap-4 px-3 py-1.5">
        <button
            class="text-default-text flex cursor-pointer items-center gap-2 text-sm font-semibold"
            onclick={() => (expanded = !expanded)}
        >
            <Fa icon={expanded ? faChevronDown : faChevronUp} />
            Problems
        </button>

        <div
            class="text-default-text flex flex-wrap items-center gap-4 text-sm"
        >
            {#if totals.errorCount === 0 && totals.warningCount === 0 && totals.infoCount === 0}
                <span class="text-green-text flex items-center gap-1.5">
                    <Fa icon={VALID_ICON} />
                    No problems found
                </span>
            {:else}
                {#each ["ERROR", "WARNING", "INFO"] as key (key)}
                    {@const count =
                        key === "ERROR"
                            ? totals.errorCount
                            : key === "WARNING"
                              ? totals.warningCount
                              : totals.infoCount}
                    {#if count > 0}
                        <span class="flex items-center gap-1.5">
                            <Fa
                                icon={FINDING_SEVERITY[key].icon}
                                class={FINDING_SEVERITY[key].text}
                            />
                            {count}
                            {FINDING_SEVERITY[key].label}{count === 1
                                ? ""
                                : "s"}
                        </span>
                    {/if}
                {/each}
            {/if}
        </div>

        <div class="ml-auto h-7 w-36 shrink-0">
            <ButtonControl
                height={7}
                variant="inline"
                callOnClick={() => workbench.validateAll()}
                disabled={workbench.validating}
            >
                <span class="flex items-center gap-2 text-sm">
                    <Fa icon={faRotate} />
                    Validate all
                </span>
            </ButtonControl>
        </div>
    </div>

    {#if expanded}
        <div class="min-h-0 flex-1 overflow-y-auto px-3 pb-3">
            {#if problems.length === 0}
                <p class="text-text-subtle text-sm italic">
                    Nothing to report for this schema's constraints.
                </p>
            {:else}
                <ul class="flex flex-col gap-2">
                    {#each problems as problem, index (index)}
                        {@const meta = severityMeta(problem.severity)}
                        <li>
                            <button
                                class="flex w-full cursor-pointer items-start gap-3 rounded border p-2 text-left {meta.card}"
                                onclick={() => onselect(problem)}
                            >
                                <Fa
                                    icon={meta.icon}
                                    class="mt-0.5 shrink-0 {meta.text}"
                                />
                                <div class="min-w-0 flex-1">
                                    <div
                                        class="text-text-subtle flex flex-wrap items-center gap-2 text-xs"
                                    >
                                        <span class="font-semibold {meta.text}">
                                            {problem.documentName}
                                        </span>
                                        {#if problem.line}
                                            <span>
                                                line {problem.line}, column {problem.column}
                                            </span>
                                        {/if}
                                        <span>
                                            {SOURCE_LABEL[problem.source] ??
                                                problem.source}
                                        </span>
                                        {#if problem.code}
                                            <span class="font-mono">
                                                {problem.code}
                                            </span>
                                        {/if}
                                    </div>
                                    <p
                                        class="text-default-text mt-0.5 text-sm break-words"
                                    >
                                        {problem.message}
                                    </p>
                                    {#if problem.foundInProfiles?.length}
                                        <p
                                            class="text-text-subtle mt-0.5 text-xs break-all"
                                        >
                                            Declared in {problem.foundInProfiles.join(
                                                ", ",
                                            )}
                                        </p>
                                    {/if}
                                </div>
                            </button>
                        </li>
                    {/each}
                </ul>
            {/if}
        </div>
    {/if}
</div>
