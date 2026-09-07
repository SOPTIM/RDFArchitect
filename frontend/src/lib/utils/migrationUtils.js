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
