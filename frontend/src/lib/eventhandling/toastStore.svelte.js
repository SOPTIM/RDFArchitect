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

/**
 * Per-toast countdown state. The handle is the active `setTimeout` id (null
 * while paused); `remaining` tracks how much of the duration is left so that
 * pause/resume can be alternated repeatedly.
 *
 * @type {Map<number, { handle: number | null, startedAt: number, remaining: number }>}
 */
const timers = new Map();

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
    pause,
    resume,
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
        timers.set(id, {
            handle,
            startedAt: Date.now(),
            remaining: resolvedDuration,
        });
    }

    return id;
}

/**
 * Halt the auto-dismiss countdown for a toast (e.g. while the user hovers it).
 *
 * @param {number} id
 */
function pause(id) {
    const timer = timers.get(id);
    if (!timer || timer.handle === null) return;
    clearTimeout(timer.handle);
    const elapsed = Date.now() - timer.startedAt;
    timer.remaining = Math.max(0, timer.remaining - elapsed);
    timer.handle = null;
}

/**
 * Resume a previously paused auto-dismiss countdown.
 *
 * @param {number} id
 */
function resume(id) {
    const timer = timers.get(id);
    if (!timer || timer.handle !== null) return;
    if (typeof window === "undefined") return;
    if (timer.remaining <= 0) {
        dismiss(id);
        return;
    }
    timer.startedAt = Date.now();
    timer.handle = window.setTimeout(() => dismiss(id), timer.remaining);
}

function dismiss(id) {
    const timer = timers.get(id);
    if (timer) {
        if (timer.handle !== null) {
            clearTimeout(timer.handle);
        }
        timers.delete(id);
    }
    toasts = toasts.filter(t => t.id !== id);
}

function clear() {
    for (const timer of timers.values()) {
        if (timer.handle !== null) {
            clearTimeout(timer.handle);
        }
    }
    timers.clear();
    toasts = [];
}
