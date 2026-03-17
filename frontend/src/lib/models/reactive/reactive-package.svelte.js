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
import {
    isInvalidLabel,
    isInvalidNamespace,
    isInvalidUuid,
} from "$lib/models/reactive/validity-rules/validityFunctions.js";

export class ReactivePackage {
    constructor({
        uuid = null,
        label = "",
        namespace = "",
        comment = null,
    } = {}) {
        this.uuid = new ReactiveValueWrapper(uuid, isInvalidUuid);
        this.label = new ReactiveValueWrapper(label, isInvalidLabel);
        this.namespace = new ReactiveValueWrapper(
            namespace,
            isInvalidNamespace,
        );
        this.comment = new ReactiveValueWrapper(comment);
    }

    /**
     * The unique identifier of this package
     * @type {ReactiveValueWrapper<string | null>}
     */
    uuid;

    /**
     * The label/name of this package
     * @type {ReactiveValueWrapper<string>}
     */
    label;

    /**
     * The namespace (URI prefix) of this package
     * @type {ReactiveValueWrapper<string>}
     */
    namespace;

    /**
     * A comment describing this package
     * @type {ReactiveValueWrapper<string | null>}
     */
    comment;

    // noinspection JSUnresolvedFunction
    /**
     * Indicates whether this package has changes
     * @type {boolean}
     */
    isModified = $derived(
        this.uuid.isModified ||
            this.label.isModified ||
            this.namespace.isModified ||
            this.comment.isModified,
    );

    // noinspection JSUnresolvedFunction
    /**
     * Indicates whether this package is valid
     * @type {boolean}
     */
    isValid = $derived(
        this.uuid.isValid &&
            this.label.isValid &&
            this.namespace.isValid &&
            this.comment.isValid,
    );

    /**
     * Resets this package to its initial values
     */
    reset() {
        this.uuid.reset();
        this.label.reset();
        this.namespace.reset();
        this.comment.reset();
    }

    /**
     * Applies changes made to this package as the new baseline
     */
    save() {
        this.uuid.save();
        this.label.save();
        this.namespace.save();
        this.comment.save();
    }

    /**
     * Checks if this package is equal to another package
     * @param {ReactivePackage} other - The other package to compare to
     * @returns {boolean} True if the packages are equal
     */
    equals(other) {
        // not checking uuid as two different packages with same properties should be considered equal
        return (
            !!other &&
            this.label.equals(other.label) &&
            this.namespace.equals(other.namespace) &&
            this.comment.equals(other.comment)
        );
    }

    /**
     * Converts this package to a plain object
     * @returns {Object} Plain object representation of this package
     */
    getPlainObject() {
        return {
            uuid: this.uuid.getPlainObject(),
            label: this.label.getPlainObject(),
            namespace: this.namespace.getPlainObject(),
            comment: this.comment.getPlainObject(),
        };
    }
}
