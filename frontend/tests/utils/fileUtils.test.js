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
import { describe, expect, it, test } from "vitest";

import {
    filenameFromContentDisposition,
    isImportableFile,
    isWorkspaceArchiveFile,
    workspaceNameOfArchiveFile,
} from "$lib/utils/fileUtils.ts";

describe("filenameFromContentDisposition", () => {
    it("reads the UTF-8 encoded name first", () => {
        expect(
            filenameFromContentDisposition(
                "attachment; filename=\"=?UTF-8?Q?=E4=B8=AD=E6=96=87.ttl?=\"; filename*=UTF-8''%E4%B8%AD%E6%96%87.ttl",
            ),
        ).toBe("中文.ttl");
    });

    it("decodes spaces and other escaped characters", () => {
        expect(
            filenameFromContentDisposition(
                "attachment; filename*=UTF-8''my%20profile%20%231.adoc",
            ),
        ).toBe("my profile #1.adoc");
    });

    it("falls back to the quoted name", () => {
        expect(
            filenameFromContentDisposition('attachment; filename="EQ v2.ttl"'),
        ).toBe("EQ v2.ttl");
    });

    it("unescapes a quoted name", () => {
        expect(
            filenameFromContentDisposition(
                'attachment; filename="say \\"hi\\".ttl"',
            ),
        ).toBe('say "hi".ttl');
    });

    it("reads an unquoted name", () => {
        expect(
            filenameFromContentDisposition("attachment; filename=EQ.ttl"),
        ).toBe("EQ.ttl");
    });

    it("falls back to the plain name when the encoded one is malformed", () => {
        expect(
            filenameFromContentDisposition(
                "attachment; filename=\"EQ.ttl\"; filename*=UTF-8''%E4%B8.ttl",
            ),
        ).toBe("EQ.ttl");
    });

    it.each([null, undefined, "", "attachment"])(
        "suggests no name for %j",
        header => {
            expect(filenameFromContentDisposition(header)).toBeNull();
        },
    );
});

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
