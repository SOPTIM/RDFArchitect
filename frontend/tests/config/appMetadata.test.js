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

import { getAppMetadata } from "$lib/config/appMetadata.js";

describe("getAppMetadata", () => {
    test("builds a GitHub commit URL and environment label", () => {
        const metadata = getAppMetadata({
            appVersion: "1.2.3-7-gabc12345",
            commitSha: "abc12345",
            repositoryUrl: "https://github.com/SOPTIM/RDFArchitect/",
            deploymentEnvironment: "dev",
        });

        expect(metadata).toEqual({
            versionLabel: "1.2.3-7-gabc12345",
            commitLabel: "abc12345",
            commitUrl: "https://github.com/SOPTIM/RDFArchitect/commit/abc12345",
            environmentLabel: "dev",
        });
    });

    test("omits optional labels when values are blank", () => {
        const metadata = getAppMetadata({
            appVersion: "",
            commitSha: "   ",
            repositoryUrl: "https://github.com/SOPTIM/RDFArchitect",
            deploymentEnvironment: "",
        });

        expect(metadata).toEqual({
            versionLabel: "",
            commitLabel: "",
            commitUrl: "",
            environmentLabel: "",
        });
    });
});
