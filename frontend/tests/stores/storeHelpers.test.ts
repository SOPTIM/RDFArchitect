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

import { writable, get } from "svelte/store";
import { beforeEach, describe, expect, test, vi } from "vitest";

import { loadSlot, makeGraphKey } from "../../src/lib/stores/storeHelpers";
import { createEmptySlot } from "../../src/lib/stores/storeTypes";

import type { AsyncSlot } from "../../src/lib/stores/storeTypes";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

type SimpleState = AsyncSlot<string[]>;

function makeStore(initial: SimpleState = createEmptySlot<string[]>()) {
    return writable<SimpleState>(initial);
}

function runLoadSlot(
    store: ReturnType<typeof makeStore>,
    fetcher: () => Promise<{ data?: string[] | null; error?: unknown }>,
    force = false,
) {
    return loadSlot(
        store,
        s => s,
        (s, patch) => ({ ...s, ...patch }),
        fetcher,
        "[test]",
        "items",
        force,
    );
}

// ---------------------------------------------------------------------------
// makeGraphKey
// ---------------------------------------------------------------------------

describe("makeGraphKey", () => {
    test("produces a key in the format 'workspace::graphURI'", () => {
        expect(makeGraphKey("myworkspace", "http://example.org/graph")).toBe(
            "myworkspace::http://example.org/graph",
        );
    });

    test("two calls with same arguments produce the same key", () => {
        expect(makeGraphKey("ds", "http://g")).toBe(
            makeGraphKey("ds", "http://g"),
        );
    });

    test("different workspace names produce different keys", () => {
        expect(makeGraphKey("ds1", "http://g")).not.toBe(
            makeGraphKey("ds2", "http://g"),
        );
    });

    test("different graph URIs produce different keys", () => {
        expect(makeGraphKey("ds", "http://g1")).not.toBe(
            makeGraphKey("ds", "http://g2"),
        );
    });
});

// ---------------------------------------------------------------------------
// loadSlot
// ---------------------------------------------------------------------------

