<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script>
    import { counted, plural } from "$lib/utils/plural.js";

    const {
        label,
        count,
        shown,
        description = "",
        noun = "item",
        onToggle,
    } = $props();

    /**
     * An option with nothing behind it is not a toggle at all: it keeps its
     * place so that the row does not rearrange itself as the user moves
     * between subjects, but it is drawn dashed and faded and cannot be
     * pressed.
     */
    const empty = $derived(count === 0);

    /**
     * Says which way the toggle is pointing through fill rather than depth,
     * borrowing the tint the navigation marks a selected entry with: an option
     * being shown is filled in and sits flat, an option hidden is an outline
     * that greys over on hover.
     */
    const appearance = $derived(
        empty
            ? "border-border text-default-text cursor-not-allowed border-dashed opacity-40"
            : shown
              ? "bg-nav-active-background text-nav-active-text border-nav-active-background hover:bg-nav-hover-background cursor-pointer"
              : "border-border-strong text-default-text hover:border-blue hover:bg-background-subtle cursor-pointer bg-white",
    );

    /** Says what the option covers and whether there is anything behind it. */
    const title = $derived.by(() => {
        const how = empty ? `no ${plural(noun)}` : counted(count, noun);
        return description ? `${description} — ${how}` : how;
    });
</script>

<button
    type="button"
    disabled={empty}
    aria-pressed={shown}
    {title}
    class="flex items-center gap-2 rounded-full border px-3 py-1 text-sm transition-colors {appearance}"
    onclick={() => onToggle(shown)}
>
    {label}
    <span class="text-xs opacity-70">
        {empty ? "–" : count}
    </span>
</button>
