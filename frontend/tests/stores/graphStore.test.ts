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

import { get } from "svelte/store";
import { beforeEach, describe, expect, test, vi } from "vitest";

import * as api from "../../src/lib/api/generated";
import { toastStore } from "../../src/lib/eventhandling/toastStore.svelte.js";
import { createGraphStore } from "../../src/lib/stores/graphStore";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const WORKSPACE_A = "workspaceA";
const WORKSPACE_B = "workspaceB";
const GRAPH_URI = "http://example.org/graph1";
const GRAPH_URI_2 = "http://example.org/graph2";

const MOCK_GRAPHS: api.GraphDto[] = [
    makeGraphDto(GRAPH_URI),
    makeGraphDto(GRAPH_URI_2),
];

const MOCK_FILE = new File(["content"], "graph.ttl", { type: "text/turtle" });

const JOB_ID = "11111111-2222-3333-4444-555555555555";

const MOCK_JOB_STATUS: api.ImportJobStatus = {
    jobId: JOB_ID,
    datasetName: WORKSPACE_A,
    state: "COMPLETED",
    files: [],
    importedGraphUris: [GRAPH_URI],
    failedImports: [],
    warnings: [],
};

// Helper to produce a GraphDto whose URI reconstructs to the given string.
function makeGraphDto(uri: string): api.GraphDto {
    return {
        uri: { prefix: uri, suffix: "" },
    } as api.GraphDto;
}

/** Waits for the store to have sent its upload and returns that request. */
async function sentRequest(): Promise<FakeXMLHttpRequest> {
    await vi.waitFor(() => {
        expect(FakeXMLHttpRequest.instances).toHaveLength(1);
        expect(FakeXMLHttpRequest.instances[0].body).not.toBeNull();
    });
    return FakeXMLHttpRequest.instances[0];
}

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("$lib/api/generated", () => ({
    listGraphs: vi.fn(),
    deleteGraph: vi.fn(),
    replaceGraph: vi.fn(),
    renameGraph: vi.fn(),
    cancelImport: vi.fn(),
    getImportStatus: vi.fn(),
}));

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

/** Stands in for the upload of an import, which the store runs through XMLHttpRequest. */
class FakeXMLHttpRequest {
    static instances: FakeXMLHttpRequest[] = [];

    upload: { onprogress?: (event: unknown) => void } = {};
    withCredentials = false;
    responseType = "";
    status = 0;
    response: unknown = null;
    method: string | null = null;
    url: string | null = null;
    body: FormData | null = null;
    onload?: () => void;
    onerror?: () => void;
    onabort?: () => void;

    constructor() {
        FakeXMLHttpRequest.instances.push(this);
    }

    open(method: string, url: string) {
        this.method = method;
        this.url = url;
    }

    send(body: FormData) {
        this.body = body;
    }

    abort() {
        this.onabort?.();
    }

