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
import { untrack } from "svelte";
import { validate as uuidValidate } from "uuid";
import { IriValidationStrategy, validateIri } from "validate-iri";

import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
import {
    CONCRETE_STEREOTYPE,
    ENUMERATION_STEREOTYPE,
    RDFS_NAMESPACE_URI,
} from "$lib/models/stereotype-constants.js";
import { getNCNameViolations } from "$lib/rdf-syntax-grammar/namespace/prefix/index.js";

export function isInvalidUuid(uuid) {
    const violations = [];
    if (validateUuid(uuid)) {
        violations.push("must be a valid input");
    }
    return violations;
}

function validateUuid(uuid) {
    return uuid !== null && (!uuid || !uuidValidate(uuid));
}

export function isInvalidLabel(label) {
    return isNotEmptyValidation(label);
}

export function isInvalidClassLabel(
    label,
    reactiveNamespace,
    compareClasses,
    ReactiveStereotypes,
) {
    const violations = [];
    const namespace = reactiveNamespace?.getPlainObject();
    if (!label || label.trim() === "") {
        violations.push("must not be empty");
    }
    if (typeof namespace === "string" && namespace.trim() !== "") {
        if (
            compareClasses?.some(
                c => c.label === label && c.prefix === namespace,
            )
        ) {
            violations.push("must be unique");
        }
    }

    if (
        label === "Class" &&
        namespace === RDFS_NAMESPACE_URI &&
        ReactiveStereotypes?.getPlainObject().includes(ENUMERATION_STEREOTYPE)
    ) {
        violations.push("enumeration cannot be named rdfs:Class");
    }
    return violations;
}

export function isValidDiagramName(diagramName, compareDiagrams) {
    const violations = [];
    if (diagramName?.trim() === "") {
        violations.push("must not be empty");
    }

    if (compareDiagrams?.some(d => d.label === diagramName)) {
        violations.push("must be unique");
    }

    return violations;
}

export function isInvalidAssociationLabel(association, associations) {
    const violations = isNotEmptyValidation(association?.label?.value);
    const assocList = Array.isArray(associations)
        ? associations
        : (associations?.values ?? []);
    if (violations.length === 0) {
        if (
            assocList.some(
                a =>
                    a.label.value === association?.label?.value &&
                    a.namespace.value === association.namespace?.value &&
                    a !== association,
            )
        ) {
            violations.push("must be unique");
        } else if (
            association &&
            association.domain &&
            association.target &&
            association.inverse &&
            association.label
        ) {
            if (
                association.domain?.value === association.target?.value &&
                association.inverse?.label?.value === association.label?.value
            ) {
                violations.push("must be unique");
            }
        }
    }
    return violations;
}

export function isInvalidInverseAssociationLabel(association, getClassByUuid) {
    const violations = isNotEmptyValidation(association?.inverse?.label?.value);
    const targetClassDto = getClassByUuid(association.target?.value);
    const assocList = targetClassDto?.associationPairs?.map(pair => pair) ?? [];
    if (violations.length === 0) {
        if (
            assocList.some(
                a =>
                    a.from.label === association?.inverse?.label?.value &&
                    a.from.prefix === association.inverse?.namespace?.value &&
                    a.from.uuid !== association.inverse?.uuid?.value,
            )
        ) {
            violations.push("must be unique");
        } else if (
            association &&
            association.domain &&
            association.target &&
            association.inverse &&
            association.label
        ) {
            if (
                association.domain?.value === association.target?.value &&
                association.inverse?.label?.value === association.label?.value
            ) {
                violations.push("must be unique");
            }
        }
    }
    return violations;
}

export function isInvalidNamespace(
    namespace,
    compareNamespaces,
    ReactiveLabel,
    ReactiveStereotypes,
) {
    const violations = isNotEmptyValidation(namespace);
    if (violations.length > 0) return violations;

    const isKnownPrefix = compareNamespaces.some(n => n.prefix === namespace);
    const hasIriViolation = validateIri(
        namespace,
        IriValidationStrategy.Pragmatic,
    );
    const hasWrongEnding = !namespace.endsWith("#") && !namespace.endsWith("/");

    if (!isKnownPrefix && hasIriViolation) {
        violations.push("must be a valid input or IRI");
    } else if (!isKnownPrefix && hasWrongEnding) {
        violations.push('must end with "#" or "/"');
    }

    if (
        ReactiveLabel?.getPlainObject() === "Class" &&
        namespace === RDFS_NAMESPACE_URI &&
        ReactiveStereotypes?.getPlainObject().includes(ENUMERATION_STEREOTYPE)
    ) {
        violations.push(
            'enumeration called "Class" cannot be in namespace rdfs',
        );
    }
    return violations;
}

export function isInvalidMultiplicityLowerBound(lowerBound, upperBound) {
    const violations = [];
    if (lowerBound < 0) {
        violations.push("must be greater than or equal to 0");
    }
    if (upperBound !== null && lowerBound > upperBound) {
        violations.push("must be less than or equal to upper bound");
    }
    if (lowerBound !== Math.floor(lowerBound)) {
        violations.push("must be an integer");
    }
    return violations;
}

export function isInvalidMultiplicityUpperBound(upperBound, lowerBound) {
    const violations = [];
    if (!upperBound) {
        // not set means unbounded
        return violations;
    }
    if (upperBound < 1) {
        violations.push("must be greater than or equal to 0");
    }
    if (upperBound < lowerBound) {
        violations.push("must be greater than or equal to lower bound");
    }
    if (upperBound !== Math.floor(upperBound)) {
        violations.push("must be an integer");
    }
    return violations;
}

