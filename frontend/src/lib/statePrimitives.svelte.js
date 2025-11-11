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
 * StateValuePair allows us to create a state with a value and a trigger,
 * we can subscribe to the trigger or alternatively subscribe to only value changes.
 * Every time the value is updated, the trigger is toggled.
 * The trigger can also be toggled manually.
 */
export class StateValuePair {
    #value = $state(null);
    #toggle = $state(false);

    constructor(value) {
        this.#value = value;
    }

    getValue() {
        return this.#value;
    }

    updateValue(value) {
        if (this.#value !== value) {
            this.#value = value;
            this.#toggle = !this.#toggle;
        }
    }

    subscribe() {
        return this.#toggle;
    }

    trigger() {
        this.#toggle = !this.#toggle;
    }
}

/**
 * A simple trigger you can subscribe to and trigger manually.
 */
export class SimpleTrigger {
    #toggle = $state(false);
    #scheduled = false;

    subscribe() {
        return this.#toggle;
    }

    /**
     * Default trigger: coalesces multiple calls into a single update in the
     * current tick to avoid cascaded UI re-renders.
     */
    trigger() {
        if (this.#scheduled) {
            return;
        }
        this.#scheduled = true;
        queueMicrotask(() => {
            this.#scheduled = false;
            this.#toggle = !this.#toggle;
        });
    }
}
