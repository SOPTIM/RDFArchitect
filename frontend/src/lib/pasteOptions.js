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

export const DEFAULT_PASTE_OPTIONS = Object.freeze({
    copyAsAbstract: false,
    copyAttributes: true,
    copyAssociations: true,
    copyInheritance: true,
    referencesToCopy: [],
});

export const PASTE_REFERENCE_KINDS = Object.freeze([
    { kind: "DATA_TYPE", option: "copyAttributes" },
    { kind: "ASSOCIATION_TARGET", option: "copyAssociations" },
    { kind: "SUPER_CLASS", option: "copyInheritance" },
]);

export const PASTE_VARIANTS = Object.freeze([
    {
        id: "paste",
        label: "Paste",
        shortcut: "Ctrl+V",
        keys: ["ctrl", "v"],
        options: DEFAULT_PASTE_OPTIONS,
    },
    {
        id: "pasteWithoutAttributes",
        label: "Paste without Attributes/Enum Entries",
        shortcut: "Ctrl+Shift+V",
        keys: ["ctrl", "shift", "v"],
        options: { ...DEFAULT_PASTE_OPTIONS, copyAttributes: false },
    },
    {
        id: "pasteWithoutAssociations",
        label: "Paste without Associations",
        shortcut: "Ctrl+Alt+V",
        keys: ["ctrl", "alt", "v"],
        options: { ...DEFAULT_PASTE_OPTIONS, copyAssociations: false },
    },
    {
        id: "pasteBare",
        label: "Paste Bare",
        shortcut: "Ctrl+Shift+Alt+V",
        keys: ["ctrl", "shift", "alt", "v"],
        options: {
            ...DEFAULT_PASTE_OPTIONS,
            copyAsAbstract: true,
            copyAttributes: false,
            copyAssociations: false,
            copyInheritance: false,
        },
    },
]);
