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

import {
    updateDatasetDiagramLayout,
    updateDatasetEdgeLayouts,
    updateDiagramLayout,
    updateEdgeLayouts,
} from "$lib/api/generated/index.ts";

import {
    dissolveCollinearBendPoints,
    getSourceEndPoint,
    getTargetEndPoint,
} from "./bendPointOperations.js";
import { EDGE_INTERACTION_CONFIG } from "./edgeInteractionConfig.js";

/**
 * The layout of an edge as the backend stores it: the full, ordered list of its points from the
 * source to the target, each end point with the side of the class it is glued to.
 */
export function toEdgeLayoutDTO(edge) {
    const points = edge.data?.bendPoints ?? [];
    const sourceEndPoint = getSourceEndPoint(points);
    const targetEndPoint = getTargetEndPoint(points);
    return {
        kind: edge.type,
        sourceObject: edge.data?.sourceObject,
        targetObject: edge.data?.targetObject,
        sourceClass: edge.source,
        targetClass: edge.target,
        points: points.map(point => ({
            id: point.id,
            xPosition: point.x,
            yPosition: point.y,
            side: sideOf(point, sourceEndPoint, targetEndPoint),
        })),
    };
}

function sideOf(point, sourceEndPoint, targetEndPoint) {
    if (point === sourceEndPoint) {
        return "source";
    }
    if (point === targetEndPoint) {
        return "target";
    }
    return null;
}

/**
 * Replaces the ids of points the backend stored under an mRID of their own by that mRID.
 *
 * @param {Array<{id: string}>} points the points of an edge
 * @param {Map<string, string>} storedIds the mRIDs by the ids the points were sent with
 * @returns the points, or the same array if none of them got an mRID
 */
export function withStoredIds(points, storedIds) {
    if (!points?.some(point => storedIds.has(point.id))) {
        return points;
    }
    return points.map(point =>
        storedIds.has(point.id)
            ? { ...point, id: storedIds.get(point.id) }
            : point,
    );
}

function requestEdgeLayouts(target, edgeLayouts, keepalive) {
    if (target.graphURI) {
        return updateEdgeLayouts({
            path: {
                datasetName: target.datasetName,
                graphURI: target.graphURI,
                diagramUUID: target.diagramUUID,
            },
            body: edgeLayouts,
            keepalive,
        });
    }
    return updateDatasetEdgeLayouts({
        path: {
            datasetName: target.datasetName,
            diagramUUID: target.diagramUUID,
        },
        body: edgeLayouts,
        keepalive,
    });
}

function requestDiagramLayout(target, diagramLayout) {
    if (target.graphURI) {
        return updateDiagramLayout({
            path: {
                datasetName: target.datasetName,
                graphURI: target.graphURI,
                diagramUUID: target.diagramUUID,
            },
            body: diagramLayout,
        });
    }
    return updateDatasetDiagramLayout({
        path: {
            datasetName: target.datasetName,
            diagramUUID: target.diagramUUID,
        },
        body: diagramLayout,
    });
}

/**
 * Saves the points of the edges of the shown diagram. An edge is saved as soon as it can no longer
 * be edited, i.e. when it is deselected, when another diagram is loaded and when the page is left.
 * While classes are dragged, the end points of their edges move along; those edges are saved
 * together with the classes once the drag ends.
 *
 * Before an edge is saved, its unnecessary bend points are dissolved, in the shown edge as well, so
 * a bend point that was created but never moved off the line is not stored.
 *
 * Changes are detected by the identity of the points array of an edge, which every edit replaces.
 * The requests of an edge are sent one after another, so the mRIDs the backend returns for new
 * points are known before the edge is sent again.
 */
export class EdgeLayoutPersistence {
    #getEdges;
    #setEdges;
    #getTarget;
    #isReadOnly;

    #target = null;
    #savedPoints = new Map();
    #unsavedEdges = new Map();
    #storedIds = new Map();
    #pendingRequests = new Map();
    #nodeDragActive = false;
    #disposed = false;

