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

import { beforeEach, describe, expect, test } from "vitest";

import { DataType, DataTypeTypes } from "$lib/models/dto/index.js";

import { createRandomClass } from "../../testHelpers.js";

describe("CIMClass", () => {
    describe("updateLabel()", () => {
        let classDummy;
        let initialClassDummy;
        const newLabel = "NewLabel";

        beforeEach(() => {
            // Arrange
            classDummy = createRandomClass();
            initialClassDummy = JSON.parse(JSON.stringify(classDummy));

            // Act
            classDummy.updateLabel(newLabel);
        });

        // Assert
        test("should update class label", () => {
            expect(classDummy.label).toEqual(newLabel);
        });

        test("should update the domain of attributes", () => {
            for (let attribute of classDummy.attributes) {
                expect(attribute.domain).toEqual(newLabel);
            }
        });

        test("should update the domain of the from-associations", () => {
            for (let association of classDummy.associationPairs) {
                expect(association.from.domain).toEqual(newLabel);
            }
        });

        test("should update the range of the to-associations", () => {
            for (let association of classDummy.associationPairs) {
                expect(association.to.range).toEqual(
                    new DataType({
                        prefix: classDummy.prefix,
                        label: newLabel,
                        type: DataTypeTypes.RANGE,
                    }),
                );
            }
        });

        test("should update the label of to-associations", () => {
            for (let association of classDummy.associationPairs) {
                expect(association.to.label).toEqual(newLabel);
            }
        });

        test("should not update any other property", () => {
            expect(classDummy.superClass).toEqual(initialClassDummy.superClass);
            expect(classDummy.comment).toEqual(initialClassDummy.comment);
            expect(classDummy.package).toEqual(initialClassDummy.package);
            expect(classDummy.stereotypes).toEqual(
                initialClassDummy.stereotypes,
            );
        });
    });
});
