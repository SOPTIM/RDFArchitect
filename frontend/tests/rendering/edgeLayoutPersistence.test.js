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

import { beforeEach, describe, expect, test, vi } from "vitest";

import {
    updateDatasetEdgeLayouts,
    updateDiagramLayout,
    updateEdgeLayouts,
} from "$lib/api/generated/index.ts";
import {
    createBendPoint,
    createEndPoint,
} from "$lib/rendering/svelteflow/interaction/bendPointOperations.js";
import {
    EdgeLayoutPersistence,
    toEdgeLayoutDTO,
    withStoredIds,
} from "$lib/rendering/svelteflow/interaction/edgeLayoutPersistence.js";

const GRAPH_TARGET = {
    datasetName: "ds",
    graphURI: "http://example.com/graph",
    diagramUUID: "diagram",
};

function edge(id, bendPoints = [], selected = false) {
    return {
        id,
        type: "association",
        source: `${id}-source-class`,
        target: `${id}-target-class`,
        selected,
        data: {
            sourceObject: `${id}-source-object`,
            targetObject: `${id}-target-object`,
            bendPoints,
        },
    };
}

function withPoints(shownEdge, bendPoints) {
    return { ...shownEdge, data: { ...shownEdge.data, bendPoints } };
}

function selected(shownEdge, isSelected) {
    return { ...shownEdge, selected: isSelected };
}

function answer(data = []) {
    return Promise.resolve({ data });
}

function sentEdges(apiCall, callIndex = 0) {
    return apiCall.mock.calls[callIndex][0].body;
}

function settle() {
    return new Promise(resolve => setTimeout(resolve));
}

/** A persistence over a mutable list of shown edges, like the edges state of the wrapper. */
function persistenceFor(
    initialEdges,
    { target = GRAPH_TARGET, readOnly = false } = {},
) {
    const state = { edges: initialEdges, target, readOnly };
    const persistence = new EdgeLayoutPersistence({
        getEdges: () => state.edges,
        setEdges: value => (state.edges = value),
        getTarget: () => state.target,
        isReadOnly: () => state.readOnly,
    });
    persistence.load(initialEdges);
    const show = nextEdges => {
        state.edges = nextEdges;
        persistence.track(nextEdges);
    };
    return { state, persistence, show };
}

vi.mock("$lib/api/generated/index.ts", () => ({
    updateDatasetDiagramLayout: vi.fn(),
    updateDatasetEdgeLayouts: vi.fn(),
    updateDiagramLayout: vi.fn(),
    updateEdgeLayouts: vi.fn(),
}));

beforeEach(() => {
    vi.clearAllMocks();
    updateEdgeLayouts.mockImplementation(() => answer());
    updateDatasetEdgeLayouts.mockImplementation(() => answer());
    updateDiagramLayout.mockImplementation(() => answer());
});

describe("toEdgeLayoutDTO", () => {
    test("sends the points in order with the side of each end point", () => {
        const source = createEndPoint(0, 0, "source");
        const bend = createBendPoint(50, 50);
        const target = createEndPoint(100, 100, "target");

        const layout = toEdgeLayoutDTO(edge("e", [source, bend, target]));

        expect(layout).toEqual({
            kind: "association",
            sourceObject: "e-source-object",
            targetObject: "e-target-object",
            sourceClass: "e-source-class",
            targetClass: "e-target-class",
            points: [
                { id: source.id, xPosition: 0, yPosition: 0, side: "source" },
                { id: bend.id, xPosition: 50, yPosition: 50, side: null },
                {
                    id: target.id,
                    xPosition: 100,
                    yPosition: 100,
                    side: "target",
                },
            ],
        });
    });

    test("keeps the side of a lone end point at the target", () => {
        const bend = createBendPoint(50, 50);
        const target = createEndPoint(100, 100, "target");

        const layout = toEdgeLayoutDTO(edge("e", [bend, target]));

        expect(layout.points.map(point => point.side)).toEqual([
            null,
            "target",
        ]);
    });
});

describe("withStoredIds", () => {
    test("replaces only the ids that were stored under an mRID", () => {
        const points = [
            { id: "new", x: 1, y: 1 },
            { id: "stored", x: 2, y: 2 },
        ];

        const replaced = withStoredIds(points, new Map([["new", "mrid"]]));

        expect(replaced.map(point => point.id)).toEqual(["mrid", "stored"]);
        expect(withStoredIds(points, new Map())).toBe(points);
    });
});

