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
import { validate as uuidValidate } from "uuid";
import { IriValidationStrategy, validateIri } from "validate-iri";

import { getNCNameViolations } from "$lib/rdf-syntax-grammar/namespace/prefix/index.js";

export function isInvalidUuid(uuid) {
    const violations = [];
    if (uuid !== null && (!uuid || !uuidValidate(uuid))) {
        // Allows null because uuids are not required
        violations.push("must be a valid UUID");
    }
    return violations;
}

export function isInvalidLabel(label) {
    return isNotEmptyValidation(label);
}

export function isInvalidNamespace(namespace) {
    return isNotEmptyValidation(namespace);
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

export function isInvalidDatatypeUri(uri) {
    return isNotEmptyValidation(uri);
}

export function isInvalidTarget(target) {
    const violations = isInvalidUuid(target);
    if (!target || target.trim() === "") {
        violations.push("must not be empty");
    }
    return violations;
}

export function isInvalidStereotype(stereotype, existingStereotypes) {
    const violations = [];
    if (!stereotype || stereotype.trim() === "") {
        violations.push("must not be empty");
    }
    if (existingStereotypes.filter(s => s.equals(stereotype)).length > 1) {
        violations.push("must be unique");
    }
    return violations;
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
