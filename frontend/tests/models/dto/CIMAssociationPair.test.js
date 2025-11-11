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

import { DataType } from "$lib/models/dto/index.js";

import { createRandomAssociationPair } from "../../testHelpers.js";

describe("AssociationPair", () => {
    let associationPair;
    let initialAssociationPair;

    describe("updateDomain()", () => {
        let newDomain;

        beforeEach(() => {
            // Arrange
            associationPair = createRandomAssociationPair();
            initialAssociationPair = JSON.parse(
                JSON.stringify(associationPair),
            );

            newDomain = "NewDomain";

            // Act
            associationPair.updateDomain(newDomain);
        });

        // Assert
        test("should update the domain of the from-association", () => {
            expect(associationPair.from.domain).toBe(newDomain);
        });

        test("should update range of the to-association", () => {
            expect(associationPair.to.range).toEqual(
                new DataType({
                    prefix: associationPair.from.prefix,
                    label: newDomain,
                    type: "RANGE",
                }),
            );
        });

        test("should update the label of the to-association", () => {
            expect(associationPair.to.label).toEqual(newDomain);
        });

        test("should not change any other property of the from-association", () => {
            expect(associationPair.from.label).toEqual(
                initialAssociationPair.from.label,
            );
            expect(associationPair.from.comment).toEqual(
                initialAssociationPair.from.comment,
            );
            expect(associationPair.from.multiplicity).toEqual(
                initialAssociationPair.from.multiplicity,
            );
            expect(associationPair.from.range).toEqual(
                initialAssociationPair.from.range,
            );
            expect(associationPair.from.associationUsed).toEqual(
                initialAssociationPair.from.associationUsed,
            );
        });

        test("should not change any other property of the to-association", () => {
            expect(associationPair.to.comment).toEqual(
                initialAssociationPair.to.comment,
            );
            expect(associationPair.to.multiplicity).toEqual(
                initialAssociationPair.to.multiplicity,
            );
            expect(associationPair.to.domain).toEqual(
                initialAssociationPair.to.domain,
            );
            expect(associationPair.to.associationUsed).toEqual(
                initialAssociationPair.to.associationUsed,
            );
        });
    });

    describe("updateRange()", () => {
        let newRange;

        beforeEach(() => {
            // Arrange
            newRange = new DataType({
                prefix: "http://example.com/",
                label: "NewRange",
                type: "RANGE",
            });
            associationPair = createRandomAssociationPair();
            initialAssociationPair = JSON.parse(
                JSON.stringify(associationPair),
            );

            // Act
            associationPair.updateRange(associationPair.from, newRange);
        });

        // Assert
        test("should update the range of the from-association", () => {
            expect(associationPair.from.range).toEqual(newRange);
        });

        test("should update the label of the from-association", () => {
            expect(associationPair.from.label).toEqual(newRange.label);
        });

        test("should update the domain of the to-association", () => {
            expect(associationPair.to.domain).toEqual(newRange.label);
        });

        test("should not change any other property of the from-association", () => {
            expect(associationPair.from.comment).toEqual(
                initialAssociationPair.from.comment,
            );
            expect(associationPair.from.multiplicity).toEqual(
                initialAssociationPair.from.multiplicity,
            );
            expect(associationPair.from.domain).toEqual(
                initialAssociationPair.from.domain,
            );
            expect(associationPair.from.associationUsed).toEqual(
                initialAssociationPair.from.associationUsed,
            );
        });

        test("should not change any other property of the to-association", () => {
            expect(associationPair.to.label).toEqual(
                initialAssociationPair.to.label,
            );
            expect(associationPair.to.comment).toEqual(
                initialAssociationPair.to.comment,
            );
            expect(associationPair.to.multiplicity).toEqual(
                initialAssociationPair.to.multiplicity,
            );
            expect(associationPair.to.range).toEqual(
                initialAssociationPair.to.range,
            );
            expect(associationPair.to.associationUsed).toEqual(
                initialAssociationPair.to.associationUsed,
            );
        });
    });
});
