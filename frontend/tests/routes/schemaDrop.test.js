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

import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";

import {
    acceptNewWorkspaceDrop,
    acceptSchemaDrop,
} from "../../src/routes/mainpage/schemaDrop.js";

function drop(fileNames, directoryNames = []) {
    return {
        files: fileNames.map(name => ({ name })),
        directoryNames,
    };
}

function refusalBody() {
    return toastStore.error.mock.calls.at(-1)[1];
}

vi.mock("$lib/eventhandling/toastStore.svelte.js", () => ({
    toastStore: { success: vi.fn(), error: vi.fn() },
}));

beforeEach(() => {
    vi.clearAllMocks();
});

describe("acceptSchemaDrop", () => {
    test("takes a drop holding a schema", () => {
        const items = drop(["EQ.rdf", "notes.txt"]);

        expect(acceptSchemaDrop(items)).toBe(items);
        expect(toastStore.error).not.toHaveBeenCalled();
    });

    test("takes a drop holding an archive", () => {
        const items = drop(["profiles.zip"]);

        expect(acceptSchemaDrop(items)).toBe(items);
    });

    test("refuses a drop holding nothing importable", () => {
        expect(acceptSchemaDrop(drop(["notes.txt"]))).toBeNull();
        expect(refusalBody()).toContain("is a schema RDFArchitect can read");
    });

    test("names the ZIP way out when folders were dropped", () => {
        expect(acceptSchemaDrop(drop([], ["profiles"]))).toBeNull();
        expect(refusalBody()).toContain("ZIP archive");
    });

    test("sends an export to the new-workspace button rather than refusing it blankly", () => {
        expect(acceptSchemaDrop(drop(["cgmes-3.0.xmi"]))).toBeNull();
        expect(refusalBody()).toContain("+ beside the workspace tabs");
    });
});

describe("acceptNewWorkspaceDrop", () => {
    test("reads an export dropped on the button", () => {
        const items = drop(["cgmes-3.0.xmi"]);

        expect(acceptNewWorkspaceDrop(items)).toEqual({
            kind: "export",
            file: items.files[0],
        });
        expect(toastStore.error).not.toHaveBeenCalled();
    });

    test("reads an export exported under .xml as well", () => {
        expect(acceptNewWorkspaceDrop(drop(["model.xml"])).kind).toBe("export");
    });

    test("imports schemas as schemas", () => {
        const items = drop(["EQ.rdf"]);

        expect(acceptNewWorkspaceDrop(items)).toEqual({
            kind: "schemas",
            items,
        });
    });

    test("lets schemas win a drop holding both, as only they have somewhere to go", () => {
        expect(acceptNewWorkspaceDrop(drop(["EQ.rdf", "model.xmi"])).kind).toBe(
            "schemas",
        );
    });

    test("takes the first export of several, one being read at a time", () => {
        const items = drop(["first.xmi", "second.xmi"]);

        expect(acceptNewWorkspaceDrop(items).file).toBe(items.files[0]);
    });

    test("refuses a drop holding neither", () => {
        expect(acceptNewWorkspaceDrop(drop(["notes.txt"]))).toBeNull();
        expect(refusalBody()).toContain("is a schema RDFArchitect can read");
    });
});
