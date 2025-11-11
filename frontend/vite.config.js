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
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

// Read version from repo root VERSION file and expose as PUBLIC_APP_VERSION
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
const versionFile = path.join(rootDir, "VERSION");
let version;
try {
    if (fs.existsSync(versionFile)) {
        const raw = fs.readFileSync(versionFile, "utf8").trim();
        if (raw) version = raw;
    }
} catch {
    // ignore
}

if (!process.env.PUBLIC_APP_VERSION && version) {
    process.env.PUBLIC_APP_VERSION = version;
}

export default defineConfig({
    plugins: [sveltekit(), tailwindcss()],
    optimizeDeps: {
        exclude: ["tinybench"], // Add the package name here
    },
    resolve: process.env.VITEST
        ? {
              conditions: ["browser"],
          }
        : undefined,
    test: {
        environment: "jsdom",
        globals: true, // Optional: makes describe, test, expect available globally
    },
});
