/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

/**
 * Lightweight toast notification store.
 *
 * Toasts are short-lived, non-blocking event notifications surfaced in a fixed
 * region of the viewport. Use them for confirmations and recoverable failures
 * where opening a dialog would be excessive.
 *
 * The store is a singleton consumed by ToastContainer.svelte (mounted once in
 * `+layout.svelte`) and by call sites via {@link toastStore}.
 *
 * @typedef {"success" | "error" | "info" | "warning"} ToastVariant
 *
 * @typedef {{
 *   id: number,
 *   variant: ToastVariant,
 *   title: string,
 *   message?: string,
 *   duration: number,
 * }} Toast
 */

/** Default auto-dismiss duration in ms, per variant. `0` disables auto-dismiss. */
const DEFAULT_DURATIONS = {
    success: 3500,
    info: 3500,
    warning: 5000,
    error: 6000,
};
const timeouts = new Map();

/**
 * Singleton facade. Methods read and mutate the reactive `toasts` state, so
 * `getToasts()` is intentionally a function — components subscribe by reading
 * it inside a `$derived`/effect.
 */
export const toastStore = {
    getToasts: () => toasts,
    show,
    success: (title, message, options = {}) =>
        show({ ...options, variant: "success", title, message }),
    error: (title, message, options = {}) =>
        show({ ...options, variant: "error", title, message }),
    info: (title, message, options = {}) =>
        show({ ...options, variant: "info", title, message }),
    warning: (title, message, options = {}) =>
        show({ ...options, variant: "warning", title, message }),
    dismiss,
    clear,
};

let toasts = $state([]);
let nextId = 1;

/**
 * Show a toast and return its id (so callers can dismiss it manually).
 *
 * @param {{
 *   variant?: ToastVariant,
 *   title: string,
 *   message?: string,
 *   duration?: number,
 * }} options
 */
function show({ variant = "info", title, message, duration } = {}) {
    const id = nextId++;
    const resolvedDuration = duration ?? DEFAULT_DURATIONS[variant] ?? 3500;
    const toast = {
        id,
        variant,
        title,
        message,
        duration: resolvedDuration,
    };
    toasts = [...toasts, toast];

    if (resolvedDuration > 0 && typeof window !== "undefined") {
        const handle = window.setTimeout(() => dismiss(id), resolvedDuration);
        timeouts.set(id, handle);
    }

    return id;
}

function dismiss(id) {
    const handle = timeouts.get(id);
    if (handle !== undefined) {
        clearTimeout(handle);
        timeouts.delete(id);
    }
    toasts = toasts.filter(t => t.id !== id);
}

function clear() {
    for (const handle of timeouts.values()) {
        clearTimeout(handle);
    }
    timeouts.clear();
    toasts = [];
}
