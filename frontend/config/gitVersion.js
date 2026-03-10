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

import { execFileSync } from "node:child_process";

const SEMVER_TAG_PATTERN = /^v(\d+\.\d+\.\d+)$/;
const DEFAULT_VERSION = "0.0.0-SNAPSHOT";

function runGitCommand(rootDir, args) {
    try {
        return execFileSync("git", args, {
            cwd: rootDir,
            encoding: "utf8",
            stdio: ["ignore", "pipe", "ignore"],
        }).trim();
    } catch {
        return "";
    }
}

function findStableTag(output) {
    for (const line of output.split(/\r?\n/)) {
        const match = SEMVER_TAG_PATTERN.exec(line.trim());
        if (match) {
            return match[1];
        }
    }

    return "";
}

export function resolveGitBuildMetadata(rootDir) {
    const exactVersion = findStableTag(
        runGitCommand(rootDir, [
            "tag",
            "--points-at",
            "HEAD",
            "--sort=-version:refname",
        ]),
    );
    const latestVersion = exactVersion
        ? exactVersion
        : findStableTag(
              runGitCommand(rootDir, [
                  "tag",
                  "--merged",
                  "HEAD",
                  "--sort=-version:refname",
              ]),
          );

    const version = exactVersion
        ? exactVersion
        : latestVersion
          ? `${latestVersion}-SNAPSHOT`
          : DEFAULT_VERSION;

    return {
        version,
        commitSha: runGitCommand(rootDir, ["rev-parse", "--short=8", "HEAD"]),
    };
}
