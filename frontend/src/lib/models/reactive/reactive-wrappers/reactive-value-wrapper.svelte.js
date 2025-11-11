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

export class ReactiveValueWrapper {
    /**
     * @param {*} value - The initial value to be wrapped
     * @param {Array<function(*): string[]> | function(*): string[]} violationChecks - An array of functions to validate the value
     */
    constructor(value, violationChecks = []) {
        if (value instanceof ReactiveValueWrapper) {
            value = value.value;
        }
        const checks = Array.isArray(violationChecks)
            ? violationChecks
            : [violationChecks];
        this.violationChecks.push(...checks);

        this.backup = value;
        this.value = value;
    }

    backup = $state();

    value = $state();

    isModified = $derived(!this.equals(this.backup));

    /**
     * Holds the functions to check for violations of the current value. Each function must return an array of violation strings.
     * @type {Array<function(*): string[]>}
     */
    violationChecks = [];

    /**
     * The validations of the current value
     * @type {string[]}
     */
    violations = $derived(
        this.violationChecks.flatMap(validationFunction =>
            validationFunction(this.value),
        ),
    );

    /**
     * Indicates whether the current value is valid
     * @type {boolean}
     */
    isValid = $derived(!this.violations || this.violations.length === 0);

    /**
     * Resets the current value to the backup value
     */
    reset() {
        this.value = this.backup;
    }

    /**
     * Saves the current value as the new backup value
     */
    save() {
        this.backup = this.value;
    }

    /**
     * Compares the current value with another ReactiveValueWrapper or a raw value
     * @param other
     * @returns {boolean}
     */
    equals(other) {
        if (other instanceof ReactiveValueWrapper) {
            other = other.value;
        }
        if (
            (other === null || other === undefined || other === "") &&
            (this.value === null ||
                this.value === undefined ||
                this.value === "")
        ) {
            return true;
        }
        return this.value === other;
    }

    getPlainObject() {
        if (this.value === undefined || this.value === "") {
            return null;
        }
        return this.value;
    }
}
