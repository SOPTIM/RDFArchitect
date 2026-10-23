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
 * What an import could not carry over, as the validation report lists a finding.
 *
 * The two are the same kind of thing — something worth reading about a schema, with what it is
 * about, why it is there and which schema it concerns — so they are shown by the same table
 * rather than by a second one built to look like it.
 */

/**
 * The warnings of an import as validation findings.
 *
 * <p>Every one of them is a warning rather than an error: what is reported here was imported, only
 * not as the file states it. The reason becomes the finding, which is what makes grouping by it
 * put everything of one kind together — the 38 header fields a CGMES 3.0 model states under a name
 * no ontology field matches, say. Where it was found takes the place a validation finding gives
 * the check that produced it, so that it is a column and a grouping of its own.
 *
 * <p>No occurrences are reported. They are the schemas a finding is about <em>with the value
 * found in each</em>, and an import warning is about one file and carries no value: given one
 * anyway, the table would offer to open a row that has nothing to show.
 *
 * @param {{fileName: string, graphUri: string, items: {resource: string, reason: string}[]}[]}
 *     warnings as the import reported them
 * @returns {{issues: object[]}} a result the validation report can be given
 */
export function asValidationResult(warnings) {
    const issues = (warnings ?? []).flatMap(warning =>
        (warning.items ?? []).map(item => ({
            severity: "WARNING",
            ruleLabel: warning.fileName,
            ruleId: warning.graphUri,
            resourceUri: item.resource,
            message: item.reason,
        })),
    );
    return { valid: issues.length === 0, issues };
}

/** How many things an import could not carry over, rather than how many files hold one. */
export function countUncarried(warnings) {
    return (warnings ?? []).reduce(
        (sum, warning) => sum + (warning.items ?? []).length,
        0,
    );
}
