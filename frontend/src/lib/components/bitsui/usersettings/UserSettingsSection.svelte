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
    let { title, children } = $props();
</script>

<div class="flex flex-col gap-2">
    {#if title}
        <div class="flex items-center gap-2">
            <span
                class="text-default-text text-xs font-semibold whitespace-nowrap uppercase opacity-80"
            >
                {title}
            </span>
            <hr class="flex-1 border-t border-gray-600" />
        </div>
    {/if}
    {@render children?.()}
</div>
