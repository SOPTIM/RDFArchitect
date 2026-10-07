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
    restoresChange,
    showsChange,
    visibleDeltas,
} from "../../src/routes/changelog/changelogFilter.js";

const GRAPH_A = "http://example.org/a";
const GRAPH_B = "http://example.org/b";

describe("showsChange", () => {
    test("shows everything when nothing is filtered", () => {
        const change = { affectedKinds: ["rdf"], affectedGraphUris: [GRAPH_A] };

        expect(showsChange(change, {})).toBe(true);
        expect(showsChange(change)).toBe(true);
    });

    test("hides a change whose only kind is hidden", () => {
        const change = { affectedKinds: ["dl"], affectedGraphUris: [GRAPH_A] };

        expect(showsChange(change, { hiddenKinds: new Set(["dl"]) })).toBe(
            false,
        );
    });

    test("keeps a change that also touched a kind still showing", () => {
        // It is still a change the user made; hiding it would make the history look shorter.
        const change = { affectedKinds: ["rdf", "dl"] };

        expect(showsChange(change, { hiddenKinds: new Set(["dl"]) })).toBe(
            true,
        );
    });

    test("holds the log to one graph", () => {
        const inA = { affectedKinds: ["rdf"], affectedGraphUris: [GRAPH_A] };
        const inB = { affectedKinds: ["rdf"], affectedGraphUris: [GRAPH_B] };

        expect(showsChange(inA, { graphUri: GRAPH_A })).toBe(true);
        expect(showsChange(inB, { graphUri: GRAPH_A })).toBe(false);
    });

    test("shows a change that spanned several graphs under each of them", () => {
        const change = {
            affectedKinds: ["rdf"],
            affectedGraphUris: [GRAPH_A, GRAPH_B],
        };

        expect(showsChange(change, { graphUri: GRAPH_A })).toBe(true);
        expect(showsChange(change, { graphUri: GRAPH_B })).toBe(true);
    });

    test("hides a change that belongs to no graph when one is in view", () => {
        // Prefixes and the workspace's own diagrams belong to the workspace, not to a graph.
        const change = { affectedKinds: ["prefixes"], affectedGraphUris: [] };

        expect(showsChange(change, { graphUri: GRAPH_A })).toBe(false);
        expect(showsChange(change, {})).toBe(true);
    });

    test("shows what became of a graph in that graph's log", () => {
        // Deleting a graph is recorded against the set of graphs, but it names the graph.
        const change = {
            affectedKinds: ["graphs"],
            affectedGraphUris: [GRAPH_A],
        };

        expect(showsChange(change, { graphUri: GRAPH_A })).toBe(true);
    });

    test("keeps the state the workspace was loaded in, whatever is filtered", () => {
        // It names no kind and no graph, but it is where every graph began as much as the
        // workspace did, and it is where a restore can take either back to.
        const loaded = { affectedKinds: [], affectedGraphUris: [] };

        expect(showsChange(loaded, {})).toBe(true);
        expect(
            showsChange(loaded, { hiddenKinds: new Set(["rdf", "dl"]) }),
        ).toBe(true);
        expect(showsChange(loaded, { graphUri: GRAPH_A })).toBe(true);
    });
});

describe("restoresChange", () => {
    test("takes back what was done inside the schema", () => {
        const change = {
            affectedGraphUris: [GRAPH_A],
            restorableGraphUris: [GRAPH_A],
        };

        expect(restoresChange(change, GRAPH_A)).toBe(true);
        expect(restoresChange(change, GRAPH_B)).toBe(false);
    });

    test("leaves what became of the schema, though its log shows it", () => {
        // Creating, deleting and renaming is recorded against the set of schemas, which belongs
        // to the workspace; a restore held to one schema does not touch it.
        const change = {
            affectedKinds: ["graphs"],
            affectedGraphUris: [GRAPH_A],
            restorableGraphUris: [],
        };

        expect(showsChange(change, { graphUri: GRAPH_A })).toBe(true);
        expect(restoresChange(change, GRAPH_A)).toBe(false);
    });

    test("copes with a change that names nothing restorable", () => {
        expect(restoresChange({}, GRAPH_A)).toBe(false);
    });
});

describe("visibleDeltas", () => {
    const change = {
        contextDeltas: [
            { contextName: "rdf", graphUri: GRAPH_A },
            { contextName: "dl", graphUri: GRAPH_A },
            { contextName: "rdf", graphUri: GRAPH_B },
            { contextName: "prefixes", graphUri: null },
        ],
    };

    test("shows every delta when nothing is filtered", () => {
        expect(visibleDeltas(change, {})).toHaveLength(4);
    });

    test("drops the deltas of a hidden kind", () => {
        const shown = visibleDeltas(change, { hiddenKinds: new Set(["dl"]) });

        expect(shown.map(delta => delta.contextName)).toEqual([
            "rdf",
            "rdf",
            "prefixes",
        ]);
    });

    test("drops the deltas of other graphs, and those of none", () => {
        const shown = visibleDeltas(change, { graphUri: GRAPH_A });

        expect(shown).toEqual([
            { contextName: "rdf", graphUri: GRAPH_A },
            { contextName: "dl", graphUri: GRAPH_A },
        ]);
    });

    test("applies both filters at once", () => {
        const shown = visibleDeltas(change, {
            hiddenKinds: new Set(["dl"]),
            graphUri: GRAPH_A,
        });

        expect(shown).toEqual([{ contextName: "rdf", graphUri: GRAPH_A }]);
    });

    test("copes with a change that carries no deltas", () => {
        expect(visibleDeltas({}, { graphUri: GRAPH_A })).toEqual([]);
    });
});
