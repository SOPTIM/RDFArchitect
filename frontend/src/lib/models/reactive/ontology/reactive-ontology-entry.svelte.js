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

import { ReactiveValueWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-value-wrapper.svelte.js";
import { isNotEmptyValidation } from "$lib/models/reactive/validity-rules/validityFunctions.js";

export class ReactiveOntologyEntry {
    constructor({
                    iri = "",
                    datatypeIri = "",
                    isIriEntry = false,
                    value = ""
                } = {}) {
        this.iri = new ReactiveValueWrapper(iri);
        this.datatypeIri = new ReactiveValueWrapper(datatypeIri);
        this.isIriEntry = new ReactiveValueWrapper(isIriEntry);
        this.value = new ReactiveValueWrapper(value, isNotEmptyValidation);
        this.initializeValidationChecks();
    }

    /**
     * The IRI of the ontology entry
     * @type {ReactiveValueWrapper<string>}
     */
    iri;

    /**
     * The datatype IRI of the ontology entry
     * @type {ReactiveValueWrapper<string>}
     */
    datatypeIri;

    /**
     * Indicates whether this entry is an IRI entry
     * @type {ReactiveValueWrapper<boolean>}
     */
    isIriEntry;

    /**
     * The value of the ontology entry
     * @type {ReactiveValueWrapper<*>}
     */
    value;

    // noinspection JSUnresolvedFunction
    /**
     * Indicates whether this ontology entry has changes
     * @type {boolean}
     */
    isModified = $derived(
        this.iri.isModified ||
        this.datatypeIri.isModified ||
        this.isIriEntry.isModified ||
        this.value.isModified
    );

    // noinspection JSUnresolvedFunction
    /**
     * Indicates whether this ontology entry is valid
     * @type {boolean}
     */
    isValid = $derived(
        this.iri.isValid &&
        this.datatypeIri.isValid &&
        this.isIriEntry.isValid &&
        this.value.isValid
    );

    /**
     * Resets this ontology entry to its initial values
     */
    reset() {
        this.iri.reset();
        this.datatypeIri.reset();
        this.isIriEntry.reset();
        this.value.reset();
    }

    /**
     * Applies changes made to this ontology entry as the new baseline
     */
    save() {
        this.iri.save();
        this.datatypeIri.save();
        this.isIriEntry.save();
        this.value.save();
    }

    /**
     * Compares this ontology entry to another object
     * @param {ReactiveOntologyEntry} other - The other ontology entry to compare to
     * @returns {boolean} True if the ontology entries are equal
     */
    equals(other) {
        return (
            !!other &&
            this.iri.equals(other.iri) &&
            this.datatypeIri.equals(other.datatypeIri) &&
            this.isIriEntry.equals(other.isIriEntry) &&
            this.value.equals(other.value)
        );
    }

    /**
     * Converts this ontology entry to a plain object
     * @returns {Object} Plain object representation of this ontology entry
     */
    getPlainObject() {
        return {
            iri: this.iri.getPlainObject(),
            datatypeIri: this.datatypeIri.getPlainObject(),
            isIriEntry: this.isIriEntry.getPlainObject(),
            value: this.value.getPlainObject()
        };
    }

    /**
     * Initializes the validation checks for this ontology entry
     */
    initializeValidationChecks() {
        this.iri.checkForViolations = isNotEmptyValidation;
        this.value.checkForViolations = isNotEmptyValidation;
    }
}
