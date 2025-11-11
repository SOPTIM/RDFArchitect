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

import { ReactiveClass } from "$lib/models/reactive/reactive-class.svelte.js";

/**
 * Maps a class DTO to a ReactiveClass instance
 * @param {Object} classDto - The class data transfer object from the API
 * @param {Array} classes - Array of existing classes for resolving references
 * @returns {ReactiveClass} The reactive class instance
 */
export function mapClassDtoToReactiveClass(classDto, classes) {
    let superClass = null;
    if (classDto.superClass) {
        superClass = classes.find(
            cls =>
                cls.prefix + cls.label ===
                classDto.superClass.prefix + classDto.superClass.label,
        );
    }
    const superClassUUID = superClass ? superClass.uuid : null;
    const attributes = mapAttributeDtoListToReactiveAttributeList(
        classDto.attributes,
    );
    const associations = mapAssociationDtoListToReactiveAssociationList(
        classDto.associationPairs,
        classes,
    );

    const enumEntries = mapEnumEntryListToReactiveEnumEntryList(
        classDto.enumEntries,
    );
    return new ReactiveClass(
        classDto.uuid,
        classDto.prefix,
        classDto.label,
        classDto.package?.uuid,
        superClassUUID,
        classDto.comment,
        classDto.stereotypes,
        attributes,
        associations,
        enumEntries,
    );
}

/**
 * Maps an array of attribute DTOs to an array of reactive attributes
 * @param {Array<Object>} attributes - Array of attribute DTOs
 * @returns {Array<ReactiveAttribute>} Array of reactive attribute objects
 */
function mapAttributeDtoListToReactiveAttributeList(attributes) {
    return attributes.map(attr => mapAttributeDtoToReactiveAttribute(attr));
}

/**
 * Maps a single attribute DTO to a reactive attribute object
 * @param {Object} attributeDto - The attribute data transfer object
 * @returns {ReactiveAttribute} The reactive attribute object with parsed properties
 */
function mapAttributeDtoToReactiveAttribute(attributeDto) {
    const multiplicity = parseMultiplicity(attributeDto.multiplicity);
    const datatypeUri =
        attributeDto.dataType.prefix + attributeDto.dataType.label;
    return {
        uuid: attributeDto.uuid,
        label: attributeDto.label,
        namespace: attributeDto.prefix,
        datatype: datatypeUri,
        multiplicityLowerBound: multiplicity.lowerBound,
        multiplicityUpperBound: multiplicity.upperBound,
        isDerived: attributeDto.isDerived,
        comment: attributeDto.comment,
        stereotypes: attributeDto.stereotypes,
    };
}

/**
 * Maps an array of association pair DTOs to an array of reactive associations
 * @param {Array<Object>} associationPairs - Array of association pair DTOs
 * @param {Array} classes - Array of existing classes for resolving references
 * @returns {Array<ReactiveAssociation>} Array of reactive association objects
 */
function mapAssociationDtoListToReactiveAssociationList(
    associationPairs,
    classes,
) {
    return associationPairs.map(association =>
        mapAssociationDtoToReactiveAssociation(association, classes),
    );
}

/**
 * Maps a single association DTO to a reactive association object
 * @param {Object} associationDto - The association data transfer object containing 'from' and 'to' properties
 * @param {Array} classes - Array of existing classes for resolving domain and target UUIDs
 * @returns {ReactiveAssociation} The reactive association object with parsed properties and inverse
 */
