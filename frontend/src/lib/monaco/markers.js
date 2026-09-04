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
 * Turns the findings of a validation report into Monaco markers.
 *
 * Kept separate from the editor component, and given the editor's coordinate lookups as callbacks
 * rather than a model, so the mapping can be tested without a DOM.
 */

/** Marker owner, so a validation run replaces only its own markers. */
export const MARKER_OWNER = "shacl";

const SOURCE_LABELS = {
    SYNTAX: "Turtle",
    SHAPE: "SHACL",
    SPARQL: "SPARQL",
    CONFLICT: "Documents",
};

/**
 * @param findings the report's findings for one document
 * @param severities Monaco's `MarkerSeverity` values, keyed `ERROR`, `WARNING` and `INFO`
 * @param extentOf `(line, column) => endColumn`, the end of the thing being complained about
 */
export function toMarkers(findings, severities, extentOf) {
    return findings.filter(hasPosition).map(finding => {
        const line = finding.line;
        const column = finding.column;
        return {
            severity: severities[finding.severity] ?? severities.INFO,
            message: finding.message ?? "",
            source: SOURCE_LABELS[finding.source] ?? finding.source,
            code: finding.code,
            startLineNumber: line,
            startColumn: column,
            endLineNumber: line,
            endColumn: Math.max(extentOf(line, column), column + 1),
        };
    });
}

/**
 * Whether a finding points at a place in the text.
 *
 * Not every one does: a conflict between two documents, or a shape the locator could not find in
 * the source, has a message but no position. Those belong in the problems panel only — a marker
 * without a range would be dropped by Monaco or, worse, land on line 1.
 */
export function hasPosition(finding) {
    return (
        typeof finding?.line === "number" && typeof finding?.column === "number"
    );
}
