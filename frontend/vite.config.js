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
import { sveltekit } from "@sveltejs/kit/vite";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

import { resolveGitBuildMetadata } from "./config/gitVersion.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
const gitBuildMetadata = resolveGitBuildMetadata(rootDir);

if (!process.env.PUBLIC_APP_VERSION) {
    process.env.PUBLIC_APP_VERSION = gitBuildMetadata.version;
}

if (!process.env.PUBLIC_COMMIT_SHA && gitBuildMetadata.commitSha) {
    process.env.PUBLIC_COMMIT_SHA = gitBuildMetadata.commitSha;
}

export default defineConfig({
    plugins: [sveltekit(), tailwindcss()],
    optimizeDeps: {
        exclude: ["tinybench"], // Add the package name here
    },
    resolve: {
        alias: {
            // svelte-collapsible and its dependency svelte-collapse ship a
            // malformed "exports" target ("src/index.js" rather than the
            // spec-compliant "./src/index.js"). Vite 7's resolver tolerated
            // this; Vite 8's rolldown-based resolver enforces the spec and
            // fails to resolve them. Both packages are unmaintained.
            "svelte-collapsible": path.resolve(
                __dirname,
                "node_modules/svelte-collapsible/src/index.js",
            ),
            "svelte-collapse": path.resolve(
                __dirname,
                "node_modules/svelte-collapse/src/index.js",
            ),
        },
        conditions: process.env.VITEST ? ["browser"] : undefined,
    },
    test: {
        environment: "jsdom",
        globals: true, // Optional: makes describe, test, expect available globally
    },
});
