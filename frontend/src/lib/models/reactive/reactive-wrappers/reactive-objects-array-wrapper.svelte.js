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

export class ReactiveObjectsArrayWrapper {
    /**
     * @template T
     * @param {T[]} values - The initial array of values to be wrapped. Each value must implement isModified, isInvalid, and reset()
     * @param EntryClass - A Class implementing .equals(other), .reset(), save(), .isModified and .isValid
     * @param {function} initializeEntryClassValidationChecks - Optional custom initialization function for the EntryClass's violation checks
     */
    constructor(
        values,
        EntryClass,
        initializeEntryClassValidationChecks = () => {},
    ) {
        this.#EntryClass = EntryClass;
        this.backup = values.map(value => new EntryClass(value));
        this.values = [...this.backup];
        this.#entryClassViolationChecksInit = (entry, allEntries) => {
            initializeEntryClassValidationChecks?.(entry, allEntries);
        };
        for (const entry of this.values) {
            this.#entryClassViolationChecksInit(entry, this.values);
        }
    }

    #EntryClass;

    #entryClassViolationChecksInit;

    backup = $state();

    values = $state();

    // noinspection JSUnresolvedFunction
    /**
     * Indicates whether this array has changes compared to its initial values
     * @type {boolean}
     */
    isModified = $derived.by(() => {
        if (this.values.length !== this.backup.length) {
            // Length has changed -> entry was added or removed
            return true;
        }
        for (const backupEntry of this.backup) {
            if (backupEntry.isModified) {
                // one of the original entries has changes
                return true;
            }
            if (!this.values.some(entry => entry.equals(backupEntry))) {
                // one of the original entries is missing (accounts for possible new entry that is equal to the old one (but a different instance))
                return true;
            }
        }
        return false;
    });

    // noinspection JSUnresolvedFunction
    /**
     * Indicates whether all entries in the array are valid
     * @type {boolean}
     */
    isValid = $derived.by(() => !this.values.some(entry => !entry.isValid));

    /**
     * Checks whether the array contains a specific value
     * @param value
     * @returns {boolean}
     */
    contains(value) {
        return this.values.some(v => v.equals(value));
    }

    /**
     * Adds a value to the end of the array
     * @param value - The value to be added
     */
    append(value) {
        const newEntry = new this.#EntryClass(value);
        this.values.push(newEntry);
        this.#entryClassViolationChecksInit(newEntry, this.values);
    }

    /**
     * Adds a value to the beginning of the array
     * @param value - The value to be added
     */
    prepend(value) {
        const newEntry = new this.#EntryClass(value);
        this.values.unshift(newEntry);
        this.#entryClassViolationChecksInit(newEntry, this.values);
    }

    /**
     * Removes a value from the array
     * @param value - The value to be removed
     * @param {boolean} removeExactMatch - If true, removes the exact instance; if false, removes by equality check
     */
    remove(value, removeExactMatch = false) {
        const filtered = this.values.filter(v =>
            removeExactMatch ? v !== value : !v.equals(value),
        );
        this.values.length = 0;
        this.values.push(...filtered);
    }

    /**
     * Resets the array to its initial values
     */
    reset() {
        for (const entry of this.backup) {
            entry.reset();
        }
        this.values.length = 0;
        this.values.push(...this.backup);
    }

    /**
     * Applies changes made to the array and its entries as the new baseline
     */
    save() {
        for (const entry of this.values) {
            entry.save();
        }
        this.backup.length = 0;
        this.backup.push(...this.values);
    }

    getPlainObject() {
        return this.values.map(entry => entry.getPlainObject());
    }
}