    respond(status: number, response: unknown) {
        this.status = status;
        this.response = response;
        this.onload?.();
    }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("graphStore", () => {
    let store: ReturnType<typeof createGraphStore>;

    beforeEach(() => {
        vi.clearAllMocks();
        FakeXMLHttpRequest.instances = [];
        vi.stubGlobal("XMLHttpRequest", FakeXMLHttpRequest);
        store = createGraphStore();
    });

    // -------------------------------------------------------------------------
    describe("Initial State", () => {
        test("store initializes with an empty graphs map", () => {
            const state = get(store);
            expect(state.graphs.size).toBe(0);
        });
    });

    // -------------------------------------------------------------------------
    describe("getGraphs", () => {
        test("returns null for empty workspaceName without calling API", async () => {
            const result = await store.getGraphs("");
            expect(result).toBeNull();
            expect(api.listGraphs).not.toHaveBeenCalled();
        });

        test("fetches and caches graphs for a workspace", async () => {
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });

            const result = await store.getGraphs(WORKSPACE_A);
            await store.getGraphs(WORKSPACE_A);

            expect(result).toEqual(MOCK_GRAPHS);
            expect(api.listGraphs).toHaveBeenCalledTimes(1);
            expect(api.listGraphs).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE_A },
            });
        });

        test("force=true bypasses the cache and re-fetches", async () => {
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });

            await store.getGraphs(WORKSPACE_A);
            await store.getGraphs(WORKSPACE_A, true);

            expect(api.listGraphs).toHaveBeenCalledTimes(2);
        });

        test("returns null on API error", async () => {
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: undefined,
                error: new Error("Network error"),
            });

            const result = await store.getGraphs(WORKSPACE_A);
            expect(result).toBeNull();
        });

        test("caches are independent for different workspaces", async () => {
            vi.mocked(api.listGraphs)
                .mockResolvedValueOnce({ data: MOCK_GRAPHS, error: undefined })
                .mockResolvedValueOnce({
                    data: [makeGraphDto(GRAPH_URI_2)],
                    error: undefined,
                });

            const resultA = await store.getGraphs(WORKSPACE_A);
            const resultB = await store.getGraphs(WORKSPACE_B);

            expect(resultA).toEqual(MOCK_GRAPHS);
            expect(resultB).toEqual([makeGraphDto(GRAPH_URI_2)]);
            expect(api.listGraphs).toHaveBeenCalledTimes(2);
        });
    });

    // -------------------------------------------------------------------------
    describe("addEmptyGraph", () => {
        test("calls API with correct arguments and invalidates workspace cache on success", async () => {
            vi.mocked(api.replaceGraph).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(WORKSPACE_A);

            const result = await store.addEmptyGraph(WORKSPACE_A, GRAPH_URI);

            expect(result.error).toBeNull();
            expect(api.replaceGraph).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE_A, graphURI: GRAPH_URI },
            });

            const state = get(store);
            expect(state.graphs.has(WORKSPACE_A)).toBe(false);

            expect(toastStore.success).toHaveBeenCalledWith(
                "Schema created",
                `"${GRAPH_URI}" was added to "${WORKSPACE_A}".`,
            );
        });

        test("returns error and preserves cache on API failure", async () => {
            const error = new Error("Create failed");
            vi.mocked(api.replaceGraph).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(WORKSPACE_A);

            const result = await store.addEmptyGraph(WORKSPACE_A, GRAPH_URI);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Create failed",
                `Could not create schema "${GRAPH_URI}".`,
            );

            const state = get(store);
            expect(state.graphs.has(WORKSPACE_A)).toBe(true);
        });
    });

    // -------------------------------------------------------------------------
    describe("startImport", () => {
        test("returns an error immediately if no files are provided", async () => {
            const result = await store.startImport(
                WORKSPACE_A,
                [],
                [GRAPH_URI],
            );

            expect(result.error).toBeInstanceOf(Error);
            expect(FakeXMLHttpRequest.instances).toHaveLength(0);
        });

        test("uploads the files and resolves with the id of the started job", async () => {
            const pending = store.startImport(
                WORKSPACE_A,
                [MOCK_FILE],
                [GRAPH_URI],
            );
            const request = await sentRequest();

            expect(request.method).toBe("POST");
            expect(request.url).toBe(
                `/api/datasets/${WORKSPACE_A}/graphs/content/imports` +
                    `?graphUris=${encodeURIComponent(GRAPH_URI)}`,
            );
            expect(request.withCredentials).toBe(true);
            expect(request.body.getAll("files")).toEqual([MOCK_FILE]);

            request.respond(202, { jobId: JOB_ID });

            expect(await pending).toEqual({
                error: null,
                data: { jobId: JOB_ID },
            });
        });

        test("reports how much of the upload has gone through", async () => {
            const reported: number[] = [];
            const pending = store.startImport(
                WORKSPACE_A,
                [MOCK_FILE],
                [GRAPH_URI],
                { onUploadProgress: percent => reported.push(percent) },
            );
            const request = await sentRequest();

            request.upload.onprogress({
                lengthComputable: true,
                loaded: 25,
                total: 100,
            });
            request.respond(202, { jobId: JOB_ID });
            await pending;

            expect(reported).toEqual([25]);
        });

        test("explains a rejected import instead of failing silently", async () => {
            const pending = store.startImport(
                WORKSPACE_A,
                [MOCK_FILE],
                [GRAPH_URI],
            );
            const request = await sentRequest();

            request.respond(409, {});

            const result = await pending;
            expect((result.error as Error).message).toContain(
                "Another import is still running",
            );
        });
    });

    // -------------------------------------------------------------------------
    describe("getImportStatus", () => {
        test("returns the status the backend reports", async () => {
            vi.mocked(api.getImportStatus).mockResolvedValue({
                data: MOCK_JOB_STATUS,
                error: undefined,
            });

            const result = await store.getImportStatus(WORKSPACE_A, JOB_ID);

            expect(api.getImportStatus).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE_A, jobId: JOB_ID },
            });
            expect(result.data).toBe(MOCK_JOB_STATUS);
        });

        test("returns an error for a job the backend does not know", async () => {
            vi.mocked(api.getImportStatus).mockResolvedValue({
                data: undefined,
                error: new Error("not found"),
            });

            const result = await store.getImportStatus(WORKSPACE_A, JOB_ID);

            expect(result.error).toBeInstanceOf(Error);
        });
    });

    // -------------------------------------------------------------------------
    describe("cancelImport", () => {
        test("asks the backend to stop the job", async () => {
            vi.mocked(api.cancelImport).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            const result = await store.cancelImport(WORKSPACE_A, JOB_ID);

            expect(api.cancelImport).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE_A, jobId: JOB_ID },
            });
            expect(result.error).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("remove (removeGraph)", () => {
        test("calls API with correct arguments on success", async () => {
            vi.mocked(api.deleteGraph).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(WORKSPACE_A);

            const result = await store.remove(WORKSPACE_A, GRAPH_URI);

            expect(result.error).toBeNull();
            expect(api.deleteGraph).toHaveBeenCalledWith({
                path: { datasetName: WORKSPACE_A, graphURI: GRAPH_URI },
            });
        });

        test("removes only the matching graph from the cached list", async () => {
            vi.mocked(api.deleteGraph).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(WORKSPACE_A);

            await store.remove(WORKSPACE_A, GRAPH_URI);

            const state = get(store);
            const cached = state.graphs.get(WORKSPACE_A)?.data;
            expect(cached).toBeDefined();
            expect(
                cached?.some(
                    g =>
                        `${g.uri?.prefix ?? ""}${g.uri?.suffix ?? ""}` ===
                        GRAPH_URI,
                ),
            ).toBe(false);
            expect(
                cached?.some(
                    g =>
                        `${g.uri?.prefix ?? ""}${g.uri?.suffix ?? ""}` ===
                        GRAPH_URI_2,
                ),
            ).toBe(true);
        });

        test("keeps the cache populated after removal (does not invalidate)", async () => {
            vi.mocked(api.deleteGraph).mockResolvedValue({
                data: undefined,
                error: undefined,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(WORKSPACE_A);

            await store.remove(WORKSPACE_A, GRAPH_URI);

            const state = get(store);
            expect(state.graphs.has(WORKSPACE_A)).toBe(true);
        });

        test("returns error and preserves the full cached list on API failure", async () => {
            const error = new Error("Delete failed");
            vi.mocked(api.deleteGraph).mockResolvedValue({
                data: undefined,
                error,
            });
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(WORKSPACE_A);

            const result = await store.remove(WORKSPACE_A, GRAPH_URI);

            expect(result.error).toBe(error);
            expect(toastStore.error).toHaveBeenCalledWith(
                "Delete failed",
                `Could not delete schema "${GRAPH_URI}".`,
            );

            const state = get(store);
            expect(state.graphs.get(WORKSPACE_A)?.data).toEqual(MOCK_GRAPHS);
        });

        test("handles removal when nothing is cached without throwing", async () => {
            vi.mocked(api.deleteGraph).mockResolvedValue({
                data: undefined,
                error: undefined,
            });

            // No prior getGraphs call — cache is empty
            const result = await store.remove(WORKSPACE_A, GRAPH_URI);

            expect(result.error).toBeNull();
            // Cache entry data should be null since there was nothing to filter
            const state = get(store);
            expect(state.graphs.get(WORKSPACE_A)?.data).toBeNull();
        });
    });

    // -------------------------------------------------------------------------
    describe("invalidateWorkspace", () => {
        test("removes the workspace entry from the cache", async () => {
            vi.mocked(api.listGraphs).mockResolvedValue({
                data: MOCK_GRAPHS,
                error: undefined,
            });
            await store.getGraphs(WORKSPACE_A);

            store.invalidateWorkspace(WORKSPACE_A);

            const state = get(store);
            expect(state.graphs.has(WORKSPACE_A)).toBe(false);
        });

        test("does not affect other workspaces", async () => {
            vi.mocked(api.listGraphs)
                .mockResolvedValueOnce({ data: MOCK_GRAPHS, error: undefined })
                .mockResolvedValueOnce({ data: MOCK_GRAPHS, error: undefined });

            await store.getGraphs(WORKSPACE_A);
            await store.getGraphs(WORKSPACE_B);

            store.invalidateWorkspace(WORKSPACE_A);

            const state = get(store);
            expect(state.graphs.has(WORKSPACE_A)).toBe(false);
            expect(state.graphs.has(WORKSPACE_B)).toBe(true);
        });
    });
});
