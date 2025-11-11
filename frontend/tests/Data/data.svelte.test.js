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

import { Data, States } from "$lib/scripts/data.svelte.js";

describe("Data Class", () => {
    let dataInstance;

    beforeEach(() => {
        // Arrange
        dataInstance = new Data("initialData", "modifiedData", States.saved);
    });

    test("should initialize with correct values", () => {
        // Act: constructor called in beforeEach

        // Assert
        expect(dataInstance.data).toBe("initialData");
        expect(dataInstance.modifiedData).toBe("modifiedData");
        expect(dataInstance.dataState).toBe(States.saved);
    });

    test("should throw error if data is missing", () => {
        // Act and assert
        expect(() => new Data()).toThrow();
    });

    test("should return correct data", () => {
        // Act
        const result = dataInstance.data;

        // Assert
        expect(result).toBe("initialData");
    });

    test("should update data correctly", () => {
        // Arrange
        const newData = "newData";

        // Act
        dataInstance.data = newData;

        // Assert
        expect(dataInstance.data).toBe(newData);
    });
});
