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

import DocumentInspector from "../../src/routes/shacl/workbench/DocumentInspector.svelte";
import ProblemsPanel from "../../src/routes/shacl/workbench/ProblemsPanel.svelte";

let mounted = null;
let target = null;

/** Only the surface the panels read, so no requests are made. */
function fakeWorkbench(overrides = {}) {
    return {
        documents: [],
        schemas: [],
        readOnly: false,
        setSchemas: vi.fn().mockResolvedValue(true),
        selected: null,
        selectedId: null,
        text: "",
        profiles: [],
        results: [],
        totals: { errorCount: 0, warningCount: 0, infoCount: 0 },
        validating: false,
        validateAll: vi.fn(),
        ...overrides,
    };
}

function render(component, props) {
    target = document.createElement("div");
    document.body.appendChild(target);
    mounted = mount(component, { target, props });
    return target;
}

vi.mock("$lib/config/runtime", () => ({
    PUBLIC_BACKEND_URL: "http://backend.test",
}));

afterEach(() => {
    if (mounted) unmount(mounted);
    target?.remove();
    mounted = null;
    target = null;
});

describe("ProblemsPanel", () => {
    const withFindings = () =>
        fakeWorkbench({
            totals: { errorCount: 2, warningCount: 1, infoCount: 0 },
            results: [
                {
                    documentId: "eq",
                    documentName: "eq.ttl",
                    findings: [
                        {
                            severity: "WARNING",
                            source: "SPARQL",
                            code: "UNSUPPORTED_DYNAMIC_PROPERTY",
                            message: "a warning about the query",
                            line: 30,
                            column: 4,
                        },
                        {
                            severity: "ERROR",
                            source: "SHAPE",
                            code: "UNKNOWN_CLASS",
                            message: "cim:Nonsense does not exist",
                            line: 12,
                            column: 9,
                            foundInProfiles: ["http://example.org/TP/1.0"],
                        },
                    ],
                },
                {
                    documentId: "custom",
                    documentName: "custom.ttl",
                    findings: [
                        {
                            severity: "ERROR",
                            source: "CONFLICT",
                            code: "DUPLICATE_SHAPE_IRI",
                            message: "also defined in eq.ttl",
                        },
                    ],
                },
            ],
        });

    test("summarises the counts and lists every finding worst first", () => {
        const panel = render(ProblemsPanel, { workbench: withFindings() });
        const summary = panel.textContent.replace(/\s+/g, " ");

        expect(summary).toContain("2 Errors");
        expect(summary).toContain("1 Warning");
        expect(summary).not.toContain("0 Infos");

        const messages = [...panel.querySelectorAll("li")].map(item =>
            item.textContent.replace(/\s+/g, " ").trim(),
        );
        expect(messages[0]).toContain("cim:Nonsense does not exist");
        expect(messages[0]).toContain("line 12, column 9");
        expect(messages[0]).toContain("Declared in http://example.org/TP/1.0");
        expect(messages[1]).toContain("also defined in eq.ttl");
        expect(messages[2]).toContain("a warning about the query");
    });

    test("a finding with no position still appears, without a line reference", () => {
        const panel = render(ProblemsPanel, { workbench: withFindings() });
        const conflict = [...panel.querySelectorAll("li")].find(item =>
            item.textContent.includes("also defined in"),
        );

        expect(conflict.textContent).toContain("Between documents");
        expect(conflict.textContent).not.toMatch(/line \d/);
    });

    test("a finding with a line but no column names only the line", () => {
        const panel = render(ProblemsPanel, {
            workbench: fakeWorkbench({
                totals: { errorCount: 1, warningCount: 0, infoCount: 0 },
                results: [
                    {
                        documentId: "eq",
                        documentName: "eq.ttl",
                        findings: [
                            {
                                severity: "ERROR",
                                source: "SYNTAX",
                                message: "does not parse",
                                line: 5,
                                column: null,
                            },
                        ],
                    },
                ],
            }),
        });

        const text = panel.querySelector("li").textContent;
        expect(text).toContain("line 5");
        expect(text).not.toContain("column");
    });

    test("the toggle says whether the list is open", () => {
        const panel = render(ProblemsPanel, { workbench: fakeWorkbench() });
        const toggle = [...panel.querySelectorAll("button")].find(button =>
            button.textContent.includes("Problems"),
        );

        expect(toggle.getAttribute("aria-expanded")).toBe("true");
        toggle.click();
        flushSync();
        expect(toggle.getAttribute("aria-expanded")).toBe("false");
    });

    test("clicking a finding hands it to the caller to jump to", () => {
        const onselect = vi.fn();
        const panel = render(ProblemsPanel, {
            workbench: withFindings(),
            onselect,
        });

        [...panel.querySelectorAll("li button")][0].click();
        flushSync();

        expect(onselect).toHaveBeenCalledWith(
            expect.objectContaining({ documentId: "eq", line: 12, column: 9 }),
        );
    });

    test("says so when there is nothing to report", () => {
        const panel = render(ProblemsPanel, { workbench: fakeWorkbench() });

        expect(panel.textContent).toContain("No problems found");
        expect(panel.querySelectorAll("li")).toHaveLength(0);
    });

    /** The strip above the header that sizes the panel. */
    function handle(panel) {
        return panel.querySelector("[role='separator']");
    }

    /** The panel's own element, whose inline height the drag changes. */
    function sized(panel) {
        return panel.firstElementChild;
    }

    /** jsdom lays nothing out, so the room the panel may take has to be stated for it. */
    function giveRoom(panel, height = 900) {
        Object.defineProperty(panel, "clientHeight", { value: height });
    }

    test("dragging the handle upwards makes the panel taller", () => {
        const panel = render(ProblemsPanel, { workbench: withFindings() });
        flushSync();
        giveRoom(panel);
        const before = parseInt(sized(panel).style.height, 10);

        handle(panel).dispatchEvent(
            new MouseEvent("pointerdown", { bubbles: true, clientY: 500 }),
        );
        window.dispatchEvent(new MouseEvent("pointermove", { clientY: 420 }));
        window.dispatchEvent(new MouseEvent("pointerup", {}));
        flushSync();

        expect(parseInt(sized(panel).style.height, 10)).toBe(before + 80);
    });

    test("the drag stops once the pointer is released", () => {
        const panel = render(ProblemsPanel, { workbench: withFindings() });
        flushSync();
        giveRoom(panel);

        handle(panel).dispatchEvent(
            new MouseEvent("pointerdown", { bubbles: true, clientY: 500 }),
        );
        window.dispatchEvent(new MouseEvent("pointerup", {}));
        const height = sized(panel).style.height;
        window.dispatchEvent(new MouseEvent("pointermove", { clientY: 100 }));
        flushSync();

        expect(sized(panel).style.height).toBe(height);
    });

    test("the arrow keys size it too, and neither direction runs away", () => {
        const panel = render(ProblemsPanel, { workbench: withFindings() });
        flushSync();
        giveRoom(panel);
        const before = parseInt(sized(panel).style.height, 10);

        handle(panel).dispatchEvent(
            new KeyboardEvent("keydown", { key: "ArrowUp", bubbles: true }),
        );
        flushSync();
        expect(parseInt(sized(panel).style.height, 10)).toBe(before + 24);

        for (let press = 0; press < 40; press += 1) {
            handle(panel).dispatchEvent(
                new KeyboardEvent("keydown", {
                    key: "ArrowDown",
                    bubbles: true,
                }),
            );
        }
        flushSync();
        // Still tall enough to read the header and a finding.
        expect(parseInt(sized(panel).style.height, 10)).toBe(120);
    });

    test("can be sized with nothing to report, so a size chosen by hand stays", () => {
        const panel = render(ProblemsPanel, { workbench: fakeWorkbench() });
        flushSync();

        expect(handle(panel)).not.toBeNull();
        expect(sized(panel).style.height).not.toBe("");
    });

    test("collapsing gives the space back and takes the handle away", () => {
        const panel = render(ProblemsPanel, {
            workbench: fakeWorkbench(),
            expanded: false,
        });

        expect(handle(panel)).toBeNull();
        expect(sized(panel).style.height).toBe("");
    });

    test("revalidating on demand goes through the workbench", () => {
        const workbench = fakeWorkbench();
        const panel = render(ProblemsPanel, { workbench });

        [...panel.querySelectorAll("button")]
            .find(button => button.textContent.includes("Validate all"))
            .click();
        flushSync();

        expect(workbench.validateAll).toHaveBeenCalled();
    });
});

