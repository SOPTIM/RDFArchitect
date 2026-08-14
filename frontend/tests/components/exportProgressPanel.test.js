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
import { afterEach, describe, expect, test } from "vitest";

import ExportProgressPanel from "$lib/components/ExportProgressPanel.svelte";
import {
    ExportProgress,
    PackageStatus,
} from "$lib/utils/exportProgress.svelte.js";

let mounted = null;
let target = null;

function render(progress) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(ExportProgressPanel, { target, props: { progress } });
    return target;
}

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("ExportProgressPanel", () => {
    test("shows the running stage without a package list", () => {
        const panel = render(new ExportProgress());

        expect(panel.textContent).toContain("Generating the document…");
        expect(panel.querySelector('[role="progressbar"]')).not.toBeNull();
        expect(panel.textContent).not.toContain("packages");
    });

    test("shows the progress and one row per package", () => {
        const progress = new ExportProgress();
        progress.documentReady();
        progress.startDiagrams([
            { uuid: "a", label: "Core" },
            { uuid: "b", label: "Wires" },
            { uuid: "c", label: "Meas" },
        ]);
        progress.packageFinished("a", PackageStatus.DONE);
        progress.packageFinished("b", PackageStatus.FAILED);
        progress.packageStarted("c");

        const panel = render(progress);
        const bar = panel.querySelector('[role="progressbar"]');

        // two of three packages finished: 10 % for the document, two thirds of 80 %
        expect(bar.getAttribute("aria-valuenow")).toBe("63");
        expect(panel.textContent).toContain("2 of 3 packages");
        expect(panel.textContent).toContain("Core");
        expect(panel.textContent).toContain("Wires");
        expect(panel.textContent).toContain("failed");
    });

    test("marks a package without classes as such rather than as a failure", () => {
        const progress = new ExportProgress();
        progress.startDiagrams([{ uuid: "a", label: "Empty" }]);
        progress.packageFinished("a", PackageStatus.EMPTY);

        const panel = render(progress);

        expect(panel.textContent).toContain("no classes");
        expect(panel.textContent).not.toContain("failed");
    });
});
