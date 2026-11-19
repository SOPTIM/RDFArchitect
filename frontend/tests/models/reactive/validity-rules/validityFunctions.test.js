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
import { describe, expect, it } from "vitest";

import {
    isInvalidSchemaName,
    isInvalidSuperClass,
    isManuallyEnteredConcreteStereotype,
} from "../../../../src/lib/models/reactive/validity-rules/validityFunctions.js";
import { CONCRETE_STEREOTYPE } from "../../../../src/lib/models/stereotype-constants.js";

describe("isManuallyEnteredConcreteStereotype", () => {
    it("flags a modified entry set to the concrete URI", () => {
        expect(
            isManuallyEnteredConcreteStereotype(CONCRETE_STEREOTYPE, true),
        ).toHaveLength(1);
    });

    it("does not flag a persisted (unmodified) concrete entry", () => {
        // A class loaded as non-abstract carries the concrete stereotype; it is
        // surfaced via the "Abstract" checkbox and must stay valid.
        expect(
            isManuallyEnteredConcreteStereotype(CONCRETE_STEREOTYPE, false),
        ).toEqual([]);
    });

    it("does not flag a regular stereotype, modified or not", () => {
        const regular = "http://example.org/some#stereotype";
        expect(isManuallyEnteredConcreteStereotype(regular, true)).toEqual([]);
        expect(isManuallyEnteredConcreteStereotype(regular, false)).toEqual([]);
    });
});

describe("isInvalidSuperClass", () => {
    it("accepts the uuid of a class of this schema", () => {
        expect(
            isInvalidSuperClass("2c9916ee-a33e-4a2a-a0b8-ad1ba1322ffd"),
        ).toEqual([]);
    });

    it("accepts the uri of a superclass that this schema only references", () => {
        expect(
            isInvalidSuperClass("http://iec.ch/TC57/CIM100#LimitSet"),
        ).toEqual([]);
    });

    it("flags anything that is neither a uuid nor a uri", () => {
        expect(isInvalidSuperClass("LimitSet")).toHaveLength(1);
    });
});

describe("isInvalidSchemaName", () => {
    const FORBIDDEN = [
        'A schema name must not contain any of \\ / : * ? " < > | ; and must not be a name reserved by the file system',
    ];

    it.each(["my-profile", "Equipment Core", "a%b#c&d+e"])(
        "accepts %j",
        name => {
            expect(isInvalidSchemaName(name, [])).toEqual([]);
        },
    );

    it("ignores whitespace around the name", () => {
        expect(isInvalidSchemaName("  Equipment  ", [])).toEqual([]);
    });

    it("flags an empty name", () => {
        expect(isInvalidSchemaName("", [])).toEqual(["must not be empty"]);
    });

    it("flags a name of whitespace only", () => {
        expect(isInvalidSchemaName("   ", [])).toEqual(["must not be empty"]);
    });

    it("flags a missing name", () => {
        expect(isInvalidSchemaName(undefined, [])).toEqual([
            "must not be empty",
        ]);
    });

    it.each(["\\", "/", ":", "*", "?", '"', "<", ">", "|", ";"])(
        "flags a name containing %j",
        character => {
            expect(isInvalidSchemaName(`a${character}b`, [])).toEqual(
                FORBIDDEN,
            );
        },
    );

    it("flags a name the file system reserves", () => {
        expect(isInvalidSchemaName("CON", [])).toEqual(FORBIDDEN);
    });

    it("flags a name another schema of the workspace already has", () => {
        expect(isInvalidSchemaName("Equipment", ["Equipment"])).toEqual([
            "A schema with this name already exists",
        ]);
    });

    it("compares the trimmed name against the other schemas", () => {
        expect(isInvalidSchemaName(" Equipment ", ["Equipment"])).toEqual([
            "A schema with this name already exists",
        ]);
    });

    it("accepts a name when there are no other schemas to compare against", () => {
        expect(isInvalidSchemaName("Equipment", undefined)).toEqual([]);
    });
});
