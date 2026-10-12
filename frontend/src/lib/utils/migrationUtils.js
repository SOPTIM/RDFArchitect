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

import { URI } from "$lib/models/dto/index.ts";

const RENAMED_FROM_VERBS = {
    RENAME: "renamed from",
    MOVE: "moved from",
    MERGE: "merged from",
    SPLIT: "split from",
};

export function isPrefixOnlyRename(oldIRI, newIRI) {
    if (!newIRI || !oldIRI) {
        return false;
    }
    const oldName = new URI(oldIRI).suffix;
    const newName = new URI(newIRI).suffix;
    return oldName === newName;
}

export function isNonInstantiableAssociationRename(renameCandidate) {
    return renameCandidate?.oldResources?.[0]?.associationUsed === false;
}

export function filterRenameCandidates(
    properties,
    { ignorePrefixes = false, alsoHide } = {},
) {
    if (!properties) return properties;

    const deletedAndRemapped = properties.deletedAndRemapped ?? [];
    const addedInClass = properties.addedInClass ?? properties.added ?? [];
    const allAdded = properties.allAdded ?? addedInClass;

    const isHidden = mapping => {
        if (
            mapping.oldResources?.length !== 1 ||
            mapping.newResources?.length !== 1
        ) {
            return false;
        }
        const oldIri = mapping.oldResources[0]?.iri;
        const newIri = mapping.newResources[0]?.iri;
        return (
            (ignorePrefixes && isPrefixOnlyRename(oldIri, newIri)) ||
            (alsoHide?.(mapping) ?? false)
        );
    };

    const hiddenTargetIRIs = new Set(
        deletedAndRemapped
            .filter(isHidden)
            .map(mapping => mapping.newResources?.[0]?.iri)
            .filter(iri => iri != null),
    );

    const filtered = {
        ...properties,
        deletedAndRemapped: deletedAndRemapped.filter(
            mapping => !isHidden(mapping),
        ),
    };
    if (properties.addedInClass) {
        filtered.addedInClass = addedInClass.filter(
            a => !hiddenTargetIRIs.has(a.iri),
        );
    }
    if (properties.allAdded) {
        filtered.allAdded = allAdded.filter(a => !hiddenTargetIRIs.has(a.iri));
    }
    if (properties.added) {
        filtered.added = addedInClass.filter(a => !hiddenTargetIRIs.has(a.iri));
    }
    return filtered;
}

export function computeMappingType(oldResources, newResources) {
    if (!newResources || newResources.length === 0) return "UNMAPPED";
    if (oldResources.length > 1) return "MERGE";
    if (newResources.length > 1) return "SPLIT";

    const oldDomain = oldResources[0]?.domain ?? null;
    const newDomain = newResources[0]?.domain ?? null;
    return oldDomain === newDomain ? "RENAME" : "MOVE";
}

export function findSplitSiblingIris(classes, getProperties) {
    const byOldIRI = new Map();
    for (const cls of classes ?? []) {
        for (const property of getProperties(cls) ?? []) {
            const isRenameOrMove =
                property.semanticResourceChangeType === "RENAME" ||
                property.semanticResourceChangeType === "MOVE";
            if (
                isRenameOrMove &&
                property.oldIRI &&
                (property.additionalOldIRIs?.length ?? 0) === 0
            ) {
                const siblings = byOldIRI.get(property.oldIRI) ?? [];
                siblings.push(property.iri);
                byOldIRI.set(property.oldIRI, siblings);
            }
        }
    }

    const result = new Set();
    for (const siblings of byOldIRI.values()) {
        if (siblings.length > 1) {
            siblings.forEach(iri => result.add(iri));
        }
    }
    return result;
}

function oldLabelWithClass(resource) {
    if (!resource.domain) return resource.label;
    return `${resource.label} (in ${new URI(resource.domain).suffix})`;
}

export function buildRenamedFromMap(classes, getMappings) {
    const map = new Map();
    const seenMappingIds = new Set();
    if (!classes) return map;

    for (const cls of classes) {
        for (const mapping of getMappings(cls) ?? []) {
            if ((mapping.newResources?.length ?? 0) === 0) continue;
            if (seenMappingIds.has(mapping.id)) continue;
            seenMappingIds.add(mapping.id);

            const type = computeMappingType(
                mapping.oldResources,
                mapping.newResources,
            );
            const verb = RENAMED_FROM_VERBS[type] ?? RENAMED_FROM_VERBS.RENAME;
            const text = `${verb} ${mapping.oldResources.map(oldLabelWithClass).join(", ")}`;

            for (const target of mapping.newResources) {
                const existing = map.get(target.iri);
                map.set(target.iri, existing ? `${existing}; ${text}` : text);
            }
        }
    }
    return map;
}

function dedupeByIri(resources) {
    const seen = new Set();
    const result = [];
    for (const resource of resources) {
        if (resource?.iri && !seen.has(resource.iri)) {
            seen.add(resource.iri);
            result.push(resource);
        }
    }
    return result;
}

