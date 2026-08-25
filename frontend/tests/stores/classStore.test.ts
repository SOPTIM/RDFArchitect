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

import { beforeEach, describe, expect, test, vi } from "vitest";

import * as api from "../../src/lib/api/generated";
import { toastStore } from "../../src/lib/eventhandling/toastStore.svelte.js";
import { createClassStore } from "../../src/lib/stores/classStore";

import type {
    AssociationPairDto,
    AttributeDto,
    ClassUmlAdaptedDto,
    EnumEntryDto,
} from "../../src/lib/api/generated";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const WORKSPACE = "workspaceA";
const GRAPH = "http://example.org/graph";

function makeClass(uuid: string, label = `Class_${uuid}`): ClassUmlAdaptedDto {
    return {
        uuid,
        label,
        attributes: [],
        enumEntries: [],
        associationPairs: [],
    } as ClassUmlAdaptedDto;
}

function makeAttribute(uuid: string): AttributeDto {
    return { uuid, label: `attr_${uuid}` } as AttributeDto;
}

function makeEnumEntry(uuid: string): EnumEntryDto {
    return { uuid, label: `entry_${uuid}` } as EnumEntryDto;
}

function makeAssociationPair(
    fromUuid: string,
    toUuid: string,
): AssociationPairDto {
    return { from: { uuid: fromUuid }, to: { uuid: toUuid } };
}

function ok<T>(data: T) {
    return { data, error: undefined };
}

function err(e = new Error("api error")) {
    return { data: undefined, error: e };
}

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("$lib/api/generated", () => ({
    getClassList: vi.fn(),
    getClassInformation: vi.fn(),
    addClass: vi.fn(),
    replaceClass: vi.fn(),
    deleteClass: vi.fn(),
    extendClass: vi.fn(),
    pasteClasses: vi.fn(),
    createAttribute: vi.fn(),
    replaceAttribute: vi.fn(),
    createAssociation: vi.fn(),
    replaceAssociation: vi.fn(),
    createEnumEntry: vi.fn(),
    replaceEnumEntry: vi.fn(),
}));

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn() },
}));

