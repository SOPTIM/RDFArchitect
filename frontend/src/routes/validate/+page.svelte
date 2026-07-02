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
    import ValidationReport from "$lib/components/ValidationReport.svelte";
    import { validationState } from "$lib/sharedState.svelte.js";

    let result = $state(null);

    $effect(() => {
        validationState.result.subscribe();
        result = validationState.result.getValue();
    });
</script>

<div class="bg-window-background flex h-full flex-col overflow-y-auto p-6">
    <h1 class="text-default-text mb-4 text-xl font-semibold">
        Validation Result
    </h1>

    {#if result}
        <ValidationReport {result} />
    {:else}
        <div class="text-default-text/70 text-sm">
            Use the menu to start a new validation.
        </div>
    {/if}
</div>