describe("DocumentInspector", () => {
    const SHAPES = `@prefix sh: <http://www.w3.org/ns/shacl#> .

eq:ACLineSegmentShape
    a sh:NodeShape ;
    sh:targetClass cim:ACLineSegment .`;

    test("shows what the document is and the shapes it declares", () => {
        const panel = render(DocumentInspector, {
            workbench: fakeWorkbench({
                selectedId: "eq",
                text: SHAPES,
                profiles: ["http://example.org/EQ/1.0"],
                selected: {
                    id: "eq",
                    name: "eq.ttl",
                    origin: "IMPORTED",
                    sourceFileName: "61970-301_Equipment-SHACL.ttl",
                    tripleCount: 51,
                    nodeShapeCount: 1,
                    propertyShapeCount: 7,
                    sparqlConstraintCount: 2,
                    enabled: true,
                },
            }),
        });

        expect(panel.textContent).toContain("eq.ttl");
        expect(panel.textContent).toContain("Imported");
        expect(panel.textContent).toContain("61970-301_Equipment-SHACL.ttl");
        expect(panel.textContent).toContain("51");
        expect(panel.textContent).toContain("Takes part");
        expect(panel.textContent).toContain("Shapes (1)");
        expect(panel.textContent).toMatch(/Property shapes\s*7/);
        expect(panel.textContent).toMatch(/SPARQL constraints\s*2/);
        expect(panel.textContent).toContain("eq:ACLineSegmentShape");
        expect(panel.textContent).toContain("targets cim:ACLineSegment");
        expect(panel.textContent).toContain("node shape");
        expect(panel.textContent).toContain("http://example.org/EQ/1.0");
    });

    describe("the shape list", () => {
        const selected = {
            id: "eq",
            name: "eq.ttl",
            origin: "AUTHORED",
            enabled: true,
            schemas: [],
        };
        const MANY = `eq:Line a sh:NodeShape ;
    sh:targetClass cim:ACLineSegment .

eq:Line.length-cardinality a sh:PropertyShape ;
    sh:path cim:ACLineSegment.length .

eq:Terminal a sh:NodeShape ;
    sh:targetClass cim:Terminal .
`;

        function search(panel, text) {
            const input = panel.querySelector("input[type='search']");
            input.value = text;
            input.dispatchEvent(new Event("input", { bubbles: true }));
            flushSync();
        }

        test("lists property shapes as well, and says which is which", () => {
            const panel = render(DocumentInspector, {
                workbench: fakeWorkbench({
                    selectedId: "eq",
                    text: MANY,
                    selected,
                }),
            });

            expect(panel.textContent).toContain("Shapes (3)");
            expect(panel.textContent).toContain("eq:Line.length-cardinality");
            expect(panel.textContent).toContain("property shape");
        });

        test("searches by name and by target class, ignoring case", () => {
            const panel = render(DocumentInspector, {
                workbench: fakeWorkbench({
                    selectedId: "eq",
                    text: MANY,
                    selected,
                }),
            });

            search(panel, "CARDINALITY");
            expect(panel.textContent).toContain("eq:Line.length-cardinality");
            expect(panel.textContent).not.toContain("eq:Terminal");

            search(panel, "cim:terminal");
            expect(panel.textContent).toContain("eq:Terminal");
            expect(panel.textContent).not.toContain("eq:Line");

            search(panel, "nothing like it");
            expect(panel.textContent).toContain("No shape matches");
        });

        test("stops listing at a limit a search gets past", () => {
            const text = Array.from(
                { length: 250 },
                (_, index) => `eq:Shape${index} a sh:NodeShape .`,
            ).join("\n");
            const panel = render(DocumentInspector, {
                workbench: fakeWorkbench({ selectedId: "eq", text, selected }),
            });

            expect(panel.textContent).toContain("Shapes (250)");
            expect(panel.textContent).toMatch(/Showing 200 of 250/);
            expect(panel.textContent).not.toContain("eq:Shape249 ");

            search(panel, "Shape249");
            expect(panel.textContent).toContain("eq:Shape249");
        });
    });

    test("says nothing about a document while a schema is being compared", () => {
        const panel = render(DocumentInspector, {
            workbench: fakeWorkbench({
                selectedId: "eq",
                comparedSchema: "http://ex.org/EQ",
                selected: {
                    id: "eq",
                    name: "eq.ttl",
                    origin: "AUTHORED",
                    enabled: true,
                    schemas: [],
                },
            }),
        });

        expect(panel.textContent).toContain("No document open");
        expect(panel.textContent).not.toContain("eq.ttl");
    });

    describe("what the document applies to", () => {
        const EQ = "http://ex.org/EQ";
        const SSH = "http://ex.org/SSH";
        const schemas = [
            { uri: EQ, label: "Equipment" },
            { uri: SSH, label: "Hypothesis" },
        ];
        const selected = schemasOf => ({
            id: "eq",
            name: "eq.ttl",
            origin: "AUTHORED",
            enabled: true,
            schemas: schemasOf,
        });

        function boxes(panel) {
            return [...panel.querySelectorAll("input[type='checkbox']")];
        }

        test("ticks the schemas it applies to", () => {
            const panel = render(DocumentInspector, {
                workbench: fakeWorkbench({
                    selectedId: "eq",
                    schemas,
                    selected: selected([SSH]),
                }),
            });

            expect(panel.textContent).toContain("Applies to");
            expect(boxes(panel).map(box => box.checked)).toEqual([false, true]);
        });

        test("ticking one adds it to the others", async () => {
            const workbench = fakeWorkbench({
                selectedId: "eq",
                schemas,
                selected: selected([SSH]),
            });
            const panel = render(DocumentInspector, { workbench });

            boxes(panel)[0].click();
            await vi.waitFor(() =>
                expect(workbench.setSchemas).toHaveBeenCalledWith("eq", [
                    SSH,
                    EQ,
                ]),
            );
        });

        test("cannot be changed in a read-only workspace", () => {
            const panel = render(DocumentInspector, {
                workbench: fakeWorkbench({
                    selectedId: "eq",
                    schemas,
                    readOnly: true,
                    selected: selected([SSH]),
                }),
            });

            boxes(panel).forEach(box => expect(box.disabled).toBe(true));
        });

        test("says when a schema it names is not here, and offers to forget it", async () => {
            const workbench = fakeWorkbench({
                selectedId: "eq",
                schemas,
                selected: selected(["http://ex.org/Deleted", SSH]),
            });
            const panel = render(DocumentInspector, { workbench });

            expect(panel.textContent).toContain("Deleted");
            expect(panel.textContent).toContain("not in this workspace");

            [...panel.querySelectorAll("button")]
                .find(button => button.textContent.includes("Forget it"))
                .click();
            await vi.waitFor(() =>
                expect(workbench.setSchemas).toHaveBeenCalledWith("eq", [SSH]),
            );
        });
    });

    test("clicking a shape asks the editor to reveal its line", () => {
        const onreveal = vi.fn();
        const panel = render(DocumentInspector, {
            workbench: fakeWorkbench({
                selectedId: "eq",
                text: SHAPES,
                selected: {
                    id: "eq",
                    name: "eq.ttl",
                    origin: "AUTHORED",
                    enabled: true,
                },
            }),
            onreveal,
        });

        [...panel.querySelectorAll("li button")][0].click();
        flushSync();

        expect(onreveal).toHaveBeenCalledWith(3, 1);
    });

    test("says nothing is open rather than rendering an empty card", () => {
        const panel = render(DocumentInspector, { workbench: fakeWorkbench() });

        expect(panel.textContent).toContain("No document open.");
    });
});
