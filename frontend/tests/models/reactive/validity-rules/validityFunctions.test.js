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

import { isManuallyEnteredConcreteStereotype } from "../../../../src/lib/models/reactive/validity-rules/validityFunctions.js";
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
