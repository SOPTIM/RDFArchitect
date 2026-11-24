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
import { afterEach, beforeAll, describe, expect, test, vi } from "vitest";

import DiagramInProvider from "./DiagramInProvider.svelte";

beforeAll(() => {
    vi.stubGlobal(
        "ResizeObserver",
        class {
            observe() {}
            unobserve() {}
            disconnect() {}
        },
    );
});

afterEach(() => {
    vi.restoreAllMocks();
    document.body.innerHTML = "";
});

describe("a diagram inside a SvelteFlowProvider", () => {
    // Leaving the main page destroys the diagram. If that throws, SvelteKit shows its error page
    // instead of the page the user went to.
    test("is destroyed without an error", async () => {
        const errors = vi.spyOn(console, "error").mockImplementation(() => {});
        const target = document.createElement("div");
        document.body.appendChild(target);
        const diagram = mount(DiagramInProvider, {
            target,
            props: {
                nodes: [
                    { id: "a", position: { x: 0, y: 0 }, data: { label: "A" } },
                ],
            },
        });
        flushSync();

        await expect(
            Promise.resolve(unmount(diagram)),
        ).resolves.toBeUndefined();
        expect(errors).not.toHaveBeenCalled();
    });
});
