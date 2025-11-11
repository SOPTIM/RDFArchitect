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

import { ReactiveOntologyEntry } from "$lib/models/reactive/ontology/reactive-ontology-entry.svelte.js";
import { ReactiveObjectsArrayWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-objects-array-wrapper.svelte.js";
import { ReactiveValueWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-value-wrapper.svelte.js";
import {
    isInvalidNamespace,
    isInvalidUuid,
} from "$lib/models/reactive/validity-rules/validityFunctions.js";

export class ReactiveOntology {
    constructor(uuid = null, namespace = "", entries = []) {
        this.uuid = new ReactiveValueWrapper(uuid, isInvalidUuid);
        this.namespace = new ReactiveValueWrapper(
            namespace,
            isInvalidNamespace,
        );
        this.entries = new ReactiveObjectsArrayWrapper(
            entries,
            ReactiveOntologyEntry,
        );
    }

    /**
     * The UUID of this ontology
     * @type {ReactiveValueWrapper<string>}
     */
    uuid;

    /**
     * The namespace of this ontology
     * @type {ReactiveValueWrapper<string>}
     */
    namespace;

    /**
     * The entries of this ontology
     * @type {ReactiveObjectsArrayWrapper<ReactiveOntologyEntry>}
     */
    entries;

    /**
     * Indicates whether this ontology has changes
     * @type {boolean}
     */
    isModified = $derived(
        this.uuid.isModified ||
            this.namespace.isModified ||
            this.entries.isModified,
    );

    /**
     * Indicates whether this class is valid
     * @type {boolean}
     */
    isValid = $derived(
        this.uuid.isValid && this.namespace.isValid && this.entries.isValid,
    );

    /**
     * Resets this ontology to its initial values
     */
    reset() {
        this.uuid.reset();
        this.namespace.reset();
        this.entries.reset();
    }

    /**
     * Saves the current values as the new initial values
     */
    save() {
        this.uuid.save();
        this.namespace.save();
        this.entries.save();
    }

    /**
     * Compares this ontology to another object
     * @param {ReactiveOntology} other - The other ontology to compare to
     * @returns {boolean} True if the ontologies are equal
     */
    equals(other) {
        return (
            !!other &&
            this.namespace.equals(other.namespace) &&
            this.entries.equals(other.entries)
        );
    }

    /**
     * Converts this ontology to a plain object
     * @returns {Object} Plain object representation of this ontology
     */
    getPlainObject() {
        return {
            uuid: this.uuid.getPlainObject(),
            namespace: this.namespace.getPlainObject(),
            entries: this.entries.getPlainObject(),
        };
    }
}
