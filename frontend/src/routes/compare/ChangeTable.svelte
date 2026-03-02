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
    import { faMinus } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import { buildInlineValueDiff } from "$lib/utils/valueDiff.js";

    let { changes } = $props();
    const diffCache = new Map();

    function getChangeType(change) {
        if (change.from === null && change.to !== null) {
            return "addition";
        } else if (change.from !== null && change.to === null) {
            return "deletion";
        } else if (change.from !== null && change.to !== null) {
            return "modification";
        }
        return "none";
    }

    function getRowClasses(change) {
        const baseClasses = "hover:bg-opacity-80 transition-colors border-l-2 ";
        const changeType = getChangeType(change);

        switch (changeType) {
            case "addition":
                return `${baseClasses} bg-green-background hover:bg-green-hover-background border-l-green-border`;
            case "deletion":
                return `${baseClasses} bg-red-background hover:bg-red-hover-background border-l-red-border`;
            case "modification":
                return `${baseClasses} bg-window-background hover:bg-default-background border-l-gray-border`;
            default:
                return `${baseClasses} hover:bg-default-background`;
        }
    }

    function getDiff(change) {
        const cacheKey = `${change.from}\u0000${change.to}`;
        if (!diffCache.has(cacheKey)) {
            diffCache.set(
                cacheKey,
                buildInlineValueDiff(change.from, change.to),
            );
        }
        return diffCache.get(cacheKey);
    }

    function getSegmentClass(segmentKind) {
        switch (segmentKind) {
            case "removed":
                return "text-red-text line-through";
            case "added":
                return "text-green-text";
            default:
                return "text-default-text";
        }
    }
</script>

<table class="w-full table-fixed text-left">
    <thead>
        <tr>
            <th
                class="border-default-text bg-window-background w-1/2 border-b p-4"
            >
                <p class="block text-sm leading-none font-normal">Property</p>
            </th>
            <th class="border-default-text bg-window-background border-b p-4">
                <p class="block text-sm leading-none font-normal">From</p>
            </th>
            <th class="border-default-text bg-window-background border-b p-4">
                <p class="block text-sm leading-none font-normal">To</p>
            </th>
        </tr>
    </thead>
    <tbody>
        {#each Object.entries(changes) as [key, change]}
            {@const changeType = getChangeType(change)}
            {@const diff =
                changeType === "modification" ? getDiff(change) : null}
            <tr class={getRowClasses(change)}>
                <td class="border-default-text border-b p-4">
                    <p class="text-default-text block text-sm">
                        {key}
                    </p>
                </td>
                <td class="border-default-text border-b p-4">
                    <p
                        class="text-default-text block font-mono text-sm break-all whitespace-pre-wrap"
                    >
                        {#if change.from === null}
                            <Fa icon={faMinus} />
                        {:else if changeType === "modification"}
                            {#each diff.fromSegments as segment}
                                <span class={getSegmentClass(segment.kind)}>
                                    {segment.text}
                                </span>
                            {/each}
                        {:else}
                            {change.from}
                        {/if}
                    </p>
                </td>
                <td class="border-default-text border-b p-4">
                    <p
                        class="text-default-text block font-mono text-sm break-all whitespace-pre-wrap"
                    >
                        {#if change.to === null}
                            <Fa icon={faMinus} />
                        {:else if changeType === "modification"}
                            {#each diff.toSegments as segment}
                                <span class={getSegmentClass(segment.kind)}>
                                    {segment.text}
                                </span>
                            {/each}
                        {:else}
                            {change.to}
                        {/if}
                    </p>
                </td>
            </tr>
        {/each}
    </tbody>
</table>
