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
 * Reading of the schema occurrences of a class: how they are labelled and
 * whether they agree on the class enough to extend it without asking.
 */

import { compareGraphs } from "$lib/utils/graph-order.js";
import { uriSuffix } from "$lib/utils/iri.js";

export function schemaLabel(occurrence) {
    return occurrence?.keyword || uriSuffix(occurrence?.graphUri);
}

/** Sorts graphs the way the navigation lists them: short name, then uri. */
export function sortByGraphOrder(entries, labelOf, uriOf) {
    return [...(entries ?? [])].sort((a, b) =>
        compareGraphs(
            { label: labelOf(a), uri: uriOf(a) },
            { label: labelOf(b), uri: uriOf(b) },
        ),
    );
}

/** Lists the schemas the way the navigation lists them. */
export function sortSchemaOccurrences(occurrences) {
    return sortByGraphOrder(occurrences, schemaLabel, entry => entry.graphUri);
}

/** Marks the schemas that do not know the class yet. */
export function schemaMarker(occurrence) {
    return occurrence?.present ? "" : "not in schema";
}

/**
 * Merges the schema lists of several classes into one: a schema counts as
 * having the class only when it defines every one of them, so a selection can
 * still be extended into a schema that misses one of its classes.
 */
export function mergeSchemaOccurrences(lists) {
    const merged = new Map();
    for (const occurrences of lists ?? []) {
        for (const occurrence of occurrences) {
            const known = merged.get(occurrence.graphUri);
            if (!known) {
                merged.set(occurrence.graphUri, { ...occurrence });
                continue;
            }
            known.present = known.present && occurrence.present;
        }
    }
    return [...merged.values()];
}

/**
 * The schemas a stub can be copied from: every schema that defines the class,
 * except the one it is about to be created in.
 */
export function sourceCandidates(occurrences, targetGraphUri) {
    return (occurrences ?? []).filter(
        occurrence =>
            occurrence.present &&
            occurrence.graphUri !== String(targetGraphUri),
    );
}

function stubSignature(occurrence) {
    const stub = occurrence?.stub;
    return JSON.stringify([
        stub?.label ?? null,
        stub?.comment ?? null,
        stub?.superClassUri ?? null,
        stub?.packageUri ?? null,
        stub?.stereotypes ?? [],
    ]);
}

/**
 * Groups the candidates by the class they would create, so that schemas which
 * agree on it are offered as one choice.
 *
 * @returns {Array<{key: string, occurrences: Array<Object>}>}
 */
export function groupCandidatesByStub(candidates) {
    const groups = new Map();
    for (const candidate of candidates ?? []) {
        const signature = stubSignature(candidate);
        const group = groups.get(signature);
        if (group) {
            group.occurrences.push(candidate);
            continue;
        }
        groups.set(signature, {
            key: candidate.graphUri,
            occurrences: [candidate],
        });
    }
    return [...groups.values()];
}

/**
 * True when the candidates would not all produce the same class, so the schema
 * to copy from has to be picked by the user.
 */
export function stubsDiffer(candidates) {
    if ((candidates?.length ?? 0) < 2) {
        return false;
    }
    const first = stubSignature(candidates[0]);
    return candidates.some(candidate => stubSignature(candidate) !== first);
}

/** The source group of a single class, as the extension expects it. */
export function sourceOfOccurrence(occurrence) {
    return [
        { graphUri: occurrence.graphUri, classUuids: [occurrence.classUUID] },
    ];
}
