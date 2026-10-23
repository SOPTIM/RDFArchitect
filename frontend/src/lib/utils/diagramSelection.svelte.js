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
 * Choosing among the class diagrams of a read export, beside the packages they depend on.
 *
 * A diagram can be imported exactly when every class it draws is part of the import. The read
 * works that dependence out once and reports it as supplier groups — one per distinct set of
 * packages that could supply a class the diagram draws — so the answer follows the package
 * selection without asking the backend again.
 */

import { ownRowId } from "$lib/utils/packageTree.svelte.js";

/** Where a diagram drawing across several schemas is stored, there being no one schema for it. */
export const WORKSPACE = "workspace";

/**
 * The supplier groups as the rows that would meet them, keyed by file and index. A package
 * supplying a class taken alone is a different row of the tree from the same package taken with
 * its sub-packages, so each scope contributes the row id it is ticked under.
 *
 * <p>The indices are handed out per export, so the file is part of the key: a drop of several
 * exports has a group 0 in each.
 */
export function groupsOf(supplierGroups) {
    const byKey = new Map();
    for (const group of supplierGroups ?? []) {
        byKey.set(groupKey(group.fileIndex, group.index), [
            ...(group.packageIds ?? []),
            ...(group.packageOnlyIds ?? []).map(ownRowId),
        ]);
    }
    return byKey;
}

function groupKey(fileIndex, index) {
    return `${fileIndex ?? 0}:${index}`;
}

/**
 * The diagrams of each package, by the package they are offered under. That is not always the one
 * declaring them — the read resolves a diagram of a package that is no schema outwards to one
 * that is.
 */
export function diagramsByPackage(diagrams) {
    const byPackage = new Map();
    for (const diagram of diagrams ?? []) {
        if (!byPackage.has(diagram.packageId)) {
            byPackage.set(diagram.packageId, []);
        }
        byPackage.get(diagram.packageId).push(diagram);
    }
    return byPackage;
}

/**
 * The row of the tree whose schema a diagram would be stored in, {@link WORKSPACE} when it draws
 * across several, or {@code null} when the selection does not supply it at all.
 *
 * <p>A class the selection supplies more than once — a datatype two profiles both carry — is
 * written into each of their schemas, and the diagram is placed against the last of them. That is
 * what the import does, so it is what is predicted here, which is why the rows are taken in the
 * order they are imported in rather than as a set.
 *
 * @param diagram as the read reported it
 * @param groups the supplier groups as {@link groupsOf} keyed them
 * @param orderedRowIds the ticked rows, in the order they are imported in
 */
export function homeOf(diagram, groups, orderedRowIds) {
    let home = null;
    for (const group of diagram?.supplierGroups ?? []) {
        const suppliers = groups.get(groupKey(diagram.fileIndex, group)) ?? [];
        const written = orderedRowIds.findLast(rowId =>
            suppliers.includes(rowId),
        );
        if (written === undefined) {
            return null;
        }
        if (home === null) {
            home = written;
        } else if (home !== written) {
            return WORKSPACE;
        }
    }
    return home;
}

/**
 * The diagrams an import would store, by where it would store them.
 *
 * @returns {{byRow: Map<string, object[]>, workspace: object[]}} the diagrams of each ticked row
 *     and those that belong to the workspace, each in the order the export declares them
 */
export function diagramHomes(diagrams, groups, orderedRowIds, deselectedIds) {
    const byRow = new Map();
    const workspace = [];
    for (const diagram of diagrams ?? []) {
        if (deselectedIds.has(diagram.id)) {
            continue;
        }
        const home = homeOf(diagram, groups, orderedRowIds);
        if (home === null) {
            continue;
        }
        if (home === WORKSPACE) {
            workspace.push(diagram);
            continue;
        }
        if (!byRow.has(home)) {
            byRow.set(home, []);
        }
        byRow.get(home).push(diagram);
    }
    return { byRow, workspace };
}

/**
 * The diagrams an import would take: every one the selection makes available, bar those turned
 * down. Taken from what the read reported rather than from the state of the moment, so that a
 * caller which has moved on to the progress of the import still asks the right question.
 *
 * @param diagrams as the read reported them
 * @param groups the supplier groups as {@link groupsOf} keyed them
 * @param orderedRowIds the ticked rows, in the order they are imported in
 * @param deselectedIds the diagrams ticked away
 */
export function chosenDiagramIds(
    diagrams,
    groups,
    orderedRowIds,
    deselectedIds,
) {
    return (diagrams ?? [])
        .filter(
            diagram =>
                !deselectedIds.has(diagram.id) &&
                homeOf(diagram, groups, orderedRowIds) !== null,
        )
        .map(diagram => diagram.id);
}

/** The selection after clicking one diagram. */
export function toggled(id, selectedIds) {
    const next = new Set(selectedIds);
    if (next.has(id)) {
        next.delete(id);
    } else {
        next.add(id);
    }
    return next;
}
