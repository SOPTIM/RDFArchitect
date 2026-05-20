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
  -->

<script>
    import {
        faCircleCheck,
        faCircleExclamation,
        faCircleInfo,
        faTriangleExclamation,
        faXmark,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    /**
     * A single toast notification card. Rendered by ToastContainer; not
     * intended for direct use at call sites — use {@link
     * $lib/eventhandling/toastStore.svelte.js} instead.
     */

    const { toast, onDismiss = () => {} } = $props();

    const VARIANTS = {
        success: {
            icon: faCircleCheck,
            iconClass: "text-green-text",
            surfaceClass: "toast-surface-success",
        },
        error: {
            icon: faCircleExclamation,
            iconClass: "text-red-text",
            surfaceClass: "toast-surface-error",
        },
        info: {
            icon: faCircleInfo,
            iconClass: "text-blue",
            surfaceClass: "toast-surface-info",
        },
        warning: {
            icon: faTriangleExclamation,
            iconClass: "text-orange",
            surfaceClass: "toast-surface-warning",
        },
    };

    const config = $derived(VARIANTS[toast.variant] ?? VARIANTS.info);

    const role = $derived(
        toast.variant === "error" || toast.variant === "warning"
            ? "alert"
            : "status",
    );
</script>

<div
    class="toast-card {config.surfaceClass} pointer-events-auto flex
        w-80 max-w-[90vw] items-start gap-3 rounded-lg border p-3 shadow-md"
    {role}
    aria-live={role === "alert" ? "assertive" : "polite"}
>
    <Fa icon={config.icon} class="{config.iconClass} mt-0.5 shrink-0" />
    <div class="min-w-0 flex-1 text-sm leading-snug">
        <p class="font-medium break-words">{toast.title}</p>
        {#if toast.message}
            <p class="text-default-text/80 mt-0.5 break-words">
                {toast.message}
            </p>
        {/if}
    </div>
    <button
        type="button"
        class="text-text-subtle hover:text-default-text -m-1 cursor-pointer
            rounded p-1 transition-colors"
        aria-label="Dismiss notification"
        onclick={() => onDismiss(toast.id)}
    >
        <Fa icon={faXmark} />
    </button>
</div>

<style>
    .toast-card {
        background: var(--color-white);
        color: var(--color-default-text);
    }

    .toast-surface-success {
        background: var(--color-green-background);
        border-color: var(--color-green-border);
    }

    .toast-surface-error {
        background: var(--color-red-background);
        border-color: var(--color-red-border);
    }

    .toast-surface-info {
        background: var(--color-lightblue);
        border-color: var(--color-blue);
    }

    .toast-surface-warning {
        background: var(--color-white);
        border-color: var(--color-orange);
    }
</style>