describe("EdgeLayoutPersistence", () => {
    test("loading a diagram saves nothing", () => {
        persistenceFor([edge("e", [createBendPoint(1, 1)])]);

        expect(updateEdgeLayouts).not.toHaveBeenCalled();
    });

    test("saves an edited edge once it is deselected", async () => {
        const shown = edge("e", [], true);
        const { show } = persistenceFor([shown]);
        const bend = createBendPoint(10, 20);

        show([withPoints(shown, [bend])]);
        expect(updateEdgeLayouts).not.toHaveBeenCalled();

        show([selected(withPoints(shown, [bend]), false)]);
        await settle();

        expect(updateEdgeLayouts).toHaveBeenCalledTimes(1);
        expect(updateEdgeLayouts.mock.calls[0][0].path).toEqual(GRAPH_TARGET);
        expect(sentEdges(updateEdgeLayouts)[0].points).toEqual([
            { id: bend.id, xPosition: 10, yPosition: 20, side: null },
        ]);
    });

    test("saves a diagram across the dataset over the dataset route", async () => {
        const datasetTarget = {
            datasetName: "ds",
            graphURI: null,
            diagramUUID: "d",
        };
        const shown = edge("e");
        const { show } = persistenceFor([shown], { target: datasetTarget });

        show([withPoints(shown, [createBendPoint(1, 1)])]);
        await settle();

        expect(updateEdgeLayouts).not.toHaveBeenCalled();
        expect(updateDatasetEdgeLayouts.mock.calls[0][0].path).toEqual({
            datasetName: "ds",
            diagramUUID: "d",
        });
    });

    test("saves nothing in a read only workspace", async () => {
        const shown = edge("e");
        const { show } = persistenceFor([shown], { readOnly: true });

        show([withPoints(shown, [createBendPoint(1, 1)])]);
        await settle();

        expect(updateEdgeLayouts).not.toHaveBeenCalled();
    });

    test("holds the edges back during a node drag and saves them with the classes", async () => {
        const shown = edge("e");
        const { persistence, show } = persistenceFor([shown]);
        const classes = [{ classUUID: "c", xPosition: 1, yPosition: 2 }];

        persistence.beginNodeDrag();
        show([withPoints(shown, [createEndPoint(5, 5, "source")])]);
        persistence.saveDiagramLayout({ classes, labels: [] });
        await settle();

        expect(updateEdgeLayouts).not.toHaveBeenCalled();
        const diagramLayout = updateDiagramLayout.mock.calls[0][0].body;
        expect(diagramLayout.classes).toBe(classes);
        expect(
            diagramLayout.edges.map(layout => layout.points[0].side),
        ).toEqual(["source"]);
    });

    test("saves all edges after a new layout and nothing more afterwards", async () => {
        const first = edge("first");
        const second = edge("second");
        const { state, persistence } = persistenceFor([first, second]);

        state.edges = [withPoints(first, [createBendPoint(1, 1)]), second];
        persistence.saveDiagramLayout({ allEdges: true });
        persistence.track(state.edges);
        await settle();

        expect(updateDiagramLayout).toHaveBeenCalledTimes(1);
        expect(sentEdges(updateDiagramLayout).edges).toHaveLength(2);
        expect(updateEdgeLayouts).not.toHaveBeenCalled();
    });

    test("saves the unsaved edges of the previous diagram before loading the next", async () => {
        const shown = edge("e", [], true);
        const { state, persistence, show } = persistenceFor([shown]);
        show([withPoints(shown, [createBendPoint(1, 1)])]);

        state.target = { ...GRAPH_TARGET, diagramUUID: "next" };
        const nextEdges = [edge("other")];
        persistence.load(nextEdges);
        state.edges = nextEdges;
        await settle();

        expect(updateEdgeLayouts.mock.calls[0][0].path.diagramUUID).toBe(
            "diagram",
        );
    });

    test("sends with keepalive when the page is left", () => {
        const shown = edge("e", [], true);
        const { persistence, show } = persistenceFor([shown]);
        show([withPoints(shown, [createBendPoint(1, 1)])]);

        persistence.saveAll({ keepalive: true });

        expect(updateEdgeLayouts.mock.calls[0][0].keepalive).toBe(true);
    });

    test("takes over the mRIDs of new points and sends them in the next request", async () => {
        const shown = edge("e");
        const { state, show } = persistenceFor([shown]);
        const bend = createBendPoint(1, 1);
        let answerFirstRequest;
        updateEdgeLayouts.mockImplementationOnce(
            () => new Promise(resolve => (answerFirstRequest = resolve)),
        );

        show([withPoints(shown, [bend])]);
        show([withPoints(shown, [{ ...bend, x: 2 }])]);
        await settle();
        expect(updateEdgeLayouts).toHaveBeenCalledTimes(1);

        answerFirstRequest({ data: [{ clientId: bend.id, id: "mrid" }] });
        await settle();

        expect(updateEdgeLayouts).toHaveBeenCalledTimes(2);
        expect(sentEdges(updateEdgeLayouts, 1)[0].points[0].id).toBe("mrid");
        expect(state.edges[0].data.bendPoints[0].id).toBe("mrid");
    });

    test("keeps the ids of a selected edge, which may be dragged right now", async () => {
        const shown = edge("e");
        const { state, persistence, show } = persistenceFor([shown]);
        const bend = createBendPoint(1, 1);
        updateEdgeLayouts.mockImplementationOnce(() =>
            answer([{ clientId: bend.id, id: "mrid" }]),
        );

        show([withPoints(shown, [bend])]);
        state.edges = [selected(state.edges[0], true)];
        await settle();

        expect(state.edges[0].data.bendPoints[0].id).toBe(bend.id);
        persistence.track(state.edges);
        expect(updateEdgeLayouts).toHaveBeenCalledTimes(1);
    });
});
