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

const GIT_DESCRIBE_PATTERN = /^v(\d+\.\d+\.\d+)(?:-(\d+)-g([0-9a-f]+))?$/;
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

function parseDescribeVersion(output) {
    const match = GIT_DESCRIBE_PATTERN.exec(output.trim());
    if (!match) {
        return "";
    }

    const [, version, commitCount, commitSha] = match;
    return commitCount && commitSha
        ? `${version}-${commitCount}-g${commitSha}`
        : version;
}

export function resolveGitBuildMetadata(rootDir) {
    const version =
        parseDescribeVersion(
            runGitCommand(rootDir, [
                "describe",
                "--tags",
                "--match",
                "v[0-9]*.[0-9]*.[0-9]*",
                "--abbrev=8",
            ]),
        ) || DEFAULT_VERSION;

    return {
        version,
        commitSha: runGitCommand(rootDir, ["rev-parse", "--short=8", "HEAD"]),
    };
}
