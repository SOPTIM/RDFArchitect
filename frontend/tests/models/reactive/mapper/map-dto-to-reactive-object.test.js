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

import { Class } from "$lib/models/dto/index.js";
import { findSuperClass } from "$lib/models/reactive/mapper/map-dto-to-reactive-object.js";

const PREFIX = "http://example.org#";

function classes(...labels) {
    return labels.map(
        label => new Class({ uuid: `uuid-${label}`, prefix: PREFIX, label }),
    );
}

describe("findSuperClass()", () => {
    test("stands in for a super class the schema does not contain", () => {
        const given = classes("Child");

        const result = findSuperClass(given, {
            superClass: { prefix: PREFIX, label: "BaseClass" },
        });

        expect(result.label).toBe("BaseClass");
        expect(result.prefix).toBe(PREFIX);
        expect(result.uuid).toBe(PREFIX + "BaseClass");
        expect(given).toHaveLength(1);
    });

    test("returns the known class when the super class is among them", () => {
        const given = classes("Child", "BaseClass");

        const result = findSuperClass(given, {
            superClass: { prefix: PREFIX, label: "BaseClass" },
        });

        expect(result).toBe(given[1]);
    });

    test("returns null without a super class", () => {
        const given = classes("Child");

        expect(findSuperClass(given, {})).toBeNull();
        expect(findSuperClass(given, { superClass: null })).toBeNull();
    });

    test("tells apart classes of the same name in different namespaces", () => {
        const result = findSuperClass(classes("BaseClass"), {
            superClass: { prefix: "http://other.org#", label: "BaseClass" },
        });

        expect(result.uuid).toBe("http://other.org#BaseClass");
    });
});
