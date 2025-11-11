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

export class ReactiveEnumEntry {
    constructor({
        uuid = null,
        namespace = "",
        label = "",
        comment = null,
        stereotype = null,
    } = {}) {
        this.uuid = new ReactiveValueWrapper(uuid, isInvalidUuid);
        this.namespace = new ReactiveValueWrapper(
            namespace,
            isInvalidNamespace,
        );
        this.label = new ReactiveValueWrapper(label, isInvalidLabel);
        this.comment = new ReactiveValueWrapper(comment);
        this.stereotype = new ReactiveValueWrapper(stereotype);
    }

    /**
     * The unique identifier of this enum entry
     * @type {ReactiveValueWrapper<string | null>}
     */
    uuid;

    /**
     * The namespace of this enum entry
     * @type {ReactiveValueWrapper<string>}
     */
    namespace;

    /**
     * The label/name of this enum entry
     * @type {ReactiveValueWrapper<string>}
     */
    label;

    /**
     * A comment describing this enum entry
     * @type {ReactiveValueWrapper<string | null>}
     */
    comment;

    /**
     * The stereotype of this enum entry
     * @type {ReactiveValueWrapper<string | null>}
     */
    stereotype;

    /**
     * Indicates whether this enum entry has changes
     * @type {boolean}
     */
    isModified = $derived(
        this.uuid.isModified ||
            this.namespace.isModified ||
            this.label.isModified ||
            this.comment.isModified ||
            this.stereotype.isModified,
    );

    /**
     * Indicates whether this enum entry is valid
     * @type {boolean}
     */
    isValid = $derived(
        this.uuid.isValid &&
            this.namespace.isValid &&
            this.label.isValid &&
            this.comment.isValid &&
            this.stereotype.isValid,
    );

    /**
     * Resets this enum entry to its initial values
     */
    reset() {
        this.uuid.reset();
        this.namespace.reset();
        this.label.reset();
        this.comment.reset();
        this.stereotype.reset();
    }

    /**
     * Applies changes made to this enum entry as the new baseline
     */
    save() {
        this.uuid.save();
        this.namespace.save();
        this.label.save();
        this.comment.save();
        this.stereotype.save();
    }

    /**
     * Checks if this enum entry is equal to another enum entry
     * @param {ReactiveEnumEntry} other - The other enum entry to compare to
     * @returns {boolean} True if the enum entries are equal
     */
    equals(other) {
        // not checking uuid as two different enum entries with same properties should be considered equal
        return (
            !!other &&
            this.namespace.equals(other.namespace) &&
            this.label.equals(other.label) &&
            this.comment.equals(other.comment) &&
            this.stereotype.equals(other.stereotype)
        );
    }

    /**
     * Converts this enum entry to a plain object
     * @returns {Object} Plain object representation of this enum entry
     */
    getPlainObject() {
        return {
            uuid: this.uuid.getPlainObject(),
            namespace: this.namespace.getPlainObject(),
            label: this.label.getPlainObject(),
            comment: this.comment.getPlainObject(),
            stereotype: this.stereotype.getPlainObject(),
        };
    }
}
