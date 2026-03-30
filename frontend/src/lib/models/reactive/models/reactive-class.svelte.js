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

import { ReactiveAssociation } from "$lib/models/reactive/models/reactive-association.svelte.js";
import { ReactiveAttribute } from "$lib/models/reactive/models/reactive-attribute.svelte.js";
import { ReactiveEnumEntry } from "$lib/models/reactive/models/reactive-enum-entry.svelte.js";
import { ReactiveObjectsArrayWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-objects-array-wrapper.svelte.js";
import { ReactiveValueCompareWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-value-compare-wrapper.svelte.js";
import { ReactiveValueWrapper } from "$lib/models/reactive/reactive-wrappers/reactive-value-wrapper.svelte.js";
import {
    hasUniqueIRI,
    hasUniqueLabel,
    isInvalidClassLabel,
    isInvalidNamespace,
    isInvalidStereotype,
    isInvalidUuid,
} from "$lib/models/reactive/validity-rules/validityFunctions.js";

function initializeStereotypeViolationChecks(stereotype, stereotypesArray) {
    stereotype.violationChecks.push(stereotype =>
        isInvalidStereotype(stereotype, stereotypesArray),
    );
}

function initializeUniqueLabelChecks(reactiveObject, enumEntriesArray) {
    reactiveObject.label.violationChecks.push(label =>
        hasUniqueLabel(label, enumEntriesArray),
    );
}

export class ReactiveClass {
    constructor(
        uuid = null,
        namespace = "",
        label = "",
        pack = null,
        superClass = null,
        comment = null,
        stereotypes = [],
        attributes = [],
        associations = [],
        enumEntries = [],
        compareClasses = [],
    ) {
        compareClasses = compareClasses.filter(c => c.uuid !== uuid);
        this.uuid = new ReactiveValueWrapper(uuid, isInvalidUuid);
        this.namespace = new ReactiveValueWrapper(
            namespace,
            isInvalidNamespace,
        );
        this.label = new ReactiveValueWrapper(label, label =>
            isInvalidClassLabel(label, this.namespace.value, compareClasses),
        );

        this.package = new ReactiveValueWrapper(pack);
        this.superClass = new ReactiveValueWrapper(superClass);
        this.comment = new ReactiveValueWrapper(comment);
        this.stereotypes = new ReactiveObjectsArrayWrapper(
            stereotypes,
            ReactiveValueWrapper,
            initializeStereotypeViolationChecks,
        );
        this.attributes = new ReactiveObjectsArrayWrapper(
            attributes,
            ReactiveAttribute,
            (reactiveObject, entriesArray) => {
                reactiveObject.label.violationChecks.push(label =>
                    hasUniqueIRI(
                        label,
                        reactiveObject.namespace.value,
                        entriesArray,
                    ),
                );
            },
        );

        this.associations = new ReactiveObjectsArrayWrapper(
            associations,
            ReactiveAssociation,
            initializeUniqueLabelChecks,
        );
        this.enumEntries = new ReactiveObjectsArrayWrapper(
            enumEntries,
            ReactiveEnumEntry,
            initializeUniqueLabelChecks,
        );
    }

    /**
     * The uuid of the class or null if the class is not yet persisted
     * @type {ReactiveValueWrapper<string | null>}
     */
    uuid;

    /**
     * The namespace of the class, if this value is not set the class is considered invalid
     * @type {ReactiveValueWrapper<string>}
     */
    namespace;

    /**
     * The label of the class
     * @type {ReactiveValueWrapper}
     */
    label;

    /**
     * The package this class belongs to or null if no package is assigned
     * @type {ReactiveValueWrapper<ReactiveClass | null>}
     */
    package;

    /**
     * The uuid of the superclass or null if no superclass is present
     * @type {ReactiveValueWrapper<string | null>}
     */
    superClass;

    /**
     * A comment describing the class null if no comment is present
     * @type {ReactiveValueWrapper<string | null>}
     */
    comment;

    /**
     * An array of stereotypes in string representation applied to the class
     * @type {ReactiveObjectsArrayWrapper<ReactiveValueWrapper<string>>}
     */
    stereotypes;

    /**
     * An array of attributes belonging to this class
     * @type {ReactiveObjectsArrayWrapper<ReactiveAttribute>}
     */
    attributes;

    /**
     * An array of associations belonging to this class
     * @type {ReactiveObjectsArrayWrapper<ReactiveAssociation>}
     */
    associations;

    /**
     * An array of enum entries belonging to this class
     * @type {ReactiveObjectsArrayWrapper<ReactiveEnumEntry>}
     */
    enumEntries;

    /**
     * Indicates whether this class has changes
     * @type {boolean}
     */
    isModified = $derived(
        this.uuid.isModified ||
            this.namespace.isModified ||
            this.label.isModified ||
            this.package.isModified ||
            this.superClass.isModified ||
            this.comment.isModified ||
            this.stereotypes.isModified ||
            this.attributes.isModified ||
            this.associations.isModified ||
            this.enumEntries.isModified,
    );

    /**
     * Indicates whether this class is valid
     * @type {boolean}
     */
    isValid = $derived(
        this.uuid.isValid &&
            this.namespace.isValid &&
            this.label.isValid &&
            this.package.isValid &&
            this.superClass.isValid &&
            this.comment.isValid &&
            this.stereotypes.isValid &&
            this.attributes.isValid &&
            this.associations.isValid &&
            this.enumEntries.isValid,
    );

    /**
     * Resets this class to its initial values
     */
    reset() {
        this.uuid.reset();
        this.namespace.reset();
        this.label.reset();
        this.package.reset();
        this.superClass.reset();
        this.comment.reset();
        this.stereotypes.reset();
        this.attributes.reset();
        this.associations.reset();
        this.enumEntries.reset();
    }

    /**
     * Saves the current values as the new initial values
     */
    save() {
        this.uuid.save();
        this.namespace.save();
        this.label.save();
        this.package.save();
        this.superClass.save();
        this.comment.save();
        this.stereotypes.save();
        this.attributes.save();
        this.associations.save();
        this.enumEntries.save();
    }

    /**
     * Checks if this class is equal to another class
     * @param {ReactiveClass} other - The other class to compare to
     * @returns {boolean} True if the classes are equal
     */
    equals(other) {
        return (
            !!other &&
            this.namespace.equals(other.namespace) &&
            this.label.equals(other.label) &&
            this.package.equals(other.package) &&
            this.superClass.equals(other.superClass) &&
            this.comment.equals(other.comment) &&
            this.stereotypes.equals(other.stereotypes) &&
            this.attributes.equals(other.attributes) &&
            this.associations.equals(other.associations) &&
            this.enumEntries.equals(other.enumEntries)
        );
    }

    /**
     * Converts this class to a plain object
     * @returns {Object} Plain object representation of this class
     */
    getPlainObject() {
        return {
            uuid: this.uuid.getPlainObject(),
            namespace: this.namespace.getPlainObject(),
            label: this.label.getPlainObject(),
            package: this.package.getPlainObject(),
            superClass: this.superClass.getPlainObject(),
            comment: this.comment.getPlainObject(),
            stereotypes: this.stereotypes.getPlainObject(),
            attributes: this.attributes.getPlainObject(),
            associations: this.associations.getPlainObject(),
            enumEntries: this.enumEntries.getPlainObject(),
        };
    }
}
