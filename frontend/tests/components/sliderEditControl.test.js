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
import { SvelteMap } from "svelte/reactivity";
import { afterEach, describe, expect, test } from "vitest";

import SliderEditControl from "$lib/components/SliderEditControl.svelte";

let mounted = null;
let target = null;

function render(props) {
    const bound = new SvelteMap([["value", props.value]]);
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(SliderEditControl, {
        target,
        props: {
            ...props,
            get value() {
                return bound.get("value");
            },
            set value(next) {
                bound.set("value", next);
            },
        },
    });
    flushSync();
    return {
        value: () => bound.get("value"),
        field: target.querySelector('input[type="text"]'),
        range: target.querySelector('input[type="range"]'),
    };
}

function type(field, text) {
    field.value = text;
    field.dispatchEvent(new Event("input", { bubbles: true }));
    flushSync();
}

function leave(field) {
    field.dispatchEvent(new Event("change", { bubbles: true }));
    flushSync();
}

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("SliderEditControl", () => {
    test("keeps the typed text when the effects run before the input handler", () => {
        const { value, field } = render({
            value: 60,
            min: 0,
            max: 100,
            step: 10,
        });
        field.addEventListener("input", () => flushSync());

        type(field, "6");
        expect(field.value).toBe("6");
        expect(value()).toBe(6);

        type(field, "");
        expect(field.value).toBe("");
        expect(value()).toBe("");
    });

    test("rounds a typed value when leaving the field and shows it between the steps", () => {
        const { value, field, range } = render({
            value: 60,
            min: 0,
            max: 100,
            step: 10,
        });

        type(field, "25.456");
        expect(field.value).toBe("25.456");
        leave(field);

        expect(value()).toBe(25.46);
        expect(field.value).toBe("25.46");
        expect(range.value).toBe("25.46");
    });

    test("keeps invalid input in the field", () => {
        const { value, field } = render({
            value: 60,
            min: 0,
            max: 100,
            step: 10,
        });

        type(field, "150");
        leave(field);
        expect(field.value).toBe("150");
        expect(value()).toBe(150);

        type(field, "abc");
        leave(field);
        expect(field.value).toBe("abc");
        expect(value()).toBe("abc");
    });

    test("snaps to the steps when sliding and moves with the arrow keys", () => {
        const { value, field, range } = render({
            value: 60,
            min: 0,
            max: 100,
            step: 10,
        });

        range.value = "33";
        range.dispatchEvent(new Event("input", { bubbles: true }));
        flushSync();
        expect(value()).toBe(30);
        expect(field.value).toBe("30");

        range.dispatchEvent(
            new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }),
        );
        flushSync();
        expect(value()).toBe(40);
    });

    test("shows the label of the selected option instead of a text field", () => {
        const options = [
            { value: "low", label: "Low" },
            { value: "high", label: "High" },
        ];
        const { value, field, range } = render({ value: "low", options });

        expect(field).toBeNull();
        expect(target.textContent).toContain("Low");

        range.dispatchEvent(
            new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }),
        );
        flushSync();
        expect(value()).toBe("high");
        expect(target.textContent).toContain("High");
    });
});
