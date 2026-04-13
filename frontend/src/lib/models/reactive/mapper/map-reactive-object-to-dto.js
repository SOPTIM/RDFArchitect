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
import { ReactiveClass } from "$lib/models/reactive/models/reactive-class.svelte.js";
import { ReactiveEnumEntry } from "$lib/models/reactive/models/reactive-enum-entry.svelte.js";
import { ReactiveNamespace } from "$lib/models/reactive/models/reactive-namespace.svelte.js";
import { ReactivePackage } from "$lib/models/reactive/models/reactive-package.svelte.js";

/**
 * Maps a ReactiveClass to a class DTO for API submission
 * @param {ReactiveClass | Object } cls - The reactive class instance or a plain object of it
 * @param {function(string)} getClassByUuid - A function that returns the class object of a given uuid or undefined if none is found
 * @param {function(string)} getDatatypeByUri - A function that returns the datatype object for a given URI
 * @param {function(string)} getPackageByUuid - A function that returns the package object for a given uuid or undefined if none is found
 * @returns {{uuid, prefix, label, package: {uuid: string, prefix: string, label: string}, superClass: {prefix: null, label: null}, comment, stereotypes: (ReactiveObjectsArrayWrapper<ReactiveValueWrapper<string>>|[]|string[]|*), attributes: Array<Object>, associationPairs: Array<Object>, enumEntries: Array<Object>}}
 */
export function mapReactiveClassToClassDto(
    cls,
    getClassByUuid,
    getDatatypeByUri,
    getPackageByUuid,
) {
    if (cls instanceof ReactiveClass) {
        cls = cls.getPlainObject();
    }

    const pack = getPackageByUuid(cls.package);
    const superClass = getClassByUuid(cls.superClass);
    return {
        uuid: cls.uuid,
        prefix: cls.namespace,
        label: cls.label,
        package:
            pack && pack.uuid
                ? {
                      uuid: pack.uuid,
                      prefix: pack.prefix,
                      label: pack.label,
                  }
                : null,
        superClass: superClass
            ? {
                  prefix: superClass.prefix,
                  label: superClass.label,
              }
            : null,
        comment: cls.comment,
        stereotypes: cls.stereotypes,
        attributes: mapReactiveAttributeListToAttributeDtoList(
            cls.attributes,
            getDatatypeByUri,
            cls.namespace + cls.label,
        ),
        associationPairs: mapReactiveAssociationListToAssociationDtoList(
            cls.associations,
            cls,
            getClassByUuid,
        ),
        enumEntries: mapReactiveEnumEntryListToEnumEntryDtoList(
            cls.enumEntries,
            cls.namespace + cls.label,
        ),
    };
}

/**
 * Maps an array of ReactiveAttribute to attribute DTOs
 * @param {Array<ReactiveAttribute | Object>} attributeArray - Array of reactive attribute instances or plain objects of them
 * @param {function(string)} getDatatypeByUri - A function that returns the datatype object for a given URI
 * @param {string} domainIri - The Iri of the domain class
 * @returns {Array<Object>} Array of attribute DTOs
 */
export function mapReactiveAttributeListToAttributeDtoList(
    attributeArray,
    getDatatypeByUri,
    domainIri,
) {
    return attributeArray.map(attr => {
        return mapReactiveAttributeToAttributeDto(
            attr,
            getDatatypeByUri,
            domainIri,
        );
    });
}

/**
 * Maps a ReactiveAttribute to an attribute DTO for API submission
 * @param {ReactiveAttribute | Object} attribute - The reactive attribute instance or a plain object of it
 * @param {function(string)} getDatatypeByUri - A function that returns the datatype object for a given URI
 * @param {string} domainIri - The Iri of the domain class
 * @returns {Object} The attribute DTO
 */
export function mapReactiveAttributeToAttributeDto(
    attribute,
    getDatatypeByUri,
    domainIri,
) {
    if (attribute instanceof ReactiveAttribute) {
        attribute = attribute.getPlainObject();
    }
    const multiplicityString = formatMultiplicity(
        attribute.multiplicityLowerBound,
        attribute.multiplicityUpperBound,
    );
    const datatype = getDatatypeByUri(attribute.datatype);
    return {
        uuid: attribute.uuid,
        label: attribute.label,
        prefix: attribute.namespace,
        multiplicity: multiplicityString,
        domain: domainIri,
        dataType: {
            prefix: datatype.prefix,
            label: datatype.label,
            type: datatype.type,
        },
        comment: attribute.comment,
        fixedValue: attribute.fixedValue,
        defaultValue: attribute.defaultValue,
    };
}

/**
 * Maps an array of ReactiveAssociation to association pair DTOs
 * @param {Array<ReactiveAssociation | Object>} associationArray - Array of reactive association instances or plain objects of them
 * @param {ReactiveClass | Object} cls - The domain class instance or a plain object of it
 * @param {function(string)} getClassByUuid - A function that returns the class object of a given uuid
 * @returns {Array<Object>} Array of association pair DTOs
 */
export function mapReactiveAssociationListToAssociationDtoList(
    associationArray,
    cls,
    getClassByUuid,
) {
    return associationArray.map(assoc => {
        return mapReactiveAssociationToAssociationDto(
            assoc,
            cls,
            getClassByUuid,
        );
    });
}

