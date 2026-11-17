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

import { expect, test } from "vitest";

import { DARK_THEME, LIGHT_THEME, THEMES } from "$lib/monaco/theme.js";

/** WCAG's minimum for text this size. */
const READABLE = 4.5;

function luminance(hex) {
    const [r, g, b] = [0, 2, 4].map(start => {
        const channel = parseInt(hex.slice(start, start + 2), 16) / 255;
        return channel <= 0.03928
            ? channel / 12.92
            : ((channel + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a, b) {
    const [lighter, darker] = [luminance(a), luminance(b)].sort(
        (x, y) => y - x,
    );
    return (lighter + 0.05) / (darker + 0.05);
}

test.each([LIGHT_THEME, DARK_THEME])(
    "every token of %s can be read on its background",
    name => {
        const theme = THEMES[name];
        const background = theme.colors["editor.background"].slice(1);
        const unreadable = theme.rules
            .filter(rule => contrast(rule.foreground, background) < READABLE)
            .map(rule => rule.token);

        expect(unreadable).toEqual([]);
    },
);

test.each([LIGHT_THEME, DARK_THEME])(
    "the line numbers of %s can be read on the gutter",
    name => {
        const { colors } = THEMES[name];
        const gutter = colors["editorGutter.background"].slice(1);

        for (const key of [
            "editorLineNumber.foreground",
            "editorLineNumber.activeForeground",
        ]) {
            expect(
                contrast(colors[key].slice(1), gutter),
            ).toBeGreaterThanOrEqual(READABLE);
        }
    },
);
