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

import SnapshotDialog from "../../src/routes/SnapshotDialog.svelte";

const WORKSPACES = vi.hoisted(() => [
    { label: "cgmes", readOnly: false, prefixes: [] },
    { label: "nc", readOnly: false, prefixes: [] },
]);

const createSnapshot = vi.hoisted(() => vi.fn());
const getWorkspaces = vi.hoisted(() => vi.fn());

let mounted = null;
let target = null;

function dialog() {
    return document.querySelector("[role='dialog']");
}

function workspaceSelect() {
    return dialog().querySelector("select");
}

function primaryButton() {
    return [...dialog().querySelectorAll("button")].find(
        button => button.textContent.trim() === "Share Snapshot",
    );
}

async function render(props = {}) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(SnapshotDialog, {
        target,
        props: { showDialog: true, ...props },
    });
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
    return target;
}

vi.mock("$lib/api/generated/index.ts", () => ({ createSnapshot }));

vi.mock("$lib/stores/workspaceStore.ts", () => ({
    workspaceStore: { getWorkspaces },
}));

beforeEach(() => {
    createSnapshot.mockResolvedValue({ data: "a-token", error: undefined });
    getWorkspaces.mockResolvedValue(WORKSPACES);
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    vi.clearAllMocks();
});

describe("SnapshotDialog", () => {
    test("offers the known workspaces when none is locked", async () => {
        await render();

        await vi.waitFor(() =>
            expect(workspaceSelect().options).toHaveLength(
                WORKSPACES.length + 1,
            ),
        );
        expect(
            [...workspaceSelect().options].slice(1).map(o => o.value),
        ).toEqual(["cgmes", "nc"]);
    });

    test("stays usable when the workspaces cannot be loaded", async () => {
        getWorkspaces.mockResolvedValue(null);

        await render();

        await vi.waitFor(() => expect(workspaceSelect()).not.toBeNull());
        expect(workspaceSelect().disabled).toBe(true);
    });

    test("sends the workspace name as an unquoted request body", async () => {
        await render({ lockedWorkspaceName: "cgmes" });

        primaryButton().click();

        await vi.waitFor(() => expect(createSnapshot).toHaveBeenCalledTimes(1));
        const options = createSnapshot.mock.calls[0][0];
        expect(options.body).toBe("cgmes");
        expect(options.bodySerializer).toBeNull();
    });

    test("builds the share link from the returned token", async () => {
        await render({ lockedWorkspaceName: "cgmes" });

        primaryButton().click();

        await vi.waitFor(() =>
            expect(dialog().textContent).toContain("?snapshot=a-token"),
        );
    });
});
