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

import NavigationEntry from "$lib/components/navigation/NavigationEntry.svelte";

let mounted = null;
let target = null;

function render(props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(NavigationEntry, { target, props });
    return target.querySelector("button.nav-entry");
}

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("NavigationEntry", () => {
    /**
     * A class entry shows the shortened IRI on hover and has a tooltip of its own; letting one
     * win dropped the other without anything saying so.
     */
    test("says both of the things it was given to say on hover", () => {
        const entry = render({
            label: "ACLineSegment",
            highlightLabel: "cim:ACLineSegment",
            title: "http://iec.ch/TC57/CIM100#ACLineSegment",
        });

        expect(entry.getAttribute("title")).toBe(
            "cim:ACLineSegment\nhttp://iec.ch/TC57/CIM100#ACLineSegment",
        );
    });

    test("says it once when both are the same", () => {
        const entry = render({
            label: "ACLineSegment",
            highlightLabel: "http://iec.ch/TC57/CIM100#ACLineSegment",
            title: "http://iec.ch/TC57/CIM100#ACLineSegment",
        });

        expect(entry.getAttribute("title")).toBe(
            "http://iec.ch/TC57/CIM100#ACLineSegment",
        );
    });

    test("has no tooltip when there is nothing to say", () => {
        const entry = render({ label: "ACLineSegment" });

        expect(entry.getAttribute("title")).toBeNull();
    });
});
