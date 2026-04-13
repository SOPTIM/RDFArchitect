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

export class NavEntry {
    label = $state();
    id = $state();
    tooltip = $state();
    #isOpen = $state(false);
    children = $state([]);
    data = $state(null);

    /** @type {NavEntry | null} */
    parent = $state(null);

    /** @returns {boolean} */
    get isOpen() {
        return this.#isOpen;
    }

    /** @param {{ label?: string, id?: string, tooltip?: string, isOpen?: boolean, data?: any }} config */
    constructor(config = {}) {
        this.label = config.label ?? "";
        this.tooltip = config.tooltip ?? "";
        this.#isOpen = config.isOpen ?? false;
        this.id = config.id ?? this.label;
        this.data = config.data ?? null;
    }

    /** Opens this entry and all ancestors. */
    open() {
        this.#isOpen = true;
        this.parent?.open();
    }

    /** Closes this entry and all descendants recursively. */
    close() {
        this.#isOpen = false;
        this.children.forEach(child => child.close());
    }

    /** Toggles the open state. Opening trickles up, closing trickles down. */
    toggle() {
        if (this.isOpen) {
            this.close();
        } else {
            this.open();
        }
    }

    /** @type {boolean} */
    hasChildren = $derived(this.children.length > 0);
}
