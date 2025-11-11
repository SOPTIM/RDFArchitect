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
    import { Fa } from "svelte-fa";

    let { title = "", description = "", icon = null } = $props();
</script>

<div
    class="empty-state-card mx-auto max-w-md rounded-xl border p-7 text-center shadow-sm backdrop-blur-sm"
>
    {#if icon}
        <div class="empty-state-icon">
            <Fa {icon} />
        </div>
    {/if}

    {#if title}
        <h3 class="empty-state-title text-lg font-semibold">{title}</h3>
    {/if}

    {#if description}
        <p class="empty-state-text mt-1 text-sm">{description}</p>
    {/if}
</div>

<style>
    .empty-state-card {
        background-color: var(--color-window-background);
        border-color: var(--color-border);
        color: var(--color-default-text);
    }

    .empty-state-icon {
        margin: 0 auto 1rem;
        display: flex;
        align-items: center;
        justify-content: center;
        height: 3rem;
        width: 3rem;
        border-radius: 0.75rem;
        background-color: var(--color-lightblue);
        color: var(--color-blue);
    }

    .empty-state-icon :global(svg) {
        height: 1.75rem;
        width: 1.75rem;
    }

    .empty-state-title {
        color: var(--color-default-text);
    }

    .empty-state-text {
        color: var(--color-text-subtle);
    }
</style>
