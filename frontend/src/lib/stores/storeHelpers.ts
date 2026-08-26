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

import { get, type Writable } from "svelte/store";

import { describeError } from "./storeLogging";
import { type AsyncSlot } from "./storeTypes";

// =============================================================================
// Graph key
// =============================================================================

export type GraphKey = `${string}::${string}`;

export function makeGraphKey(
    workspaceName: string,
    graphURI: string,
): GraphKey {
    return `${workspaceName}::${graphURI}`;
}

// =============================================================================
// Generic async loader
// =============================================================================

/**
 * Standard load pattern used across all graph-scoped stores:
 * - Returns early on cache hit (unless force=true)
 * - Coalesces concurrent fetches via `pending`
 * - Updates the slot on success/error
 *
 * @param store     The writable store
 * @param getSlot   Reads the current AsyncSlot from state
 * @param setSlot   Returns new state with the updated slot
 * @param fetcher   The API call; must return `{ data?, error? }`
 * @param logPrefix Log prefix string, e.g. "[packageStore]"
 * @param label     Human-readable label for logging, e.g. "packages"
 * @param force     Skip cache check and reload
 */
export async function loadSlot<TState, TData>(
    store: Writable<TState>,
    getSlot: (state: TState) => AsyncSlot<TData>,
    setSlot: (state: TState, next: Partial<AsyncSlot<TData>>) => TState,
    fetcher: () => Promise<{ data?: TData | null; error?: unknown }>,
    logPrefix: string,
    label: string,
    force: boolean,
): Promise<TData | null> {
    const slot = getSlot(get(store));

    if (!force && slot.data !== null) return slot.data;
    if (slot.pending !== null && !force) {
        await slot.pending;
        return getSlot(get(store)).data;
    }

    console.log(`${logPrefix} Loading ${label}, force=${force}`);

    const promise = (async () => {
        try {
            const { data, error } = await fetcher();

            if (error) {
                console.error(
                    `${logPrefix} Failed to load ${label}:`,
                    await describeError(error),
                );
                store.update(s => setSlot(s, { pending: null, error }));
                return null;
            }

            store.update(s =>
                setSlot(s, {
                    data: data ?? null,
                    fetchedAt: Date.now(),
                    pending: null,
                    error: null,
                }),
            );

            console.log(`${logPrefix} Loaded ${label}`);
            return data ?? null;
        } catch (err) {
            console.error(
                `${logPrefix} Unexpected error loading ${label}:`,
                err,
            );
            store.update(s => setSlot(s, { pending: null, error: err }));
            return null;
        }
    })();

    store.update(s => setSlot(s, { pending: promise }));
    return promise;
}