/**
 * Maps a ReactiveAssociation to an association pair DTO for API submission
 * @param {ReactiveAssociation | Object} association - The reactive association instance or a plain object of it
 * @param {ReactiveClass | Object} cls - The domain class or a plain object of it
 * @param {function(string)} getClassByUuid - A function that returns the class object of a given uuid
 * @returns {Object} The association pair DTO with 'from' and 'to' properties
 */
export function mapReactiveAssociationToAssociationDto(
    association,
    cls,
    getClassByUuid,
) {
    if (cls instanceof ReactiveClass) {
        const labelBackup = cls.label.backup;
        const namespaceBackup = cls.namespace.backup;
        cls = cls.getPlainObject();
        cls.label = labelBackup;
        cls.namespace = namespaceBackup;
    }
    if (association instanceof ReactiveAssociation) {
        association = association.getPlainObject();
    }
    const targetClass = getClassByUuid(association.target);
    const fromMultiplicityString = formatMultiplicity(
        association.multiplicityLowerBound,
        association.multiplicityUpperBound,
    );
    const inverseMultiplicityString = formatMultiplicity(
        association.inverse.multiplicityLowerBound,
        association.inverse.multiplicityUpperBound,
    );
    const domainClass = {
        label: cls.label,
        prefix: cls.namespace,
        type: "RANGE",
    };
    const targetClassDTO = {
        label: targetClass.label,
        prefix: targetClass.prefix,
        type: "RANGE",
    };
    return {
        from: {
            uuid: association.uuid,
            label: association.label,
            prefix: association.namespace,
            multiplicity: fromMultiplicityString,
            domain: domainClass.prefix + domainClass.label,
            comment: association.comment,
            range: targetClassDTO,
            associationUsed: association.isUsed,
        },
        to: {
            uuid: association.inverse.uuid,
            label: association.inverse.label,
            prefix: association.inverse.namespace,
            multiplicity: inverseMultiplicityString,
            domain: targetClassDTO.prefix + targetClassDTO.label,
            comment: association.inverse.comment,
            range: domainClass,
            associationUsed: association.inverse.isUsed,
        },
    };
}

/**
 * Maps an array of ReactiveEnumEntry to enum entry DTOs
 * @param {string} domainIri - The Iri of the class the enum entry belongs to
 * @param {Array<ReactiveEnumEntry>} enumEntries - Array of reactive enum entry instances
 * @returns {Array<Object>} Array of enum entry DTOs
 */
export function mapReactiveEnumEntryListToEnumEntryDtoList(
    enumEntries,
    domainIri,
) {
    return enumEntries.map(entry =>
        mapReactiveEnumEntryToEnumEntryDto(entry, domainIri),
    );
}

/**
 * Maps a ReactiveEnumEntry to an enum entry DTO for API submission
 * @param {ReactiveEnumEntry | Object} enumEntry - The reactive enum entry instance
 * @param {string} domainIri - The Iri of the class the enum entry belongs to
 * @returns {Object} The enum entry DTO
 */
export function mapReactiveEnumEntryToEnumEntryDto(enumEntry, domainIri) {
    if (enumEntry instanceof ReactiveEnumEntry) {
        enumEntry = enumEntry.getPlainObject();
    }
    return {
        uuid: enumEntry.uuid,
        label: enumEntry.label,
        prefix: enumEntry.namespace,
        type: domainIri,
        comment: enumEntry.comment,
        stereotype: enumEntry.stereotype,
    };
}

/**
 * Maps a ReactiveNamespace to a namespace DTO for API submission
 * @param namespace - The reactive namespace instance or a plain object of it
 * @returns {{prefix: *, substitutedPrefix: *}} The namespace DTO
 */
export function mapReactiveNamespaceToNamespaceDto(namespace) {
    if (namespace instanceof ReactiveNamespace) {
        namespace = namespace.getPlainObject();
    }
    return {
        prefix: namespace.iri,
        substitutedPrefix: namespace.prefix,
    };
}

/**
 * Maps a ReactivePackage to a package DTO for API submission
 * @param {ReactivePackage | Object} pkg - The reactive package instance or a plain object of it
 * @returns {Object} The package DTO
 */
export function mapReactivePackageToPackageDto(pkg) {
    if (pkg instanceof ReactivePackage) {
        pkg = pkg.getPlainObject();
    }
    return {
        uuid: pkg.uuid,
        prefix: pkg.namespace,
        label: pkg.label,
        comment: pkg.comment ?? null,
    };
}

/**
 * Converts multiplicity bounds to a multiplicity string
 * @param {number} lowerBound - The lower bound of the multiplicity
 * @param {number|null} upperBound - The upper bound of the multiplicity (null represents unbounded)
 * @returns {string} The multiplicity string in format "M:lower..upper" or "M:lower..n"
 * @example
 * formatMultiplicity(1, 5)    // "M:1..5"
 * formatMultiplicity(0, null) // "M:0..n"
 * formatMultiplicity(1, 1)    // "M:1..1"
 */
function formatMultiplicity(lowerBound, upperBound) {
    if (lowerBound === 0 && upperBound === null) {
        return "M:n";
    }
    if (lowerBound === upperBound) {
        return "M:" + lowerBound;
    }
    return "M:" + lowerBound + ".." + (upperBound || "n");
}
