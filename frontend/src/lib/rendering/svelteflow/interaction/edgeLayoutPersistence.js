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
    updateClassPositions,
    updateDatasetClassPositions,
    updateDatasetDiagramLayout,
    updateDatasetEdgeLayouts,
    updateDiagramLayout,
    updateEdgeLayouts,
} from "$lib/api/generated/index.ts";

import { EDGE_INTERACTION_CONFIG } from "./edgeInteractionConfig.js";
import {
    dissolveCollinearBendPoints,
    getSourceDockingPoint,
    getTargetDockingPoint,
} from "./edgePointOperations.js";

/**
 * Whether a point of an edge is being dragged right now. Set by the component that drags the
 * points, the drag is saved once the point is dropped.
 */
export const edgePointDrag = { active: false };

/**
 * The layout of an edge as the backend stores it: the full, ordered list of its edge points from
 * the source to the target, each docking point with the side of the class it is pinned to.
 */
export function toEdgeLayoutDTO(edge) {
    const points = edge.data?.edgePoints ?? [];
    const sourceDockingPoint = getSourceDockingPoint(points);
    const targetDockingPoint = getTargetDockingPoint(points);
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
            side: sideOf(point, sourceDockingPoint, targetDockingPoint),
        })),
    };
}

function sideOf(point, sourceDockingPoint, targetDockingPoint) {
    if (point === sourceDockingPoint) {
        return "source";
    }
    if (point === targetDockingPoint) {
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

function requestClassPositions(target, classes) {
    if (target.graphURI) {
        return updateClassPositions({
            path: {
                datasetName: target.datasetName,
                graphURI: target.graphURI,
                diagramUUID: target.diagramUUID,
            },
            body: classes,
        });
    }
    return updateDatasetClassPositions({
        path: {
            datasetName: target.datasetName,
            diagramUUID: target.diagramUUID,
        },
        body: classes,
    });
}

function requestDiagramLayout(target, diagramLayout, keepalive) {
    if (target.graphURI) {
        return updateDiagramLayout({
            path: {
                datasetName: target.datasetName,
                graphURI: target.graphURI,
                diagramUUID: target.diagramUUID,
            },
            body: diagramLayout,
            keepalive,
        });
    }
    return updateDatasetDiagramLayout({
        path: {
            datasetName: target.datasetName,
            diagramUUID: target.diagramUUID,
        },
        body: diagramLayout,
        keepalive,
    });
}

function withoutDuplicateLabels(labels) {
    const seen = new Set();
    return labels.filter(label => {
        const key = `${label.identifiedObjectUUID}:${label.kind}`;
        if (seen.has(key)) {
            return false;
        }
        seen.add(key);
        return true;
    });
}

/**
 * Saves the points of the edges of the shown diagram. An edge is saved as soon as it can no longer
 * be edited, i.e. when it is deselected, when another diagram is loaded and when the page is left.
 * While classes are dragged, the docking points of their edges move along; those edges are saved
 * together with the classes once the drag ends. Neither a class drag nor a point drag is saved
 * before the drop.
 *
 * Before an edge is saved, its unnecessary bend points are dissolved, in the shown edge as well, so
 * a bend point that was created but never moved off the line is not stored. An edge that ends up
 * with the points it was saved with is not sent at all.
 *
 * Changes are detected by the identity of the points array of an edge, which every edit replaces.
 * The requests of an edge are sent one after another, so the mRIDs the backend returns for new
 * points are known before the edge is sent again.
 *
 * Manually placed labels sit relative to the point where their edge meets the class, so they move
 * along when the edge points of their edge change. They are saved together with their edge in one
 * request.
 */
export class EdgeLayoutPersistence {
    #getEdges;
    #setEdges;
    #getTarget;
    #isReadOnly;
    #getLabelsOf;

    #target = null;
    #savedPoints = new Map();
    #persistedPoints = new Map();
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
     * @param {(edges: Array) => Array} [options.getLabelsOf] the positions of the manually placed
     *     labels of the given shown edges, saved together with them
     */
    constructor({
        getEdges,
        setEdges,
        getTarget,
        isReadOnly,
        getLabelsOf = () => [],
    }) {
        this.#getEdges = getEdges;
        this.#setEdges = setEdges;
        this.#getTarget = getTarget;
        this.#isReadOnly = isReadOnly;
        this.#getLabelsOf = getLabelsOf;
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
            edges.map(edge => [edge.id, edge.data?.edgePoints]),
        );
        this.#persistedPoints = new Map(
            edges.map(edge => [edge.id, toEdgeLayoutDTO(edge).points]),
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

    /**
     * Takes the points of the given edges as saved without sending them, for points the diagram
     * moved on its own, e.g. docking points kept on the border of a resized class. Saving them would
     * clear the redo stack of the backend, e.g. right after an undo. They are sent with the next
     * change of their edge. An edge whose points were changed before, by the user, keeps that change
     * to be saved as usual, even if it was not recorded yet because a drag held it back.
     *
     * @param {Array} previousEdges the edges before the diagram moved their points
     * @param {Array} edges the edges with the moved points
     */
    acceptWithoutSaving(previousEdges, edges) {
        const previousPoints = new Map(
            previousEdges.map(edge => [edge.id, edge.data?.edgePoints]),
        );
        for (const edge of edges) {
            if (
                this.#savedPoints.has(edge.id) &&
                !this.#unsavedEdges.has(edge.id) &&
                this.#savedPoints.get(edge.id) === previousPoints.get(edge.id)
            ) {
                this.#savedPoints.set(edge.id, edge.data?.edgePoints);
            }
        }
    }

    /** Holds the edges back until the dragged classes are saved with {@link saveDiagramLayout}. */
    beginNodeDrag() {
        this.#nodeDragActive = true;
    }

    /**
     * Saves the given classes and labels together with the unsaved edges of the shown diagram, or
     * with all of its edges, e.g. after the diagram was laid out anew. The backend then drops the
     * points of the edges hidden by a filter, which do not fit the new layout. Classes that moved
     * without any edge or label are saved on their own.
     */
    saveDiagramLayout({ classes = [], labels = [], allEdges = false }) {
        this.#nodeDragActive = false;
        const edges = this.#getEdges();
        let changedEdges;
        if (allEdges) {
            changedEdges = edges;
            for (const edge of edges) {
                this.#savedPoints.set(edge.id, edge.data?.edgePoints);
                this.#persistedPoints.set(
                    edge.id,
                    toEdgeLayoutDTO(edge).points,
                );
                this.#unsavedEdges.delete(edge.id);
            }
        } else {
            this.#recordChanges(edges);
            changedEdges = this.#changedEdges(
                this.#takeUnsavedEdges(
                    [...this.#unsavedEdges]
                        .filter(
                            ([, unsaved]) => unsaved.target === this.#target,
                        )
                        .map(([edgeId]) => edgeId),
                ).map(unsaved => unsaved.edge),
            );
            labels = withoutDuplicateLabels([
                ...labels,
                ...this.#getLabelsOf(changedEdges),
            ]);
        }
        if (
            this.#isReadOnly() ||
            !this.#target?.diagramUUID ||
            classes.length + labels.length + changedEdges.length === 0
        ) {
            return;
        }
        const target = this.#target;
        if (!allEdges && labels.length + changedEdges.length === 0) {
            this.#enqueue([], () => requestClassPositions(target, classes));
            return;
        }
        this.#enqueue(
            changedEdges.map(edge => edge.id),
            () =>
                requestDiagramLayout(target, {
                    classes,
                    labels,
                    edges: changedEdges.map(edge => this.#toSentLayout(edge)),
                    allEdges,
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
        if (this.#nodeDragActive || edgePointDrag.active) {
            return;
        }
        const readOnly = this.#isReadOnly();
        for (const edge of edges) {
            const points = edge.data?.edgePoints;
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
            edgesByTarget.get(target).push(edge);
        }
        for (const [target, unsavedEdges] of edgesByTarget) {
            const edges = this.#changedEdges(unsavedEdges);
            if (edges.length === 0) {
                continue;
            }
            const labels =
                target === this.#target ? this.#getLabelsOf(edges) : [];
            const send = () => {
                const edgeLayouts = edges.map(edge => this.#toSentLayout(edge));
                return labels.length > 0
                    ? requestDiagramLayout(
                          target,
                          { labels, edges: edgeLayouts },
                          keepalive,
                      )
                    : requestEdgeLayouts(target, edgeLayouts, keepalive);
            };
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
     * The given edges without their unnecessary bend points, leaving out those that end up with
     * the points they were saved with. The remaining ones count as saved from here on.
     */
    #changedEdges(edges) {
        const changedEdges = [];
        for (const unsavedEdge of edges) {
            const edge = this.#withoutUnnecessaryBendPoints(unsavedEdge);
            const points = toEdgeLayoutDTO(edge).points;
            if (this.#samePoints(points, this.#persistedPoints.get(edge.id))) {
                continue;
            }
            this.#persistedPoints.set(edge.id, points);
            changedEdges.push(edge);
        }
        return changedEdges;
    }

    #samePoints(points, persistedPoints) {
        if (!persistedPoints || points.length !== persistedPoints.length) {
            return false;
        }
        return points.every((point, index) => {
            const persisted = persistedPoints[index];
            return (
                this.#storedIdOf(point.id) === this.#storedIdOf(persisted.id) &&
                point.xPosition === persisted.xPosition &&
                point.yPosition === persisted.yPosition &&
                point.side === persisted.side
            );
        });
    }

    #storedIdOf(id) {
        return this.#storedIds.get(id) ?? id;
    }

    /**
     * The edge without its unnecessary bend points. If the edge is still shown with the points it
     * is saved with, the shown edge loses them too.
     */
    #withoutUnnecessaryBendPoints(edge) {
        const points = edge.data?.edgePoints ?? [];
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
        return { ...edge, data: { ...edge.data, edgePoints: dissolved } };
    }

    #replaceShownPoints(edgeId, points, nextPoints) {
        let changed = false;
        const edges = this.#getEdges().map(edge => {
            if (edge.id !== edgeId || edge.data?.edgePoints !== points) {
                return edge;
            }
            changed = true;
            return { ...edge, data: { ...edge.data, edgePoints: nextPoints } };
        });
        if (changed) {
            this.#setEdges(edges);
        }
    }

    #toSentLayout(edge) {
        const layout = toEdgeLayoutDTO(edge);
        for (const point of layout.points) {
            point.id = this.#storedIdOf(point.id);
        }
        return layout;
    }

    /**
     * Sends a request once the earlier requests of the given edges are answered, and takes over
     * the mRIDs of the new points it answers with. If the request fails, its edges no longer count
     * as saved, so their next save sends them in any case.
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
            .catch(error => {
                for (const edgeId of edgeIds) {
                    this.#persistedPoints.delete(edgeId);
                }
                console.error("Saving the edge layout failed:", error);
            })
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
            const points = edge.data?.edgePoints;
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
            return { ...edge, data: { ...edge.data, edgePoints: nextPoints } };
        });
        if (changed) {
            this.#setEdges(edges);
        }
    }
}
