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

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __filepath = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filepath);
const __filename = "LICENSES-THIRD-PARTY.md";

// Command line interface
const command = process.argv[2];

function generateLicenseContent() {
    const packageJsonPath = path.join(__dirname, "package.json");
    const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, "utf8"));
    const dependencies = {
        ...packageJson.dependencies,
        ...packageJson.devDependencies,
    };

    const packages = [];

    for (const [name] of Object.entries(dependencies)) {
        try {
            const pkgPath = path.join(
                __dirname,
                "node_modules",
                name,
                "package.json",
            );
            const pkg = JSON.parse(fs.readFileSync(pkgPath, "utf8"));

            const license = pkg.license || "Unknown";
            const repository = pkg.homepage || pkg.repository?.url || "";
            const reference = repository
                .replace(/^git\+/, "")
                .replace(/\.git$/, "");

            packages.push({
                name: pkg.name,
                version: pkg.version,
                license,
                reference,
            });
        } catch {
            console.warn(`Warning: Could not read package info for ${name}`);
        }
    }

    let markdown = "# Third-Party Licenses\n\n";

    for (const pkg of packages.sort((a, b) => a.name.localeCompare(b.name))) {
        markdown += `### ${pkg.name}\n`;
        markdown += `- **Package:** ${pkg.name}\n`;
        markdown += `- **Version:** ${pkg.version}\n`;
        markdown += `- **License:** ${pkg.license}\n`;
        if (pkg.reference) {
            markdown += `- **URL:** [${pkg.reference}](${pkg.reference})\n`;
        }
        markdown += "\n";
    }

    return markdown;
}

function checkLicenses(licensesFile) {
    if (!fs.existsSync(licensesFile)) {
        console.error(
            __filename + " does not exist! Run: npm run licenses:generate",
        );
        process.exit(1);
    }

    const currentContent = fs.readFileSync(licensesFile, "utf8");
    const newContent = generateLicenseContent();

    if (currentContent !== newContent) {
        console.error(
            "LICENSES-THIRD-PARTY.md is outdated! Run: npm run licenses:generate",
        );
        process.exit(1);
    }

    console.log("LICENSES-THIRD-PARTY.md is up to date");
}

if (command === "check") {
    const licensesFile = path.join(__dirname, __filename);
    checkLicenses(licensesFile);
} else if (command === "generate") {
    const outputFile = path.join(__dirname, __filename);
    const content = generateLicenseContent();
    fs.writeFileSync(outputFile, content);
    console.log(__filename + " generated");
}
