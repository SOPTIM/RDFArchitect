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

import { multiSelectState } from "$lib/sharedState.svelte.js";

const RIGHT_DRAG_THRESHOLD_PX = 5;

export class PanController {
    #ctrlHeld = $state(false);
    #shiftHeld = $state(false);
    #panningActive = $state(false);

    #manualPan = null;
    #suppressClick = false;
    #suppressContextMenu = false;

    #boxAdditive = false;
    #boxPriorSelection = [];

    #getSvelteFlow;
    #getContainer;

    constructor({ getSvelteFlow, getContainer }) {
        this.#getSvelteFlow = getSvelteFlow;
        this.#getContainer = getContainer;
    }

    get ctrlHeld() {
        return this.#ctrlHeld;
    }

    get shiftHeld() {
        return this.#shiftHeld;
    }

    get panningActive() {
        return this.#panningActive;
    }

    get boxAdditive() {
        return this.#boxAdditive;
    }

    get boxPriorSelection() {
        return this.#boxPriorSelection;
    }

    syncModifierKeys(event) {
        this.#ctrlHeld = event.ctrlKey;
        this.#shiftHeld = event.shiftKey;
    }

    clearModifiers() {
        this.#ctrlHeld = false;
        this.#shiftHeld = false;
    }

    clearAdditive() {
        this.#boxAdditive = false;
    }

    resetBox() {
        this.#boxAdditive = false;
        this.#boxPriorSelection = [];
    }

    handleContainerPointerDown(event) {
        this.#suppressClick = false;
        this.#suppressContextMenu = false;
        if ((event.button === 0 && event.ctrlKey) || event.button === 2) {
            this.#startManualPan(event);
            return;
        }
        this.#boxAdditive = false;
        if (event.button === 0 && event.shiftKey) {
            this.#boxPriorSelection = [...multiSelectState.getSelected()];
            this.#armAdditiveBoxOnDrag(event);
        }
    }

    handleContainerClickCapture(event) {
        if (this.#suppressClick) {
            event.stopPropagation();
            event.preventDefault();
            this.#suppressClick = false;
        }
    }

    handleContainerContextMenuCapture(event) {
        if (this.#suppressContextMenu) {
            event.preventDefault();
            event.stopPropagation();
            this.#suppressContextMenu = false;
        }
    }

    #startManualPan(event) {
        const svelteFlow = this.#getSvelteFlow();
        if (!svelteFlow) {
            return;
        }
        const isCtrlLeft = event.button === 0;
        const viewport = svelteFlow.getViewport();
        this.#manualPan = {
            startX: event.clientX,
            startY: event.clientY,
            vpX: viewport.x,
            vpY: viewport.y,
            zoom: viewport.zoom,
            pointerId: event.pointerId,
            button: event.button,
            moved: false,
        };

        if (isCtrlLeft) {
            event.preventDefault();
            event.stopPropagation();
            this.#suppressClick = true;
            this.#panningActive = true;
            const container = this.#getContainer();
            if (container) {
                container.style.cursor = "grabbing";
                container.setPointerCapture?.(event.pointerId);
            }
        }

        window.addEventListener("pointermove", this.#onManualPanMove);
        window.addEventListener("pointerup", this.#endManualPan, {
            once: true,
        });
    }

    #onManualPanMove = event => {
        if (!this.#manualPan) {
            return;
        }
        const dx = event.clientX - this.#manualPan.startX;
        const dy = event.clientY - this.#manualPan.startY;

        if (
            !this.#manualPan.moved &&
            Math.hypot(dx, dy) > RIGHT_DRAG_THRESHOLD_PX
        ) {
            this.#manualPan.moved = true;
            if (this.#manualPan.button === 2) {
                this.#panningActive = true;
                this.#suppressContextMenu = true;
                const container = this.#getContainer();
                if (container) {
                    container.style.cursor = "grabbing";
                }
            }
        }

        if (this.#manualPan.button === 0 || this.#manualPan.moved) {
            this.#getSvelteFlow().setViewport({
                x: this.#manualPan.vpX + dx,
                y: this.#manualPan.vpY + dy,
                zoom: this.#manualPan.zoom,
            });
        }
    };

    #endManualPan = () => {
        const container = this.#getContainer();
        if (container) {
            container.style.cursor = "";
            if (
                this.#manualPan?.button === 0 &&
                this.#manualPan?.pointerId !== undefined
            ) {
                container.releasePointerCapture?.(this.#manualPan.pointerId);
            }
        }
        this.#manualPan = null;
        this.#panningActive = false;
        window.removeEventListener("pointermove", this.#onManualPanMove);
    };

    #armAdditiveBoxOnDrag(event) {
        const { clientX: startX, clientY: startY } = event;
        const onMove = moveEvent => {
            if (moveEvent.clientX !== startX || moveEvent.clientY !== startY) {
                this.#boxAdditive = true;
                stop();
            }
        };
        const stop = () => {
            window.removeEventListener("pointermove", onMove, true);
            window.removeEventListener("pointerup", stop, true);
        };
        window.addEventListener("pointermove", onMove, true);
        window.addEventListener("pointerup", stop, true);
    }
}
