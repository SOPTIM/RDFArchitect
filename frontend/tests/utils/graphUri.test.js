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
    asGraphUri,
    GRAPH_NAMESPACE_URI,
    graphNameFrom,
    graphUriFrom,
} from "$lib/utils/graphUri.js";

describe("GRAPH_NAMESPACE_URI", () => {
    test("is the namespace the backend writes imported schemas under", () => {
        // Pinned on both sides: RDFATest asserts the same literal for RDFA.GRAPH_URI. There is no
        // contract carrying it, so changing one side alone has to fail here or there.
        expect(GRAPH_NAMESPACE_URI).toBe("http://graph#");
    });
});

describe("graphNameFrom", () => {
    test("drops the path and the extension", () => {
        expect(graphNameFrom("/some/where/Topology.ttl")).toBe("Topology");
        expect(graphNameFrom("C:\\models\\Topology.rdf")).toBe("Topology");
    });

    test("replaces what a name cannot hold", () => {
        expect(graphNameFrom("Topology EQ-v3.ttl")).toBe("Topology_EQ_v3");
    });

    test("falls back where there is no name at all", () => {
        expect(graphNameFrom("")).toBe("graph");
        expect(graphNameFrom(null)).toBe("graph");
    });
});

describe("graphUriFrom", () => {
    test("puts the file name in the graph namespace", () => {
        expect(graphUriFrom("Topology.ttl")).toBe(
            `${GRAPH_NAMESPACE_URI}Topology`,
        );
    });
});

describe("asGraphUri", () => {
    test("leaves something that already names a scheme alone", () => {
        expect(asGraphUri("https://example.com/Topology", "x.ttl")).toBe(
            "https://example.com/Topology",
        );
    });

    test("puts a bare name in the graph namespace", () => {
        expect(asGraphUri("Topology", "x.ttl")).toBe(
            `${GRAPH_NAMESPACE_URI}Topology`,
        );
    });

    test("falls back to the file name where nothing was typed", () => {
        expect(asGraphUri("   ", "Topology.ttl")).toBe(
            `${GRAPH_NAMESPACE_URI}Topology`,
        );
    });
});