    /**
     * @param {object} options
     * @param {() => Array} options.getEdges the edges currently shown
     * @param {(edges: Array) => void} options.setEdges replaces the edges shown
     * @param {() => {datasetName: string, graphURI: string, diagramUUID: string}} options.getTarget
     *     the diagram currently shown, without graphURI for a diagram across the whole dataset
     * @param {() => boolean} options.isReadOnly whether the workspace may not be changed
     */
    constructor({ getEdges, setEdges, getTarget, isReadOnly }) {
        this.#getEdges = getEdges;
        this.#setEdges = setEdges;
        this.#getTarget = getTarget;
        this.#isReadOnly = isReadOnly;
    }

    /**
     * Starts tracking the edges of a newly loaded diagram. Unsaved edges of the diagram shown
     * before are saved first.
     */
    load(edges) {
        if (this.#target !== null) {
            this.saveAll();
        }
        this.#target = this.#getTarget();
        this.#savedPoints = new Map(
            edges.map(edge => [edge.id, edge.data?.bendPoints]),
        );
    }

    /** Records the changed edges and saves those that can no longer be edited. */
    track(edges) {
        this.#recordChanges(edges);
        if (this.#nodeDragActive) {
            return;
        }
        const selectedEdgeIds = new Set(
            edges.filter(edge => edge.selected).map(edge => edge.id),
        );
        this.#saveUnsavedEdges(
            [...this.#unsavedEdges.keys()].filter(
                edgeId => !selectedEdgeIds.has(edgeId),
            ),
        );
    }

    /** Holds the edges back until the dragged classes are saved with {@link saveDiagramLayout}. */
    beginNodeDrag() {
        this.#nodeDragActive = true;
    }

    /**
     * Saves the given classes and labels together with the unsaved edges of the shown diagram, or
     * with all of its edges, e.g. after the diagram was laid out anew.
     */
    saveDiagramLayout({ classes = [], labels = [], allEdges = false }) {
        this.#nodeDragActive = false;
        const edges = this.#getEdges();
        let changedEdges;
        if (allEdges) {
            changedEdges = edges;
            for (const edge of edges) {
                this.#savedPoints.set(edge.id, edge.data?.bendPoints);
                this.#unsavedEdges.delete(edge.id);
            }
        } else {
            this.#recordChanges(edges);
            changedEdges = this.#takeUnsavedEdges(
                [...this.#unsavedEdges]
                    .filter(([, unsaved]) => unsaved.target === this.#target)
                    .map(([edgeId]) => edgeId),
            ).map(unsaved => this.#withoutUnnecessaryBendPoints(unsaved.edge));
        }
        if (
            this.#isReadOnly() ||
            !this.#target?.diagramUUID ||
            classes.length + labels.length + changedEdges.length === 0
        ) {
            return;
        }
        const target = this.#target;
        this.#enqueue(
            changedEdges.map(edge => edge.id),
            () =>
                requestDiagramLayout(target, {
                    classes,
                    labels,
                    edges: changedEdges.map(edge => this.#toSentLayout(edge)),
                }),
        );
    }

    /**
     * Saves all unsaved edges right away.
     *
     * @param {object} [options]
     * @param {boolean} [options.keepalive] whether the requests have to outlive the page; they
     *     are then sent without waiting for earlier requests of the same edges
     */
    saveAll({ keepalive = false } = {}) {
        this.#recordChanges(this.#getEdges());
        this.#saveUnsavedEdges([...this.#unsavedEdges.keys()], keepalive);
    }

    /** Saves all unsaved edges and stops touching the shown edges. */
    dispose() {
        this.saveAll();
        this.#disposed = true;
    }

    #recordChanges(edges) {
        const readOnly = this.#isReadOnly();
        for (const edge of edges) {
            const points = edge.data?.bendPoints;
            if (
                !this.#savedPoints.has(edge.id) ||
                this.#savedPoints.get(edge.id) === points
            ) {
                continue;
            }
            this.#savedPoints.set(edge.id, points);
            if (!readOnly) {
                this.#unsavedEdges.set(edge.id, { target: this.#target, edge });
            }
        }
    }

    #takeUnsavedEdges(edgeIds) {
        const taken = [];
        for (const edgeId of edgeIds) {
            taken.push(this.#unsavedEdges.get(edgeId));
            this.#unsavedEdges.delete(edgeId);
        }
        return taken;
    }

    #saveUnsavedEdges(edgeIds, keepalive = false) {
        const edgesByTarget = new Map();
        for (const { target, edge } of this.#takeUnsavedEdges(edgeIds)) {
            if (!target?.diagramUUID) {
                continue;
            }
            if (!edgesByTarget.has(target)) {
                edgesByTarget.set(target, []);
            }
            edgesByTarget
                .get(target)
                .push(this.#withoutUnnecessaryBendPoints(edge));
        }
        for (const [target, edges] of edgesByTarget) {
            const send = () =>
                requestEdgeLayouts(
                    target,
                    edges.map(edge => this.#toSentLayout(edge)),
                    keepalive,
                );
            if (keepalive) {
                send();
            } else {
                this.#enqueue(
                    edges.map(edge => edge.id),
                    send,
                );
            }
        }
    }

    /**
     * The edge without its unnecessary bend points. If the edge is still shown with the points it
     * is saved with, the shown edge loses them too.
     */
    #withoutUnnecessaryBendPoints(edge) {
        const points = edge.data?.bendPoints ?? [];
        const dissolved = dissolveCollinearBendPoints(
            points,
            EDGE_INTERACTION_CONFIG.collinearBendPointTolerancePx,
        );
        if (dissolved === points) {
            return edge;
        }
        if (this.#savedPoints.get(edge.id) === points) {
            this.#savedPoints.set(edge.id, dissolved);
        }
        if (!this.#disposed) {
            this.#replaceShownPoints(edge.id, points, dissolved);
        }
        return { ...edge, data: { ...edge.data, bendPoints: dissolved } };
    }

    #replaceShownPoints(edgeId, points, nextPoints) {
        let changed = false;
        const edges = this.#getEdges().map(edge => {
            if (edge.id !== edgeId || edge.data?.bendPoints !== points) {
                return edge;
            }
            changed = true;
            return { ...edge, data: { ...edge.data, bendPoints: nextPoints } };
        });
        if (changed) {
            this.#setEdges(edges);
        }
    }

    #toSentLayout(edge) {
        const layout = toEdgeLayoutDTO(edge);
        for (const point of layout.points) {
            point.id = this.#storedIds.get(point.id) ?? point.id;
        }
        return layout;
    }

    /**
     * Sends a request once the earlier requests of the given edges are answered, and takes over
     * the mRIDs of the new points it answers with.
     */
    #enqueue(edgeIds, send) {
        const earlierRequests = edgeIds
            .map(edgeId => this.#pendingRequests.get(edgeId))
            .filter(Boolean);
        const request = Promise.all(earlierRequests)
            .then(send)
            .then(({ data, error }) => {
                if (error) {
                    throw error;
                }
                this.#acceptStoredIds(data);
            })
            .catch(error =>
                console.error("Saving the edge layout failed:", error),
            )
            .finally(() => {
                for (const edgeId of edgeIds) {
                    if (this.#pendingRequests.get(edgeId) === request) {
                        this.#pendingRequests.delete(edgeId);
                    }
                }
            });
        for (const edgeId of edgeIds) {
            this.#pendingRequests.set(edgeId, request);
        }
    }

    /**
     * Remembers the mRIDs of new points and puts them into the shown edges. A selected edge keeps
     * the ids it has, as one of its points may be dragged right now; its points are sent with
     * their mRIDs nonetheless.
     */
    #acceptStoredIds(storedIds) {
        if (!storedIds?.length) {
            return;
        }
        for (const { clientId, id } of storedIds) {
            this.#storedIds.set(clientId, id);
        }
        if (this.#disposed) {
            return;
        }
        let changed = false;
        const edges = this.#getEdges().map(edge => {
            const points = edge.data?.bendPoints;
            const nextPoints = edge.selected
                ? points
                : withStoredIds(points, this.#storedIds);
            if (nextPoints === points) {
                return edge;
            }
            changed = true;
            if (this.#savedPoints.get(edge.id) === points) {
                this.#savedPoints.set(edge.id, nextPoints);
            }
            return { ...edge, data: { ...edge.data, bendPoints: nextPoints } };
        });
        if (changed) {
            this.#setEdges(edges);
        }
    }
}
