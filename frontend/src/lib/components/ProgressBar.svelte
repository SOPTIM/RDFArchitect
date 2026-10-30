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
     * A labelled progress bar: the label on the left, the percentage on the right and the
     * bar underneath.
     */
    let { label, percent, ariaLabel } = $props();

    let rounded = $derived(Math.round(Math.min(100, Math.max(0, percent))));
    // A step that takes a while sits on the same percentage for seconds at a time, where a bar
    // that only has a width reads as stuck rather than as working.
    let working = $derived(rounded < 100);
</script>

<div class="mb-1 flex items-baseline justify-between gap-2">
    <span class="text-default-text truncate text-sm" aria-live="polite">
        {label}
    </span>
    <span class="text-text-subtle shrink-0 text-sm tabular-nums">
        {rounded}%
    </span>
</div>

<div
    class="bg-default-background h-2 w-full overflow-hidden rounded"
    role="progressbar"
    aria-valuenow={rounded}
    aria-valuemin={0}
    aria-valuemax={100}
    aria-label={ariaLabel}
>
    <div
        class="bg-blue relative h-full overflow-hidden rounded transition-[width] duration-200 ease-out"
        style={`width:${rounded}%`}
    >
        {#if working}
            <span class="sheen"></span>
        {/if}
    </div>
</div>

<style>
    .sheen {
        position: absolute;
        inset: 0;
        background: linear-gradient(
            90deg,
            transparent 0%,
            rgb(255 255 255 / 0.5) 50%,
            transparent 100%
        );
        animation: sheen 1.4s linear infinite;
    }

    @keyframes sheen {
        from {
            transform: translateX(-100%);
        }
        to {
            transform: translateX(100%);
        }
    }

    @media (prefers-reduced-motion: reduce) {
        .sheen {
            animation: none;
        }
    }
</style>
