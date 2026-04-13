/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import { ReactiveValueWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-value-wrapper.svelte.js";
import {
    isInvalidNamespaceIri,
    isInvalidNamespacePrefix,
} from "$lib/models/reactive/validity-rules/validityFunctions.js";

export class ReactiveNamespace {
    constructor({ iri = "", prefix = "" } = {}) {
        this.iri = new ReactiveValueWrapper(iri, isInvalidNamespaceIri);
        this.prefix = new ReactiveValueWrapper(
            prefix,
            isInvalidNamespacePrefix,
        );
    }

    /**
     * The iri associated with this namespace
     * @type {ReactiveValueWrapper<string>}
     */
    iri;

    /**
     * The prefix associated with this namespace
     * @type {ReactiveValueWrapper<string>}
     */
    prefix;

    /**
     * Indicates whether this namespace has changes
     * @type {boolean}
     */
    isModified = $derived(this.iri.isModified || this.prefix.isModified);

    /**
     * Indicates whether this namespace is valid
     * @type {boolean}
     */
    isValid = $derived(this.iri.isValid && this.prefix.isValid);

    /**
     * Resets this namespace to its initial values
     */
    reset() {
        this.iri.reset();
        this.prefix.reset();
    }

    /**
     * Applies changes made to this namespace as the new baseline
     */
    save() {
        this.iri.save();
        this.prefix.save();
    }

    /**
     * Checks if this namespace is equal to another namespace
     * @param {ReactiveNamespace} other - The other namespace to compare with
     * @returns {boolean} True if the namespaces are equal
     */
    equals(other) {
        return this.iri.equals(other.iri) && this.prefix.equals(other.prefix);
    }

    /**
     * Converts this namespace to a plain object
     * @returns {Object} Plain object representation of this namespace
     */
    getPlainObject() {
        return {
            iri: this.iri.getPlainObject(),
            prefix: this.prefix.getPlainObject(),
        };
    }
}