describe("loadSlot", () => {
    beforeEach(() => {
        vi.restoreAllMocks();
        // suppress log output in tests
        vi.spyOn(console, "log").mockImplementation(() => {});
        vi.spyOn(console, "error").mockImplementation(() => {});
    });

    // -------------------------------------------------------------------------
    describe("happy path", () => {
        test("returns the data from the fetcher", async () => {
            const store = makeStore();
            const fetcher = vi.fn().mockResolvedValue({ data: ["a", "b"] });

            const result = await runLoadSlot(store, fetcher);

            expect(result).toEqual(["a", "b"]);
        });

        test("stores the data in the slot after a successful fetch", async () => {
            const store = makeStore();
            const fetcher = vi.fn().mockResolvedValue({ data: ["x"] });

            await runLoadSlot(store, fetcher);

            expect(get(store).data).toEqual(["x"]);
        });

        test("sets fetchedAt to a recent timestamp after success", async () => {
            const store = makeStore();
            const before = Date.now();
            await runLoadSlot(store, vi.fn().mockResolvedValue({ data: [] }));
            const after = Date.now();

            const { fetchedAt } = get(store);
            expect(fetchedAt).not.toBeNull();
            expect(fetchedAt!).toBeGreaterThanOrEqual(before);
            expect(fetchedAt!).toBeLessThanOrEqual(after);
        });

        test("clears pending after a successful fetch", async () => {
            const store = makeStore();
            await runLoadSlot(store, vi.fn().mockResolvedValue({ data: [] }));
            expect(get(store).pending).toBeNull();
        });

        test("treats a null data response as an empty result (not an error)", async () => {
            const store = makeStore();
            const result = await runLoadSlot(
                store,
                vi.fn().mockResolvedValue({ data: null }),
            );
            expect(result).toBeNull();
            expect(get(store).error).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("caching", () => {
        test("does not call the fetcher again when data is already in the slot", async () => {
            const store = makeStore();
            const fetcher = vi.fn().mockResolvedValue({ data: ["a"] });

            await runLoadSlot(store, fetcher);
            await runLoadSlot(store, fetcher);

            expect(fetcher).toHaveBeenCalledTimes(1);
        });

        test("returns the cached data on the second call", async () => {
            const store = makeStore();
            const fetcher = vi.fn().mockResolvedValue({ data: ["cached"] });

            await runLoadSlot(store, fetcher);
            const second = await runLoadSlot(store, fetcher);

            expect(second).toEqual(["cached"]);
        });

        test("force=true re-fetches even when data is already cached", async () => {
            const store = makeStore();
            const fetcher = vi.fn().mockResolvedValue({ data: ["fresh"] });

            await runLoadSlot(store, fetcher, false);
            await runLoadSlot(store, fetcher, true);

            expect(fetcher).toHaveBeenCalledTimes(2);
        });

        test("force=true overwrites stale data with the new response", async () => {
            const store = makeStore({
                data: ["old"],
                fetchedAt: 0,
                pending: null,
                error: null,
            });
            const fetcher = vi.fn().mockResolvedValue({ data: ["new"] });

            const result = await runLoadSlot(store, fetcher, true);

            expect(result).toEqual(["new"]);
            expect(get(store).data).toEqual(["new"]);
        });
    });

    // -------------------------------------------------------------------------
    describe("concurrent calls", () => {
        test("a second concurrent call reuses the in-flight promise instead of firing a second fetch", async () => {
            const store = makeStore();
            let resolveFirst!: (v: { data: string[] }) => void;
            const blocker = new Promise<{ data: string[] }>(res => {
                resolveFirst = res;
            });
            const fetcher = vi.fn().mockReturnValue(blocker);

            // Start two calls in parallel — neither has resolved yet
            const p1 = runLoadSlot(store, fetcher);
            const p2 = runLoadSlot(store, fetcher);

            resolveFirst({ data: ["shared"] });
            const [r1, r2] = await Promise.all([p1, p2]);

            expect(fetcher).toHaveBeenCalledTimes(1);
            expect(r1).toEqual(["shared"]);
            expect(r2).toEqual(["shared"]);
        });
    });

    // -------------------------------------------------------------------------
    describe("error handling", () => {
        test("returns null when the fetcher signals an error", async () => {
            const store = makeStore();
            const fetcher = vi
                .fn()
                .mockResolvedValue({ error: new Error("oops") });

            const result = await runLoadSlot(store, fetcher);

            expect(result).toBeNull();
        });

        test("stores the error object in the slot", async () => {
            const store = makeStore();
            const err = new Error("server down");
            await runLoadSlot(store, vi.fn().mockResolvedValue({ error: err }));

            expect(get(store).error).toBe(err);
        });

        test("does not overwrite valid cached data when an error is returned", async () => {
            // Pre-populate with good data
            const store = makeStore({
                data: ["good"],
                fetchedAt: 1,
                pending: null,
                error: null,
            });
            await runLoadSlot(
                store,
                vi.fn().mockResolvedValue({ error: new Error("fail") }),
                true, // force so it actually re-fetches
            );

            // Data should be untouched; only error is set
            expect(get(store).data).toEqual(["good"]);
        });

        test("clears pending after an error", async () => {
            const store = makeStore();
            await runLoadSlot(
                store,
                vi.fn().mockResolvedValue({ error: new Error("x") }),
            );

            expect(get(store).pending).toBeNull();
        });

        test("returns null and records error when the fetcher throws unexpectedly", async () => {
            const store = makeStore();
            const fetcher = vi
                .fn()
                .mockRejectedValue(new Error("network crash"));

            const result = await runLoadSlot(store, fetcher);

            expect(result).toBeNull();
            expect(get(store).error).toBeInstanceOf(Error);
            expect(get(store).pending).toBeNull();
        });
    });
});
