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

/**
 * Central configuration for edge interaction behaviour (bend points, end points
 * and the auto-pan while dragging them). Kept separate from the geometric edge
 * rendering constants in edgeUtils.ts, which describe how edges are drawn rather
 * than how the user interacts with them.
 */
export const EDGE_INTERACTION_CONFIG = {
    /** Maximum number of active bend points allowed per edge. */
    maxBendPointsPerEdge: 20,

    /** Radius (in flow units) of an active bend/end point handle. */
    activePointRadiusPx: 8,
    /** Radius (in flow units) of an inactive bend/end point hint. */
    inactivePointRadiusPx: 7,

    /**
     * Base hit radius (in screen pixels) for detecting a bend or end point under
     * the cursor on right-click. Divided by the zoom level so the felt hit area
     * stays constant across zoom levels.
     */
    pointHitRadiusPx: 10,

    /** Auto-pan behaviour while dragging a bend or end point near the border. */
    autoPan: {
        /** Distance from the container edge within which auto-pan triggers. */
        edgeZonePx: 40,
        /** Minimum pan speed (px per frame) at the outer border of the zone. */
        minSpeedPx: 0.5,
        /** Maximum pan speed (px per frame) at (or past) the container edge. */
        maxSpeedPx: 22,
    },
};
