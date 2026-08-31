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
import { afterEach, describe, expect, test, vi } from "vitest";

import { ReactiveAttribute } from "$lib/models/reactive/models/reactive-attribute.svelte.js";

import SHACLShapeTtlRenderer from "../../src/routes/shacl/shaclclassspecific/SHACLShapeTtlRenderer.svelte";
import SHACLPropertySpecificDialog from "../../src/routes/shacl/SHACLPropertySpecificDialog.svelte";

const CIM = "http://iec.ch/TC57/CIM100#";

const NAMESPACES = "@prefix sh: <http://www.w3.org/ns/shacl#> .\n";

const NODE_SHAPES = [
    {
        id: "http://example.org/ACLineSegmentShape",
        triples: `ex:ACLineSegmentShape a sh:NodeShape ; sh:targetClass <${CIM}ACLineSegment> .`,
    },
];

const PROPERTY_SHAPES = [
    {
        label: "length",
        propertyShapes: [
            {
                id: "http://example.org/length-cardinality",
                triples: `[ sh:path <${CIM}ACLineSegment.length> ; sh:minCount 1 ]`,
            },
        ],
    },
];

const DERIVED = [
    {
        label: "IdentifiedObject.name",
        propertyShapes: [
            {
                id: "http://example.org/name",
                triples: `[ sh:path <${CIM}IdentifiedObject.name> ]`,
            },
        ],
    },
];

// Monaco cannot be created in jsdom, and this suite is about what the view offers rather than
// about the editor. The stub records what it was asked to render, read-only included.
const editors = [];

let mounted = null;
let target = null;

function render(props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(SHACLShapeTtlRenderer, { target, props });
    return target;
}
vi.mock("$lib/monaco/TurtleEditor.svelte", async () => {
    const { createRawSnippet } = await import("svelte");
    return {
        default: function TurtleEditorStub(anchor, props) {
            editors.push(props);
            return createRawSnippet(() => ({
                render: () => `<div class="turtle-editor-stub"></div>`,
            }))(anchor);
        },
    };
});

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

// The property dialog reaches for four endpoints on open. Only what it renders is under test.
vi.mock("$lib/api/generated/index.ts", () => ({
    getAttributeShacl: vi.fn().mockResolvedValue({
        data: {
            custom: [
                {
                    id: "http://example.org/length-cardinality",
                    triples:
                        "[ sh:path cim:ACLineSegment.length ; sh:minCount 1 ]",
                },
            ],
            generated: [],
        },
    }),
    getAssociationShacl: vi
        .fn()
        .mockResolvedValue({ data: { custom: [], generated: [] } }),
    getCustomShaclNamespacesAsString: vi.fn().mockResolvedValue({
        data: "@prefix sh: <http://www.w3.org/ns/shacl#> .\n",
    }),
    getGeneratedShaclNamespacesAsString: vi.fn().mockResolvedValue({
        data: "@prefix sh: <http://www.w3.org/ns/shacl#> .\n",
    }),
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
    editors.length = 0;
});

describe("SHACLShapeTtlRenderer", () => {
    test("groups the shapes it was given and counts them", () => {
        const view = render({
            namespaces: NAMESPACES,
            nodeShapesList: NODE_SHAPES,
            propertyShapesWrapperList: PROPERTY_SHAPES,
            derivedPropertyShapesWrapperList: DERIVED,
        });
        const text = view.textContent.replace(/\s+/g, " ");

        expect(text).toContain("Class rules (1)");
        expect(text).toContain("Property rules (1)");
        expect(text).toContain("Inherited property rules (1)");
    });

    test("offers nothing that writes", () => {
        const view = render({
            namespaces: NAMESPACES,
            nodeShapesList: NODE_SHAPES,
            propertyShapesWrapperList: PROPERTY_SHAPES,
            derivedPropertyShapesWrapperList: [],
        });

        // The endpoint behind "Save Changes" wrote every edit into the graph's default document,
        // whichever document the rule came from, and never updated that document's text.
        expect(view.textContent).not.toContain("Save");
        expect(view.textContent).not.toContain("add new custom shapes");
    });

    test("every editor it mounts is read-only", async () => {
        const view = render({
            namespaces: NAMESPACES,
            nodeShapesList: NODE_SHAPES,
            propertyShapesWrapperList: PROPERTY_SHAPES,
            derivedPropertyShapesWrapperList: DERIVED,
        });

        // A shape's Turtle is behind its own toggle, so open every one of them, and the prefixes.
        for (const button of view.querySelectorAll("button")) {
            button.click();
        }
        flushSync();

        expect(editors.length).toBeGreaterThan(0);
        expect(editors.every(props => props.readOnly === true)).toBe(true);
    });

    test("says so once when nothing targets the class", () => {
        const view = render({
            namespaces: "",
            nodeShapesList: [],
            propertyShapesWrapperList: [],
            derivedPropertyShapesWrapperList: [],
        });
        const text = view.textContent.replace(/\s+/g, " ").trim();

        // Four stacked "No … found" lines was the old answer to an empty class.
        expect(text).toBe("No constraints target this class.");
    });

    test("keeps quiet about the parts that are empty", () => {
        const view = render({
            namespaces: "",
            nodeShapesList: [],
            propertyShapesWrapperList: PROPERTY_SHAPES,
            derivedPropertyShapesWrapperList: [],
        });
        const text = view.textContent.replace(/\s+/g, " ");

        expect(text).toContain("Property rules (1)");
        expect(text).not.toContain("Class rules");
        expect(text).not.toContain("Inherited");
        expect(text).not.toContain("Prefixes");
    });
});

describe("SHACLPropertySpecificDialog", () => {
    function renderDialog() {
        target = document.createElement("div");
        document.body.appendChild(target);
        mounted = mount(SHACLPropertySpecificDialog, {
            target,
            props: {
                showDialog: true,
                property: new ReactiveAttribute({
                    uuid: "22222222-3333-4444-5555-666666666666",
                    label: "length",
                }),
                classUuidOverride: "11111111-2222-3333-4444-555555555555",
            },
        });
        flushSync();
        // bits-ui portals the dialog's content to the body, not into the mount target.
        return document.body;
    }

    test("offers no way to save, and points at the workbench instead", async () => {
        const view = renderDialog();
        await vi.waitFor(() =>
            expect(view.textContent).toContain("Custom Constraints"),
        );

        // Both endpoints it used to save through funnelled into updatePropertyShacl, which wrote
        // to the graph's default document whichever document the rule actually came from.
        expect(view.textContent).not.toContain("Save Changes");
        expect(view.textContent).toContain("Edit in workbench");
    });

    test("shows the constraints read-only", async () => {
        const view = renderDialog();
        await vi.waitFor(() => expect(editors.length).toBeGreaterThan(0));

        expect(view.textContent).toContain("Constraints (SHACL) for");
        expect(editors.every(props => props.readOnly === true)).toBe(true);
    });
});
