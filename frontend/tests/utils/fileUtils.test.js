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

import {
    isImportableFile,
    isWorkspaceArchiveFile,
    workspaceNameOfArchiveFile,
} from "$lib/utils/fileUtils.ts";

describe("workspace archives", () => {
    test("are taken by the workspace import, also when renamed to .zip", () => {
        expect(isWorkspaceArchiveFile("CGMES 3.0.rdfa")).toBe(true);
        expect(isWorkspaceArchiveFile("CGMES 3.0.RDFA")).toBe(true);
        expect(isWorkspaceArchiveFile("CGMES 3.0.rdfa.zip")).toBe(true);
        expect(isWorkspaceArchiveFile("core.ttl")).toBe(false);
    });

    test("are not taken by the schema import, which would unpack them", () => {
        expect(isImportableFile("CGMES 3.0.rdfa")).toBe(false);
    });

    test("suggest their name without the extension as the workspace name", () => {
        expect(workspaceNameOfArchiveFile("CGMES 3.0.rdfa")).toBe("CGMES 3.0");
        expect(workspaceNameOfArchiveFile("CGMES 3.0.rdfa.zip")).toBe(
            "CGMES 3.0",
        );
        expect(workspaceNameOfArchiveFile("CGMES 3.0.zip")).toBe("CGMES 3.0");
    });
});
