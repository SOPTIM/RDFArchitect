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
    isInvalidMultiplicityLowerBound,
    isInvalidMultiplicityUpperBound,
    isInvalidNamespace,
    isInvalidUuid,
    isInvalidDatatypeUri,
} from "$lib/models/reactive/validity-rules/validityFunctions.js";

export class ReactiveAttribute {
    constructor({
        uuid = null,
        label = "",
        namespace = "",
        multiplicityLowerBound = 0,
        multiplicityUpperBound = null,
        datatype = null,
        comment = null,
        fixedValue = null,
        defaultValue = null,
    } = {}) {
        this.uuid = new ReactiveValueWrapper(uuid, isInvalidUuid);
        this.label = new ReactiveValueWrapper(label, isInvalidLabel);
        this.namespace = new ReactiveValueWrapper(
            namespace,
            isInvalidNamespace,
        );
        this.multiplicityLowerBound = new ReactiveValueWrapper(
            multiplicityLowerBound,
            lowerBound =>
                isInvalidMultiplicityLowerBound(
                    lowerBound,
                    this.multiplicityUpperBound.value,
                ),
        );
        this.multiplicityUpperBound = new ReactiveValueWrapper(
            multiplicityUpperBound,
            upperBound =>
                isInvalidMultiplicityUpperBound(
                    upperBound,
                    this.multiplicityLowerBound.value,
                ),
        );
        this.datatype = new ReactiveValueWrapper(
            datatype,
            isInvalidDatatypeUri,
        );
        this.comment = new ReactiveValueWrapper(comment);
        this.fixedValue = new ReactiveValueWrapper(fixedValue);
        this.defaultValue = new ReactiveValueWrapper(defaultValue);
    }

    /**
     * The unique identifier of this attribute
     * @type {ReactiveValueWrapper<string | null>}
     */
    uuid;

    /**
     * The label/name of this attribute
     * @type {ReactiveValueWrapper<string>}
     */
    label;

    /**
     * The namespace of this attribute
     * @type {ReactiveValueWrapper<string>}
     */
    namespace;

    /**
     * The lower bound of the multiplicity of this attribute
     * @type {ReactiveValueWrapper<number>}
     */
    multiplicityLowerBound;

    /**
     * The upper bound of the multiplicity of this attribute
     * @type {ReactiveValueWrapper<number | null>}
     */
    multiplicityUpperBound;

    /**
     * The datatype of this attribute
     * is either an uuid or an uri
     * @type {ReactiveValueWrapper<string>}
     */
    datatype;

    /**
     * A comment describing this attribute
     * @type {ReactiveValueWrapper<string>}
     */
    comment;

    /**
     * The fixed value of this attribute if specified
     * @type {ReactiveValueWrapper<string | null>}
     */
    fixedValue;

    /**
     * The default value of this attribute if specified
     * @type {ReactiveValueWrapper<string | null>}
     */
    defaultValue;

    // noinspection JSUnresolvedFunction
    /**
     * Indicates whether this attribute has changes
     * @type {boolean}
     */
    isModified = $derived(
        this.uuid.isModified ||
            this.label.isModified ||
            this.namespace.isModified ||
            this.multiplicityLowerBound.isModified ||
            this.multiplicityUpperBound.isModified ||
            this.datatype.isModified ||
            this.comment.isModified ||
            this.fixedValue.isModified ||
            this.defaultValue.isModified,
    );

    // noinspection JSUnresolvedFunction
    /**
     * Indicates whether this attribute is valid
     * @type {boolean}
     */
    isValid = $derived(
        this.uuid.isValid &&
            this.label.isValid &&
            this.namespace.isValid &&
            this.multiplicityLowerBound.isValid &&
            this.multiplicityUpperBound.isValid &&
            this.datatype.isValid &&
            this.comment.isValid &&
            this.fixedValue.isValid &&
            this.defaultValue.isValid,
    );

    /**
     * Resets this attribute to its initial values
     */
    reset() {
        this.uuid.reset();
        this.label.reset();
        this.namespace.reset();
        this.multiplicityLowerBound.reset();
        this.multiplicityUpperBound.reset();
        this.datatype.reset();
        this.comment.reset();
        this.fixedValue.reset();
        this.defaultValue.reset();
    }

    /**
     * Applies changes made to this attribute as the new baseline
     */
    save() {
        this.uuid.save();
        this.label.save();
        this.namespace.save();
        this.multiplicityLowerBound.save();
        this.multiplicityUpperBound.save();
        this.datatype.save();
        this.comment.save();
        this.fixedValue.save();
        this.defaultValue.save();
    }

    /**
     * Checks if this attribute is equal to another attribute
     * @param {ReactiveAttribute} other - The other attribute to compare to
     * @returns {boolean} True if the attributes are equal
     */
    equals(other) {
        // not checking uuid as two different attributes with same properties should be considered equal
        return (
            !!other &&
            this.label.equals(other.label) &&
            this.namespace.equals(other.namespace) &&
            this.multiplicityLowerBound.equals(other.multiplicityLowerBound) &&
            this.multiplicityUpperBound.equals(other.multiplicityUpperBound) &&
            this.datatype.equals(other.datatype) &&
            this.comment.equals(other.comment) &&
            this.fixedValue.equals(other.fixedValue) &&
            this.defaultValue.equals(other.defaultValue)
        );
    }

    /**
     * Converts this attribute to a plain object
     * @returns {Object} Plain object representation of this attribute
     */
    getPlainObject() {
        return {
            uuid: this.uuid.getPlainObject(),
            label: this.label.getPlainObject(),
            namespace: this.namespace.getPlainObject(),
            multiplicityLowerBound:
                this.multiplicityLowerBound.getPlainObject(),
            multiplicityUpperBound:
                this.multiplicityUpperBound.getPlainObject(),
            datatype: this.datatype.getPlainObject(),
            comment: this.comment.getPlainObject(),
            fixedValue: this.fixedValue.getPlainObject(),
            defaultValue: this.defaultValue.getPlainObject(),
        };
    }
}
