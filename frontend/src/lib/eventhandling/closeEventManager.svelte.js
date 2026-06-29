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

export let eventStack;

export const EventType = {
    DIALOG: "dialog",
    CLASS_EDITOR: "classEditor",
};

class EventStack {
    constructor() {
        this.stack = [];
        this.actionGuard = null;
    }

    stack;

    /**
     * @param {() => void} event - the close handler
     * @param {string} [type] - optional category, e.g. "DIALOG" or "CLASS_EDITOR"
     */
    addEvent(event, type = EventType.DIALOG) {
        this.stack.push({ event, type });
    }

    removeEvent(event) {
        const idOfEvent = this.stack.findIndex(e => e.event === event);
        if (idOfEvent === -1) return;
        this.stack.splice(idOfEvent, 1);
    }

    executeNewestEvent(...args) {
        if (this.stack.length === 0) {
            console.debug(
                "Trying to execute newest event on an empty event stack",
                this.stack.length,
            );
            return;
        }
        this.stack.at(-1).event(...args);
    }

    /**
     * Closes all open events whose type is not in the excludedTypes list.
     * Used when opening a new dialog via shortcut: all other dialogs close,
     * but e.g. the ClassEditor (type "classEditor") stays open.
     * @param {string[]} excludedTypes
     */
    closeAllExcept(excludedTypes = []) {
        const toClose = this.stack
            .filter(e => !excludedTypes.includes(e.type))
            .reverse();

        for (const { event } of toClose) {
            if (event() === false) {
                return false;
            }
        }
        return true;
    }

    registerActionGuard(fn) {
        this.actionGuard = fn;
    }

    unregisterActionGuard(fn) {
        if (this.actionGuard === fn) {
            this.actionGuard = null;
        }
    }

    guardAction(action) {
        if (this.actionGuard) {
            const result = this.actionGuard(action);
            return result ?? Promise.resolve(false);
        }
        return action();
    }
}
eventStack = new EventStack();
