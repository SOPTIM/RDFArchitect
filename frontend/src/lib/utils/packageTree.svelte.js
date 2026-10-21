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
 * The packages of a read export, as the dialog shows and selects them.
 *
 * The backend reports them flat with a `parentId`. Nesting them here keeps the component free of
 * tree building, and the selection rules live beside the tree they are about.
 *
 * ## What can be picked
 *
 * A row that becomes a schema, which is not every package:
 *
 * - a **profile** is one. A release ships one file per profile and its sub-packages are part of
 *   it, so it is taken whole and not drilled into.
 * - a package with **no sub-packages** is one.
 * - a package **holding others** is not. Its checkbox picks its children, the way a folder does,
 *   because that is what picking it was taken to mean.
 * - the classes such a package declares **beside** its sub-packages are one, shown as a row of
 *   its own. Without it they could not be picked at all: 162 classes of the CGMES 3.0 model sit
 *   like that, 39 in `Environmental` alone.
 */

/** The scopes the backend knows, mirrored. */
export const PackageScope = {
    WITH_SUB_PACKAGES: "WITH_SUB_PACKAGES",
    PACKAGE_ONLY: "PACKAGE_ONLY",
};

/** The id of the row standing for the classes a package declares beside its sub-packages. */
export function ownRowId(packageId) {
    return `${packageId}::own`;
}

/**
 * Nests the reported packages, keeping the order they were reported in.
 *
 * A package whose parent was not reported — one whose parent holds no classes anywhere — becomes
 * a root of its own rather than disappearing with it.
 *
 * @param {object[]} packages as the read reported them
 * @returns {object[]} the outermost packages, each with a `children` array
 */
export function nest(packages) {
    const byId = new Map();
    for (const scanned of packages ?? []) {
        byId.set(scanned.id, { ...scanned, children: [] });
    }
    const roots = [];
    for (const node of byId.values()) {
        const parent = node.parentId ? byId.get(node.parentId) : null;
        if (parent) {
            parent.children.push(node);
        } else {
            roots.push(node);
        }
    }
    return roots;
}

/** Whether this row becomes a schema of its own, as opposed to grouping others. */
export function isSchema(node) {
    return node.profile || node.children.length === 0;
}

/**
 * Whether a package declares classes beside its sub-packages, which then need a row of their own.
 * A profile needs none: it is taken whole.
 */
export function hasOwnRow(node) {
    return !isSchema(node) && (node.ownClasses ?? 0) > 0;
}

/** Every row at or below this one that becomes a schema, the own-classes row included. */
export function schemaRowsUnder(node) {
    if (isSchema(node)) {
        return [{ node, scope: PackageScope.WITH_SUB_PACKAGES, id: node.id }];
    }
    const rows = hasOwnRow(node)
        ? [{ node, scope: PackageScope.PACKAGE_ONLY, id: ownRowId(node.id) }]
        : [];
    for (const child of node.children) {
        rows.push(...schemaRowsUnder(child));
    }
    return rows;
}

/**
 * How a group row reads: ticked when every schema below it is, partly when some are.
 *
 * @returns {"none"|"some"|"all"}
 */
export function groupState(node, selectedIds) {
    const rows = schemaRowsUnder(node);
    if (rows.length === 0) {
        return "none";
    }
    const picked = rows.filter(row => selectedIds.has(row.id)).length;
    if (picked === 0) {
        return "none";
    }
    return picked === rows.length ? "all" : "some";
}

/**
 * The selection after clicking a group row: everything below it off when all of it was on,
 * everything below it on otherwise. The same way a folder behaves.
 */
export function toggledGroup(node, selectedIds) {
    const next = new Set(selectedIds);
    const turnOff = groupState(node, selectedIds) === "all";
    for (const row of schemaRowsUnder(node)) {
        if (turnOff) {
            next.delete(row.id);
        } else {
            next.add(row.id);
        }
    }
    return next;
}

/** The selection after clicking a schema row. */
export function toggledSchema(rowId, selectedIds) {
    const next = new Set(selectedIds);
    if (next.has(rowId)) {
        next.delete(rowId);
    } else {
        next.add(rowId);
    }
    return next;
}

/**
 * What a row costs, as a sentence: what it holds and what its attribute types and superclasses
 * require on top, which ends up in the schema just the same.
 *
 * @param {object} node a nested package
 * @param {string} scope which shape of it is meant
 */
export function describeSize(node, scope = PackageScope.WITH_SUB_PACKAGES) {
    const own = scope === PackageScope.PACKAGE_ONLY;
    const declared = (own ? node.ownClasses : node.declaredClasses) ?? 0;
    const dependencies =
        (own ? node.ownDependencyClasses : node.dependencyClasses) ?? 0;
    const classes = `${declared} class${declared === 1 ? "" : "es"}`;
    return dependencies > 0
        ? `${classes} + ${dependencies} ${dependencies === 1 ? "dependency" : "dependencies"}`
        : classes;
}

/** How many classes a row's schema ends up holding. */
export function sizeOf(node, scope = PackageScope.WITH_SUB_PACKAGES) {
    const own = scope === PackageScope.PACKAGE_ONLY;
    return (
        ((own ? node.ownClasses : node.declaredClasses) ?? 0) +
        ((own ? node.ownDependencyClasses : node.dependencyClasses) ?? 0)
    );
}

/**
 * The schemas the current selection produces, in the order the export declares their packages.
 * This is what the dialog shows beside the tree and what it sends.
 *
 * @param {object[]} packages as the read reported them
 * @param {Set<string>} selectedIds the ticked row ids
 */
export function selectedSchemas(packages, selectedIds) {
    const schemas = [];
    for (const root of nest(packages)) {
        for (const row of schemaRowsUnder(root)) {
            if (selectedIds.has(row.id)) {
                schemas.push(row);
            }
        }
    }
    return schemas;
}

/**
 * The selection as the backend takes it.
 *
 * Each schema is left to be named after its package. The namespaces a profile declares are shown
 * beside it but not used as the name: which of several is meant is a question nobody has been
 * asked yet, and picking the first would answer it silently.
 *
 * @param {object[]} packages as the read reported them
 * @param {Set<string>} selectedIds the ticked row ids
 * @param {string|null} extensionNamespace what to name extension-stereotyped elements with, for an
 *     export that states it nowhere
 */
export function selectionOf(packages, selectedIds, extensionNamespace = null) {
    const supplied = extensionNamespace?.trim() || null;
    return selectedSchemas(packages, selectedIds).map(row => ({
        fileIndex: row.node.fileIndex ?? 0,
        packageId: row.node.id,
        name: row.node.name,
        scope: row.scope,
        graphUri: null,
        extensionNamespace: supplied,
    }));
}
