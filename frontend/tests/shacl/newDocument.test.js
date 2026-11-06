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

import { newDocumentText, shapesNamespace } from "$lib/shacl/newDocument.js";
import { parsePrefixes } from "$lib/shacl/turtleTerms.js";

const CIM = "http://iec.ch/TC57/CIM100#";

describe("a new constraints document", () => {
    test("binds the SHACL vocabularies, the workspace's namespaces and its own", () => {
        const prefixes = parsePrefixes(
            newDocumentText({
                graphUri: "http://example.org/Profile/1.0",
                namespaces: [
                    { prefix: CIM, substitutedPrefix: "cim:" },
                    {
                        prefix: "http://example.org/eu#",
                        substitutedPrefix: "eu",
                    },
                ],
                keyword: "PR",
            }),
        );

        expect(prefixes).toMatchObject({
            sh: "http://www.w3.org/ns/shacl#",
            rdf: "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            rdfs: "http://www.w3.org/2000/01/rdf-schema#",
            xsd: "http://www.w3.org/2001/XMLSchema#",
            cim: CIM,
            eu: "http://example.org/eu#",
            pr: "http://example.org/Profile/1.0/Constraints#",
        });
    });

    test("puts its shapes under the profile's namespace, as the official files do", () => {
        // An imported schema's graph URI is one the importer made up; built from that, the
        // shapes lived under http://graph/…, where the official file has them under the profile.
        const prefixes = parsePrefixes(
            newDocumentText({
                graphUri: "http://graph#61970_600_2_Equipment_AP_Voc_RDFS2020",
                namespaces: [
                    { prefix: CIM, substitutedPrefix: "cim:" },
                    {
                        prefix: "http://iec.ch/TC57/ns/CIM/CoreEquipment-EU#",
                        substitutedPrefix: "eq:",
                    },
                ],
                keyword: "EQ",
            }),
        );

        expect(Object.values(prefixes)).toContain(
            "http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/Constraints#",
        );
        expect(Object.values(prefixes).join(" ")).not.toContain("http://graph");
    });

    test("never rebinds a standard prefix or a namespace twice", () => {
        const text = newDocumentText({
            graphUri: "http://example.org/P",
            namespaces: [
                { prefix: "http://elsewhere.org/", substitutedPrefix: "sh" },
                { prefix: CIM, substitutedPrefix: "cim" },
                { prefix: CIM, substitutedPrefix: "cim100" },
                { prefix: "http://x.org/", substitutedPrefix: "not a name" },
            ],
        });
        const prefixes = parsePrefixes(text);

        expect(prefixes.sh).toBe("http://www.w3.org/ns/shacl#");
        expect(prefixes.cim).toBe(CIM);
        expect(prefixes).not.toHaveProperty("cim100");
        expect(Object.values(prefixes)).not.toContain("http://x.org/");
        expect(prefixes.shapes).toBe("http://example.org/P/Constraints#");
    });

    test("falls back to a free name when the keyword is taken", () => {
        const prefixes = parsePrefixes(
            newDocumentText({
                graphUri: "http://example.org/P",
                namespaces: [{ prefix: CIM, substitutedPrefix: "cim" }],
                keyword: "CIM",
            }),
        );

        expect(prefixes.cim).toBe(CIM);
        expect(prefixes.shapes).toBe("http://example.org/P/Constraints#");
    });

    test("is valid Turtle with room to start writing", () => {
        const text = newDocumentText({ graphUri: "http://example.org/P" });

        expect(text.endsWith("\n\n")).toBe(true);
        for (const line of text.trim().split("\n")) {
            expect(line).toMatch(/^@prefix [A-Za-z][\w.-]*: +<[^>]+> \.$/);
        }
    });
});

describe("the shapes namespace", () => {
    test("follows the official convention", () => {
        expect(shapesNamespace("http://example.org/Profile/3.0")).toBe(
            "http://example.org/Profile/3.0/Constraints#",
        );
        expect(shapesNamespace("http://example.org/Profile/")).toBe(
            "http://example.org/Profile/Constraints#",
        );
    });

    test("keeps a graph URI's fragment apart from another's", () => {
        expect(shapesNamespace("http://graph#EQ")).toBe(
            "http://graph/EQ/Constraints#",
        );
        expect(shapesNamespace("http://graph#TP")).not.toBe(
            shapesNamespace("http://graph#EQ"),
        );
    });
});