export function isInvalidDatatypeUri(uri, compareDatatypes) {
    const violations = isNotEmptyValidation(uri);

    if (
        violations.length === 0 &&
        !compareDatatypes.some(
            datatype => datatype.prefix + datatype.label === uri,
        )
    ) {
        violations.push("must be a valid input");
    }

    return violations;
}

export function isInvalidTarget(target) {
    const violations = isNotEmptyValidation(target);

    if (violations.length === 0 && validateUuid(target)) {
        violations.push("must be a valid input");
    }

    return violations;
}

export function isInvalidStereotype(
    stereotype,
    existingStereotypes,
    reactiveNamespace,
    reactiveLabel,
) {
    const violations = [];
    if (!stereotype || stereotype.trim() === "") {
        violations.push("must not be empty");
    }
    if (
        stereotype !== CONCRETE_STEREOTYPE &&
        existingStereotypes.filter(s => s.equals(stereotype)).length > 1
    ) {
        violations.push("must be unique");
    }
    if (
        stereotype === ENUMERATION_STEREOTYPE &&
        reactiveLabel?.value === "Class" &&
        reactiveNamespace?.getPlainObject() === RDFS_NAMESPACE_URI
    ) {
        untrack(() =>
            toastStore.warning(
                "rdfs:Class cannot be an enum",
                "This is a known limitation",
            ),
        );
        violations.push("Cannot transform rdfs:Class into an enum");
    }
    return violations;
}

/**
 * Flags a stereotype that the user has manually set to the concrete URI. The
 * concrete stereotype is managed exclusively through the "Abstract" checkbox,
 * so it must not be entered as a regular stereotype. Only modified entries are
 * flagged, so a class loaded with a persisted concrete stereotype (which is
 * surfaced via the checkbox and hidden from the list) stays valid.
 * @param {string} stereotype - the current stereotype value
 * @param {boolean} isModified - whether the entry differs from its saved value
 * @returns {string[]} the violations
 */
export function isManuallyEnteredConcreteStereotype(stereotype, isModified) {
    if (isModified && stereotype === CONCRETE_STEREOTYPE) {
        return ['use the "Abstract" checkbox to mark a class as concrete'];
    }
    return [];
}

export function isNotEmptyValidation(value) {
    const violations = [];
    if (!value || value.trim() === "") {
        violations.push("must not be empty");
    }
    return violations;
}

export function hasUniqueLabel(label, reactiveObjectsArray) {
    const violations = [];
    if (
        reactiveObjectsArray.filter(obj => obj.label.value === label).length > 1
    ) {
        violations.push("must be unique");
    }
    return violations;
}

export function hasUniqueIRI(label, namespace, compareArray) {
    const violations = [];
    if (typeof namespace === "string" && namespace.trim() !== "") {
        if (
            compareArray &&
            compareArray.filter(
                c => c.label.value === label && c.namespace.value === namespace,
            ).length > 1
        ) {
            violations.push("must be unique");
        }
    }
    return violations;
}

export function isInvalidNamespaceIri(iri) {
    const violations = [];
    if (!iri || iri.trim() === "") {
        violations.push("must not be empty");
    }

    const hasViolation = validateIri(iri, IriValidationStrategy.Pragmatic);
    if (hasViolation) {
        violations.push("must be a valid IRI");
    }

    if (!iri?.endsWith("#") && !iri?.endsWith("/")) {
        violations.push('must end with "#" or "/"');
    }
    return violations;
}

export function isInvalidIri(iri) {
    const violations = isNotEmptyValidation(iri);
    if (validateIri(iri, IriValidationStrategy.Pragmatic)) {
        violations.push("must be a valid IRI");
    }
    return violations;
}

export function isInvalidOntologyValue(value, isIri) {
    const violations = isNotEmptyValidation(value);
    if (
        violations.length === 0 &&
        isIri &&
        validateIri(value, IriValidationStrategy.Pragmatic)
    ) {
        violations.push("must be a valid IRI");
    }
    return violations;
}

export function isInvalidNamespacePrefix(prefix) {
    const violations = [];
    if (!prefix) prefix = "";
    const normalizedNsPrefix = prefix.endsWith(":")
        ? prefix.slice(0, -1)
        : prefix;
    if (normalizedNsPrefix.length === 0) {
        return violations;
    }
    const violationsFromNcName = getNCNameViolations(normalizedNsPrefix);
    if (violationsFromNcName.length > 0) {
        const formattedViolations = violationsFromNcName
            .map(v => `'${v}'`)
            .join(", ");

        violations.push(
            "must not contain invalid characters: " + formattedViolations,
        );
    }
    return violations;
}

export function namespacePrefixesAreUnique(prefix, reactiveNamespacesArray) {
    const violations = [];

    const normalize = p => (p || "").replace(/:$/, "");

    const normalizedPrefix = normalize(prefix);

    const matches = reactiveNamespacesArray.filter(
        namespace => normalize(namespace.prefix.value) === normalizedPrefix,
    );

    if (matches.length > 1) {
        violations.push("must be unique");
    }

    return violations;
}

export function isInvalidPackage(pack) {
    return isInvalidUuid(pack);
}

export function isInvalidSuperClass(superClass) {
    return isInvalidUuid(superClass);
}
