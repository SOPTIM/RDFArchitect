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

import { describe, expect, test } from "vitest";

import { extractOutline } from "$lib/shacl/outline.js";

describe("extractOutline", () => {
    test("lists each shape with the line it starts on", () => {
        const turtle = `@prefix sh:  <http://www.w3.org/ns/shacl#> .
@prefix cim: <http://iec.ch/TC57/CIM100#> .

cim:ACLineSegmentShape
    a sh:NodeShape ;
    sh:targetClass cim:ACLineSegment .

cim:TerminalShape
    a sh:NodeShape ;
    sh:targetClass cim:Terminal .`;

        expect(extractOutline(turtle)).toEqual([
            {
                name: "cim:ACLineSegmentShape",
                line: 4,
                kind: "NodeShape",
                targetClass: "cim:ACLineSegment",
            },
            {
                name: "cim:TerminalShape",
                line: 8,
                kind: "NodeShape",
                targetClass: "cim:Terminal",
            },
        ]);
    });

    test("ignores directives, comments and continuation lines", () => {
        const turtle = `# a leading comment
@prefix sh: <http://www.w3.org/ns/shacl#> .
@base <http://example.org/> .
PREFIX ex: <http://example.org/>

ex:Shape a sh:NodeShape ;
    sh:closed true .`;

        expect(extractOutline(turtle)).toEqual([
            { name: "ex:Shape", line: 6, kind: "NodeShape", targetClass: null },
        ]);
    });

    test("does not mistake embedded SPARQL for new subjects", () => {
        // The query's own lines start in the first column, which is exactly the shape a subject
        // has. Without tracking the triple-quoted string, "cim:x" below would be listed.
        const turtle = `ex:Shape
    a sh:NodeShape ;
    sh:sparql [
        sh:select """
SELECT $this WHERE {
cim:x cim:y ?z .
}
""" ;
    ] .

ex:Other a sh:NodeShape .`;

        expect(extractOutline(turtle).map(shape => shape.name)).toEqual([
            "ex:Shape",
            "ex:Other",
        ]);
    });

    test("recognises absolute IRIs and separates property shapes", () => {
        const turtle = `<http://example.org/NodeShape> a sh:NodeShape ;
    sh:targetClass <http://example.org/Class> .

<http://example.org/PropShape> a sh:PropertyShape ;
    sh:path ex:name .`;

        expect(extractOutline(turtle)).toEqual([
            {
                name: "<http://example.org/NodeShape>",
                line: 1,
                kind: "NodeShape",
                targetClass: "<http://example.org/Class>",
            },
            {
                name: "<http://example.org/PropShape>",
                line: 4,
                kind: "PropertyShape",
                targetClass: null,
            },
        ]);
    });

    test("survives text that does not parse", () => {
        // The outline is wanted while a document is being edited, which is when it is broken.
        expect(
            extractOutline("ex:Half a sh:NodeShape ;\n    sh:targetClass"),
        ).toEqual([
            { name: "ex:Half", line: 1, kind: "NodeShape", targetClass: null },
        ]);
    });

    test("returns nothing for empty or missing text", () => {
        expect(extractOutline("")).toEqual([]);
        expect(extractOutline(undefined)).toEqual([]);
    });
});
