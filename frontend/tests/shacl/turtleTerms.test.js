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

import {
    completionEntries,
    hoverMarkdown,
    parsePrefixes,
    resolveTerm,
    termAt,
    tokenAt,
    writeTerm,
} from "$lib/shacl/turtleTerms.js";

const CIM = "http://iec.ch/TC57/CIM100#";

const PREFIXES = { cim: CIM, sh: "http://www.w3.org/ns/shacl#" };

describe("parsePrefixes", () => {
    test("reads Turtle and SPARQL prefix declarations", () => {
        // A sh:select block may bind its own prefixes in SPARQL's spelling.
        const prefixes = parsePrefixes(`@prefix cim: <${CIM}> .
@prefix sh:   <http://www.w3.org/ns/shacl#> .
PREFIX ex: <http://example.org/>`);

        expect(prefixes).toEqual({
            cim: CIM,
            sh: "http://www.w3.org/ns/shacl#",
            ex: "http://example.org/",
        });
    });

    test("handles the default namespace and no text at all", () => {
        expect(parsePrefixes("@prefix : <http://example.org/> .")).toEqual({
            "": "http://example.org/",
        });
        expect(parsePrefixes("")).toEqual({});
        expect(parsePrefixes(undefined)).toEqual({});
    });
});

describe("tokenAt", () => {
    test("spans the whole prefixed name the cursor is in", () => {
        const line = "    sh:targetClass cim:ACLineSegment ;";
        const token = tokenAt(line, 25);

        expect(token.text).toBe("cim:ACLineSegment");
        expect(line.slice(token.startColumn - 1, token.endColumn - 1)).toBe(
            "cim:ACLineSegment",
        );
    });

    test("keeps the dot inside a CIM property name", () => {
        // cim:ACLineSegment.length is one name, not a name and a statement terminator.
        expect(tokenAt("  sh:path cim:ACLineSegment.length ;", 20).text).toBe(
            "cim:ACLineSegment.length",
        );
    });

    test("spans an absolute IRI in angle brackets", () => {
        expect(tokenAt("  sh:targetClass <http://ex.org/A> .", 25).text).toBe(
            "<http://ex.org/A>",
        );
    });

    test("finds nothing in whitespace", () => {
        expect(tokenAt("    ", 2)).toBeNull();
        expect(tokenAt("", 1)).toBeNull();
    });
});

describe("resolveTerm", () => {
    test("expands a prefixed name through the document's own bindings", () => {
        expect(resolveTerm("cim:ACLineSegment", PREFIXES)).toBe(
            `${CIM}ACLineSegment`,
        );
    });

    test("unwraps an absolute IRI", () => {
        expect(resolveTerm("<http://ex.org/A>", PREFIXES)).toBe(
            "http://ex.org/A",
        );
    });

    test("drops a statement's trailing full stop", () => {
        // "ex:o." at the end of a line is a name plus a terminator, written without a space.
        expect(resolveTerm("cim:Terminal.", PREFIXES)).toBe(`${CIM}Terminal`);
    });

    test("refuses a prefix the document does not bind", () => {
        expect(resolveTerm("nope:Thing", PREFIXES)).toBeNull();
        expect(resolveTerm("NodeShape", PREFIXES)).toBeNull();
        expect(resolveTerm("", PREFIXES)).toBeNull();
    });
});

describe("termAt", () => {
    test("gives the IRI and the span to underline", () => {
        const term = termAt(
            "  sh:targetClass cim:ACLineSegment ;",
            22,
            PREFIXES,
        );

        expect(term.iri).toBe(`${CIM}ACLineSegment`);
        expect(term.startColumn).toBe(18);
        expect(term.endColumn).toBe(35);
    });

    test("is silent where there is no term", () => {
        expect(termAt("  a sh:NodeShape ;", 3, PREFIXES)).toBeNull();
    });
});

describe("writeTerm", () => {
    const term = {
        iri: `${CIM}ACLineSegment`,
        namespace: CIM,
        localName: "ACLineSegment",
    };

    test("uses whatever prefix the document binds the namespace to", () => {
        expect(writeTerm(term, PREFIXES)).toBe("cim:ACLineSegment");
        expect(writeTerm(term, { c: CIM })).toBe("c:ACLineSegment");
    });

    test("falls back to the full IRI rather than inventing a prefix", () => {
        // Adding an @prefix line would edit a part of the file the user is not looking at.
        expect(writeTerm(term, {})).toBe(`<${CIM}ACLineSegment>`);
    });
});

describe("completionEntries", () => {
    const terms = [
        {
            iri: `${CIM}ACLineSegment`,
            namespace: CIM,
            localName: "ACLineSegment",
            kind: "CLASS",
        },
        {
            iri: "http://other.org/Thing",
            namespace: "http://other.org/",
            localName: "Thing",
            kind: "CLASS",
            label: "A thing",
        },
    ];

    test("offers each term as the document would write it", () => {
        const entries = completionEntries(terms, PREFIXES);

        expect(entries[0]).toMatchObject({
            label: "cim:ACLineSegment",
            insertText: "cim:ACLineSegment",
            detail: "ACLineSegment",
            kind: "CLASS",
        });
        expect(entries[1].label).toBe("<http://other.org/Thing>");
        expect(entries[1].detail).toBe("A thing");
    });

    test("sorts the terms the document can abbreviate first", () => {
        const entries = completionEntries(terms, PREFIXES);

        expect(entries[0].sortText < entries[1].sortText).toBe(true);
    });

    test("copes with no terms loaded yet", () => {
        expect(completionEntries(undefined, PREFIXES)).toEqual([]);
    });
});

describe("hoverMarkdown", () => {
    test("shows the comment and the property's shape", () => {
        const markdown = hoverMarkdown(
            {
                iri: `${CIM}ACLineSegment.length`,
                namespace: CIM,
                localName: "ACLineSegment.length",
                label: "length",
                comment: "Segment length.",
                domains: [`${CIM}ACLineSegment`],
                ranges: [`${CIM}Length`],
                multiplicity: "1..1",
                profiles: ["http://ex.org/EQ/1.0"],
            },
            PREFIXES,
        );

        expect(markdown).toContain("**`cim:ACLineSegment.length`** — length");
        expect(markdown).toContain("Segment length.");
        expect(markdown).toContain("| **Domain** | `cim:ACLineSegment` |");
        expect(markdown).toContain("| **Range** | `cim:Length` |");
        expect(markdown).toContain("| **Multiplicity** | `1..1` |");
        expect(markdown).toContain("| **Profile** | `http://ex.org/EQ/1.0` |");
    });

    test("leaves out the label when it only repeats the name", () => {
        const markdown = hoverMarkdown(
            {
                iri: `${CIM}Terminal`,
                namespace: CIM,
                localName: "Terminal",
                label: "Terminal",
                profiles: [],
            },
            PREFIXES,
        );

        expect(markdown).toBe("**`cim:Terminal`**");
    });

    test("says nothing about a term the schema does not know", () => {
        expect(hoverMarkdown(null, PREFIXES)).toBeNull();
    });
});
