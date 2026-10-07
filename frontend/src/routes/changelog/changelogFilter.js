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
 * What the changelog shows of a workspace's history.
 *
 * The log is recorded for the workspace as a whole, so a graph's changelog is derived from it
 * rather than kept apart: a change belongs to a graph when it named that graph, which covers both
 * what was done inside it and what was done to it — creating, deleting and renaming are recorded
 * against the set of graphs, not against the graph itself.
 *
 * @typedef {{hiddenKinds?: Set<string>, graphUri?: string|null}} ChangelogFilter
 */

/**
 * Returns whether a change is one the filter leaves showing.
 *
 * A change survives the kind filter as long as one of the kinds it touched is still showing: it is
 * still a change the user made, and hiding it entirely because part of it is hidden would make the
 * history look shorter than it is.
 *
 * An entry that touched no kind at all is the state the workspace was loaded in, and it is shown
 * whatever is being filtered: it names no kind and no schema, but it is where every schema began
 * as much as the workspace did. It is the mark of where the workspace stands once everything has
 * been undone, and the only entry a restore can go back to the beginning from.
 *
 * @param {{affectedKinds?: string[], affectedGraphUris?: string[]}} change the recorded change
 * @param {ChangelogFilter} filter what is being shown
 * @returns {boolean} whether to show it
 */
export function showsChange(change, { hiddenKinds, graphUri } = {}) {
    const kinds = change.affectedKinds ?? [];
    if (kinds.length === 0) {
        return true;
    }
    if (graphUri && !(change.affectedGraphUris ?? []).includes(graphUri)) {
        return false;
    }
    const hidden = hiddenKinds ?? new Set();
    return kinds.some(kind => !hidden.has(kind));
}

/**
 * Returns whether a restore held to one schema takes this change back.
 *
 * A different question from whether the change shows in that schema's log: creating, deleting and
 * renaming a schema is recorded against the set of schemas, which belongs to the workspace and is
 * therefore left where it is by a restore held to a schema. Asking the one question for the other
 * would promise to undo something that stays.
 *
 * @param {{restorableGraphUris?: string[]}} change the recorded change
 * @param {string} graphUri the schema the restore is held to
 * @returns {boolean} whether the restore takes it back
 */
export function restoresChange(change, graphUri) {
    return (change.restorableGraphUris ?? []).includes(graphUri);
}

/**
 * Returns the deltas of a change that the filter leaves showing, and that have something to show.
 *
 * A delta carries either triples or named values, depending on whether the data behind it is held
 * as RDF: the prefixes and the colours the schemas are drawn in are neither added nor deleted, they
 * are given another value. One carrying neither would be a heading over nothing — the triples of a
 * delta are held weakly, so they can be gone while the entry naming them remains.
 *
 * @param {{contextDeltas?: Array<object>}} change the change
 * @param {ChangelogFilter} filter what is being shown
 * @returns {Array<object>} the deltas to show
 */
export function visibleDeltas(change, { hiddenKinds, graphUri } = {}) {
    const hidden = hiddenKinds ?? new Set();
    return (change.contextDeltas ?? []).filter(
        delta =>
            !hidden.has(delta.contextName) &&
            (!graphUri || delta.graphUri === graphUri) &&
            showsSomething(delta),
    );
}

function showsSomething(delta) {
    return (
        delta.additions?.length > 0 ||
        delta.deletions?.length > 0 ||
        delta.values?.length > 0
    );
}
