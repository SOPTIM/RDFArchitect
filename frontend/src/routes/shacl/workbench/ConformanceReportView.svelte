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
    /**
     * Does this constraints document still agree with the schema it describes?
     *
     * Grouped by kind rather than listed flat, because the four kinds call for different actions:
     * a contradiction means somebody has to decide who is right, while a difference is often the
     * profile doing its job and narrowing what the schema allows.
     */

    import {
        faCircleCheck,
        faCircleExclamation,
        faCircleInfo,
        faScaleBalanced,
        faTriangleExclamation,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import EmptyStateCard from "$lib/components/EmptyStateCard.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import { conformanceKind } from "$lib/shacl/conformanceState.svelte.js";
    import { writeTerm } from "$lib/shacl/turtleTerms.js";

    let {
        conformance,
        documentId = null,
        documentName = "",
        prefixes = {},
    } = $props();

    /** Reuses the validation palette, so the same word means the same colour across the app. */
    const STYLE = {
        CONTRADICTED: {
            icon: faCircleExclamation,
            card: "bg-red-background border-red-border",
            text: "text-red-text",
        },
        DIFFERENT: {
            icon: faTriangleExclamation,
            card: "bg-orange/10 border-orange",
            text: "text-orange",
        },
        MISSING_IN_DOCUMENT: {
            icon: faCircleInfo,
            card: "bg-lightblue border-blue",
            text: "text-blue",
        },
        NOT_IN_SCHEMA: {
            icon: faCircleInfo,
            card: "bg-background-subtle border-border",
            text: "text-default-text",
        },
    };

    const report = $derived(
        conformance.reportedOn === documentId ? conformance.report : null,
    );

    const groups = $derived.by(() => {
        const byKind = new Map();
        for (const finding of report?.findings ?? []) {
            if (!byKind.has(finding.kind)) {
                byKind.set(finding.kind, []);
            }
            byKind.get(finding.kind).push(finding);
        }
        return [...byKind.entries()].sort(
            (a, b) => conformanceKind(a[0]).order - conformanceKind(b[0]).order,
        );
    });

    function short(iri) {
        if (!iri) {
            return "";
        }
        const cut = Math.max(iri.lastIndexOf("#"), iri.lastIndexOf("/"));
        return writeTerm(
            {
                iri,
                namespace: iri.slice(0, cut + 1),
                localName: iri.slice(cut + 1),
            },
            prefixes,
        );
    }
</script>

<div class="flex h-full min-h-0 flex-col">
    <div
        class="border-border flex shrink-0 items-center gap-2 border-b px-3 py-2"
    >
        <h2 class="text-default-text grow text-sm font-semibold">
            Agreement with the schema
        </h2>
        <div class="h-7 w-36">
            <ButtonControl
                height={7}
                variant="inline"
                disabled={conformance.running || !documentId}
                callOnClick={() => conformance.run(documentId)}
            >
                <span class="flex items-center gap-2 text-sm">
                    <Fa icon={faScaleBalanced} />
                    {report ? "Compare again" : "Compare"}
                </span>
            </ButtonControl>
        </div>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto p-3">
        {#if conformance.running}
            <div class="flex h-full items-center justify-center">
                <LoadingSpinner />
            </div>
        {:else if conformance.error}
            <p
                class="bg-red-background border-red-border text-red-text rounded border p-3 text-sm"
            >
                {conformance.error}
            </p>
        {:else if !report}
            <div class="flex h-full items-center justify-center">
                <EmptyStateCard
                    icon={faScaleBalanced}
                    title="Compare with the schema"
                    description="Checks whether {documentName ||
                        'this document'} still says what the schema implies — and, more usefully, whether the two now contradict each other."
                />
            </div>
        {:else}
            <div
                class={`mb-4 flex flex-wrap items-center gap-x-6 gap-y-2 rounded border p-4 ${
                    report.conforms
                        ? "bg-green-background border-green-border"
                        : "bg-red-background border-red-border"
                }`}
            >
                <div class="flex items-center gap-2">
                    <Fa
                        icon={report.conforms
                            ? faCircleCheck
                            : faCircleExclamation}
                        class={report.conforms
                            ? "text-green-text"
                            : "text-red-text"}
                    />
                    <span
                        class={`text-base font-semibold ${
                            report.conforms
                                ? "text-green-text"
                                : "text-red-text"
                        }`}
                    >
                        {report.conforms
                            ? "The document agrees with the schema"
                            : "The document and the schema disagree"}
                    </span>
                </div>
                <span class="text-default-text text-sm">
                    {report.agreeing} of {report.compared} property constraints agree
                </span>
            </div>

            {#if groups.length === 0}
                <p class="text-text-subtle text-sm italic">
                    Every constraint the schema implies is stated in the
                    document, and nothing is stated that the schema does not
                    have.
                </p>
            {:else}
                <div class="flex flex-col gap-4">
                    {#each groups as [kind, findings] (kind)}
                        {@const meta = conformanceKind(kind)}
                        {@const style = STYLE[kind] ?? STYLE.DIFFERENT}
                        <section>
                            <h3
                                class={`mb-1 flex items-center gap-2 text-sm font-semibold ${style.text}`}
                            >
                                <Fa icon={style.icon} />
                                {meta.label}
                                <span class="text-text-subtle font-normal">
                                    ({findings.length}) — {meta.explanation}
                                </span>
                            </h3>
                            <ul class="flex flex-col gap-2">
                                {#each findings as finding (finding.targetClass + finding.path)}
                                    <li
                                        class={`rounded border p-3 ${style.card}`}
                                    >
                                        <p
                                            class="text-default-text font-mono text-xs break-all"
                                        >
                                            {short(finding.targetClass)} · {short(
                                                finding.path,
                                            )}
                                        </p>
                                        <p
                                            class="text-default-text mt-1 text-sm"
                                        >
                                            {finding.message}
                                        </p>
                                        <dl
                                            class="text-text-subtle mt-1 flex flex-wrap gap-x-6 text-xs"
                                        >
                                            {#if finding.schemaSays}
                                                <div class="flex gap-1">
                                                    <dt>schema:</dt>
                                                    <dd class="font-mono">
                                                        {finding.schemaSays}
                                                    </dd>
                                                </div>
                                            {/if}
                                            {#if finding.documentSays}
                                                <div class="flex gap-1">
                                                    <dt>document:</dt>
                                                    <dd class="font-mono">
                                                        {finding.documentSays}
                                                    </dd>
                                                </div>
                                            {/if}
                                        </dl>
                                    </li>
                                {/each}
                            </ul>
                        </section>
                    {/each}
                </div>
            {/if}
        {/if}
    </div>
</div>
