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

export const shortcutStore = {
    /**
     * @param {string} id - unique identifier
     * @param {string[] | string[][]} keys - e.g. ["ctrl", "s"] or [["ctrl", "s"], ["ctrl", "shift", "s"]]
     * @param {() => void} handler
     */
    register(id, keys, handler) {
        // Normalize to an array of key-combinations
        const combinations = Array.isArray(keys[0]) ? keys : [keys];
        const normalizedCombos = combinations.map(normalizeCombo);
        registry[id] = { combos: normalizedCombos, handler };
    },

    unregister(id) {
        delete registry[id];
    },

    /**
     * Called from the global keydown handler in +layout.svelte
     * Returns true if a handler was executed
     */
    handleEvent(event) {
        const normalized = normalizeEvent(event);
        for (const { combos, handler } of Object.values(registry)) {
            if (combos.includes(normalized)) {
                event.preventDefault();
                handler();
                return true;
            }
        }
        return false;
    },
};

const MODIFIER_ORDER = ["ctrl", "shift", "alt"];

const registry = $state({});

/**
 * Normalizes a key-combination array into a canonical string.
 * Order of modifiers does not matter: ["shift", "ctrl", "s"] === ["ctrl", "shift", "s"]
 * @param {string[]} combo
 * @returns {string} e.g. "ctrl+shift+s"
 */
function normalizeCombo(combo) {
    const keys = combo.map(k => k.toLowerCase());
    const modifiers = MODIFIER_ORDER.filter(m => keys.includes(m));
    const nonModifiers = keys.filter(k => !MODIFIER_ORDER.includes(k));
    return [...modifiers, ...nonModifiers].join("+");
}

/**
 * Normalizes a keyboard event into a canonical string key like "ctrl+shift+s".
 * For special characters (non-alphanumeric single chars), modifiers are ignored
 * because they are part of the character itself (e.g. Shift+ß = ? on German keyboards)
 */
function normalizeEvent(event) {
    const key = event.key.toLowerCase();
    const isSpecialChar = key.length === 1 && !/[a-z0-9]/.test(key);

    if (isSpecialChar) {
        return key;
    }

    const parts = [];
    if (event.ctrlKey || event.metaKey) parts.push("ctrl");
    if (event.shiftKey) parts.push("shift");
    if (event.altKey || event.code === "AltLeft") parts.push("alt");
    parts.push(
        event.code.replace("Key", "").replace("Digit", "").toLowerCase(),
    );
    return parts.join("+");
}
