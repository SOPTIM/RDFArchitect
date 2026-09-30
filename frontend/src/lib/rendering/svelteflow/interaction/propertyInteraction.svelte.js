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

import { bypassesProperties } from "./modifierKeys.svelte.js";

export const propertySelection = createPropertySelection();

export const propertyContextMenu = createPropertyContextMenu();

function samePropertyTarget(one, other) {
    return (
        !!one &&
        !!other &&
        one.classUuid === other.classUuid &&
        one.kind === other.kind &&
        one.propertyUuid === other.propertyUuid
    );
}

function createPropertySelection() {
    let marked = $state(null);

    return {
        get current() {
            return marked;
        },

        isMarked(target) {
            return samePropertyTarget(marked, target);
        },

        mark(target) {
            marked = target;
        },

        clear() {
            if (marked === null) {
                return false;
            }
            marked = null;
            return true;
        },
    };
}

function createPropertyContextMenu() {
    let request = $state(null);

    return {
        get request() {
            return request;
        },

        open(event, target) {
            if (bypassesProperties(event)) {
                return;
            }
            event.preventDefault();
            event.stopPropagation();
            request = { x: event.clientX, y: event.clientY, target };
        },

        close() {
            request = null;
        },
    };
}
