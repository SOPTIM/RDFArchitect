/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import { flushSync, mount, unmount } from "svelte";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { search } from "$lib/api/generated/index.ts";
import { shortcutStore } from "$lib/eventhandling/shortcutStore.svelte.js";
import { editorState } from "$lib/sharedState.svelte.js";
import { navigateToClass } from "$lib/utils/model-navigation.js";

import Searchbar from "../../src/routes/Searchbar.svelte";

/** A class as the search endpoint reports it. */
const RESULT = {
    type: "CLASS",
    uuid: "c-1",
    workspaceName: "Draft",
    schemaName: "Core",
    packageUUID: "p-1",
    packageLabel: { value: "Core" },
    label: { value: "Terminal" },
};

let mounted = null;
let target = null;

async function settle() {
    for (let i = 0; i < 3; i++) {
        await new Promise(resolve => setTimeout(resolve, 0));
        flushSync();
    }
}

function render() {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(Searchbar, { target });
    flushSync();
}

const toggle = () => target.querySelector("button[aria-controls]");
const form = () => target.querySelector("form");
const input = () => target.querySelector("input");
const scopeButton = () =>
    target.querySelector('button[aria-label^="Search in"]');
const results = () => target.querySelectorAll('[role="button"]');

function pressEscape(element) {
    const event = new KeyboardEvent("keydown", {
        key: "Escape",
        code: "Escape",
        bubbles: true,
        cancelable: true,
    });
    element.dispatchEvent(event);
    flushSync();
    return event;
}

async function typeQuery(text) {
    input().value = text;
    input().dispatchEvent(new Event("input", { bubbles: true }));
    await settle();
}

vi.mock("$lib/config/runtime", () => ({ PUBLIC_BACKEND_URL: "" }));

vi.mock("$lib/api/generated/index.ts", () => ({ search: vi.fn() }));

vi.mock("$lib/utils/model-navigation.js", () => ({
    navigateToClass: vi.fn(),
    navigateToPackage: vi.fn(),
}));

beforeEach(() => {
    vi.mocked(search).mockReset();
    vi.mocked(search).mockResolvedValue({
        data: { internalSearchResults: [RESULT], externalSearchResults: [] },
        error: null,
    });
    vi.mocked(navigateToClass).mockReset();
    editorState.reset();
});

afterEach(() => {
    if (mounted) unmount(mounted);
    mounted = null;
    target?.remove();
});

describe("Searchbar folded into a button", () => {
    test("openSearch_buttonClickedThenEscape_focusesFieldAndClosesAgain", async () => {
        render();
        expect(toggle().getAttribute("aria-expanded")).toBe("false");

        toggle().click();
        await settle();

        expect(toggle().getAttribute("aria-expanded")).toBe("true");
        expect(form().className).toContain("max-md:absolute");
        expect(document.activeElement).toBe(input());

        const escape = pressEscape(input());

        expect(escape.defaultPrevented).toBe(true);
        expect(toggle().getAttribute("aria-expanded")).toBe("false");
        expect(form().className).toContain("max-md:hidden");
        expect(document.activeElement).toBe(toggle());
    });

    test("openSearch_ctrlF_opensWithFieldFocused", async () => {
        render();

        shortcutStore.handleEvent(
            new KeyboardEvent("keydown", {
                key: "f",
                code: "KeyF",
                ctrlKey: true,
                cancelable: true,
            }),
        );
        await settle();

        expect(toggle().getAttribute("aria-expanded")).toBe("true");
        expect(document.activeElement).toBe(input());
    });

    test("handleKeydown_escapeWithResults_takesBackResultsBeforeField", async () => {
        render();
        toggle().click();
        flushSync();
        await typeQuery("Term");
        expect(results()).toHaveLength(1);

        pressEscape(input());

        expect(results()).toHaveLength(0);
        expect(toggle().getAttribute("aria-expanded")).toBe("true");

        pressEscape(input());

        expect(toggle().getAttribute("aria-expanded")).toBe("false");
    });

    test("chooseResult_resultClicked_closesSearchbar", async () => {
        render();
        toggle().click();
        flushSync();
        await typeQuery("Term");

        results()[0].click();
        flushSync();

        expect(navigateToClass).toHaveBeenCalledWith(
            expect.objectContaining({ classUUID: "c-1" }),
        );
        expect(toggle().getAttribute("aria-expanded")).toBe("false");
    });
});

describe("Searchbar field", () => {
    test("handleKeydown_escapeWithNothingToTakeBack_leavesEscToTheEditor", () => {
        render();

        expect(pressEscape(input()).defaultPrevented).toBe(false);
    });

    test("handleKeydown_escapeAfterCtrlFInAWideWindow_leavesEscToTheEditor", async () => {
        vi.stubGlobal("matchMedia", () => ({ matches: true }));
        try {
            render();
            shortcutStore.handleEvent(
                new KeyboardEvent("keydown", {
                    key: "f",
                    code: "KeyF",
                    ctrlKey: true,
                    cancelable: true,
                }),
            );
            await settle();
            expect(document.activeElement).toBe(input());

            expect(pressEscape(input()).defaultPrevented).toBe(false);
            expect(toggle().getAttribute("aria-expanded")).toBe("false");
        } finally {
            vi.unstubAllGlobals();
        }
    });

    test("chooseFilter_currentSchemaChosen_namesScopeBrieflyAndSearchesWithinIt", async () => {
        editorState.selectGraph("Draft", "Core");
        render();
        expect(scopeButton().textContent.trim()).toBe("All");
        await typeQuery("Term");

        scopeButton().click();
        flushSync();
        [...target.querySelectorAll("button")]
            .find(button => button.textContent.trim() === "Current Schema")
            .click();
        await settle();

        expect(scopeButton().textContent.trim()).toBe("Schema");
        expect(scopeButton().getAttribute("aria-label")).toBe(
            "Search in: Current Schema",
        );
        expect(search).toHaveBeenLastCalledWith({
            query: { query: "Term" },
            body: {
                workspaceName: "Draft",
                schemaName: "Core",
                packageUUID: null,
            },
        });
        expect(document.activeElement).toBe(input());
    });
});
