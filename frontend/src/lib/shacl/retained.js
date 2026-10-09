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
 * What a shape or a rule says that the form shows but never rewrites.
 *
 * The backend splits every clause of a shape into one the form owns and one it keeps exactly as
 * the document wrote it. Both halves have to reach the screen: a kept clause the form has a field
 * for replaces that field, so nobody types into a box whose value cannot be saved, and one it has
 * no field for is listed underneath so the card is honest about what it is not showing.
 */

/**
 * The kept clauses by the field each would have filled, as `Map<field, clause[]>`.
 *
 * A list per field rather than one clause, because "the document states this twice" is one of the
 * reasons a field is kept as written, and showing one of the two values would be worse than
 * showing neither.
 */
export function keptFields(retained) {
    const byField = new Map();
    for (const clause of retained ?? []) {
        if (!clause.field) {
            continue;
        }
        byField.set(clause.field, [
            ...(byField.get(clause.field) ?? []),
            clause,
        ]);
    }
    return byField;
}

/**
 * The kept clauses no field on the card shows.
 *
 * Two kinds: a clause the form has no field for at all — `sh:sparql`, `sh:minInclusive` — and one
 * whose field the form knows about but does not put on screen, such as `sh:order`. Both have to be
 * listed or they vanish from the card entirely, which is how a shape would appear to say less than
 * it does. `shownFields` is the card's own list of what it renders, so the two cannot drift.
 */
export function keptClauses(retained, shownFields = []) {
    const shown = new Set(shownFields);
    return (retained ?? []).filter(
        clause => !clause.field || !shown.has(clause.field),
    );
}
