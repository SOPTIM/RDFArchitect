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

import {
    eventStack,
    EventType,
} from "$lib/eventhandling/closeEventManager.svelte.js";

export const shortcutStore = {
    /**
     * @param {string} id - unique identifier
     * @param {string[] | string[][]} keys - e.g. ["ctrl", "s"] or [["ctrl", "s"], ["ctrl", "shift", "s"]]
     * @param {(event: KeyboardEvent) => void} handler - receives the keydown event, e.g. to tell a held key by event.repeat
     * @param closeDialogs define if the shortcut should close open dialogs (default: false). If true, all open dialogs will be closed before executing the handler.
     * @param {() => boolean} [isActive] whether the shortcut applies right now. One that does not
     *     still keeps the browser's own action for the keys from running, but leaves them to the
     *     next shortcut registered for the same keys, e.g. the class editor's Ctrl+S, which only
     *     saves a class that was changed, to the desktop app's Save.
     */
    register(id, keys, handler, closeDialogs = false, isActive = null) {
        // Normalize to an array of key-combinations
        if (registry[id]) {
            console.warn(
                `Shortcut with id "${id}" is already registered. Overwriting.`,
            );
        }
        const combinations = Array.isArray(keys[0]) ? keys : [keys];
        const normalizedCombos = combinations.map(normalizeCombo);
        // Re-added rather than overwritten, so that it counts as registered last.
        delete registry[id];
        registry[id] = {
            combos: normalizedCombos,
            handler,
            closeDialogs,
            isActive,
        };
        return () => this.unregister(id);
    },

    unregister(id) {
        delete registry[id];
    },

    /**
     * Called from the global keydown handler in +layout.svelte
     * Returns true if a handler was executed
     *
     * Of several shortcuts for the same keys, the one registered last wins: it belongs to the
     * part of the page that opened most recently.
     */
    handleEvent(event) {
        const normalized = normalizeEvent(event);
        const matching = Object.values(registry)
            .filter(({ combos }) => combos.includes(normalized))
            .reverse();
        if (matching.length === 0) {
            return false;
        }
        event.preventDefault();
        const shortcut = matching.find(
            ({ isActive }) => !isActive || isActive(),
        );
        if (!shortcut) {
            return false;
        }
        const { handler, closeDialogs } = shortcut;
        eventStack.guardAction(() => {
            if (closeDialogs) {
                eventStack.closeAllExcept([EventType.CLASS_EDITOR]);
            }
            handler(event);
        });
        return true;
    },
};

const MODIFIER_ORDER = ["ctrl", "shift", "alt"];

const registry = {};

/**
 * Returns true for single-character keys that are not alphanumeric
 * (e.g. "?", "/", "@"). Shift is part of the character itself on many
 * keyboard layouts (e.g. Shift+ß = ? on German keyboards) and is therefore
 * never included for these keys.
 */
function isSpecialChar(key) {
    return key.length === 1 && !/[a-z0-9]/.test(key);
}

/**
 * Normalizes a key-combination array into a canonical string.
 * Order of modifiers does not matter: ["shift", "ctrl", "s"] === ["ctrl", "shift", "s"]
 * For special characters, Shift is dropped (it is part of the character).
 * @param {string[]} combo
 * @returns {string} e.g. "ctrl+shift+s" or "ctrl+?"
 */
function normalizeCombo(combo) {
    const keys = combo.map(k => k.toLowerCase());
    const nonModifiers = keys.filter(k => !MODIFIER_ORDER.includes(k));
    const special = nonModifiers.some(isSpecialChar);

    const modifiers = MODIFIER_ORDER.filter(m => {
        if (special && m === "shift") return false;
        return keys.includes(m);
    });

    return [...modifiers, ...nonModifiers].join("+");
}

/**
 * Normalizes a keyboard event into a canonical string key like "ctrl+shift+s".
 * For special characters (e.g. "?"), Shift is ignored because it is part of
 * the character itself, but Ctrl/Alt are kept so "?" and "Ctrl+?" can be
 * registered independently.
 */
function normalizeEvent(event) {
    const key = event.key.toLowerCase();
    const special = isSpecialChar(key);

    const parts = [];
    if (event.ctrlKey || event.metaKey) parts.push("ctrl");
    if (event.shiftKey && !special) parts.push("shift");
    if (event.altKey || event.code === "AltLeft") parts.push("alt");

    if (special) {
        parts.push(key);
    } else {
        parts.push(
            event.code.replace("Key", "").replace("Digit", "").toLowerCase(),
        );
    }

    return parts.join("+");
}
