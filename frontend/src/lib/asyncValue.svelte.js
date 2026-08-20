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

import { untrack } from "svelte";

import { forceReloadTrigger } from "$lib/sharedState.svelte.js";

/**
 * Loads a value for the key returned by `getKey` and reloads it whenever the
 * key changes or a global reload is triggered. `current` stays null until the
 * loaded value belongs to the current key, so a value of a previous key is
 * never shown.
 *
 * Must be called while a component initialises, since it installs an effect.
 *
 * @template K, V
 * @param {() => K} getKey key to load for; null or undefined loads nothing
 * @param {(key: K) => Promise<V>} load
 * @returns {{ current: V | null }}
 */
export function asyncValue(getKey, load) {
    let value = $state(null);
    let valueKey = $state(null);
    let latestRequest = 0;

    $effect(() => {
        forceReloadTrigger.subscribe();
        const key = getKey();
        const request = ++latestRequest;
        if (key === null || key === undefined) {
            value = null;
            valueKey = null;
            return;
        }
        untrack(async () => {
            try {
                const loaded = await load(key);
                if (request !== latestRequest) {
                    return;
                }
                value = loaded;
                valueKey = key;
            } catch (err) {
                if (request !== latestRequest) {
                    return;
                }
                console.error("Failed to load value for", key, err);
                value = null;
                valueKey = null;
            }
        });
    });

    return {
        get current() {
            return valueKey === getKey() ? value : null;
        },
    };
}
