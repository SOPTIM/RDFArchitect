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
    isInvalidMultiplicityLowerBound,
    isInvalidMultiplicityUpperBound,
    isInvalidNamespace,
    isInvalidTarget,
    isInvalidUuid,
} from "$lib/models/reactive/validity-rules/validityFunctions.js";

export class ReactiveAssociation {
    constructor({
        uuid = null,
        label = "",
        namespace = "",
        domain = null,
        target = null,
        multiplicityLowerBound = 0,
        multiplicityUpperBound = null,
        comment = null,
        isUsed = false,
        inverse = {},
    } = {}) {
        // Destructure inverse with defaults
        const {
            uuid: inverseUuid = null,
            label: inverseLabel = "",
            namespace: inverseNamespace = "",
            multiplicityLowerBound: inverseLowerBound = 0,
            multiplicityUpperBound: inverseUpperBound = null,
            comment: inverseComment = null,
            isUsed: inverseIsUsed = false,
        } = inverse;

        // Set top-level properties
        this.uuid = new ReactiveValueWrapper(uuid, isInvalidUuid);
        this.label = new ReactiveValueWrapper(label);
        this.namespace = new ReactiveValueWrapper(
            namespace,
            isInvalidNamespace,
        );
        this.domain = new ReactiveValueWrapper(domain, isInvalidUuid);
        this.target = new ReactiveValueWrapper(target, isInvalidTarget);
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
        this.comment = new ReactiveValueWrapper(comment);
        this.isUsed = new ReactiveValueWrapper(isUsed);

        // Set inverse properties
        this.inverse = {
            uuid: new ReactiveValueWrapper(inverseUuid, isInvalidUuid),
            label: new ReactiveValueWrapper(inverseLabel),
            namespace: new ReactiveValueWrapper(
                inverseNamespace,
                isInvalidNamespace,
            ),
            multiplicityLowerBound: new ReactiveValueWrapper(
                inverseLowerBound,
                lowerBound =>
                    isInvalidMultiplicityLowerBound(
                        lowerBound,
                        this.inverse.multiplicityUpperBound.value,
                    ),
            ),
            multiplicityUpperBound: new ReactiveValueWrapper(
                inverseUpperBound,
                upperBound =>
                    isInvalidMultiplicityUpperBound(
                        upperBound,
                        this.inverse.multiplicityLowerBound.value,
                    ),
            ),
            comment: new ReactiveValueWrapper(inverseComment),
            isUsed: new ReactiveValueWrapper(inverseIsUsed),
        };
    }

    /**
     * The unique identifier of this association
     * @type {ReactiveValueWrapper<string | null>}
     */
    uuid;

    /**
     * The label/name of this association
     * @type {ReactiveValueWrapper<string>}
     */
    label;

    /**
     * The namespace of this association
     * @type {ReactiveValueWrapper<string>}
     */
    namespace;

    /**
     * The domain (source class) of this association
     * @type {ReactiveValueWrapper<string | null>}
     */
    domain;

    /**
     * The target (destination class) of this association
     * @type {ReactiveValueWrapper<string | null>}
     */
    target;

    /**
     * The lower bound of the multiplicity of this association
     * @type {ReactiveValueWrapper<number>}
     */
    multiplicityLowerBound;

    /**
     * The upper bound of the multiplicity of this association
     * @type {ReactiveValueWrapper<number | null>}
     */
    multiplicityUpperBound;

    /**
     * A comment describing this association
     * @type {ReactiveValueWrapper<string | null>}
     */
    comment;

    /**
     * Indicates whether this association is currently used
     * @type {ReactiveValueWrapper<boolean>}
     */
    isUsed;

    /**
     * The inverse association
     * @type {{
     *   uuid: ReactiveValueWrapper<string | null>,
     *   label: ReactiveValueWrapper<string>,
     *   namespace: ReactiveValueWrapper<string>,
     *   multiplicityLowerBound: ReactiveValueWrapper<number>,
     *   multiplicityUpperBound: ReactiveValueWrapper<number | null>,
     *   comment: ReactiveValueWrapper<string | null>,
     *   isUsed: ReactiveValueWrapper<boolean>
     * }}
     */
    inverse;

    /**
     * Indicates whether this association has changes
     * @type {boolean}
     */
    isModified = $derived(
        this.uuid.isModified ||
            this.label.isModified ||
            this.namespace.isModified ||
            this.domain.isModified ||
            this.multiplicityLowerBound.isModified ||
            this.multiplicityUpperBound.isModified ||
            this.target.isModified ||
            this.comment.isModified ||
            this.isUsed.isModified ||
            this.inverse.uuid.isModified ||
            this.inverse.label.isModified ||
            this.inverse.namespace.isModified ||
            this.inverse.multiplicityLowerBound.isModified ||
            this.inverse.multiplicityUpperBound.isModified ||
            this.inverse.comment.isModified ||
            this.inverse.isUsed.isModified,
    );

