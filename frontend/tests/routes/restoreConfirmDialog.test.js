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
import { afterEach, describe, expect, test, vi } from "vitest";

import RestoreConfirmDialog from "../../src/routes/changelog/RestoreConfirmDialog.svelte";

const GRAPH_A = "http://example.org/a";
const GRAPH_B = "http://example.org/b";

/** A change in schema A, taken back by a restore of A. */
const inA = {
    changeId: "in-a",
    message: "edited A",
    affectedKinds: ["rdf"],
    restorableGraphUris: [GRAPH_A],
};

/** A change in schema B, which a restore held to A leaves alone. */
const inB = {
    changeId: "in-b",
    message: "edited B",
    affectedKinds: ["rdf"],
    restorableGraphUris: [GRAPH_B],
};

let mounted = null;
let target = null;

function dialog() {
    return document.querySelector("[role='alertdialog'], [role='dialog']");
}

function button(text) {
    return [...dialog().querySelectorAll("button")].find(
        each => each.textContent.trim() === text,
    );
}

/** The dialog's text with the markup's line breaks collapsed. */
function text() {
    return dialog().textContent.replace(/\s+/g, " ");
}

function listed() {
    return [...dialog().querySelectorAll("li")].map(item =>
        item.textContent.replace(/\s+/g, " ").trim(),
    );
}

async function render(given) {
    const props = { onConfirm: vi.fn(), onCancel: vi.fn(), ...given };
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(RestoreConfirmDialog, { target, props });
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
    return props;
}

function requestTo(restoreTargetGraphUris) {
    return {
        change: {
            changeId: "target",
            message: "the target",
            restoreTargetGraphUris,
        },
        takenBack: [inB, inA],
        undoneAhead: 0,
    };
}

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("RestoreConfirmDialog", () => {
    test("held to the schema, lists only what it takes back there", async () => {
        const props = await render({
            request: requestTo([GRAPH_A]),
            graphUri: GRAPH_A,
        });

        expect(listed()).toEqual(["edited A (Schema)"]);
        button("Restore").click();

        expect(props.onConfirm).toHaveBeenCalledWith([GRAPH_A]);
    });

    test("cleared, restores the whole workspace and lists every change", async () => {
        const props = await render({
            request: requestTo([GRAPH_A]),
            graphUri: GRAPH_A,
        });

        dialog().querySelector("input[type='checkbox']").click();
        flushSync();

        expect(listed()).toHaveLength(2);
        button("Restore").click();
        expect(props.onConfirm).toHaveBeenCalledWith([]);
    });

    test("for a schema created after the target, offers only the whole workspace", async () => {
        // Holding the restore to the schema could only be refused: it has no
        // state at that version to go back to.
        const props = await render({
            request: requestTo([GRAPH_B]),
            graphUri: GRAPH_A,
        });

        expect(dialog().querySelector("input[type='checkbox']")).toBeNull();
        expect(text()).toContain(
            "This schema did not exist yet at that version",
        );
        expect(listed()).toHaveLength(2);
        button("Restore").click();
        expect(props.onConfirm).toHaveBeenCalledWith([]);
    });

    test("warns that a schema restore drops what could be redone", async () => {
        await render({
            request: { ...requestTo([GRAPH_A]), undoneAhead: 2 },
            graphUri: GRAPH_A,
        });

        expect(text()).toContain(
            "The 2 changes ahead of the workspace cannot be redone afterwards.",
        );
    });

    test("in the workspace log, offers no schema restore at all", async () => {
        const props = await render({ request: requestTo([GRAPH_A]) });

        expect(dialog().querySelector("input[type='checkbox']")).toBeNull();
        button("Restore").click();
        expect(props.onConfirm).toHaveBeenCalledWith([]);
    });
});
