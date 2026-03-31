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

export class ReactiveValueCompareWrapper {
    /**
     * @param {*} value - The initial value to be wrapped
     * @param compareValues - The values to compare with when checking for modifications
     * @param {Array<function(*): string[]> | function(*): string[]} violationChecks - An array of functions to validate the value
     * @param secondValue - A second optional value that can be used for the violation checks
     */
    constructor(value, compareValues, violationChecks = [], secondValue = null) {
        if (value instanceof ReactiveValueCompareWrapper) {
            value = value.value;
        }
        const checks = Array.isArray(violationChecks)
            ? violationChecks
            : [violationChecks];
        this.violationChecks.push(...checks);

        this.backup = value;
        this.value = value;
        this.secondValue = secondValue;
        this.compareValues = compareValues;
    }

    backup = $state();

    value = $state();

    secondValue = $state();

    compareValues = $state();

    isModified = $derived(!this.equals(this.backup));

    /**
     * Holds the functions to check for violations of the current value. Each function must return an array of violation strings.
     * @type {Array<function(*, *): string[]>}
     */
    violationChecks = [];

    /**
     * The validations of the current value
     * @type {string[]}
     */
    violations = $derived(
        this.violationChecks.flatMap(validationFunction => {
            console.log("validating");
            const second = typeof this.secondValue === 'function'
                ? this.secondValue()
                : this.secondValue;
            if (second !== null) {
                return validationFunction(this.value, second, this.compareValues);
            } else {
                validationFunction(this.value, this.compareValues);
            }
        })
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
        if (other instanceof ReactiveValueCompareWrapper) {
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