export function groupMappingsForSubmission(mappings) {
    const rows = (mappings ?? []).filter(
        m => (m.newResources?.length ?? 0) > 0,
    );
    if (rows.length === 0) {
        return { mappings: [], invalidMappingIds: new Set() };
    }

    const parent = new Map();
    function find(iri) {
        if (!parent.has(iri)) parent.set(iri, iri);
        let root = iri;
        while (parent.get(root) !== root) root = parent.get(root);
        let cur = iri;
        while (parent.get(cur) !== root) {
            const next = parent.get(cur);
            parent.set(cur, root);
            cur = next;
        }
        return root;
    }
    function union(a, b) {
        const rootA = find(a);
        const rootB = find(b);
        if (rootA !== rootB) parent.set(rootA, rootB);
    }

    for (const row of rows) {
        const iris = [
            ...row.oldResources.map(r => r.iri),
            ...row.newResources.map(r => r.iri),
        ];
        iris.forEach(iri => find(iri));
        for (let i = 1; i < iris.length; i++) {
            union(iris[0], iris[i]);
        }
    }

    const rowsByRoot = new Map();
    for (const row of rows) {
        const anchorIri = row.oldResources[0]?.iri ?? row.newResources[0]?.iri;
        const root = find(anchorIri);
        if (!rowsByRoot.has(root)) rowsByRoot.set(root, []);
        rowsByRoot.get(root).push(row);
    }

    const resultMappings = [];
    const invalidMappingIds = new Set();
    const mappingByRowId = new Map();

    for (const groupRows of rowsByRoot.values()) {
        const oldResources = dedupeByIri(
            groupRows.flatMap(r => r.oldResources),
        );
        const newResources = dedupeByIri(
            groupRows.flatMap(r => r.newResources),
        );

        if (oldResources.length > 1 && newResources.length > 1) {
            groupRows.forEach(r => invalidMappingIds.add(r.id));
            continue;
        }

        const merged =
            groupRows.length === 1
                ? {
                      ...groupRows[0],
                      type: computeMappingType(oldResources, newResources),
                  }
                : {
                      id: crypto.randomUUID(),
                      oldResources,
                      newResources,
                      confidenceScore: 1,
                      type: computeMappingType(oldResources, newResources),
                  };

        resultMappings.push(merged);
        groupRows.forEach(r => mappingByRowId.set(r.id, merged));
    }

    return { mappings: resultMappings, invalidMappingIds, mappingByRowId };
}

function hasChange(attribute, changeType) {
    return (
        attribute.changes?.some(
            c => c.semanticFieldChangeType === changeType,
        ) ?? false
    );
}

/**
 * A renamed datatype is deliberately not a datatype change: the backend reports it as
 * DATATYPE_RENAME and nothing about the stored values has to change.
 */
export function hasDataTypeChange(attribute) {
    return hasChange(attribute, "DATATYPE_CHANGE");
}

/**
 * Whether this attribute could keep its values across the datatype change, which is what the
 * equivalence option offers. It needs a datatype the attribute actually had before: a newly added
 * attribute also carries a DATATYPE_CHANGE, but from nothing, and has no existing values to keep.
 */
export function canKeepExistingValues(attribute) {
    if (
        attribute.semanticResourceChangeType !== "CHANGE" &&
        attribute.semanticResourceChangeType !== "RENAME"
    ) {
        return false;
    }
    return (
        attribute.changes?.some(
            c =>
                c.semanticFieldChangeType === "DATATYPE_CHANGE" &&
                Boolean(c.from),
        ) === true
    );
}

export function keepsExistingValues(attribute) {
    return (
        canKeepExistingValues(attribute) &&
        attribute.dataTypesEquivalent === true
    );
}

export function describeAttributeChange(attribute) {
    if (attribute.semanticResourceChangeType === "ADD") {
        return attribute.optional === true
            ? "Attribute added (optional)"
            : "Attribute added (required)";
    }
    if (attribute.semanticResourceChangeType === "ADDED_FROM_INHERITANCE") {
        return "Attribute newly inherited";
    }
    if (hasDataTypeChange(attribute)) {
        return keepsExistingValues(attribute)
            ? "Data type equivalent"
            : "Data type changed";
    }
    if (hasChange(attribute, "MADE_REQUIRED")) {
        return "Made required";
    }
    return "other";
}

export function allowsDefaultValueInput(attribute) {
    return (
        attribute.semanticResourceChangeType === "ADD" ||
        attribute.semanticResourceChangeType === "ADDED_FROM_INHERITANCE" ||
        (attribute.semanticResourceChangeType !== "DELETE" &&
            (hasChange(attribute, "MADE_REQUIRED") ||
                hasDataTypeChange(attribute)))
    );
}

export function requiresDefaultValue(attribute) {
    if (
        attribute.semanticResourceChangeType === "ADD" ||
        attribute.semanticResourceChangeType === "ADDED_FROM_INHERITANCE"
    ) {
        return !attribute.optional;
    }
    if (
        attribute.semanticResourceChangeType !== "CHANGE" &&
        attribute.semanticResourceChangeType !== "RENAME"
    ) {
        return false;
    }
    if (hasChange(attribute, "MADE_REQUIRED")) {
        return true;
    }
    return hasDataTypeChange(attribute) && !keepsExistingValues(attribute);
}

export function hasDefaultValue(attribute) {
    return Boolean(attribute.defaultValue?.trim());
}

export function skipsInitialization(attribute) {
    return requiresDefaultValue(attribute) && attribute.noDefaultValue === true;
}

export function isDefaultValueMissing(attribute) {
    return (
        requiresDefaultValue(attribute) &&
        !hasDefaultValue(attribute) &&
        !skipsInitialization(attribute)
    );
}
