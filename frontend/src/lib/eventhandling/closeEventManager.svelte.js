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

class EventStack {
    constructor() {
        this.stack = [];
    }

    stack;

    addEvent(event) {
        this.stack.push(event);
    }

    removeEvent(event) {
        const idOfEvent = this.stack.indexOf(event);
        if (idOfEvent === -1) return;
        this.stack.splice(idOfEvent, 1);
    }

    executeNewestEvent(...args) {
        if (this.stack.length === 0) {
            console.warn(
                "Trying to execute newest event on an empty event stack",
                this.stack.length,
            );
            return;
        }
        this.stack.at(-1)(...args);
    }
}
eventStack = new EventStack();
