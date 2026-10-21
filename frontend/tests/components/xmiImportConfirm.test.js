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

import XmiImportConfirmHost from "./XmiImportConfirmHost.svelte";

const MODEL_NS = "http://iec.ch/TC57/CIM#";
const EXTENSION_NS = "http://entsoe.eu/CIM/SchemaExtension/3/1#";

const CHOICE = { schemas: [{}], classes: 12, diagramIds: ["one"] };

let mounted = null;
let target = null;

/** The read as the dialog hands it over: one package, and the export's prefix table. */
function read() {
    return {
        packages: [
            {
                modelNamespace: MODEL_NS,
                extensionNamespace: EXTENSION_NS,
                hasExtensionElements: true,
            },
        ],
        prefixes: [
            { fileIndex: 0, prefix: "cim", namespace: MODEL_NS },
            { fileIndex: 0, prefix: "eu", namespace: EXTENSION_NS },
            { fileIndex: 0, prefix: "dcat", namespace: "http://dcat#" },
            { fileIndex: 0, prefix: "prov", namespace: "http://prov#" },
        ],
    };
}

function render(props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(XmiImportConfirmHost, {
        target,
        props: { read: read(), choice: CHOICE, ...props },
    });
    flushSync();
    return target;
}

/** Opens the list of prefixes the import carries over, which starts closed. */
function openCarriedOver(node) {
    [...node.querySelectorAll("button")]
        .find(button => button.textContent.includes("carried over"))
        .click();
    flushSync();
}

function textOf(node) {
    return node.textContent.replace(/\s+/g, " ").trim();
}

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("XmiImportConfirm", () => {
    test("counts the prefixes carried over beside the two decided here", () => {
        const confirm = render({});

        expect(textOf(confirm)).toContain(
            "2 further prefixes are carried over",
        );
    });

    test("lists them with their namespaces once opened", () => {
        // Naming a few of them raises the question which the others are without answering it.
        const confirm = render({});

        expect(textOf(confirm)).not.toContain("http://dcat#");

        openCarriedOver(confirm);

        expect(textOf(confirm)).toContain("dcat http://dcat#");
        expect(textOf(confirm)).toContain("prov");
    });

    test("counts a decided namespace's prefix as carried over once it is overruled", () => {
        // Overruling the model namespace does not take cim: out of the table; it leaves it as one
        // more declaration the workspace takes over, which is exactly what this line is about.
        const confirm = render({ modelNamespace: "http://example.com/mine#" });

        expect(textOf(confirm)).toContain(
            "3 further prefixes are carried over",
        );

        openCarriedOver(confirm);

        expect(textOf(confirm)).toContain("cim");
    });

    test("says nothing where the export carries no prefix table", () => {
        const confirm = render({ read: { ...read(), prefixes: [] } });

        expect(textOf(confirm)).not.toContain("carried over");
    });
});
