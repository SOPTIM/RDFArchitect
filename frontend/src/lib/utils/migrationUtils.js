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

import { URI } from "$lib/models/dto/index.ts";

export function isPrefixOnlyRename(oldIRI, newIRI) {
    if (!newIRI || !oldIRI) {
        return false;
    }
    const oldName = new URI(oldIRI).suffix;
    const newName = new URI(newIRI).suffix;
    return oldName === newName;
}

/**
 * Whether the user should be spared the rename mapping for this association. An association with
 * AssociationUsed = "No" cannot be instantiated, so no instance data ever used the old predicate
 * and there is nothing a mapping could rewrite. Judged on the old side, which is where the values
 * would have come from.
 */
export function isNonInstantiableAssociationRename(renameCandidate) {
    return renameCandidate?.oldResource?.associationUsed === false;
}

/**
 * Drops the rename candidates the user should not be asked about from a property overview,
 * returning a copy: the caller keeps the unfiltered overview and still submits the hidden
 * candidates with the target that was detected for them.
 *
 * `alsoHide` adds a rule on top of the prefix-only one, which applies only when the migration was
 * started with "ignore prefixes".
 */
export function filterRenameCandidates(
    properties,
    { ignorePrefixes = false, alsoHide } = {},
) {
    if (!properties) return properties;

    const deletedAndRenamed = properties.deletedAndRenamed ?? [];
    const added = properties.added ?? [];

    const isHidden = candidate =>
        (ignorePrefixes &&
            isPrefixOnlyRename(
                candidate.oldResource.iri,
                candidate.newResource?.iri,
            )) ||
        (alsoHide?.(candidate) ?? false);

    const hiddenTargetIRIs = deletedAndRenamed
        .filter(isHidden)
        .map(candidate => candidate.newResource?.iri)
        .filter(iri => iri != null);

    return {
        ...properties,
        deletedAndRenamed: deletedAndRenamed.filter(
            candidate => !isHidden(candidate),
        ),
        added: added.filter(a => !hiddenTargetIRIs.includes(a.iri)),
    };
}

function hasChange(attribute, changeType) {
    return (
        attribute.changes?.some(
            c => c.semanticFieldChangeType === changeType,
        ) ?? false
    );
}

/**
 * A renamed datatype is deliberately not a datatype change: the backend reports it as
 * DATATYPE_RENAME and nothing about the stored values has to change.
 */
export function hasDataTypeChange(attribute) {
    return hasChange(attribute, "DATATYPE_CHANGE");
}

/**
 * Whether this attribute could keep its values across the datatype change, which is what the
 * equivalence option offers. It needs a datatype the attribute actually had before: a newly added
 * attribute also carries a DATATYPE_CHANGE, but from nothing, and has no existing values to keep.
 */
export function canKeepExistingValues(attribute) {
    if (
        attribute.semanticResourceChangeType !== "CHANGE" &&
        attribute.semanticResourceChangeType !== "RENAME"
    ) {
        return false;
    }
    return (
        attribute.changes?.some(
            c =>
                c.semanticFieldChangeType === "DATATYPE_CHANGE" &&
                Boolean(c.from),
        ) === true
    );
}

/** True once the user declared the old and the new datatype interchangeable. */
export function keepsExistingValues(attribute) {
    return (
        canKeepExistingValues(attribute) &&
        attribute.dataTypesEquivalent === true
    );
}

export function describeAttributeChange(attribute) {
    if (attribute.semanticResourceChangeType === "ADD") {
        return attribute.optional === true
            ? "Attribute added (optional)"
            : "Attribute added (required)";
    }
    if (attribute.semanticResourceChangeType === "ADDED_FROM_INHERITANCE") {
        return "Attribute newly inherited";
    }
    if (hasDataTypeChange(attribute)) {
        return keepsExistingValues(attribute)
            ? "Data type equivalent"
            : "Data type changed";
    }
    if (hasChange(attribute, "MADE_REQUIRED")) {
        return "Made required";
    }
    return "other";
}

/** Whether the step offers a default value for this attribute at all. */
export function allowsDefaultValueInput(attribute) {
    return (
        attribute.semanticResourceChangeType === "ADD" ||
        attribute.semanticResourceChangeType === "ADDED_FROM_INHERITANCE" ||
        (attribute.semanticResourceChangeType !== "DELETE" &&
            (hasChange(attribute, "MADE_REQUIRED") ||
                hasDataTypeChange(attribute)))
    );
}

/**
 * Whether a default value has to be given before the migration can continue. Marking the two
 * datatypes equivalent keeps every existing value, so the datatype change needs no default - but
 * an attribute that also became mandatory still needs one for the instances that lack a value.
 */
export function requiresDefaultValue(attribute) {
    if (
        attribute.semanticResourceChangeType === "ADD" ||
        attribute.semanticResourceChangeType === "ADDED_FROM_INHERITANCE"
    ) {
        return !attribute.optional;
    }
    if (
        attribute.semanticResourceChangeType !== "CHANGE" &&
        attribute.semanticResourceChangeType !== "RENAME"
    ) {
        return false;
    }
    if (hasChange(attribute, "MADE_REQUIRED")) {
        return true;
    }
    return hasDataTypeChange(attribute) && !keepsExistingValues(attribute);
}

/** Whether a default value was filled in for this attribute. */
export function hasDefaultValue(attribute) {
    return Boolean(attribute.defaultValue?.trim());
}

/**
 * True once the user waived the default value for an attribute that requires one (RDFA-714): the
 * attribute is knowingly left uninitialised because no default makes sense for it. Tied to the
 * requirement, so the flag turns inert as soon as the attribute stops asking for a default.
 */
export function skipsInitialization(attribute) {
    return requiresDefaultValue(attribute) && attribute.noDefaultValue === true;
}

/**
 * Whether this attribute still blocks the defaults step: it requires a default value that was
 * neither given nor waived.
 */
export function isDefaultValueMissing(attribute) {
    return (
        requiresDefaultValue(attribute) &&
        !hasDefaultValue(attribute) &&
        !skipsInitialization(attribute)
    );
}
