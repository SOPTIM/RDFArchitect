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

import MigrationArtifacts from "../../src/routes/migrate/steps/MigrationArtifacts.svelte";

const generateMigrationReport = vi.hoisted(() => vi.fn());
const generateMigrationScript = vi.hoisted(() => vi.fn());
const saveFile = vi.hoisted(() => vi.fn());

let mounted = null;
let target = null;
let downloaded = false;

/**
 * Mounts the step with `downloaded` as an accessor pair, exactly as the wizard's
 * `bind:downloaded` would. The wizard reads that flag to decide whether leaving has to be
 * confirmed, so it is the whole contract this step has towards the leave guard.
 */
function render() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(MigrationArtifacts, {
        target,
        props: {
            get downloaded() {
                return downloaded;
            },
            set downloaded(value) {
                downloaded = value;
            },
        },
    });
    return target;
}

function button(label) {
    return [...target.querySelectorAll("button")].find(candidate =>
        candidate.textContent.includes(label),
    );
}

function responseWith(filename) {
    return {
        data: new Blob(["content"]),
        response: { headers: new Headers({ "content-disposition": filename }) },
    };
}

vi.mock("$lib/api/generated/index.ts", () => ({
    generateMigrationReport,
    generateMigrationScript,
}));

vi.mock("$lib/utils/fileUtils.js", () => ({
    saveFile,
    sparqlMediaType: { fileExtension: ".sparql" },
}));

vi.mock("$app/navigation", () => ({ goto: vi.fn() }));

beforeEach(() => {
    generateMigrationScript.mockResolvedValue(responseWith("migration.sparql"));
    generateMigrationReport.mockResolvedValue(
        responseWith("migration_report.md"),
    );
});

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    downloaded = false;
    vi.clearAllMocks();
});

describe("MigrationArtifacts", () => {
    test("nothing counts as downloaded before a button is pressed", () => {
        render();

        expect(downloaded).toBe(false);
    });

    test.each([
        ["Download script", "the migration script"],
        ["Download report", "a report"],
    ])("%s reports back that %s was taken", async label => {
        render();

        button(label).click();

        await vi.waitFor(() => expect(saveFile).toHaveBeenCalled());
        expect(downloaded).toBe(true);
    });

    test("a failed generation does not count as downloaded", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        generateMigrationScript.mockRejectedValue(new Error("backend is down"));
        render();

        button("Download script").click();

        await vi.waitFor(() =>
            expect(generateMigrationScript).toHaveBeenCalled(),
        );
        expect(saveFile).not.toHaveBeenCalled();
        expect(downloaded).toBe(false);
    });
});
