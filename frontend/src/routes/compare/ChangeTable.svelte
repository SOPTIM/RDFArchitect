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

    let { changes } = $props();

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
</script>

<table class="w-full table-fixed text-left">
    <thead>
        <tr>
            <th
                class="border-default-text bg-window-background w-1/2 border-b p-4"
            >
                <p class="block text-sm leading-none font-normal">Predicate</p>
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
        {#each changes as change}
            <tr class={getRowClasses(change)}>
                <td class="border-default-text border-b p-4">
                    <p class="text-default-text block text-sm">
                        {change.predicate}
                    </p>
                </td>
                <td class="border-default-text border-b p-4">
                    <p class="text-default-text block text-sm">
                        {#if change.from === null}
                            <Fa icon={faMinus} />
                        {:else}
                            {change.from}
                        {/if}
                    </p>
                </td>
                <td class="border-default-text border-b p-4">
                    <p class="text-default-text block text-sm">
                        {#if change.to === null}
                            <Fa icon={faMinus} />
                        {:else}
                            {change.to}
                        {/if}
                    </p>
                </td>
            </tr>
        {/each}
    </tbody>
</table>