function mapAssociationDtoToReactiveAssociation(associationDto, classes) {
    const fromMultiplicity = parseMultiplicity(
        associationDto.from.multiplicity,
    );
    const toMultiplicity = parseMultiplicity(associationDto.to.multiplicity);
    const targetUuid = classes.find(
        cls =>
            cls.prefix + cls.label ===
            associationDto.from.range.prefix + associationDto.from.range.label,
    ).uuid;
    const domainUuid = classes.find(
        cls =>
            cls.prefix + cls.label ===
            associationDto.to.range.prefix + associationDto.to.range.label,
    ).uuid;
    return {
        uuid: associationDto.from.uuid,
        label: associationDto.from.label,
        namespace: associationDto.from.prefix,
        domain: domainUuid,
        target: targetUuid,
        multiplicityLowerBound: fromMultiplicity.lowerBound,
        multiplicityUpperBound: fromMultiplicity.upperBound,
        comment: associationDto.from.comment,
        isUsed: associationDto.from.associationUsed,
        inverse: {
            uuid: associationDto.to.uuid,
            label: associationDto.to.label,
            namespace: associationDto.to.prefix,
            multiplicityLowerBound: toMultiplicity.lowerBound,
            multiplicityUpperBound: toMultiplicity.upperBound,
            comment: associationDto.to.comment,
            isUsed: associationDto.to.associationUsed,
        },
    };
}

/**
 * Maps an array of enum entry DTOs to an array of reactive enum entries
 * @param {Array<Object>} enumEntries - Array of enum entry DTOs
 * @returns {Array<ReactiveNamespace>} Array of reactive enum entry objects
 */
function mapEnumEntryListToReactiveEnumEntryList(enumEntries) {
    return enumEntries.map(entry => mapEnumEntryDtoToReactiveEnumEntry(entry));
}

/**
 * Maps a single enum entry DTO to a reactive enum entry object
 * @param {Object} enumEntryDto - The enum entry data transfer object
 * @returns {ReactiveNamespace} The reactive enum entry object with parsed properties
 */
function mapEnumEntryDtoToReactiveEnumEntry(enumEntryDto) {
    return {
        uuid: enumEntryDto.uuid,
        label: enumEntryDto.label,
        namespace: enumEntryDto.prefix,
        comment: enumEntryDto.comment,
        stereotype: enumEntryDto.stereotype,
    };
}

/**
 * Maps a namespace DTO to a reactive namespace object
 * @param namespaceDto - The namespace data transfer object from the API
 * @returns {{iri: *, prefix: *}} The reactive namespace object
 */
export function mapNamespaceDtoToReactiveNamespace(namespaceDto) {
    return {
        iri: namespaceDto.prefix,
        prefix: namespaceDto.substitutedPrefix,
    };
}

/**
 * Parses a multiplicity string into lower and upper bounds
 * @param {string} multiplicityString - The multiplicity string (e.g., "M:1..5", "M:*", "M:1")
 * @returns {{lowerBound: number, upperBound: number|null}} Object containing the parsed bounds
 * @example
 * parseMultiplicity("M:1..5") // { lowerBound: 1, upperBound: 5 }
 * parseMultiplicity("M:*")    // { lowerBound: 0, upperBound: null }
 * parseMultiplicity("M:1")    // { lowerBound: 1, upperBound: 1 }
 */
function parseMultiplicity(multiplicityString) {
    if (!multiplicityString) {
        return { lowerBound: null, upperBound: null };
    }

    const trimmed = multiplicityString.replace("M:", "");
    const parts = trimmed.split("..");

    const isFirstPartNumber = /^\d+$/.test(parts[0]);
    let lowerBound;
    let upperBound;
    if (parts.length === 1) {
        // Format: "5" or "*"/"n"
        if (isFirstPartNumber) {
            // Format: "5"
            lowerBound = Number.parseInt(parts[0]);
            upperBound = lowerBound;
            return { lowerBound, upperBound };
        }
        // Format: "*"/"n"
        return { lowerBound: 0, upperBound: null };
    }
    // Format: "1..5" or "1..*"
    if (isFirstPartNumber) {
        lowerBound = Number.parseInt(parts[0]);
    } else {
        // invalid format, log error and set default
        console.error(
            `Invalid multiplicity format: "${multiplicityString}". Expected number before "..", got "${parts[0]}"`,
        );
        lowerBound = 0;
    }
    const isSecondPartNumber = /^\d+$/.test(parts[1]);
    upperBound = isSecondPartNumber ? Number.parseInt(parts[1]) : null;

    return { lowerBound, upperBound };
}
