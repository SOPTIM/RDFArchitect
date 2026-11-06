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
import { afterEach, describe, expect, test, vi } from "vitest";

import {
    deleteResources,
    getDeletionImpact,
} from "$lib/api/generated/index.ts";
import { graphStore } from "$lib/stores/graphStore.ts";
import { ontologyStore } from "$lib/stores/ontologyStore.ts";

import DeleteDependenciesDialog from "../../src/routes/delete-relations-dialog/DeleteDependenciesDialog.svelte";

const WORKSPACE = "cgmes";
const GRAPH = "http://graph#Equipment";

/** Deleting a profile header: the resource the navigation takes a schema's name from. */
const HEADER = {
    resourceIdentifier: { uuid: "uuid-ontology", label: "Equipment" },
    type: "ONTOLOGY",
    reason: "SELECTED",
    actions: ["DELETE"],
    children: [],
};

let mounted = null;
let target = null;

/** The dialog's own Delete, not the per-resource action of the same name above it. */
function primaryDeleteButton() {
    return [...document.querySelectorAll("button")]
        .filter(candidate => candidate.textContent.trim() === "Delete")
        .at(-1);
}

async function deleteHeader() {
    getDeletionImpact.mockResolvedValue({
        data: { [HEADER.resourceIdentifier.uuid]: HEADER },
    });
    deleteResources.mockResolvedValue({ error: undefined });

    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(DeleteDependenciesDialog, {
        target,
        props: {
            showDialog: true,
            workspaceName: WORKSPACE,
            graphUri: GRAPH,
            resourceUuid: HEADER.resourceIdentifier.uuid,
        },
    });
    await vi.waitFor(() =>
        expect(document.body.textContent).toContain('"Equipment"'),
    );

    primaryDeleteButton().click();
    await vi.waitFor(() => expect(deleteResources).toHaveBeenCalled());
}

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));

vi.mock("$lib/api/generated/index.ts", () => ({
    getDeletionImpact: vi.fn(),
    deleteResources: vi.fn(),
}));
vi.mock("$lib/stores/graphStore.ts", () => ({
    graphStore: { invalidateWorkspace: vi.fn() },
}));
vi.mock("$lib/stores/ontologyStore.ts", () => ({
    ontologyStore: { invalidateGraph: vi.fn() },
}));
vi.mock("$lib/stores/classStore.ts", () => ({
    classStore: { invalidateGraph: vi.fn() },
}));
vi.mock("$lib/stores/packageStore.ts", () => ({
    packageStore: { invalidateGraph: vi.fn() },
}));
vi.mock("$lib/stores/crossProfileStore.ts", () => ({
    crossProfileStore: { invalidateWorkspace: vi.fn() },
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.clearAllMocks();
});

describe("DeleteDependenciesDialog", () => {
    test("forgets the schema's name once the header naming it is deleted", async () => {
        await deleteHeader();

        await vi.waitFor(() =>
            expect(graphStore.invalidateWorkspace).toHaveBeenCalledWith(
                WORKSPACE,
            ),
        );
        expect(ontologyStore.invalidateGraph).toHaveBeenCalledWith(
            WORKSPACE,
            GRAPH,
        );
    });
});
