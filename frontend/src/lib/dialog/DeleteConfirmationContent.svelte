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
    import { faExclamation } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    let { title, description, children } = $props();
</script>

<div class="flex w-full flex-col gap-4">
    <div class="space-y-2">
        <div class="flex items-center gap-3">
            <div
                class="bg-red flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-white"
            >
                <Fa icon={faExclamation} />
            </div>
            <h2 class="text-default-text text-lg leading-tight font-semibold">
                {title}
            </h2>
        </div>
        {#if description}
            <p class="text-default-text text-sm leading-relaxed">
                {description}
            </p>
        {/if}
    </div>
    {#if children}
        <div class="text-default-text text-sm leading-relaxed">
            {@render children?.()}
        </div>
    {/if}
</div>
