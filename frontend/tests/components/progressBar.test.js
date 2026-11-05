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
import { afterEach, describe, expect, test } from "vitest";

import ProgressBar from "../../src/lib/components/ProgressBar.svelte";

let mounted = null;
let target = null;

function render(percent) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(ProgressBar, {
        target,
        props: { label: "Reading the export", percent, ariaLabel: "Progress" },
    });
    flushSync();
    return target;
}

function sheen() {
    return target.querySelector(".sheen");
}

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("ProgressBar", () => {
    test("sweeps a highlight across the bar while there is work left", () => {
        // A step that takes a while sits on the same percentage for seconds, where a bar that
        // only has a width reads as stuck.
        render(40);

        expect(sheen()).not.toBeNull();
    });

    test("comes to rest once it is complete", () => {
        render(100);

        expect(sheen()).toBeNull();
    });

    test("stays within what it has reached", () => {
        render(40);

        expect(target.querySelector("[role='progressbar']").textContent).toBe(
            "",
        );
        expect(sheen().closest("div").style.width).toBe("40%");
    });
});
