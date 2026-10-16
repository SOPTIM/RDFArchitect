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

import { describe, expect, test } from "vitest";

import { FocusField, PropertyKind } from "$lib/propertyEditorRequest.svelte.js";
import {
    associationEditTarget,
    isEditableEdge,
} from "$lib/rendering/svelteflow/interaction/edgeEditing.js";

function label(associationEndUUID) {
    return { associationEndUUID, identifiedObjectUUID: associationEndUUID };
}

function association(data) {
    return {
        id: "edge",
        type: "association",
        source: "source-class",
        target: "target-class",
        data,
    };
}

describe("associationEditTarget", () => {
    test("opens the association end of the source class", () => {
        const edge = association({
            graphUri: "http://graph#EQ",
            sourceMultiplicityLabel: label("end-of-target-class"),
            targetMultiplicityLabel: label("end-of-source-class"),
        });

        expect(associationEditTarget(edge)).toEqual({
            classUuid: "source-class",
            graphUri: "http://graph#EQ",
            kind: PropertyKind.ASSOCIATION,
            propertyUuid: "end-of-source-class",
            focus: FocusField.LABEL,
        });
    });

    test("falls back to the end of the target class", () => {
        const edge = association({
            sourceAssociationLabel: label("end-of-target-class"),
        });

        expect(associationEditTarget(edge)).toMatchObject({
            classUuid: "target-class",
            graphUri: null,
            propertyUuid: "end-of-target-class",
        });
    });

    test("is null for an edge without association ends", () => {
        expect(associationEditTarget(association({}))).toBeNull();
    });
});

describe("isEditableEdge", () => {
    test("inheritances and associations with an end can be edited", () => {
        expect(isEditableEdge({ type: "inheritance", data: {} })).toBe(true);
        expect(
            isEditableEdge(
                association({ targetMultiplicityLabel: label("end") }),
            ),
        ).toBe(true);
        expect(isEditableEdge(association({}))).toBe(false);
        expect(isEditableEdge(null)).toBe(false);
    });
});