    /**
     * Indicates whether this association is valid
     * @type {boolean}
     */
    isValid = $derived(
        this.uuid.isValid &&
            this.label.isValid &&
            this.namespace.isValid &&
            this.domain.isValid &&
            this.multiplicityLowerBound.isValid &&
            this.multiplicityUpperBound.isValid &&
            this.target.isValid &&
            this.comment.isValid &&
            this.isUsed.isValid &&
            this.inverse.uuid.isValid &&
            this.inverse.label.isValid &&
            this.inverse.namespace.isValid &&
            this.inverse.multiplicityLowerBound.isValid &&
            this.inverse.multiplicityUpperBound.isValid &&
            this.inverse.comment.isValid &&
            this.inverse.isUsed.isValid,
    );

    /**
     * Resets this association to its initial values
     */
    reset() {
        this.uuid.reset();
        this.label.reset();
        this.namespace.reset();
        this.domain.reset();
        this.multiplicityLowerBound.reset();
        this.multiplicityUpperBound.reset();
        this.target.reset();
        this.comment.reset();
        this.isUsed.reset();
        this.inverse.uuid.reset();
        this.inverse.label.reset();
        this.inverse.namespace.reset();
        this.inverse.multiplicityLowerBound.reset();
        this.inverse.multiplicityUpperBound.reset();
        this.inverse.comment.reset();
        this.inverse.isUsed.reset();
    }

    /**
     * Applies changes made to this association as the new baseline
     */
    save() {
        this.uuid.save();
        this.label.save();
        this.namespace.save();
        this.domain.save();
        this.multiplicityLowerBound.save();
        this.multiplicityUpperBound.save();
        this.target.save();
        this.comment.save();
        this.isUsed.save();
        this.inverse.uuid.save();
        this.inverse.label.save();
        this.inverse.namespace.save();
        this.inverse.multiplicityLowerBound.save();
        this.inverse.multiplicityUpperBound.save();
        this.inverse.comment.save();
        this.inverse.isUsed.save();
    }

    /**
     * Checks if this association is equal to another association
     * @param {ReactiveAssociation} other - The other association to compare to
     * @returns {boolean} True if the associations are equal
     */
    equals(other) {
        // not checking uuid as two different associations with same properties should be considered equal
        return (
            !!other &&
            this.label.equals(other.label) &&
            this.namespace.equals(other.namespace) &&
            this.domain.equals(other.domain) &&
            this.multiplicityLowerBound.equals(other.multiplicityLowerBound) &&
            this.multiplicityUpperBound.equals(other.multiplicityUpperBound) &&
            this.target.equals(other.target) &&
            this.comment.equals(other.comment) &&
            this.isUsed.equals(other.isUsed) &&
            this.inverse.label.equals(other.inverse.label) &&
            this.inverse.namespace.equals(other.inverse.namespace) &&
            this.inverse.multiplicityLowerBound.equals(
                other.inverse.multiplicityLowerBound,
            ) &&
            this.inverse.multiplicityUpperBound.equals(
                other.inverse.multiplicityUpperBound,
            ) &&
            this.inverse.comment.equals(other.inverse.comment) &&
            this.inverse.isUsed.equals(other.inverse.isUsed)
        );
    }

    /**
     * Converts this association to a plain object
     * @returns {Object} Plain object representation of this association
     */
    getPlainObject() {
        return {
            uuid: this.uuid.getPlainObject(),
            label: this.label.getPlainObject(),
            namespace: this.namespace.getPlainObject(),
            domain: this.domain.getPlainObject(),
            multiplicityLowerBound:
                this.multiplicityLowerBound.getPlainObject(),
            multiplicityUpperBound:
                this.multiplicityUpperBound.getPlainObject(),
            target: this.target.getPlainObject(),
            comment: this.comment.getPlainObject(),
            isUsed: this.isUsed.getPlainObject(),
            inverse: {
                uuid: this.inverse.uuid.getPlainObject(),
                label: this.inverse.label.getPlainObject(),
                namespace: this.inverse.namespace.getPlainObject(),
                multiplicityLowerBound:
                    this.inverse.multiplicityLowerBound.getPlainObject(),
                multiplicityUpperBound:
                    this.inverse.multiplicityUpperBound.getPlainObject(),
                comment: this.inverse.comment.getPlainObject(),
                isUsed: this.inverse.isUsed.getPlainObject(),
            },
        };
    }
}
