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

import { mount, unmount } from "svelte";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { graphColors } from "$lib/graphColors.svelte.js";
import { graphStore } from "$lib/stores/graphStore.ts";

import SchemaColorsDialog from "../../src/routes/mainpage/packageNavigation/SchemaColorsDialog.svelte";

const CURRENT = {
    uri: { prefix: "http://graph#", suffix: "Equipment" },
    keyword: "EQ",
    label: "Core Equipment Vocabulary",
};

/** Two legacy profiles reading alike, as the official equipment profiles do. */
const LEGACY_CORE = {
    uri: { prefix: "http://graph#", suffix: "EquipmentCore" },
    keyword: "EQ",
    label: "EquipmentProfile",
};

const LEGACY_OPERATION = {
    uri: { prefix: "http://graph#", suffix: "EquipmentOperation" },
    keyword: "EQ",
    label: "EquipmentProfile",
};

let mounted = null;
let target = null;

async function open() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(SchemaColorsDialog, {
        target,
        props: { showDialog: true, workspaceName: "cgmes" },
    });
    await vi.waitFor(() => expect(names().length).toBe(3));
}

function names() {
    return [...document.querySelectorAll("p.font-medium")].map(p =>
        p.textContent.trim(),
    );
}

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));

vi.mock("$lib/graphColors.svelte.js", () => ({
    graphColors: { reload: vi.fn(), replaceAll: vi.fn() },
}));

vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { getGraphs: vi.fn() },
}));

beforeEach(() => {
    graphColors.reload.mockResolvedValue({
        "http://graph#EquipmentOperation": "#112233",
        "http://graph#Equipment": "#445566",
        "http://graph#EquipmentCore": "#778899",
    });
    graphStore.getGraphs.mockResolvedValue([
        CURRENT,
        LEGACY_CORE,
        LEGACY_OPERATION,
    ]);
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.clearAllMocks();
});

describe("SchemaColorsDialog", () => {
    /**
     * A color is picked for a schema, so the schema has to be named the way the tree names it —
     * the tail of the graph URI is a different name for the same thing.
     */
    test("names and orders the schemas the way the navigation does", async () => {
        await open();

        expect(names()).toEqual([
            "Core Equipment Vocabulary",
            "EquipmentProfile (EquipmentCore)",
            "EquipmentProfile (EquipmentOperation)",
        ]);
    });

    test("falls back to the graph for a schema the list does not know", async () => {
        graphStore.getGraphs.mockResolvedValue([]);

        await open();

        expect(names()).toEqual([
            "Equipment",
            "EquipmentCore",
            "EquipmentOperation",
        ]);
    });
});
