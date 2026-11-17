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
    restoreRequestFor,
    restoresChange,
    showsChange,
    visibleDeltas,
} from "../../src/routes/changelog/changelogFilter.js";

const GRAPH_A = "http://example.org/a";
const GRAPH_B = "http://example.org/b";

describe("showsChange", () => {
    test("shows everything when nothing is filtered", () => {
        const change = {
            affectedKinds: ["rdf"],
            affectedGraphNames: [GRAPH_A],
        };

        expect(showsChange(change, {})).toBe(true);
        expect(showsChange(change)).toBe(true);
    });

    test("hides a change whose only kind is hidden", () => {
        const change = { affectedKinds: ["dl"], affectedGraphNames: [GRAPH_A] };

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
        const inA = { affectedKinds: ["rdf"], affectedGraphNames: [GRAPH_A] };
        const inB = { affectedKinds: ["rdf"], affectedGraphNames: [GRAPH_B] };

        expect(showsChange(inA, { graphName: GRAPH_A })).toBe(true);
        expect(showsChange(inB, { graphName: GRAPH_A })).toBe(false);
    });

    test("shows a change that spanned several graphs under each of them", () => {
        const change = {
            affectedKinds: ["rdf"],
            affectedGraphNames: [GRAPH_A, GRAPH_B],
        };

        expect(showsChange(change, { graphName: GRAPH_A })).toBe(true);
        expect(showsChange(change, { graphName: GRAPH_B })).toBe(true);
    });

    test("hides a change that belongs to no graph when one is in view", () => {
        // Prefixes and the workspace's own diagrams belong to the workspace, not to a graph.
        const change = { affectedKinds: ["prefixes"], affectedGraphNames: [] };

        expect(showsChange(change, { graphName: GRAPH_A })).toBe(false);
        expect(showsChange(change, {})).toBe(true);
    });

    test("shows what became of a graph in that graph's log", () => {
        // Deleting a graph is recorded against the set of graphs, but it names the graph.
        const change = {
            affectedKinds: ["graphs"],
            affectedGraphNames: [GRAPH_A],
        };

        expect(showsChange(change, { graphName: GRAPH_A })).toBe(true);
    });

    test("keeps the state the workspace was loaded in, whatever is filtered", () => {
        // It names no kind and no graph, but it is where every graph began as much as the
        // workspace did, and it is where a restore can take either back to.
        const loaded = { affectedKinds: [], affectedGraphNames: [] };

        expect(showsChange(loaded, {})).toBe(true);
        expect(
            showsChange(loaded, { hiddenKinds: new Set(["rdf", "dl"]) }),
        ).toBe(true);
        expect(showsChange(loaded, { graphName: GRAPH_A })).toBe(true);
    });
});

describe("restoresChange", () => {
    test("takes back what was done inside the schema", () => {
        const change = {
            affectedGraphNames: [GRAPH_A],
            restorableGraphNames: [GRAPH_A],
        };

        expect(restoresChange(change, GRAPH_A)).toBe(true);
        expect(restoresChange(change, GRAPH_B)).toBe(false);
    });

    test("leaves what became of the schema, though its log shows it", () => {
        // Creating, deleting and renaming is recorded against the set of schemas, which belongs
        // to the workspace; a restore held to one schema does not touch it.
        const change = {
            affectedKinds: ["graphs"],
            affectedGraphNames: [GRAPH_A],
            restorableGraphNames: [],
        };

        expect(showsChange(change, { graphName: GRAPH_A })).toBe(true);
        expect(restoresChange(change, GRAPH_A)).toBe(false);
    });

    test("copes with a change that names nothing restorable", () => {
        expect(restoresChange({}, GRAPH_A)).toBe(false);
    });
});

describe("visibleDeltas", () => {
    const triple = { subject: "s", predicate: "p", object: "o" };
    const rdfInA = {
        contextName: "rdf",
        graphName: GRAPH_A,
        additions: [triple],
    };
    const dlInA = {
        contextName: "dl",
        graphName: GRAPH_A,
        deletions: [triple],
    };
    const rdfInB = {
        contextName: "rdf",
        graphName: GRAPH_B,
        additions: [triple],
    };
    const prefixes = {
        contextName: "prefixes",
        graphName: null,
        values: [{ key: "ex", before: null, after: "http://example.org/" }],
    };
    const change = { contextDeltas: [rdfInA, dlInA, rdfInB, prefixes] };

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
        const shown = visibleDeltas(change, { graphName: GRAPH_A });

        expect(shown).toEqual([rdfInA, dlInA]);
    });

    test("applies both filters at once", () => {
        const shown = visibleDeltas(change, {
            hiddenKinds: new Set(["dl"]),
            graphName: GRAPH_A,
        });

        expect(shown).toEqual([rdfInA]);
    });

    test("keeps a delta that changed values rather than triples", () => {
        // The prefixes and the colours are not held as RDF, and a change to them would otherwise
        // be a row that cannot be opened.
        expect(visibleDeltas({ contextDeltas: [prefixes] }, {})).toEqual([
            prefixes,
        ]);
    });

    test("drops a delta that has nothing behind it", () => {
        // The triples of a delta are held weakly, so they can be gone while the entry remains.
        const collected = { contextName: "rdf", graphName: GRAPH_A };

        expect(visibleDeltas({ contextDeltas: [collected] }, {})).toEqual([]);
    });

    test("copes with a change that carries no deltas", () => {
        expect(visibleDeltas({}, { graphName: GRAPH_A })).toEqual([]);
    });
});

describe("restoreRequestFor", () => {
    // Newest first, like the log: two changes undone above the current one.
    const LOG = [
        { changeId: "e", undone: true },
        { changeId: "d", undone: true },
        { changeId: "c" },
        { changeId: "b" },
        { changeId: "a" },
    ];

    test("takes back what lies between the workspace and an older change", () => {
        const request = restoreRequestFor(LOG, LOG[4]);

        expect(request.takenBack.map(change => change.changeId)).toEqual([
            "c",
            "b",
        ]);
        expect(request.undoneAhead).toBe(2);
    });

    test("reapplies what lies between an undone change and the workspace", () => {
        const request = restoreRequestFor(LOG, LOG[0]);

        expect(request.reapplied.map(change => change.changeId)).toEqual([
            "e",
            "d",
        ]);
        expect(request.takenBack).toBeUndefined();
    });

    test("says where the workspace stood, for the backend to check", () => {
        const request = restoreRequestFor(LOG, LOG[4]);

        expect(request.standingOn).toEqual({ current: "c", newest: "e" });
    });

    test("reapplies every change when all of them are undone", () => {
        // The workspace stands below the oldest change, so it is reapplied too.
        const allUndone = [
            { changeId: "b", undone: true },
            { changeId: "a", undone: true },
        ];

        const request = restoreRequestFor(allUndone, allUndone[0]);

        expect(request.reapplied.map(change => change.changeId)).toEqual([
            "b",
            "a",
        ]);
        expect(request.standingOn).toEqual({
            current: undefined,
            newest: "b",
        });
    });
});