vi.spyOn(console, "log").mockImplementation(() => {});
vi.spyOn(console, "error").mockImplementation(() => {});
vi.spyOn(console, "warn").mockImplementation(() => {});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("ClassStore", () => {
    let store: ReturnType<typeof createClassStore>;

    beforeEach(() => {
        vi.clearAllMocks();
        store = createClassStore();
    });

    // =========================================================================
    describe("getClasses", () => {
        test("returns classes from the API", async () => {
            const classes = [makeClass("uuid-1"), makeClass("uuid-2")];
            vi.mocked(api.getClassList).mockResolvedValue(ok(classes));

            const result = await store.getClasses(WORKSPACE, GRAPH);

            expect(result).toEqual(classes);
        });

        test("caches results – does not re-fetch on second call", async () => {
            vi.mocked(api.getClassList).mockResolvedValue(
                ok([makeClass("uuid-1")]),
            );

            await store.getClasses(WORKSPACE, GRAPH);
            await store.getClasses(WORKSPACE, GRAPH);

            expect(api.getClassList).toHaveBeenCalledTimes(1);
        });

        test("force=true bypasses the cache", async () => {
            vi.mocked(api.getClassList).mockResolvedValue(ok([]));

            await store.getClasses(WORKSPACE, GRAPH);
            await store.getClasses(WORKSPACE, GRAPH, false, true);

            expect(api.getClassList).toHaveBeenCalledTimes(2);
        });

        test("internal-only and all-classes variants are cached independently", async () => {
            vi.mocked(api.getClassList).mockResolvedValue(ok([]));

            await store.getClasses(WORKSPACE, GRAPH, false); // internalOnly
            await store.getClasses(WORKSPACE, GRAPH, true); // all

            // Each variant must be fetched once
            expect(api.getClassList).toHaveBeenCalledTimes(2);

            // Second call to each variant should be a cache hit
            await store.getClasses(WORKSPACE, GRAPH, false);
            await store.getClasses(WORKSPACE, GRAPH, true);
            expect(api.getClassList).toHaveBeenCalledTimes(2);
        });

        test("returns null when workspaceName is empty", async () => {
            expect(await store.getClasses("", GRAPH)).toBeNull();
            expect(api.getClassList).not.toHaveBeenCalled();
        });

        test("returns null when graphURI is empty", async () => {
            expect(await store.getClasses(WORKSPACE, "")).toBeNull();
            expect(api.getClassList).not.toHaveBeenCalled();
        });

        test("returns null on API error", async () => {
            vi.mocked(api.getClassList).mockResolvedValue(err());
            expect(await store.getClasses(WORKSPACE, GRAPH)).toBeNull();
        });
    });

    // =========================================================================
    describe("getClassInfo", () => {
        test("fetches class details and merges them into both variants", async () => {
            const cls = makeClass("uuid-1");
            const detailed = {
                ...cls,
                attributes: [makeAttribute("attr-1")],
                _detailsLoaded: true,
            };

            vi.mocked(api.getClassList).mockResolvedValue(ok([cls]));
            vi.mocked(api.getClassInformation).mockResolvedValue(ok(detailed));

            // Populate both variants first so the detail can be merged into both
            await store.getClasses(WORKSPACE, GRAPH, false);
            await store.getClasses(WORKSPACE, GRAPH, true);

            const result = await store.getClassInfo(WORKSPACE, GRAPH, "uuid-1");

            expect(result?.uuid).toBe("uuid-1");
            expect(result?.attributes).toHaveLength(1);
        });

        test("returns cached details without re-fetching", async () => {
            const cls = makeClass("uuid-1");
            vi.mocked(api.getClassList).mockResolvedValue(ok([cls]));
            vi.mocked(api.getClassInformation).mockResolvedValue(
                ok({ ...cls, _detailsLoaded: true }),
            );

            await store.getClasses(WORKSPACE, GRAPH, false);
            await store.getClassInfo(WORKSPACE, GRAPH, "uuid-1");
            await store.getClassInfo(WORKSPACE, GRAPH, "uuid-1");

            expect(api.getClassInformation).toHaveBeenCalledTimes(1);
        });

        test("force=true re-fetches even when details are cached", async () => {
            const cls = makeClass("uuid-1");
            vi.mocked(api.getClassList).mockResolvedValue(ok([cls]));
            vi.mocked(api.getClassInformation).mockResolvedValue(
                ok({ ...cls, _detailsLoaded: true }),
            );

            await store.getClasses(WORKSPACE, GRAPH, false);
            await store.getClassInfo(WORKSPACE, GRAPH, "uuid-1");
            await store.getClassInfo(WORKSPACE, GRAPH, "uuid-1", true);

            expect(api.getClassInformation).toHaveBeenCalledTimes(2);
        });

        test("coalesces concurrent detail requests to a single API call", async () => {
            const cls = makeClass("uuid-1");
            vi.mocked(api.getClassList).mockResolvedValue(ok([cls]));

            let resolve!: (v: {
                data: ClassUmlAdaptedDto;
                error: undefined;
            }) => void;
            const blocker = new Promise<{
                data: ClassUmlAdaptedDto;
                error: undefined;
            }>(r => {
                resolve = r;
            });
            vi.mocked(api.getClassInformation).mockReturnValue(
                blocker as never,
            );

            await store.getClasses(WORKSPACE, GRAPH, false);

            const p1 = store.getClassInfo(WORKSPACE, GRAPH, "uuid-1");
            const p2 = store.getClassInfo(WORKSPACE, GRAPH, "uuid-1");

            resolve({
                data: { ...cls, _detailsLoaded: true } as ClassUmlAdaptedDto,
                error: undefined,
            });
            await Promise.all([p1, p2]);

            expect(api.getClassInformation).toHaveBeenCalledTimes(1);
        });

        test("returns null on API error", async () => {
            vi.mocked(api.getClassInformation).mockResolvedValue(err());
            expect(
                await store.getClassInfo(WORKSPACE, GRAPH, "uuid-1"),
            ).toBeNull();
        });

        test("returns null when any param is empty", async () => {
            expect(await store.getClassInfo("", GRAPH, "uuid-1")).toBeNull();
            expect(
                await store.getClassInfo(WORKSPACE, "", "uuid-1"),
            ).toBeNull();
            expect(await store.getClassInfo(WORKSPACE, GRAPH, "")).toBeNull();
            expect(api.getClassInformation).not.toHaveBeenCalled();
        });
    });

    // =========================================================================
    describe("addClass", () => {
        test("invalidates the graph cache and returns the new UUID", async () => {
            vi.mocked(api.getClassList).mockResolvedValue(ok([]));
            vi.mocked(api.addClass).mockResolvedValue(ok("new-uuid"));

            await store.getClasses(WORKSPACE, GRAPH);
            const result = await store.addClass(WORKSPACE, GRAPH, {} as never);

            expect(result.error).toBeNull();
            expect(result.data).toBe("new-uuid");

            // Cache should be invalidated: next fetch re-calls the API
            await store.getClasses(WORKSPACE, GRAPH);
            expect(api.getClassList).toHaveBeenCalledTimes(2);
        });

        test("shows success toast on success", async () => {
            vi.mocked(api.addClass).mockResolvedValue(ok("new-uuid"));
            await store.addClass(WORKSPACE, GRAPH, {} as never);
            expect(toastStore.success).toHaveBeenCalledOnce();
        });

        test("returns error and shows error toast on failure", async () => {
            const error = new Error("failed");
            vi.mocked(api.addClass).mockResolvedValue(err(error));

            const result = await store.addClass(WORKSPACE, GRAPH, {} as never);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledOnce();
        });
    });

    // =========================================================================
    describe("replaceClass", () => {
        test("updates the class in the store locally without invalidating the cache", async () => {
            const original = makeClass("uuid-1", "OriginalName");
            const updated = makeClass("uuid-1", "UpdatedName");

            vi.mocked(api.getClassList).mockResolvedValue(ok([original]));
            vi.mocked(api.replaceClass).mockResolvedValue(ok(undefined));

            await store.getClasses(WORKSPACE, GRAPH);
            await store.replaceClass(WORKSPACE, GRAPH, "uuid-1", updated);

            // Should not have re-fetched
            expect(api.getClassList).toHaveBeenCalledTimes(1);

            // Local state should reflect the update
            const classes = await store.getClasses(WORKSPACE, GRAPH);
            expect(classes?.find(c => c.uuid === "uuid-1")?.label).toBe(
                "UpdatedName",
            );
        });

        test("updates both the 'all' and 'internalOnly' variants", async () => {
            const original = makeClass("uuid-1", "Original");
            const updated = makeClass("uuid-1", "Updated");

            vi.mocked(api.getClassList).mockResolvedValue(ok([original]));
            vi.mocked(api.replaceClass).mockResolvedValue(ok(undefined));

            // Populate both variants
            await store.getClasses(WORKSPACE, GRAPH, false);
            await store.getClasses(WORKSPACE, GRAPH, true);

            await store.replaceClass(WORKSPACE, GRAPH, "uuid-1", updated);

            const internal = await store.getClasses(WORKSPACE, GRAPH, false);
            const all = await store.getClasses(WORKSPACE, GRAPH, true);

            expect(internal?.find(c => c.uuid === "uuid-1")?.label).toBe(
                "Updated",
            );
            expect(all?.find(c => c.uuid === "uuid-1")?.label).toBe("Updated");
        });

        test("returns error and does not mutate the store on failure", async () => {
            const original = makeClass("uuid-1", "Original");
            vi.mocked(api.getClassList).mockResolvedValue(ok([original]));
            vi.mocked(api.replaceClass).mockResolvedValue(err());

            await store.getClasses(WORKSPACE, GRAPH);
            await store.replaceClass(
                WORKSPACE,
                GRAPH,
                "uuid-1",
                makeClass("uuid-1", "Broken"),
            );

            const classes = await store.getClasses(WORKSPACE, GRAPH);
            expect(classes?.find(c => c.uuid === "uuid-1")?.label).toBe(
                "Original",
            );
        });
    });

    // =========================================================================
    describe("addAttribute", () => {
        test("appends the attribute with backend-assigned UUID to the class in the store", async () => {
            const cls = makeClass("class-1");
            vi.mocked(api.getClassList).mockResolvedValue(ok([cls]));
            vi.mocked(api.createAttribute).mockResolvedValue(
                ok("attr-uuid-from-backend"),
            );

            await store.getClasses(WORKSPACE, GRAPH);
            await store.addAttribute(
                WORKSPACE,
                GRAPH,
                "class-1",
                makeAttribute(""),
            );

            const classes = await store.getClasses(WORKSPACE, GRAPH);
            const stored = classes?.find(c => c.uuid === "class-1");
            expect(stored?.attributes).toHaveLength(1);
            expect(stored?.attributes?.[0].uuid).toBe("attr-uuid-from-backend");
        });

        test("falls back to the local attribute UUID if backend returns none", async () => {
            const cls = makeClass("class-1");
            vi.mocked(api.getClassList).mockResolvedValue(ok([cls]));
            vi.mocked(api.createAttribute).mockResolvedValue(ok(undefined));

            await store.getClasses(WORKSPACE, GRAPH);
            await store.addAttribute(
                WORKSPACE,
                GRAPH,
                "class-1",
                makeAttribute("local-uuid"),
            );

            const classes = await store.getClasses(WORKSPACE, GRAPH);
            expect(
                classes?.find(c => c.uuid === "class-1")?.attributes?.[0].uuid,
            ).toBe("local-uuid");
        });

        test("returns error and shows toast on failure", async () => {
            vi.mocked(api.createAttribute).mockResolvedValue(err());
            const result = await store.addAttribute(
                WORKSPACE,
                GRAPH,
                "class-1",
                makeAttribute("a"),
            );
            expect(result.error).toBeDefined();
            expect(toastStore.error).toHaveBeenCalledOnce();
        });
    });

    // =========================================================================
    describe("replaceAttribute", () => {
        test("updates the attribute in the store locally", async () => {
            const cls = {
                ...makeClass("class-1"),
                attributes: [makeAttribute("attr-1")],
            };
            vi.mocked(api.getClassList).mockResolvedValue(ok([cls]));
            vi.mocked(api.replaceAttribute).mockResolvedValue(ok(undefined));

            await store.getClasses(WORKSPACE, GRAPH);
            await store.replaceAttribute(WORKSPACE, GRAPH, "class-1", {
                uuid: "attr-1",
                label: "updated-attr",
            } as AttributeDto);

            const classes = await store.getClasses(WORKSPACE, GRAPH);
            expect(
                classes?.find(c => c.uuid === "class-1")?.attributes?.[0].label,
            ).toBe("updated-attr");
        });

        test("returns error immediately if attribute.uuid is missing", async () => {
            const result = await store.replaceAttribute(
                WORKSPACE,
                GRAPH,
                "class-1",
                { label: "no-uuid" } as AttributeDto,
            );
            expect(result.error).toBeInstanceOf(Error);
            expect(api.replaceAttribute).not.toHaveBeenCalled();
        });
    });

    // =========================================================================
    describe("addEnumEntry", () => {
        test("appends the enum entry with backend UUID to the class in the store", async () => {
            const cls = makeClass("class-1");
            vi.mocked(api.getClassList).mockResolvedValue(ok([cls]));
            vi.mocked(api.createEnumEntry).mockResolvedValue(ok("entry-uuid"));

            await store.getClasses(WORKSPACE, GRAPH);
            await store.addEnumEntry(
                WORKSPACE,
                GRAPH,
                "class-1",
                makeEnumEntry(""),
            );

            const classes = await store.getClasses(WORKSPACE, GRAPH);
            expect(
                classes?.find(c => c.uuid === "class-1")?.enumEntries?.[0].uuid,
            ).toBe("entry-uuid");
        });

        test("returns error and shows toast on failure", async () => {
            vi.mocked(api.createEnumEntry).mockResolvedValue(err());
            const result = await store.addEnumEntry(
                WORKSPACE,
                GRAPH,
                "class-1",
                makeEnumEntry("e"),
            );
            expect(result.error).toBeDefined();
            expect(toastStore.error).toHaveBeenCalledOnce();
        });
    });

    // =========================================================================
    describe("replaceEnumEntry", () => {
        test("updates the enum entry in the store locally", async () => {
            const cls = {
                ...makeClass("class-1"),
                enumEntries: [makeEnumEntry("entry-1")],
            };
            vi.mocked(api.getClassList).mockResolvedValue(ok([cls]));
            vi.mocked(api.replaceEnumEntry).mockResolvedValue(ok(undefined));

            await store.getClasses(WORKSPACE, GRAPH);
            await store.replaceEnumEntry(WORKSPACE, GRAPH, "class-1", {
                uuid: "entry-1",
                label: "updated",
            } as EnumEntryDto);

            const classes = await store.getClasses(WORKSPACE, GRAPH);
            expect(
                classes?.find(c => c.uuid === "class-1")?.enumEntries?.[0]
                    .label,
            ).toBe("updated");
        });

        test("returns error immediately if enumEntry.uuid is missing", async () => {
            const result = await store.replaceEnumEntry(
                WORKSPACE,
                GRAPH,
                "class-1",
                { label: "no-uuid" } as EnumEntryDto,
            );
            expect(result.error).toBeInstanceOf(Error);
            expect(api.replaceEnumEntry).not.toHaveBeenCalled();
        });
    });

    // =========================================================================
    describe("addAssociationPair", () => {
        test("appends the pair with backend-assigned UUIDs to the class in the store", async () => {
            const cls = makeClass("class-1");
            vi.mocked(api.getClassList).mockResolvedValue(ok([cls]));
            vi.mocked(api.createAssociation).mockResolvedValue(
                ok({ fromUUID: "from-uuid", toUUID: "to-uuid" }),
            );

            await store.getClasses(WORKSPACE, GRAPH);
            await store.addAssociationPair(
                WORKSPACE,
                GRAPH,
                "class-1",
                makeAssociationPair("", ""),
            );

            const classes = await store.getClasses(WORKSPACE, GRAPH);
            const pair = classes?.find(c => c.uuid === "class-1")
                ?.associationPairs?.[0];
            expect(pair?.from?.uuid).toBe("from-uuid");
            expect(pair?.to?.uuid).toBe("to-uuid");
        });

        test("returns error and shows toast on failure", async () => {
            vi.mocked(api.createAssociation).mockResolvedValue(err());
            const result = await store.addAssociationPair(
                WORKSPACE,
                GRAPH,
                "class-1",
                makeAssociationPair("f", "t"),
            );
            expect(result.error).toBeDefined();
            expect(toastStore.error).toHaveBeenCalledOnce();
        });
    });

    // =========================================================================
    describe("replaceAssociationPair", () => {
        test("replaces the association pair in the store by from.uuid", async () => {
            const pair = makeAssociationPair("from-1", "to-1");
            const cls = { ...makeClass("class-1"), associationPairs: [pair] };
            vi.mocked(api.getClassList).mockResolvedValue(ok([cls]));
            vi.mocked(api.replaceAssociation).mockResolvedValue(
                ok({ fromUUID: "from-1", toUUID: "to-2" }),
            );

            await store.getClasses(WORKSPACE, GRAPH);
            await store.replaceAssociationPair(
                WORKSPACE,
                GRAPH,
                "class-1",
                makeAssociationPair("from-1", "to-2"),
            );

            const classes = await store.getClasses(WORKSPACE, GRAPH);
            const stored = classes?.find(
                c => c.uuid === "class-1",
            )?.associationPairs;
            // Should still be exactly one pair
            expect(stored).toHaveLength(1);
            expect(stored?.[0].to?.uuid).toBe("to-2");
        });

        test("returns error immediately if pair.from.uuid is missing", async () => {
            const result = await store.replaceAssociationPair(
                WORKSPACE,
                GRAPH,
                "class-1",
                { from: {}, to: {} },
            );
            expect(result.error).toBeInstanceOf(Error);
            expect(api.replaceAssociation).not.toHaveBeenCalled();
        });
    });

    // =========================================================================
    describe("saveCopyClass", () => {
        test("returns early with no error when sources list is empty", async () => {
            const result = await store.saveCopyClass(WORKSPACE, GRAPH, {
                sources: [],
            });
            expect(result.error).toBeNull();
            expect(api.pasteClasses).not.toHaveBeenCalled();
        });

        test("invalidates graph cache on success with one class", async () => {
            vi.mocked(api.getClassList).mockResolvedValue(ok([]));
            vi.mocked(api.pasteClasses).mockResolvedValue(
                ok([{ name: "PastedClass" }]),
            );

            await store.getClasses(WORKSPACE, GRAPH);
            await store.saveCopyClass(WORKSPACE, GRAPH, {
                sources: [{ classUUID: "src-uuid" }],
            });

            await store.getClasses(WORKSPACE, GRAPH);
            expect(api.getClassList).toHaveBeenCalledTimes(2);
            expect(toastStore.success).toHaveBeenCalledOnce();
        });

        test("returns error and shows toast on API failure", async () => {
            vi.mocked(api.pasteClasses).mockResolvedValue(err());
            const result = await store.saveCopyClass(WORKSPACE, GRAPH, {
                sources: [{ classUUID: "src" }],
            });
            expect(result.error).toBeDefined();
            expect(toastStore.error).toHaveBeenCalledOnce();
        });
    });

    // =========================================================================
    describe("invalidateGraph", () => {
        test("removes the graph from the cache so the next fetch re-fetches", async () => {
            vi.mocked(api.getClassList).mockResolvedValue(ok([]));

            await store.getClasses(WORKSPACE, GRAPH);
            store.invalidateGraph(WORKSPACE, GRAPH);
            await store.getClasses(WORKSPACE, GRAPH);

            expect(api.getClassList).toHaveBeenCalledTimes(2);
        });

        test("does not affect other graphs in the cache", async () => {
            vi.mocked(api.getClassList).mockResolvedValue(ok([]));

            await store.getClasses(WORKSPACE, GRAPH);
            await store.getClasses(WORKSPACE, "http://other-graph");

            store.invalidateGraph(WORKSPACE, GRAPH);
            await store.getClasses(WORKSPACE, "http://other-graph");

            // other-graph should still be cached
            expect(api.getClassList).toHaveBeenCalledTimes(2);
        });
    });

    // =========================================================================
    describe("invalidateWorkspace", () => {
        test("removes all graphs of a workspace from the cache", async () => {
            vi.mocked(api.getClassList).mockResolvedValue(ok([]));

            await store.getClasses(WORKSPACE, GRAPH);
            await store.getClasses(WORKSPACE, "http://other-graph");
            await store.getClasses("workspaceB", GRAPH);

            store.invalidateWorkspace(WORKSPACE);

            await store.getClasses(WORKSPACE, GRAPH);
            await store.getClasses(WORKSPACE, "http://other-graph");
            await store.getClasses("workspaceB", GRAPH);

            // WORKSPACE had 2 graphs invalidated → 2 extra calls; workspaceB stays cached
            expect(api.getClassList).toHaveBeenCalledTimes(5);
        });

        test("does not affect workspaces other than the invalidated one", async () => {
            vi.mocked(api.getClassList).mockResolvedValue(ok([]));

            await store.getClasses("workspaceB", GRAPH);
            store.invalidateWorkspace(WORKSPACE); // invalidate a different workspace
            await store.getClasses("workspaceB", GRAPH);

            // workspaceB should still be cached
            expect(api.getClassList).toHaveBeenCalledTimes(1);
        });
    });
});
