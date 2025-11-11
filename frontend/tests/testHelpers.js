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

import { faker } from "@faker-js/faker";

import {
    Association,
    AssociationPair,
    Attribute,
    Class,
    DataType,
    DataTypeTypes,
    Package,
    SuperClass,
} from "$lib/models/dto";
import { Data, States } from "$lib/scripts/data.svelte.js";

export function createRandomData() {
    const randomString = faker.lorem.words();
    const randomState = faker.helpers.arrayElement(Object.values(States));
    return new Data(randomString, randomString, randomState);
}

export function createRandomDataArray(size) {
    const dataArray = [];
    for (let i = 0; i < size; i++) {
        dataArray.push(createRandomData());
    }
    return dataArray;
}

export function createConcreteData() {
    return new Data(
        "http://iec.ch/TC57/NonStandard/UML#concrete",
        faker.lorem.words(),
        faker.helpers.arrayElement(Object.values(States)),
    );
}

export function createRandomMultiplicity() {
    const firstNumber = faker.number.int({ min: 0 });
    const secondNumber = faker.number.int({ min: firstNumber + 1 });
    return `M:${firstNumber}..${secondNumber}`;
}

export function createAssociation(
    classPrefix = faker.internet.url() + "#",
    classDomain = faker.lorem.word(),
    classRange = new DataType({
        prefix: faker.internet.url() + "#",
        label: faker.lorem.word(),
        type: DataTypeTypes.RANGE,
    }),
) {
    const label = classRange.label;
    const domain = classDomain;
    const range = classRange;
    const comment = faker.lorem.sentence();
    const associationUsed = "Yes";
    const multiplicity = createRandomMultiplicity();

    return new Association({
        prefix: classPrefix,
        label: label,
        domain: domain,
        range: range,
        comment: comment,
        associationUsed: associationUsed,
        multiplicity: multiplicity,
    });
}

export function createRandomAssociationPair(
    from = createRandomClass(false),
    to = createRandomClass(false),
) {
    return new AssociationPair({
        from: createAssociation(
            from.prefix,
            from.label,
            new DataType({
                prefix: to.prefix,
                label: to.label,
                type: DataTypeTypes.RANGE,
            }),
        ),
        to: createAssociation(
            to.prefix,
            to.label,
            new DataType({
                prefix: from.prefix,
                label: from.label,
                type: DataTypeTypes.RANGE,
            }),
        ),
    });
}

export function createRandomStereotypeList() {
    const size = faker.number.int({ min: 1, max: 10 });
    return Array.from({ length: size }, () => faker.lorem.word());
}

export function createRandomAttributeList(classLabel = faker.lorem.word()) {
    const size = faker.number.int({ min: 1, max: 10 });
    return Array.from({ length: size }, () =>
        createRandomAttribute(classLabel),
    );
}

export function createRandomAssociationPairList(
    parentClass = createRandomClass(false),
) {
    const size = faker.number.int({ min: 1, max: 10 });
    return Array.from({ length: size }, () =>
        createRandomAssociationPair(parentClass, createRandomClass(false)),
    );
}

export function createRandomAttribute(classLabel = faker.lorem.word()) {
    const attName = faker.lorem.word();

    return new Attribute({
        label: attName,
        prefix: faker.internet.url() + "#",
        domain: classLabel,
        multiplicity: "M:1..1",
        dataType: new DataType({
            prefix: faker.internet.url() + "#",
            label: faker.lorem.word(),
            type: DataTypeTypes.UNKNOWN,
        }),
        comment: faker.lorem.sentence(),
        fixedValue: null,
        defaultValue: null,
    });
}

export function createRandomClass(includeOptionals = true) {
    const prefix = faker.internet.url() + "#";

    const label = faker.lorem.word();

    const superClassLabel = faker.lorem.word();
    const superClass = new SuperClass({
        prefix: faker.internet.url() + "#",
        label: superClassLabel,
    });
    const comment = faker.lorem.sentence();

    const pkg = new Package({
        uuid: null,
        prefix: faker.internet.url() + "#",
        label: faker.lorem.word(),
        external: false,
    });

    let newClass = new Class({
        prefix: prefix,
        label: label,
        superClass: superClass,
        comment: comment,
        package: pkg,
        stereotypes: [],
        attributes: [],
        associationPairs: [],
    });

    if (includeOptionals) {
        newClass.stereotypes = createRandomStereotypeList();
        newClass.attributes = createRandomAttributeList(label);
        newClass.associationPairs = createRandomAssociationPairList(newClass);
    }

    return newClass;
}
