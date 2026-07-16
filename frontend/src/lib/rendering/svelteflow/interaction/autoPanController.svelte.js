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

import { EDGE_INTERACTION_CONFIG } from "./edgeInteractionConfig.js";

const {
    edgeZonePx: AUTO_PAN_EDGE_PX,
    minSpeedPx: AUTO_PAN_MIN_SPEED_PX,
    maxSpeedPx: AUTO_PAN_MAX_SPEED_PX,
} = EDGE_INTERACTION_CONFIG.autoPan;

/**
 * Drives an auto-pan loop that shifts the SvelteFlow viewport while the cursor
 * sits near the container border during a drag. Once started, it runs on every
 * animation frame (independently of pointer movement) and reports the last known
 * pointer event back via a tick callback, so the dragged element can follow the
 * shifting canvas.
 */
export class AutoPanController {
    #getViewport;
    #setViewport;
    #getContainerRect;

    #frame = null;
    #lastPointerEvent = null;
    #onTick = null;

    constructor({ getViewport, setViewport, getContainerRect }) {
        this.#getViewport = getViewport;
        this.#setViewport = setViewport;
        this.#getContainerRect = getContainerRect;
    }

    /**
     * Records the latest pointer event and ensures the loop is running.
     * `onTick` is called once per frame with the last pointer event whenever the
     * viewport was actually shifted, so the caller can re-apply its drag move.
     */
    update(event, onTick) {
        this.#lastPointerEvent = event;
        this.#onTick = onTick;
        this.#start();
    }

    /** Stops the loop and clears the retained pointer state. */
    stop() {
        if (this.#frame !== null) {
            cancelAnimationFrame(this.#frame);
            this.#frame = null;
        }
        this.#lastPointerEvent = null;
        this.#onTick = null;
    }

    #start() {
        if (this.#frame === null) {
            this.#frame = requestAnimationFrame(this.#tick);
        }
    }

    #tick = () => {
        if (!this.#lastPointerEvent) {
            this.#frame = null;
            return;
        }
        const { dx, dy } = this.#computeDelta(this.#lastPointerEvent);
        if (dx !== 0 || dy !== 0) {
            const viewport = this.#getViewport();
            this.#setViewport({
                x: viewport.x + dx,
                y: viewport.y + dy,
                zoom: viewport.zoom,
            });
            this.#onTick?.(this.#lastPointerEvent);
        }
        this.#frame = requestAnimationFrame(this.#tick);
    };

    #computeDelta(event) {
        const rect = this.#getContainerRect();
        if (!rect) return { dx: 0, dy: 0 };

        return {
            dx: this.#axisSpeed(
                event.clientX - rect.left,
                rect.right - event.clientX,
            ),
            dy: this.#axisSpeed(
                event.clientY - rect.top,
                rect.bottom - event.clientY,
            ),
        };
    }

    // Returns the pan speed for one axis. Positive speed pans towards the
    // start (left/top) edge, negative towards the end (right/bottom) edge.
    #axisSpeed(distanceToStart, distanceToEnd) {
        if (distanceToStart < AUTO_PAN_EDGE_PX) {
            return this.#scaledSpeed(AUTO_PAN_EDGE_PX - distanceToStart);
        }
        if (distanceToEnd < AUTO_PAN_EDGE_PX) {
            return -this.#scaledSpeed(AUTO_PAN_EDGE_PX - distanceToEnd);
        }
        return 0;
    }

    // Maps how deep the cursor is inside the edge zone onto a speed between the
    // min and max, clamped at the max (also when pushed past the container edge).
    #scaledSpeed(depthIntoZone) {
        const ratio = Math.min(depthIntoZone / AUTO_PAN_EDGE_PX, 1);
        return (
            AUTO_PAN_MIN_SPEED_PX +
            ratio * (AUTO_PAN_MAX_SPEED_PX - AUTO_PAN_MIN_SPEED_PX)
        );
    }
}
