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
     * One named group of fields on a card.
     *
     * A rule can say sixteen things and almost every rule says three of them, so the groups that
     * are empty start closed and the card stays the size of what it actually constrains. A group
     * that holds something opens itself, because a value nobody can see is worse than a long card.
     */
    import CollapseToggle from "$lib/components/CollapseToggle.svelte";

    let {
        title,
        /** Whether this group already says something, which is what decides if it starts open. */
        filled = false,
        children,
    } = $props();

    /** What the user last chose, or null while they have not chosen — see below. */
    let toggled = $state(null);

    /**
     * Open when it holds something, until somebody says otherwise.
     *
     * Seeding a piece of state from `filled` would fix the answer at the moment the card was
     * built, and a group that gains its first value while the card is open would stay shut over it.
     */
    const open = $derived(toggled ?? filled);
</script>

<div class="border-border/60 border-t pt-2">
    <CollapseToggle
        expanded={open}
        label={title}
        onclick={() => (toggled = !open)}
    >
        <span
            class="text-text-subtle text-xs font-semibold tracking-wide uppercase"
        >
            {title}
        </span>
    </CollapseToggle>
    {#if open}
        <div class="mt-2 grid grid-cols-2 gap-3">
            {@render children?.()}
        </div>
    {/if}
</div>
