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

export function isPrefixOnlyRename(oldIRI, newIRI) {
    if (!newIRI || !oldIRI) {
        return false;
    }
    const oldName = new URI(oldIRI).suffix;
    const newName = new URI(newIRI).suffix;
    return oldName === newName;
}

/**
 * Whether the user should be spared the rename mapping for this association. An association with
 * AssociationUsed = "No" cannot be instantiated, so no instance data ever used the old predicate
 * and there is nothing a mapping could rewrite. Judged on the old side, which is where the values
 * would have come from.
 */
export function isNonInstantiableAssociationRename(renameCandidate) {
    return renameCandidate?.oldResources?.[0]?.associationUsed === false;
}

/**
 * Drops the rename candidates the user should not be asked about from a property overview,
 * returning a copy: the caller keeps the unfiltered overview and still submits the hidden
 * candidates with the target that was detected for them.
 *
 * `alsoHide` adds a rule on top of the prefix-only one, which applies only when the migration was
 * started with "ignore prefixes".
 *
 * Handles both overview shapes: the class overview's `added`, and the property overview's
 * `addedInClass`/`allAdded` (the latter spans the whole inheritance chain). Auto-detected
 * candidates are always 1:1 at this point (a row only grows past one old/new resource once the
 * user links it in the UI), so filtering only has to look at the first old/new resource.
 */
export function filterRenameCandidates(
    properties,
    { ignorePrefixes = false, alsoHide } = {},
) {
    if (!properties) return properties;

    const deletedAndRemapped = properties.deletedAndRemapped ?? [];
    const addedInClass = properties.addedInClass ?? properties.added ?? [];
    const allAdded = properties.allAdded ?? addedInClass;

    const isHidden = mapping => {
        const oldIri = mapping.oldResources?.[0]?.iri;
        const newIri = mapping.newResources?.[0]?.iri;
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

/**
 * Classifies a mapping row from the cardinality of its two sides, mirroring the backend's own
 * `ResourceMapping` constructors so a freshly-assembled row is classified exactly the way the
 * server would have classified it, since the server trusts whatever `type` is submitted rather
 * than re-deriving it on the way in.
 *
 * A resource "moved" when it's linked 1:1 but its `domain` (the owning class IRI) differs between
 * the old and new side; classes have no `domain`, so a class-to-class link is always a rename.
 */
export function computeMappingType(oldResources, newResources) {
    if (!newResources || newResources.length === 0) return "UNMAPPED";
    if (oldResources.length > 1) return "MERGE";
    if (newResources.length > 1) return "SPLIT";

    const oldDomain = oldResources[0]?.domain ?? null;
    const newDomain = newResources[0]?.domain ?? null;
    return oldDomain === newDomain ? "RENAME" : "MOVE";
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

/**
 * Assembles the mapping rows a mapping table edited into the shape the backend expects for
 * submission: a genuine N:M `ResourceMapping` whenever two rows ended up sharing a resource (the
 * user merged them by linking two old resources to the same new one, or split one by linking it
 * to more than one new resource), found via connected components over every old/new resource IRI
 * the rows reference. Submitting repeated 1:1 mappings that merely share a target instead of one
 * real N:M mapping would make the backend attach the same result twice.
 *
 * Rows with no linked target are dropped (nothing to submit). A component that ends up with more
 * than one old AND more than one new resource at once can't be represented by the backend's model
 * (neither a pure merge nor a pure split) - such rows are excluded from `mappings` and returned in
 * `invalidMappingIds` so the UI can flag them and block submission until the user resolves them.
 */
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

/** True once the user declared the old and the new datatype interchangeable. */
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

/** Whether the step offers a default value for this attribute at all. */
export function allowsDefaultValueInput(attribute) {
    return (
        attribute.semanticResourceChangeType === "ADD" ||
        attribute.semanticResourceChangeType === "ADDED_FROM_INHERITANCE" ||
        (attribute.semanticResourceChangeType !== "DELETE" &&
            (hasChange(attribute, "MADE_REQUIRED") ||
                hasDataTypeChange(attribute)))
    );
}

/**
 * Whether a default value has to be given before the migration can continue. Marking the two
 * datatypes equivalent keeps every existing value, so the datatype change needs no default - but
 * an attribute that also became mandatory still needs one for the instances that lack a value.
 */
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
