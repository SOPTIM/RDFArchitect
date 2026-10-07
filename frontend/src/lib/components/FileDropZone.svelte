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
    import { fade } from "svelte/transition";
    import { Fa } from "svelte-fa";

    import {
        dragHasFiles,
        endFileDrag,
        extractDroppedItems,
        fileDragState,
    } from "$lib/fileDragState.svelte.js";

    let {
        onFiles,
        disabled = false,
        icon = null,
        message = "Drop files here",
        info = "",
        disabledMessage = "Files cannot be dropped here",
        overlay = false,
        class: className = "",
        children,
    } = $props();

    let over = $state(false);

    /** The dashed frame the zone wears itself, for a zone that is part of the layout. */
    const inlineTone = $derived(
        disabled
            ? "border-red bg-red-background text-red-text"
            : over
              ? "border-blue bg-blue/10"
              : fileDragState.active
                ? "border-blue/60 bg-window-background"
                : "border-border bg-window-background",
    );

    /**
     * The dashed frame laid over the content, for a zone that only appears while dragging. It
     * leaves what it frames untinted, so that one can see what is being added to.
     */
    const overlayTone = $derived(
        disabled
            ? "border-red bg-red-background/40 text-red-text"
            : over
              ? "border-blue bg-lightblue/40 text-blue"
              : "border-blue/60 text-blue",
    );

    const fileCountLabel = $derived(
        `${fileDragState.fileCount} ${fileDragState.fileCount === 1 ? "file" : "files"}`,
    );

    // A zone that takes files is exempt from the page-wide dimming.
    const frameClasses = $derived(
        overlay
            ? fileDragState.active
                ? "z-40"
                : ""
            : `rounded-xl border-2 border-dashed transition-colors ${inlineTone}`,
    );

    function handleDragOver(event) {
        if (!dragHasFiles(event)) {
            return;
        }
        // Without this the browser refuses the drop and opens the file in the tab instead.
        event.preventDefault();
        over = true;
    }

    function handleDragLeave(event) {
        // Moving onto a child element leaves the zone as far as the event is concerned, but not as
        // far as the user is concerned.
        if (event.currentTarget.contains(event.relatedTarget)) {
            return;
        }
        over = false;
    }

    function handleDrop(event) {
        if (!dragHasFiles(event)) {
            return;
        }
        event.preventDefault();
        over = false;
        endFileDrag();
        if (!disabled) {
            onFiles?.(extractDroppedItems(event.dataTransfer));
        }
    }
</script>

<div
    class={`relative ${frameClasses} ${className}`}
    role="group"
    ondragover={handleDragOver}
    ondragleave={handleDragLeave}
    ondrop={handleDrop}
>
    {@render children?.()}

    {#if overlay && fileDragState.active}
        <!-- The zone is lifted out of the page-wide dimming, so the box dims the margin it leaves
             around itself; what stays lit is exactly what lies inside the dashed outline. -->
        <div
            class={`drag-cutout pointer-events-none absolute inset-1 z-40 flex flex-col items-center justify-center gap-1 rounded-2xl border-[3px] border-dashed p-4 text-center transition-colors ${overlayTone}`}
            in:fade={{ duration: 120 }}
            out:fade={{ duration: 80 }}
        >
            <!-- Only this much is opaque: the label has to be read off whatever it covers. -->
            <div
                class="bg-window-background/85 flex flex-col items-center gap-1 rounded-xl px-5 py-3 shadow-sm"
            >
                {#if icon && !disabled}
                    <span class="mb-1 text-2xl"><Fa {icon} /></span>
                {/if}
                <p class="text-sm font-semibold">
                    {disabled ? disabledMessage : message}
                </p>
                {#if !disabled && fileDragState.fileCount > 0}
                    <p class="text-xs">{fileCountLabel}</p>
                {/if}
                {#if !disabled && info}
                    <p class="mt-1 text-xs opacity-80">{info}</p>
                {/if}
            </div>
        </div>
    {/if}
</div>
