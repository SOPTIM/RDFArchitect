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

/**
 * Naming an imported constraints document.
 *
 * A document is named after the file it came from, because that is how the CGMES release names its
 * files and how a modeller refers to them — "the DiagramLayout simple constraints" is a file name,
 * not a description someone would invent. Names are unique within a graph, so a second copy has to
 * be distinguished rather than rejected.
 */

/** The wanted name, or the first free `name (n)` after it. */
export function uniqueDocumentName(existingNames, wanted) {
    const taken = new Set(existingNames ?? []);
    if (!taken.has(wanted)) {
        return wanted;
    }
    let suffix = 2;
    while (taken.has(`${wanted} (${suffix})`)) {
        suffix += 1;
    }
    return `${wanted} (${suffix})`;
}
