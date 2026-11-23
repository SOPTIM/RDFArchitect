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
import { flushSync, mount, unmount } from "svelte";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { ShapesWorkbench } from "$lib/shacl/workbenchState.svelte.js";

import DocumentInspector from "../../src/routes/shacl/workbench/DocumentInspector.svelte";

const DOCUMENT = "document-id";

let server;
let mounted = null;
let target = null;

/**
 * A server holding one document, which applies the PATCHes it is sent in the order they arrive.
 * `hold` keeps every PATCH waiting until released, as a slow network does.
 */
function fakeServer() {
    const server = {
        document: {
            id: DOCUMENT,
            name: "doc.ttl",
            order: 0,
            enabled: true,
            tripleCount: 1,
            schemas: ["EQ"],
        },
        patches: [],
        gate: null,
    };
    server.fetch = async request => {
        const url = new URL(request.url);
        const json = value =>
            new Response(JSON.stringify(value), {
                status: 200,
                headers: { "content-type": "application/json" },
            });
        if (request.method === "PATCH") {
            const schemas = url.searchParams.getAll("schema");
            server.patches.push(schemas);
            await server.gate;
            server.document = {
                ...server.document,
                schemas: schemas.filter(name => name !== ""),
            };
            return json(server.document);
        }
        if (url.pathname.endsWith("/shacl/documents")) {
            return json([server.document]);
        }
        return new Response("", {
            status: 200,
            headers: { "content-type": "text/plain" },
        });
    };
    return server;
}

function checkbox(label) {
    const labelElement = [...target.querySelectorAll("label")].find(
        candidate => candidate.textContent.trim() === label,
    );
    return target.querySelector(`[id="${labelElement.htmlFor}"]`);
}

function toggle(label) {
    const box = checkbox(label);
    box.checked = !box.checked;
    box.dispatchEvent(new Event("change", { bubbles: true }));
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));
vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: {
        getGraphs: vi.fn(async () => [
            { name: "EQ", keyword: "EQ" },
            { name: "SSH", keyword: "SSH" },
            { name: "TP", keyword: "TP" },
        ]),
    },
}));
vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { isReadOnly: vi.fn(async () => false) },
}));

beforeEach(async () => {
    server = fakeServer();
    const workbench = new ShapesWorkbench({
        workspaceName: "cgmes",
        preferredSchema: "EQ",
        requestOptions: { fetch: server.fetch },
    });
    await workbench.load();
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(DocumentInspector, { target, props: { workbench } });
    flushSync();
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("which schemas a document applies to", () => {
    test("takes two ticks in quick succession both", async () => {
        let release;
        server.gate = new Promise(resolve => (release = resolve));

        toggle("SSH");
        toggle("TP");
        release();

        await vi.waitFor(() =>
            expect(server.document.schemas).toEqual(["EQ", "SSH", "TP"]),
        );
        await vi.waitFor(() => expect(checkbox("TP").checked).toBe(true));
        expect(checkbox("SSH").checked).toBe(true);
    });

    test("takes a tick and an untick in quick succession both", async () => {
        let release;
        server.gate = new Promise(resolve => (release = resolve));

        toggle("SSH");
        toggle("EQ");
        release();

        await vi.waitFor(() =>
            expect(server.document.schemas).toEqual(["SSH"]),
        );
    });
});
