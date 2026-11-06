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

import { existsSync, readFileSync } from "node:fs";
import { describe, expect, test } from "vitest";

import { extractOutline, extractSubjects } from "$lib/shacl/outline.js";

const OFFICIAL_FILE =
    "../external/entsoe-application-profiles-library/CGMES/CurrentRelease/SHACL/TTL/61970-301_ShortCircuit-AP-Con-Complex-SHACL.ttl";

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

    test("keeps dots inside a name and drops the statement's full stop", () => {
        const turtle = `ex:Line.length-cardinality a sh:PropertyShape ;
    sh:path ex:Line.length .

ex:Line a sh:NodeShape ;
    sh:targetClass ex:Line.Kind.`;

        expect(extractOutline(turtle)).toEqual([
            {
                name: "ex:Line.length-cardinality",
                line: 1,
                kind: "PropertyShape",
                targetClass: null,
            },
            {
                name: "ex:Line",
                line: 4,
                kind: "NodeShape",
                targetClass: "ex:Line.Kind",
            },
        ]);
    });

    test("reads the type however it is written", () => {
        const turtle = `ex:A rdf:type sh:NodeShape .
ex:B <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/ns/shacl#PropertyShape> .
ex:C a owl:Class, sh:NodeShape .`;

        expect(extractOutline(turtle).map(shape => shape.kind)).toEqual([
            "NodeShape",
            "PropertyShape",
            "NodeShape",
        ]);
    });

    test("lists only shapes, recognising the untyped ones by what they say", () => {
        const turtle = `ex:Ontology a owl:Ontology ;
    rdfs:label "not a shape" .

ex:Group a sh:PropertyGroup .

ex:Untyped sh:targetClass ex:Thing .

ex:UntypedRule sh:path ex:name ;
    sh:minCount 1 .`;

        expect(
            extractOutline(turtle).map(shape => [shape.name, shape.kind]),
        ).toEqual([
            ["ex:Untyped", "NodeShape"],
            ["ex:UntypedRule", "PropertyShape"],
        ]);
    });

    test("reads shapes written the way the official files write them", () => {
        // Dotted rule names, and the type on the line after the subject, padded into columns.
        const turtle = `@prefix sh:  <http://www.w3.org/ns/shacl#> .
@prefix scu: <http://iec.ch/TC57/CIM100-European#> .
@prefix cim: <http://iec.ch/TC57/CIM100#> .

scu:PowerTransformerEnd
        a               sh:NodeShape ;
        sh:property     scu:PowerTransformerEnd.phaseAngleClock-valueRange ;
        sh:targetClass  cim:PowerTransformerEnd .

scu:PetersenCoil.positionCurrent-valueRangeTypical
        a                sh:PropertyShape ;
        sh:description   "Typically in the range of 20 mA to 200 mA." ;
        sh:maxInclusive  "0.2"^^xsd:float .

scu:PetersenCoil  a    sh:NodeShape ;
        sh:targetClass  cim:PetersenCoil .`;

        expect(
            extractOutline(turtle).map(shape => [shape.name, shape.kind]),
        ).toEqual([
            ["scu:PowerTransformerEnd", "NodeShape"],
            [
                "scu:PetersenCoil.positionCurrent-valueRangeTypical",
                "PropertyShape",
            ],
            ["scu:PetersenCoil", "NodeShape"],
        ]);
    });

    // The submodule is there on a developer's machine, and frontend CI does not check it out.
    test.runIf(existsSync(OFFICIAL_FILE))(
        "counts the shapes an official constraints file declares",
        () => {
            const text = readFileSync(OFFICIAL_FILE, "utf8");
            const typed = kind =>
                (
                    text.match(
                        new RegExp(
                            `^[^#\\n]*\\s(?:a|rdf:type)\\s+sh:${kind}\\b`,
                            "gm",
                        ),
                    ) ?? []
                ).length;
            const shapes = extractOutline(text);

            expect(
                shapes.filter(shape => shape.kind === "NodeShape"),
            ).toHaveLength(typed("NodeShape"));
            expect(
                shapes.filter(shape => shape.kind === "PropertyShape"),
            ).toHaveLength(typed("PropertyShape"));
            expect(new Set(shapes.map(shape => shape.name)).size).toBe(
                shapes.length,
            );
        },
    );
});

describe("extractSubjects", () => {
    test("lists every subject, shape or not", () => {
        const turtle = `ex:Ontology a owl:Ontology .
ex:Group a sh:PropertyGroup .
<http://example.org/Shape> a sh:NodeShape .`;

        expect(extractSubjects(turtle)).toEqual([
            { name: "ex:Ontology", line: 1 },
            { name: "ex:Group", line: 2 },
            { name: "<http://example.org/Shape>", line: 3 },
        ]);
    });
});
