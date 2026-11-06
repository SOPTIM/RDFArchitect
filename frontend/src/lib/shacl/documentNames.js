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

/** Extensions of the RDF syntaxes an import converts to Turtle. */
const CONVERTED_EXTENSION = /\.(rdf|xml|owl|nt)$/i;

/** Extensions Turtle is already written under. */
const TURTLE_EXTENSION = /\.(ttl|shacl|n3)$/i;

/**
 * The wanted name, or the first free `name (n).ext` after it.
 *
 * The number goes before the extension, so the copy is still a `.ttl` file — `eq.ttl (2)` was
 * downloaded as `eq.ttl (2).ttl`.
 *
 * Compared without regard to case, as the backend compares them: `EQ.ttl` and `eq.ttl` read as one
 * name and collide as file names on export, so the server refuses the second, and a name this
 * offered as free was answered with a conflict.
 */
export function uniqueDocumentName(existingNames, wanted) {
    const taken = new Set(
        (existingNames ?? []).map(name => name.toLowerCase()),
    );
    const isTaken = name => taken.has(name.toLowerCase());
    if (!isTaken(wanted)) {
        return wanted;
    }
    const dot = wanted.lastIndexOf(".");
    const [stem, extension] =
        dot > 0 ? [wanted.slice(0, dot), wanted.slice(dot)] : [wanted, ""];
    let suffix = 2;
    while (isTaken(`${stem} (${suffix})${extension}`)) {
        suffix += 1;
    }
    return `${stem} (${suffix})${extension}`;
}

/**
 * The file name a document downloads as.
 *
 * What is stored is always Turtle — an RDF/XML or N-Triples import is converted once, on the way
 * in — so a document named after such a file is saved as `.ttl` rather than as `.rdf.ttl`.
 */
export function downloadNameOf(name) {
    if (TURTLE_EXTENSION.test(name)) {
        return name;
    }
    return `${name.replace(CONVERTED_EXTENSION, "")}.ttl`;
}
